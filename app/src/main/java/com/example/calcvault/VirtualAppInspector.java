package com.example.calcvault;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import java.io.File;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Inspects a copied APK and produces the component map needed by the
 * virtual-app launcher. It does not install or execute the copied APK.
 */
public final class VirtualAppInspector {
    public static final class Inspection {
        public final String packageName;
        public final String applicationClass;
        public final String launchActivity;
        public final int activities;
        public final int services;
        public final int receivers;
        public final int providers;

        Inspection(String packageName, String applicationClass, String launchActivity,
                   int activities, int services, int receivers, int providers) {
            this.packageName = packageName;
            this.applicationClass = applicationClass;
            this.launchActivity = launchActivity;
            this.activities = activities;
            this.services = services;
            this.receivers = receivers;
            this.providers = providers;
        }
    }

    private final Context context;

    public VirtualAppInspector(Context context) {
        this.context = context.getApplicationContext();
    }

    public Inspection inspect(VirtualAppContainer.Instance instance) throws IOException {
        if (instance == null || instance.apkFile == null || !instance.apkFile.isFile()) {
            throw new IOException("Virtual APK is missing");
        }

        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(
                instance.apkFile.getAbsolutePath(),
                PackageManager.GET_ACTIVITIES
                        | PackageManager.GET_SERVICES
                        | PackageManager.GET_RECEIVERS
                        | PackageManager.GET_PROVIDERS
                        | PackageManager.GET_META_DATA);

        if (info == null || info.applicationInfo == null) {
            throw new IOException("APK manifest could not be inspected");
        }

        String launch = null;
        int activityCount = info.activities == null ? 0 : info.activities.length;
        if (info.activities != null) {
            for (ActivityInfo a : info.activities) {
                if (a != null && a.exported) {
                    launch = a.name;
                    break;
                }
            }
        }

        int serviceCount = info.services == null ? 0 : info.services.length;
        int receiverCount = info.receivers == null ? 0 : info.receivers.length;
        int providerCount = info.providers == null ? 0 : info.providers.length;

        String appClass = info.applicationInfo.className;
        if (appClass == null || appClass.trim().isEmpty()) {
            appClass = "android.app.Application";
        }

        Inspection result = new Inspection(
                info.packageName,
                appClass,
                launch,
                activityCount,
                serviceCount,
                receiverCount,
                providerCount);

        writeInspection(instance, result);
        return result;
    }

    private void writeInspection(VirtualAppContainer.Instance instance, Inspection i)
            throws IOException {
        try {
            JSONObject json = new JSONObject();
            json.put("packageName", i.packageName);
            json.put("applicationClass", i.applicationClass);
            json.put("launchActivity", i.launchActivity == null ? "" : i.launchActivity);
            json.put("activities", i.activities);
            json.put("services", i.services);
            json.put("receivers", i.receivers);
            json.put("providers", i.providers);
            json.put("inspectedAt", System.currentTimeMillis());

            java.io.FileOutputStream out =
                    new java.io.FileOutputStream(new File(instance.root, "inspection.json"));
            try {
                out.write(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
        } catch (Exception e) {
            throw new IOException("Could not save virtual APK inspection", e);
        }
    }
}
