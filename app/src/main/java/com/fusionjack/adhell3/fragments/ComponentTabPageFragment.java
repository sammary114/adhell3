package com.fusionjack.adhell3.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.fusionjack.adhell3.R;
import com.fusionjack.adhell3.adapter.ActivityInfoAdapter;
import com.fusionjack.adhell3.adapter.ComponentAdapter;
import com.fusionjack.adhell3.adapter.PermissionInfoAdapter;
import com.fusionjack.adhell3.adapter.ProviderInfoAdapter;
import com.fusionjack.adhell3.adapter.ReceiverInfoAdapter;
import com.fusionjack.adhell3.adapter.ServiceInfoAdapter;
import com.fusionjack.adhell3.db.entity.AppPermission;
import com.fusionjack.adhell3.model.AppComponent;
import com.fusionjack.adhell3.model.IComponentInfo;
import com.fusionjack.adhell3.model.ReceiverInfo;
import com.fusionjack.adhell3.utils.AppComponentFactory;
import com.fusionjack.adhell3.utils.AppPreferences;
import com.fusionjack.adhell3.utils.dialog.QuestionDialogBuilder;
import com.fusionjack.adhell3.utils.LogUtils;
import com.fusionjack.adhell3.utils.UiUtils;
import com.fusionjack.adhell3.utils.rx.RxCompletableIoBuilder;
import com.fusionjack.adhell3.utils.rx.RxSingleComputationBuilder;
import com.fusionjack.adhell3.utils.rx.RxSingleIoBuilder;
import com.fusionjack.adhell3.viewmodel.AppComponentViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.functions.Action;

import static com.fusionjack.adhell3.db.entity.AppPermission.STATUS_ACTIVITY;
import static com.fusionjack.adhell3.db.entity.AppPermission.STATUS_PERMISSION;
import static com.fusionjack.adhell3.db.entity.AppPermission.STATUS_PROVIDER;
import static com.fusionjack.adhell3.db.entity.AppPermission.STATUS_RECEIVER;
import static com.fusionjack.adhell3.db.entity.AppPermission.STATUS_SERVICE;

public class ComponentTabPageFragment extends Fragment {

    private static final String ARG_PAGE = "page";
    private static final String ARG_PACKAGE_NAME = "packageName";
    private static final String ARG_DISABLED_COMPONENT_MODE = "isDisabledComponentMode";

    private static final int UNKNOWN_PAGE = -1;
    private static final int PERMISSIONS_PAGE = 0;
    private static final int ACTIVITIES_PAGE = 1;
    private static final int SERVICES_PAGE = 2;
    private static final int RECEIVERS_PAGE = 3;
    private static final int PROVIDERS_PAGE = 4;

    private int pageId;
    private String packageName;
    private boolean isDisabledComponentMode;

    private List<IComponentInfo> adapterAppComponentList;
    private List<IComponentInfo> initialAppComponentList;
    private ComponentAdapter adapter;

    public static ComponentTabPageFragment newInstance(int page, String packageName, boolean isDisabledComponentMode) {
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);
        args.putString(ARG_PACKAGE_NAME, packageName);
        args.putBoolean(ARG_DISABLED_COMPONENT_MODE, isDisabledComponentMode);
        ComponentTabPageFragment fragment = new ComponentTabPageFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static int[] toPages(List<Integer> componentType) {
        LogUtils.info("componentType from database: " + componentType);
        return componentType.stream()
                .mapToInt(type -> {
                    switch (type) {
                        case STATUS_PERMISSION:
                            return PERMISSIONS_PAGE;
                        case STATUS_ACTIVITY:
                            return ACTIVITIES_PAGE;
                        case STATUS_SERVICE:
                            return SERVICES_PAGE;
                        case STATUS_RECEIVER:
                            return RECEIVERS_PAGE;
                        case STATUS_PROVIDER:
                            return PROVIDERS_PAGE;
                        default:
                            return UNKNOWN_PAGE;
                    }})
                .filter(page -> page != UNKNOWN_PAGE)
                .sorted()
                .toArray();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Optional.ofNullable(getArguments()).ifPresent(bundle -> {
            this.pageId = bundle.getInt(ARG_PAGE);
            this.packageName = bundle.getString(ARG_PACKAGE_NAME);
            this.isDisabledComponentMode = bundle.getBoolean(ARG_DISABLED_COMPONENT_MODE);
        });
        this.initialAppComponentList = Collections.emptyList();
        this.adapterAppComponentList = new ArrayList<>();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return AppComponentPage.toAppComponentPage(pageId).map(page -> {
            View view = inflater.inflate(page.layoutId, container, false);

            Optional<ListView> listViewOpt = Optional.ofNullable(view.findViewById(page.listViewId));
            listViewOpt.ifPresent(listView -> {
                page.getAdapter(getContext(), adapterAppComponentList).ifPresent(adapter -> {
                    this.adapter = adapter;
                    listView.setAdapter(adapter);

                    boolean toggleEnabled = AppPreferences.getInstance().isAppComponentToggleEnabled();
                    if (toggleEnabled) {
                        listView.setOnItemClickListener((AdapterView<?> adView, View view2, int position, long id) -> {
                            Action action = () -> page.toggleAppComponent(packageName, adapter.getItem(position));
                            new RxCompletableIoBuilder().async(Completable.fromAction(action));
                        });
                        listView.setOnItemLongClickListener((parent, view1, position, id) -> {
                            if (page.pageId == PERMISSIONS_PAGE) {
                                return true;
                            }
                            String componentName = adapter.getItem(position).getName();
                            Action action = () -> page.appendComponentNameToFile(componentName);
                            String message = "'" + adapter.getNamePart(componentName) + "' is inserted to file";
                            Runnable callback = () -> Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                            new RxCompletableIoBuilder()
                                    .showErrorAlert(getContext())
                                    .async(Completable.fromAction(action), callback);
                            return true;
                        });
                    }

                    Consumer<LiveData<List<AppPermission>>> callback = liveData -> {
                        safeGuardLiveData(() -> {
                            liveData.observe(getViewLifecycleOwner(), dbAppComponentList -> {
                                if (initialAppComponentList.isEmpty()) {
                                    if (isDisabledComponentMode) {
                                        initialAppComponentList = page.convertDbAppComponentList(packageName, dbAppComponentList);
                                    } else {
                                        initialAppComponentList = page.combineAppComponentList(packageName, dbAppComponentList);
                                    }
                                    adapterAppComponentList.addAll(initialAppComponentList);
                                }
                                adapter.notifyDataSetChanged();
                            });
                        });
                    };

                    AppComponentViewModel viewModel = new ViewModelProvider(this).get(AppComponentViewModel.class);
                    page.getAppComponentList(viewModel, packageName).ifPresent(action ->
                            new RxSingleComputationBuilder().async(action, callback)
                    );
                });
            });

            return view;
        }).orElse(null);
    }

    private void safeGuardLiveData(Runnable action) {
        if (getView() == null) {
            LogUtils.error("View is null");
            return;
        }
        action.run();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (menu.size() == 0) {
            inflater.inflate(R.menu.appcomponent_menu, menu);
            UiUtils.setMenuIconColor(menu, getContext());
            initSearchView(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_enable_all) {
            enableAllComponents();
        }
        return super.onOptionsItemSelected(item);
    }

    private void enableAllComponents() {
        AppComponentPage.toAppComponentPage(pageId).ifPresent(page -> {
            Runnable onPositiveButton = () -> {
                Action action = () -> page.enableAppComponents(packageName);
                new RxCompletableIoBuilder().async(Completable.fromAction(action));
            };

            String titlePlaceholder = getResources().getString(R.string.enable_apps_component_dialog_title);
            String title = String.format(titlePlaceholder, page.getName());
            String questionPlaceholder = getResources().getString(R.string.enable_apps_component_dialog_text);
            String question = String.format(questionPlaceholder, page.getName());

            new QuestionDialogBuilder(getView())
                    .setTitle(title)
                    .setQuestion(question)
                    .show(onPositiveButton);
        });
    }

    private void initSearchView(Menu menu) {
        SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String text) {
                if (text.isEmpty()) {
                    updateAppComponentList(initialAppComponentList);
                } else {
                    SingleOnSubscribe<List<IComponentInfo>> source = emitter -> {
                        List<IComponentInfo> filteredList = initialAppComponentList.stream()
                                .filter(componentInfo -> {
                                    String componentName = componentInfo.getName().toLowerCase();
                                    return componentName.contains(text.toLowerCase());
                                })
                                .collect(Collectors.toList());
                        emitter.onSuccess(filteredList);
                    };
                    new RxSingleIoBuilder().async(Single.create(source), list -> updateAppComponentList(list));
                }
                return false;
            }
        });
        UiUtils.setSearchIconColor(searchView, getContext());
    }

    private void updateAppComponentList(List<IComponentInfo> list) {
        if (adapter != null) {
            adapterAppComponentList.clear();
            adapterAppComponentList.addAll(list);
            adapter.notifyDataSetChanged();
        }
    }

    private static class AppComponentPage {
        @LayoutRes private int layoutId;
        @IdRes private int listViewId;
        private final int pageId;

        AppComponentPage(int pageId) {
            this.pageId = pageId;
        }

        static Optional<AppComponentPage> toAppComponentPage(int pageId) {
            AppComponentPage page = null;
            switch (pageId) {
                case PERMISSIONS_PAGE:
                    page = createPermissionPage(pageId);
                    break;
                case ACTIVITIES_PAGE:
                    page = createActivityPage(pageId);
                    break;
                case SERVICES_PAGE:
                    page = createServicePage(pageId);
                    break;
                case RECEIVERS_PAGE:
                    page = createReceiverPage(pageId);
                    break;
                case PROVIDERS_PAGE:
                    page = createProviderPage(pageId);
            }
            return Optional.ofNullable(page);
        }

        private static AppComponentPage createPermissionPage(int pageId) {
            AppComponentPage page = new AppComponentPage(pageId);
            page.layoutId = R.layout.fragment_app_permission;
            page.listViewId = R.id.permissionInfoListView;
            return page;
        }

        private static AppComponentPage createActivityPage(int pageId) {
            AppComponentPage page = new AppComponentPage(pageId);
            page.layoutId = R.layout.fragment_app_activity;
            page.listViewId = R.id.activityInfoListView;
            return page;
        }

        private static AppComponentPage createServicePage(int pageId) {
            AppComponentPage page = new AppComponentPage(pageId);
            page.layoutId = R.layout.fragment_app_service;
            page.listViewId = R.id.serviceInfoListView;
            return page;
        }

        private static AppComponentPage createReceiverPage(int pageId) {
            AppComponentPage page = new AppComponentPage(pageId);
            page.layoutId = R.layout.fragment_app_receiver;
            page.listViewId = R.id.receiverInfoListView;
            return page;
        }

        private static AppComponentPage createProviderPage(int pageId) {
            AppComponentPage page = new AppComponentPage(pageId);
            page.layoutId = R.layout.fragment_app_provider;
            page.listViewId = R.id.providerInfoListView;
            return page;
        }

        String getName() {
            switch (pageId) {
                case PERMISSIONS_PAGE:
                    return "permissions";
                case ACTIVITIES_PAGE:
                    return "activities";
                case SERVICES_PAGE:
                    return "services";
                case RECEIVERS_PAGE:
                    return "receivers";
                case PROVIDERS_PAGE:
                    return "content providers";
            }
            return "";
        }

        Optional<ComponentAdapter> getAdapter(Context context, List<IComponentInfo> list) {
            ComponentAdapter adapter = null;
            switch (pageId) {
                case PERMISSIONS_PAGE:
                    adapter = new PermissionInfoAdapter(context, list);
                    break;
                case ACTIVITIES_PAGE:
                    adapter = new ActivityInfoAdapter(context, list);
                    break;
                case SERVICES_PAGE:
                    adapter = new ServiceInfoAdapter(context, list);
                    break;
                case RECEIVERS_PAGE:
                    adapter = new ReceiverInfoAdapter(context, list);
                    break;
                case PROVIDERS_PAGE:
                    adapter = new ProviderInfoAdapter(context, list);
                    break;
            }
            return Optional.ofNullable(adapter);
        }

        Optional<Single<LiveData<List<AppPermission>>>> getAppComponentList(AppComponentViewModel viewModel, String packageName) {
            Single<LiveData<List<AppPermission>>> observable = null;
            switch (pageId) {
                case PERMISSIONS_PAGE:
                    observable = viewModel.getPermissions(packageName);
                    break;
                case ACTIVITIES_PAGE:
                    observable = viewModel.getActivities(packageName);
                    break;
                case SERVICES_PAGE:
                    observable = viewModel.getServices(packageName);
                    break;
                case RECEIVERS_PAGE:
                    observable = viewModel.getReceivers(packageName);
                    break;
                case PROVIDERS_PAGE:
                    observable = viewModel.getProviders(packageName);
            }
            return Optional.ofNullable(observable);
        }

        void appendComponentNameToFile(String componentName) throws Exception {
            switch (pageId) {
                case ACTIVITIES_PAGE:
                    AppComponentFactory.getInstance().appendActivityNameToFile(componentName);
                    break;
                case SERVICES_PAGE:
                    AppComponentFactory.getInstance().appendServiceNameToFile(componentName);
                    break;
                case RECEIVERS_PAGE:
                    AppComponentFactory.getInstance().appendReceiverNameToFile(componentName);
                    break;
                case PROVIDERS_PAGE:
                    AppComponentFactory.getInstance().appendProviderNameToFile(componentName);
                    break;
            }
        }

        void toggleAppComponent(String packageName, IComponentInfo info) {
            switch (pageId) {
                case PERMISSIONS_PAGE:
                    AppComponentFactory.getInstance().togglePermissionState(packageName, info.getName());
                    break;
                case ACTIVITIES_PAGE:
                    AppComponentFactory.getInstance().toggleActivityState(packageName, info.getName());
                    break;
                case SERVICES_PAGE:
                    AppComponentFactory.getInstance().toggleServiceState(packageName, info.getName());
                    break;
                case RECEIVERS_PAGE:
                    ReceiverInfo receiverInfo = (ReceiverInfo) info;
                    AppComponentFactory.getInstance().toggleReceiverState(packageName, receiverInfo.getName(), receiverInfo.getPermission());
                    break;
                case PROVIDERS_PAGE:
                    AppComponentFactory.getInstance().toggleProviderState(packageName, info.getName());
                    break;
            }
        }

        void enableAppComponents(String packageName) {
            switch (pageId) {
                case PERMISSIONS_PAGE:
                    AppComponentFactory.getInstance().enablePermissions(packageName);
                    break;
                case ACTIVITIES_PAGE:
                    AppComponentFactory.getInstance().enableActivities(packageName);
                    break;
                case SERVICES_PAGE:
                    AppComponentFactory.getInstance().enableServices(packageName);
                    break;
                case RECEIVERS_PAGE:
                    AppComponentFactory.getInstance().enableReceivers(packageName);
                    break;
                case PROVIDERS_PAGE:
                    AppComponentFactory.getInstance().enableProviders(packageName);
            }
        }

        List<IComponentInfo> combineAppComponentList(String packageName, List<AppPermission> dbAppComponentList) {
            switch (pageId) {
                case PERMISSIONS_PAGE:
                    return AppComponent.getPermissions(packageName);
                case ACTIVITIES_PAGE:
                    return AppComponent.combineActivitiesList(packageName, dbAppComponentList);
                case SERVICES_PAGE:
                    return AppComponent.combineServicesList(packageName, dbAppComponentList);
                case RECEIVERS_PAGE:
                    return AppComponent.combineReceiversList(packageName, dbAppComponentList);
                case PROVIDERS_PAGE:
                    return AppComponent.combineProvidersList(packageName, dbAppComponentList);
                default:
                    return Collections.emptyList();
            }
        }

        List<IComponentInfo> convertDbAppComponentList(String packageName, List<AppPermission> dbAppComponentList) {
            switch (pageId) {
                case PERMISSIONS_PAGE:
                    return AppComponent.toPermissionInfoList(packageName, dbAppComponentList);
                case ACTIVITIES_PAGE:
                    return AppComponent.toActivityInfoList(packageName, dbAppComponentList);
                case SERVICES_PAGE:
                    return AppComponent.toServiceInfoList(packageName, dbAppComponentList);
                case RECEIVERS_PAGE:
                    return AppComponent.toReceiverInfoList(packageName, dbAppComponentList);
                case PROVIDERS_PAGE:
                    return AppComponent.toProviderInfoList(packageName, dbAppComponentList);
                default:
                    return Collections.emptyList();
            }
        }
    }

}
