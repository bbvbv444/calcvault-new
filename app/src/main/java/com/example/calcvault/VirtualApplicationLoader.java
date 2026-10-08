package com.example.calcvault;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.IOException;

/**
 * Loads the copied application's Application class into CalcVault's process.
 *
 * This is intentionally a preparation layer only. Android framework lifecycle
 * attachment is still owned by the host Activity and is not faked here.
 */
public final class VirtualApplicationLoader {
    private final Context host;

    public VirtualApplicationLoader(Context host) {
        this.host = host.getApplicationContext();
    }

    public LoadedApplication load(
            VirtualAppContainer.Instance instance) throws IOException {
        if (instance == null || instance.apkFile == null || !instance.apkFile.isFile()) {
            throw new IOException("Virtual APK is missing");
        }

        PackageManager pm = host.getPackageManager();
        ApplicationInfo info = pm.getApplicationInfo(
                instance.clone.packageName,
                PackageManager.GET_META_DATA);

        String className = info.className;
        if (className == null || className.trim().isEmpty()) {
            className = "android.app.Application";
        }

        File optimizedDir = new File(instance.root, "dex/application");
        if (!optimizedDir.exists() && !optimizedDir.mkdirs()) {
            throw new IOException("Could not create virtual dex directory");
        }

        File nativeDir = new File(instance.root, "native");
        if (!nativeDir.exists() && !nativeDir.mkdirs()) {
            throw new IOException("Could not create virtual native directory");
        }

        DexClassLoader loader = new DexClassLoader(
                instance.apkFile.getAbsolutePath(),
                optimizedDir.getAbsolutePath(),
                nativeDir.getAbsolutePath(),
                host.getClassLoader());

        try {
            Class<?> applicationClass = loader.loadClass(className);
            if (!android.app.Application.class.isAssignableFrom(applicationClass)) {
                throw new IOException("Copied application class is invalid");
            }

            return new LoadedApplication(
                    className,
                    applicationClass,
                    loader,
                    new VirtualContext(host, instance));
        } catch (ClassNotFoundException e) {
            throw new IOException("Copied application class could not be loaded", e);
        }
    }

    public static final class LoadedApplication {
        public final String className;
        public final Class<?> applicationClass;
        public final DexClassLoader classLoader;
        public final VirtualContext context;

        LoadedApplication(
                String className,
                Class<?> applicationClass,
                DexClassLoader classLoader,
                VirtualContext context) {
            this.className = className;
            this.applicationClass = applicationClass;
            this.classLoader = classLoader;
            this.context = context;
        }
    }
}
