package com.fusionjack.adhell3.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.fusionjack.adhell3.R;
import com.fusionjack.adhell3.adapter.ComponentPagerAdapter;
import com.google.android.material.tabs.TabLayout;

public class ComponentTabFragment extends TabFragment {

    private final int[] imageResId = {
            R.drawable.ic_permission,
            R.drawable.ic_activity,
            R.drawable.ic_service,
            R.drawable.ic_receiver,
            R.drawable.ic_provider
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        String packageName = "";
        String appName = "";
        boolean isDisabledComponentMode = false;
        int[] pages = new int[0];

        Bundle bundle = getArguments();
        if (bundle != null) {
            packageName = bundle.getString("packageName");
            appName = bundle.getString("appName");
            isDisabledComponentMode = bundle.getBoolean("isDisabledComponentMode");
            pages = bundle.getIntArray("pages");
        }

        getActivity().setTitle(appName.isEmpty() ? "App Component" : appName);
        AppCompatActivity parentActivity = (AppCompatActivity) getActivity();
        if (parentActivity.getSupportActionBar() != null) {
            parentActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            parentActivity.getSupportActionBar().setHomeButtonEnabled(true);
            parentActivity.getSupportActionBar().setDisplayShowCustomEnabled(false);
        }

        ComponentPagerAdapter adapter = new ComponentPagerAdapter(getChildFragmentManager(), getContext(), packageName, isDisabledComponentMode, pages);

        View view = inflateFragment(R.layout.fragment_app_component_tabs, inflater, container, adapter, 1, imageResId, 0);

        TabLayout tabLayout = view.findViewById(R.id.sliding_tabs);
        TabLayout.Tab tab = tabLayout.getTabAt(0);
        if (tab != null) {
            tab.select();
        }
        return view;
    }

}
