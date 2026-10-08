package com.example.calcvault;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.IOException;

/**
 * Execution bridge for the virtual Activity host.
 *
 * This stage deliberately stops before invoking arbitrary third-party Activity
 * lifecycle methods. It verifies that the requested Activity class can be
 * resolved from the copied APK and records a deterministic failure instead of
 * falsely claiming the clone is running.
 */
public final class VirtualActivityExecutor {
    public static final class Resolution {
        public final VirtualAppContainer.Instance instance;
        public final VirtualAppInspector.Inspection inspection;
        public final Class<?> activityClass;
        public final DexClassLoader classLoader;

        Resolution(
                VirtualAppContainer.Instance instance,
                VirtualAppInspector.Inspection inspection,
                Class<?> activityClass,
                DexClassLoader classLoader) {
            this.instance = instance;
            this.inspection = inspection;
            this.activityClass = activityClass;
            this.classLoader = classLoader;
        }
    }

    private final ClassLoader hostClassLoader;

    public VirtualActivityExecutor(ClassLoader hostClassLoader) {
        this.hostClassLoader = hostClassLoader;
    }

    public Resolution resolve(VirtualRuntimeCoordinator.Prepared prepared)
            throws IOException {
        if (prepared == null || prepared.instance == null || prepared.inspection == null) {
            throw new IOException("Virtual runtime preparation missing");
        }

        String activityName = prepared.inspection.launchActivity;
        if (activityName == null || activityName.trim().isEmpty()) {
            throw new IOException("No virtual launch Activity found");
        }

        File apk = prepared.instance.apkFile;
        File dexDir = new File(prepared.instance.root, "dex");
        if (!dexDir.exists() && !dexDir.mkdirs()) {
            throw new IOException("Could not create virtual dex directory");
        }

        DexClassLoader loader = new DexClassLoader(
                apk.getAbsolutePath(),
                dexDir.getAbsolutePath(),
                null,
                hostClassLoader);

        try {
            Class<?> activityClass = Class.forName(activityName, false, loader);
            if (!Activity.class.isAssignableFrom(activityClass)) {
                throw new IOException("Virtual launch component is not an Activity");
            }

            return new Resolution(
                    prepared.instance,
                    prepared.inspection,
                    activityClass,
                    loader);
        } catch (ClassNotFoundException e) {
            throw new IOException(
                    "Virtual Activity class could not be resolved: " + activityName, e);
        } catch (LinkageError e) {
            throw new IOException(
                    "Virtual Activity dependencies could not be resolved: "
                            + e.getClass().getSimpleName(), e);
        }
    }

    public void showResolution(Activity host, Resolution resolution) {
        if (host == null || resolution == null) return;

        TextView view = new TextView(host);
        view.setGravity(Gravity.CENTER);
        view.setPadding(48, 48, 48, 48);
        view.setText(
                "Virtual Activity resolved\n\n"
                        + resolution.inspection.launchActivity
                        + "\n\nExecution bridge ready.");
        host.setContentView(view);
    }
}
