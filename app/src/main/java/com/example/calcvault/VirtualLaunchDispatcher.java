package com.example.calcvault;

import android.app.Activity;
import android.content.Intent;
import java.io.IOException;

/**
 * Host-side dispatcher for launching a prepared virtual component.
 *
 * The dispatcher only uses public Android APIs. It deliberately does not hook
 * hidden ActivityManager APIs, because those hooks are blocked or changed
 * across Android releases and would not provide a reliable cross-version
 * solution.
 */
public final class VirtualLaunchDispatcher {
    private final Activity host;

    public VirtualLaunchDispatcher(Activity host) {
        if (host == null) {
            throw new IllegalArgumentException("Host Activity missing");
        }
        this.host = host;
    }

    public void launch(
            VirtualRuntimeCoordinator.Prepared prepared) throws IOException {
        if (prepared == null || prepared.instance == null) {
            throw new IOException("Virtual preparation missing");
        }

        VirtualActivityHost virtualHost =
                VirtualActivityHost.create(host, prepared);
        VirtualComponentSession.register(virtualHost);
        virtualHost.markReady();

        Intent proxyIntent = new Intent(host, VirtualActivityProxy.class);
        proxyIntent.putExtra(
                VirtualActivityProxy.EXTRA_CLONE_ID,
                prepared.instance.clone.id);
        proxyIntent.putExtra(
                VirtualActivityProxy.EXTRA_VIRTUAL_PACKAGE,
                prepared.instance.virtualPackageName);
        proxyIntent.putExtra(
                VirtualActivityProxy.EXTRA_APK,
                prepared.instance.apkFile.getAbsolutePath());

        host.startActivity(proxyIntent);
    }
}
