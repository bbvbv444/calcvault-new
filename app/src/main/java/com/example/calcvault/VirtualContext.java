package com.example.calcvault;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Configuration;
import java.io.File;
import java.io.IOException;

/**
 * Context foundation for the virtual runtime.
 *
 * It gives code running under the future virtual host a separate package/data
 * identity at the Context layer without changing Android's real installed
 * package registry. It does not claim to virtualize system services yet.
 */
public final class VirtualContext extends ContextWrapper {
    private final VirtualAppContainer.Instance instance;
    private final String virtualPackageName;
    private final AssetManager assets;
    private final Resources resources;

    public VirtualContext(
            Context base,
            VirtualAppContainer.Instance instance) throws IOException {
        super(base);
        if (instance == null) throw new IOException("Virtual instance missing");
        if (instance.apkFile == null || !instance.apkFile.isFile()) {
            throw new IOException("Virtual APK is missing");
        }

        this.instance = instance;
        this.virtualPackageName = instance.virtualPackageName;

        try {
            AssetManager manager = AssetManager.class.getDeclaredConstructor().newInstance();
            int cookie = manager.addAssetPath(instance.apkFile.getAbsolutePath());
            if (cookie == 0) {
                throw new IOException("Could not load virtual APK resources");
            }
            this.assets = manager;

            Resources baseResources = base.getResources();
            Configuration configuration = new Configuration(baseResources.getConfiguration());
            this.resources = new Resources(
                    assets,
                    baseResources.getDisplayMetrics(),
                    configuration);
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException(
                    "Could not create virtual resource context: "
                            + e.getClass().getSimpleName(), e);
        }
    }

    public VirtualAppContainer.Instance getVirtualInstance() {
        return instance;
    }

    @Override
    public String getPackageName() {
        return virtualPackageName;
    }

    @Override
    public String getOpPackageName() {
        return virtualPackageName;
    }

    @Override
    public File getFilesDir() {
        return instance.filesDir;
    }

    @Override
    public File getCacheDir() {
        return instance.cacheDir;
    }

    @Override
    public File getDataDir() {
        return instance.dataDir;
    }

    @Override
    public AssetManager getAssets() {
        return assets;
    }

    @Override
    public Resources getResources() {
        return resources;
    }
}
