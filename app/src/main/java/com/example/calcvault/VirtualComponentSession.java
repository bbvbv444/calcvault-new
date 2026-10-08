package com.example.calcvault;

import android.content.Intent;
import java.util.HashMap;
import java.util.Map;

/**
 * Keeps virtual component sessions separate from Android's installed-package
 * registry. This is the host-side registry that later framework hooks can use
 * when an Activity, service, receiver or provider request belongs to a copy.
 */
public final class VirtualComponentSession {
    private static final Map<String, VirtualActivityHost> ACTIVE =
            new HashMap<>();

    private VirtualComponentSession() {}

    public static synchronized void register(
            VirtualActivityHost host) {
        if (host == null || host.getSession() == null) return;

        String cloneId =
                host.getSession().getPrepared().instance.clone.id;
        ACTIVE.put(cloneId, host);
    }

    public static synchronized VirtualActivityHost find(
            String cloneId) {
        if (cloneId == null) return null;
        return ACTIVE.get(cloneId);
    }

    public static synchronized VirtualActivityHost findForIntent(
            Intent intent) {
        if (intent == null || intent.getComponent() == null) return null;

        String packageName = intent.getComponent().getPackageName();
        for (VirtualActivityHost host : ACTIVE.values()) {
            if (host.getSession()
                    .getPrepared()
                    .instance
                    .virtualPackageName
                    .equals(packageName)) {
                return host;
            }
        }
        return null;
    }

    public static synchronized void remove(String cloneId) {
        if (cloneId != null) ACTIVE.remove(cloneId);
    }

    public static synchronized void clear() {
        ACTIVE.clear();
    }

    public static synchronized int size() {
        return ACTIVE.size();
    }
}
