package com.fusionjack.adhell3.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fusionjack.adhell3.R;
import com.fusionjack.adhell3.model.ProviderInfo;
import com.fusionjack.adhell3.model.IComponentInfo;
import com.fusionjack.adhell3.utils.AdhellFactory;
import com.fusionjack.adhell3.utils.AppComponentFactory;
import com.fusionjack.adhell3.utils.AppPreferences;
import com.fusionjack.adhell3.utils.rx.RxCompletableIoBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;

public class ProviderInfoAdapter extends ComponentAdapter {

    private final boolean toggleIsEnabled;

    public ProviderInfoAdapter(@NonNull Context context, @NonNull List<IComponentInfo> componentInfos) {
        super(context, componentInfos);
        this.toggleIsEnabled = AppPreferences.getInstance().isAppComponentToggleEnabled();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_provider_info, parent, false);
        }

        IComponentInfo componentInfo = getItem(position);
        if (componentInfo == null) {
            return convertView;
        }

        if (componentInfo instanceof ProviderInfo) {
            ProviderInfo providerInfo = (ProviderInfo) componentInfo;

            TextView providerNameTextView = convertView.findViewById(R.id.providerNameTextView);
            TextView providerPackageTextView = convertView.findViewById(R.id.providerPackageTextView);
            SwitchMaterial permissionSwitch = convertView.findViewById(R.id.switchDisable);

            String providerName = providerInfo.getName();
            providerNameTextView.setText(getNamePart(providerName));
            providerPackageTextView.setText(getPackagePart(providerName));

            String packageName = providerInfo.getPackageName();
            boolean state = AdhellFactory.getInstance().getComponentState(packageName, providerName);
            if (!state) {
                Completable action = Completable.fromAction(() -> AppComponentFactory.getInstance().addProviderToDatabaseIfNotExist(packageName, providerName));
                new RxCompletableIoBuilder().async(action);
            }
            permissionSwitch.setChecked(state);
            permissionSwitch.setEnabled(toggleIsEnabled);
        }

        return convertView;
    }

}
