package com.fusionjack.adhell3.fragments;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.fusionjack.adhell3.R;
import com.fusionjack.adhell3.adapter.BlockUrlProviderAdapter;
import com.fusionjack.adhell3.db.entity.BlockUrlProvider;
import com.fusionjack.adhell3.tasks.DomainRxTaskFactory;
import com.fusionjack.adhell3.utils.AdhellAppIntegrity;
import com.fusionjack.adhell3.utils.AdhellFactory;
import com.fusionjack.adhell3.utils.AppPreferences;
import com.fusionjack.adhell3.utils.FileUtils;
import com.fusionjack.adhell3.utils.LogUtils;
import com.fusionjack.adhell3.utils.rx.RxCompletableComputationBuilder;
import com.fusionjack.adhell3.utils.rx.RxCompletableIoBuilder;
import com.fusionjack.adhell3.utils.rx.RxSingleIoBuilder;
import com.fusionjack.adhell3.viewmodel.BlockUrlProvidersViewModel;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.functions.Action;
import toan.android.floatingactionmenu.FloatingActionButton;
import toan.android.floatingactionmenu.FloatingActionsMenu;
import toan.android.floatingactionmenu.ScrollDirectionListener;

import static com.fusionjack.adhell3.fragments.DomainTabPageFragment.PROVIDER_CONTENT_PAGE;

public class ProviderListFragment extends Fragment {
    private Context context;
    private FragmentActivity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.context = getContext();
        this.activity = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_provider, container, false);

        BlockUrlProvidersViewModel model = new ViewModelProvider(activity).get(BlockUrlProvidersViewModel.class);

        // Init URL limit
        TextView hintTextView = view.findViewById(R.id.providerInfoTextView);
        String strFormat = getResources().getString(R.string.provider_info);
        hintTextView.setText(String.format(strFormat, AdhellAppIntegrity.BLOCK_URL_LIMIT));

        // Init total domain count - LiveData
        TextView infoTextView = view.findViewById(R.id.infoTextView);
        initDomainCount(model, infoTextView);

        // Init provider list with an empty list - LiveData
        List<BlockUrlProvider> providerList = new ArrayList<>();
        BlockUrlProviderAdapter providerAdapter = new BlockUrlProviderAdapter(context, providerList);
        ListView providerListView = view.findViewById(R.id.providerListView);
        providerListView.setAdapter(providerAdapter);
        initProviderList(model, providerList, providerAdapter);

        providerListView.setOnItemClickListener((parent, view1, position, id) -> {
            BlockUrlProvider provider = (BlockUrlProvider) parent.getItemAtPosition(position);
            AppPreferences.getInstance().setCurrentProviderId(provider.id);

            TabLayout tabLayout = getParentFragment().getActivity().findViewById(R.id.sliding_tabs);
            if (tabLayout != null) {
                TabLayout.Tab tab = tabLayout.getTabAt(PROVIDER_CONTENT_PAGE);
                if (tab != null) {
                    tab.select();
                }
            }
        });

        SwipeRefreshLayout dnsSwipeContainer = view.findViewById(R.id.providerSwipeContainer);
        dnsSwipeContainer.setOnRefreshListener(() ->
                updateAllProviders(dnsSwipeContainer)
        );

        FloatingActionsMenu providerFloatMenu = view.findViewById(R.id.provider_actions);
        providerFloatMenu.attachToListView(providerListView, new ScrollDirectionListener() {
            @Override
            public void onScrollDown() {
                providerFloatMenu.setVisibleWithAnimation(true);
            }
            @Override
            public void onScrollUp() {
                providerFloatMenu.setVisibleWithAnimation(true);
            }
        });

        FloatingActionButton actionAddRemoteProvider = view.findViewById(R.id.action_add_remote_provider);
        actionAddRemoteProvider.setOnClickListener(v -> {
            providerFloatMenu.collapse();
            View dialogView = inflater.inflate(R.layout.dialog_add_remote_provider, container, false);
            new AlertDialog.Builder(context)
                    .setView(dialogView)
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                        EditText urlEditText = dialogView.findViewById(R.id.urlEditText);
                        String providerUrl = urlEditText.getText().toString();
                        if (URLUtil.isValidUrl(providerUrl)) {
                            addProvider(providerUrl);
                        } else {
                            Toast.makeText(getContext(), "Url is invalid", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton(android.R.string.no, null).show();
        });

        FloatingActionButton actionAddLocalProvider = view.findViewById(R.id.action_add_local_provider);
        actionAddLocalProvider.setOnClickListener(v -> {
            providerFloatMenu.collapse();

            View dialogView = inflater.inflate(R.layout.dialog_add_local_provider, container, false);
            EditText filePathEditText = dialogView.findViewById(R.id.filePathEditText);
            CheckBox checkBox = dialogView.findViewById(R.id.autoLoadCheckBox);
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> filePathEditText.setEnabled(!isChecked));

            new AlertDialog.Builder(context)
                    .setView(dialogView)
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                        if (checkBox.isChecked()) {
                            Consumer<Integer> callback = size -> {
                                if (size == 0) {
                                    Toast.makeText(getContext(), "No hosts file found!", Toast.LENGTH_LONG).show();
                                }
                            };
                            SingleOnSubscribe<Integer> source = emitter -> {
                                List<File> files = FileUtils.getHostsFiles();
                                files.forEach(file -> addProvider("file://" + file.getAbsolutePath()));
                                emitter.onSuccess(files.size());
                            };
                            new RxSingleIoBuilder().async(Single.create(source), callback);
                        } else {
                            Action action = () -> {
                                String filePath = filePathEditText.getText().toString();
                                if (!filePath.startsWith("file://")) filePath = "file://" + filePath;
                                addProvider(filePath);
                            };
                            new RxCompletableIoBuilder().async(Completable.fromAction(action));
                        }
                    })
                    .setNegativeButton(android.R.string.no, null).show();
        });

        return view;
    }

    void safeGuardLiveData(Runnable action) {
        if (getView() == null) {
            LogUtils.error("View is null");
            return;
        }
        action.run();
    }

    private void initProviderList(BlockUrlProvidersViewModel model, List<BlockUrlProvider> providerList, BlockUrlProviderAdapter providerAdapter) {
        Consumer<LiveData<List<BlockUrlProvider>>> callback = liveData -> {
            safeGuardLiveData(() -> {
                liveData.observe(getViewLifecycleOwner(), _providerList -> {
                    providerList.clear();
                    providerList.addAll(_providerList);
                    providerAdapter.notifyDataSetChanged();
                });
            });
        };
        new RxSingleIoBuilder().async(model.getBlockUrlProviders(), callback);
    }

    private void initDomainCount(BlockUrlProvidersViewModel model, TextView infoTextView) {
        Consumer<LiveData<Integer>> callback = liveData -> {
            safeGuardLiveData(() -> {
                liveData.observe(getViewLifecycleOwner(), count -> {
                    String strFormat = context.getResources().getString(R.string.total_unique_domains);
                    infoTextView.setText(String.format(strFormat, count));
                });
            });
        };
        new RxSingleIoBuilder().async(model.getDomainCount(), callback);
    }

    private void addProvider(String providerUrl) {
        Consumer<BlockUrlProvider> callback = provider -> {
            Runnable onCompleteCallback = () -> Toast.makeText(getContext(), "Hosts provider has been loaded", Toast.LENGTH_LONG).show();
            Runnable onErrorCallback = () -> new RxCompletableIoBuilder().async(DomainRxTaskFactory.deleteProvider(provider));
            new RxCompletableComputationBuilder()
                    .setShowDialog("Loading hosts provider ...", getContext())
                    .async(DomainRxTaskFactory.loadProvider(provider), () -> {}, onCompleteCallback, onErrorCallback);
        };

        new RxSingleIoBuilder().async(DomainRxTaskFactory.addProvider(providerUrl), callback);
    }

    private void updateAllProviders(SwipeRefreshLayout swipeContainer) {
        ProgressDialog dialog = new ProgressDialog(context);
        boolean hasInternetAccess = AdhellFactory.getInstance().hasInternetAccess(context);
        if (!hasInternetAccess) {
            swipeContainer.setRefreshing(false);
            Toast.makeText(getContext(), "There is no internet connection", Toast.LENGTH_LONG).show();
        } else {
            Runnable onSubscribeCallback = () -> {
                swipeContainer.setRefreshing(false);
                dialog.setMessage("Updating providers ...");
                dialog.show();
            };
            Runnable dismissCallback = dialog::dismiss;
            new RxCompletableComputationBuilder().async(DomainRxTaskFactory.updateAllProviders(), onSubscribeCallback, dismissCallback, dismissCallback);
        }
    }

}
