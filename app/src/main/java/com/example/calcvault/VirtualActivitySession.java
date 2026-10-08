package com.example.calcvault;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.io.IOException;

/**
 * Owns the state needed by the virtual Activity host while a copied app is
 * being prepared. This is intentionally a preparation/session layer: Android
 * does not allow an arbitrary Activity class from another APK to simply be
 * attached to a host Activity by Java reflection.
 */
public final class VirtualActivitySession {
    private final VirtualRuntimeCoordinator.Prepared prepared;
    private final VirtualActivityExecutor.Resolution resolution;
    private final VirtualContext virtualContext;
    private final Intent sourceIntent;

    private VirtualActivitySession(
            VirtualRuntimeCoordinator.Prepared prepared,
            VirtualActivityExecutor.Resolution resolution,
            VirtualContext virtualContext,
            Intent sourceIntent) {
        this.prepared = prepared;
        this.resolution = resolution;
        this.virtualContext = virtualContext;
        this.sourceIntent = sourceIntent;
    }

    public static VirtualActivitySession open(
            Activity host,
            VirtualRuntimeCoordinator.Prepared prepared) throws IOException {
        if (host == null) throw new IOException("Host Activity missing");
        if (prepared == null) throw new IOException("Virtual preparation missing");

        VirtualActivityExecutor executor =
                new VirtualActivityExecutor(host.getClassLoader());
        VirtualActivityExecutor.Resolution resolution = executor.resolve(prepared);

        VirtualApplicationLoader.LoadedApplication application =
                new VirtualApplicationLoader(host).load(prepared.instance);

        VirtualContext context = application.context;

        Intent source = new Intent(prepared.launchPlan.sourceIntent);
        source.setClassName(
                prepared.inspection.packageName,
                prepared.inspection.launchActivity);

        return new VirtualActivitySession(
                prepared,
                resolution,
                context,
                source);
    }

    public VirtualRuntimeCoordinator.Prepared getPrepared() {
        return prepared;
    }

    public VirtualActivityExecutor.Resolution getResolution() {
        return resolution;
    }

    public VirtualContext getVirtualContext() {
        return virtualContext;
    }

    public Intent getSourceIntent() {
        return new Intent(sourceIntent);
    }

    public String getActivityClassName() {
        return resolution.activityClass.getName();
    }

    public Bundle buildInitialState() {
        Bundle state = new Bundle();
        state.putString(
                VirtualActivityProxy.EXTRA_CLONE_ID,
                prepared.instance.clone.id);
        state.putString(
                VirtualActivityProxy.EXTRA_VIRTUAL_PACKAGE,
                prepared.instance.virtualPackageName);
        state.putString(
                VirtualActivityProxy.EXTRA_APK,
                prepared.instance.apkFile.getAbsolutePath());
        state.putString("calcvault_virtual_activity", getActivityClassName());
        return state;
    }
}
