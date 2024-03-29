package com.fusionjack.adhell3.utils;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.fusionjack.adhell3.App;
import com.fusionjack.adhell3.BuildConfig;
import com.fusionjack.adhell3.db.AppDatabase;
import com.fusionjack.adhell3.db.entity.AppInfo;
import com.fusionjack.adhell3.db.entity.BlockUrl;
import com.fusionjack.adhell3.db.entity.BlockUrlProvider;
import com.fusionjack.adhell3.db.entity.DisabledPackage;
import com.fusionjack.adhell3.db.entity.FirewallWhitelistedPackage;
import com.fusionjack.adhell3.db.entity.PolicyPackage;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AdhellAppIntegrity {
    public static final String ADHELL_STANDARD_PACKAGE = BuildConfig.DEFAULT_HOST;
    public static final int BLOCK_URL_LIMIT = BuildConfig.DOMAIN_LIMIT;
    public final static String DEFAULT_POLICY_ID = "default-policy";

    private static final String DEFAULT_POLICY_CHECKED = "adhell_default_policy_created";
    private static final String DISABLED_PACKAGES_MOVED = "adhell_disabled_packages_moved";
    private static final String FIREWALL_WHITELISTED_PACKAGES_MOVED = "adhell_firewall_whitelisted_packages_moved";
    private static final String MOVE_APP_PERMISSIONS = "adhell_app_permissions_moved";
    private static final String DEFAULT_PACKAGES_FIREWALL_WHITELISTED = "adhell_default_packages_firewall_whitelisted";
    private static final String CHECK_ADHELL_STANDARD_PACKAGE = "adhell_adhell_standard_package";
    private static final String CHECK_PACKAGE_DB = "adhell_packages_filled_db";

    private AppDatabase appDatabase;
    private SharedPreferences sharedPreferences;

    private static AdhellAppIntegrity instance;

    private AdhellAppIntegrity() {
        this.appDatabase = AdhellFactory.getInstance().getAppDatabase();
        this.sharedPreferences = AdhellFactory.getInstance().getSharedPreferences();
    }

    public static AdhellAppIntegrity getInstance() {
        if (instance == null) {
            instance = new AdhellAppIntegrity();
        }
        return instance;
    }

    public void checkDefaultPolicyExists() {
        PolicyPackage policyPackage = appDatabase.policyPackageDao().getPolicyById(DEFAULT_POLICY_ID);
        if (policyPackage != null) {
            LogUtils.info( "Default PolicyPackage exists");
            return;
        }
        LogUtils.info( "Default PolicyPackage does not exist. Creating default policy.");
        policyPackage = new PolicyPackage();
        policyPackage.id = DEFAULT_POLICY_ID;
        policyPackage.name = "Default Policy";
        policyPackage.description = "Automatically generated policy from current Adhell app settings";
        policyPackage.active = true;
        policyPackage.createdAt = policyPackage.updatedAt = new Date();
        appDatabase.policyPackageDao().insert(policyPackage);
        LogUtils.info( "Default PolicyPackage has been added");
    }

    private void copyDataFromAppInfoToDisabledPackage() {
        List<DisabledPackage> disabledPackages = appDatabase.disabledPackageDao().getAll();
        if (disabledPackages.size() > 0) {
            LogUtils.info( "DisabledPackages is not empty. No need to move data from AppInfo table");
            return;
        }
        List<AppInfo> disabledApps = appDatabase.applicationInfoDao().getDisabledApps();
        if (disabledApps.size() == 0) {
            LogUtils.info( "No disabledgetDisabledApps apps in AppInfo table");
            return;
        }
        LogUtils.info( "There is " + disabledApps.size() + " to move to DisabledPackage table");
        disabledPackages = new ArrayList<>();
        for (AppInfo appInfo : disabledApps) {
            DisabledPackage disabledPackage = new DisabledPackage();
            disabledPackage.packageName = appInfo.packageName;
            disabledPackage.policyPackageId = DEFAULT_POLICY_ID;
            disabledPackages.add(disabledPackage);
        }
        appDatabase.disabledPackageDao().insertAll(disabledPackages);
    }

    private void copyDataFromAppInfoToFirewallWhitelistedPackage() {
        List<FirewallWhitelistedPackage> firewallWhitelistedPackages
                = appDatabase.firewallWhitelistedPackageDao().getAll();
        if (firewallWhitelistedPackages.size() > 0) {
            LogUtils.info( "FirewallWhitelist package size is: " + firewallWhitelistedPackages.size() + ". No need to move data");
            return;
        }
        List<AppInfo> whitelistedApps = appDatabase.applicationInfoDao().getWhitelistedApps();
        if (whitelistedApps.size() == 0) {
            LogUtils.info( "No whitelisted apps in AppInfo table");
            return;
        }
        LogUtils.info( "There is " + whitelistedApps.size() + " to move");
        firewallWhitelistedPackages = new ArrayList<>();
        for (AppInfo appInfo : whitelistedApps) {
            FirewallWhitelistedPackage whitelistedPackage = new FirewallWhitelistedPackage();
            whitelistedPackage.packageName = appInfo.packageName;
            whitelistedPackage.policyPackageId = DEFAULT_POLICY_ID;
            firewallWhitelistedPackages.add(whitelistedPackage);
        }
        appDatabase.firewallWhitelistedPackageDao().insertAll(firewallWhitelistedPackages);
    }

    private void addDefaultAdblockWhitelist() {
        List<FirewallWhitelistedPackage> firewallWhitelistedPackages = new ArrayList<>();
        firewallWhitelistedPackages.add(new FirewallWhitelistedPackage("com.google.android.music", DEFAULT_POLICY_ID));
        firewallWhitelistedPackages.add(new FirewallWhitelistedPackage("com.google.android.apps.fireball", DEFAULT_POLICY_ID));
        firewallWhitelistedPackages.add(new FirewallWhitelistedPackage("com.nttdocomo.android.ipspeccollector2", DEFAULT_POLICY_ID));
        appDatabase.firewallWhitelistedPackageDao().insertAll(firewallWhitelistedPackages);
    }

    public void checkAdhellStandardPackage() {
        BlockUrlProvider blockUrlProvider = appDatabase.blockUrlProviderDao().getByUrl(ADHELL_STANDARD_PACKAGE);
        if (blockUrlProvider != null) {
            return;
        }

        // Remove existing default
        if (appDatabase.blockUrlProviderDao().getDefaultSize() > 0) {
            appDatabase.blockUrlProviderDao().deleteDefault();
        }

        // Add the default package
        blockUrlProvider = new BlockUrlProvider();
        blockUrlProvider.url = ADHELL_STANDARD_PACKAGE;
        blockUrlProvider.lastUpdated = null;
        blockUrlProvider.deletable = false;
        blockUrlProvider.selected = true;
        blockUrlProvider.policyPackageId = DEFAULT_POLICY_ID;
        long[] ids = appDatabase.blockUrlProviderDao().insertAll(blockUrlProvider);
        blockUrlProvider.id = ids[0];
        List<BlockUrl> blockUrls;
        try {
            blockUrls = BlockUrlUtils.loadBlockUrls(blockUrlProvider);
            blockUrlProvider.count = blockUrls.size();
            blockUrlProvider.lastUpdated = new Date();
            LogUtils.info( "Number of urls to insert: " + blockUrlProvider.count);
            // Save url provider
            appDatabase.blockUrlProviderDao().updateBlockUrlProviders(blockUrlProvider);
            // Save urls from providers
            appDatabase.blockUrlDao().insertAll(blockUrls);
        } catch (Exception e) {
            LogUtils.error( e.getMessage(), e);
        }
    }

    public boolean isPackageDbEmpty() {
        return appDatabase.applicationInfoDao().getAppSize() == 0;
    }
}
