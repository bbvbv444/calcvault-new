package com.example.calcvault;

import android.content.ComponentName;
import android.content.Intent;
import java.io.IOException;

/**
 * Resolves intents against the copied APK's manifest without installing the
 * copied package into Android's package manager.
 */
public final class VirtualIntentRouter {
    private final VirtualComponentResolver resolver;

    public VirtualIntentRouter(android.content.Context context) {
        this.resolver = new VirtualComponentResolver(context);
    }

    public Intent routeLaunch(
            VirtualRuntimeCoordinator.Prepared prepared) throws IOException {
        if (prepared == null || prepared.instance == null) {
            throw new IOException("Virtual preparation missing");
        }

        ComponentName component =
                resolver.resolveVirtualComponent(prepared.instance);

        Intent intent = new Intent(prepared.launchPlan.sourceIntent);
        intent.setComponent(component);
        intent.putExtra(
                VirtualActivityProxy.EXTRA_CLONE_ID,
                prepared.instance.clone.id);
        intent.putExtra(
                VirtualActivityProxy.EXTRA_VIRTUAL_PACKAGE,
                prepared.instance.virtualPackageName);
        intent.putExtra(
                VirtualActivityProxy.EXTRA_APK,
                prepared.instance.apkFile.getAbsolutePath());

        return intent;
    }

    public boolean targetsVirtualPackage(
            VirtualRuntimeCoordinator.Prepared prepared,
            Intent intent) {
        if (prepared == null || prepared.instance == null || intent == null) {
            return false;
        }

        ComponentName component = intent.getComponent();
        return component != null
                && prepared.instance.virtualPackageName.equals(
                        component.getPackageName());
    }
}
