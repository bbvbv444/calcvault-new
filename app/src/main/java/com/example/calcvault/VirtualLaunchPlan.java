package com.example.calcvault;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import java.io.IOException;

/**
 * Builds the launch description for a virtual app without asking Android to
 * install the copied package. The actual process/component virtualization is
 * a later runtime layer.
 */
public final class VirtualLaunchPlan {
    public final VirtualAppContainer.Instance instance;
    public final VirtualAppInspector.Inspection inspection;
    public final ComponentName virtualActivity;
    public final Intent sourceIntent;

    private VirtualLaunchPlan(
            VirtualAppContainer.Instance instance,
            VirtualAppInspector.Inspection inspection,
            ComponentName virtualActivity,
            Intent sourceIntent) {
        this.instance = instance;
        this.inspection = inspection;
        this.virtualActivity = virtualActivity;
        this.sourceIntent = sourceIntent;
    }

    public static VirtualLaunchPlan create(
            Context context,
            VirtualAppContainer.Instance instance,
            VirtualAppInspector.Inspection inspection) throws IOException {
        if (instance == null) throw new IOException("Virtual instance missing");
        if (inspection == null) throw new IOException("APK inspection missing");
        if (inspection.launchActivity == null || inspection.launchActivity.trim().isEmpty()) {
            throw new IOException("No launchable activity found");
        }

        ComponentName component = new ComponentName(
                context.getPackageName(),
                "VirtualActivityProxy");

        Intent source = new Intent(Intent.ACTION_MAIN);
        source.addCategory(Intent.CATEGORY_LAUNCHER);
        source.setComponent(new ComponentName(
                inspection.packageName,
                inspection.launchActivity));
        source.putExtra("calcvault_virtual_clone_id", instance.clone.id);
        source.putExtra("calcvault_virtual_package", instance.virtualPackageName);
        source.putExtra("calcvault_virtual_apk", instance.apkFile.getAbsolutePath());

        return new VirtualLaunchPlan(instance, inspection, component, source);
    }
}
