package com.example.calcvault;

import android.content.ComponentName;
import android.content.Intent;

/**
 * Rewrites intents that explicitly target the virtual package so they remain
 * inside CalcVault's virtual component space.
 *
 * This is a routing primitive. It does not hook Android's global ActivityManager
 * and therefore does not claim to intercept arbitrary framework calls yet.
 */
public final class VirtualIntentInterceptor {
    private VirtualIntentInterceptor() {}

    public static Intent intercept(Intent incoming) {
        if (incoming == null) return null;

        VirtualActivityHost host =
                VirtualComponentSession.findForIntent(incoming);

        if (host == null) {
            return new Intent(incoming);
        }

        ComponentName component = incoming.getComponent();
        Intent routed = new Intent(incoming);

        routed.putExtra(
                VirtualActivityProxy.EXTRA_CLONE_ID,
                host.getSession().getPrepared().instance.clone.id);
        routed.putExtra(
                VirtualActivityProxy.EXTRA_VIRTUAL_PACKAGE,
                host.getSession().getPrepared().instance.virtualPackageName);
        routed.putExtra(
                VirtualActivityProxy.EXTRA_APK,
                host.getSession().getPrepared().instance.apkFile.getAbsolutePath());

        if (component != null) {
            routed.setComponent(
                    new ComponentName(
                            host.getHost().getPackageName(),
                            VirtualActivityProxy.class.getName()));
        }

        return routed;
    }

    public static boolean belongsToVirtualApp(Intent intent) {
        return VirtualComponentSession.findForIntent(intent) != null;
    }
}
