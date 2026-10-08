package com.example.calcvault;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.IOException;

/**
 * Resolves the virtual launch component from the copied APK's manifest.
 *
 * Android still sees only CalcVault as the installed package. This resolver
 * provides the virtual runtime with the original component metadata without
 * registering the copied APK with Android's PackageManager.
 */
public final class VirtualComponentResolver {
    private final Context context;

    public VirtualComponentResolver(Context context) {
        this.context = context.getApplicationContext();
    }

    public ActivityInfo resolveLaunchActivity(VirtualAppContainer.Instance instance)
            throws IOException {
        if (instance == null || instance.apkFile == null || !instance.apkFile.isFile()) {
            throw new IOException("Virtual APK is missing");
        }

        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(
                instance.apkFile.getAbsolutePath(),
                PackageManager.GET_ACTIVITIES);

        if (info == null || info.activities == null) {
            throw new IOException("Virtual APK has no readable activities");
        }

        for (ActivityInfo activity : info.activities) {
            if (activity != null && activity.exported && activity.name != null) {
                return activity;
            }
        }

        throw new IOException("No launchable activity found");
    }

    public ComponentName resolveVirtualComponent(VirtualAppContainer.Instance instance)
            throws IOException {
        ActivityInfo activity = resolveLaunchActivity(instance);
        return new ComponentName(instance.virtualPackageName, activity.name);
    }
}
