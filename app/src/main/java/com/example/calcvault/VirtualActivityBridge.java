package com.example.calcvault;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.io.IOException;

/**
 * Prepares the framework-facing data needed before a copied Activity can be
 * hosted. It does not construct or invoke a foreign Activity lifecycle by
 * reflection because Android requires framework attachment for a real Activity.
 */
public final class VirtualActivityBridge {
    public static final String EXTRA_BRIDGE_READY =
            "calcvault_virtual_bridge_ready";

    private final VirtualRuntimeCoordinator coordinator;
    private final VirtualActivitySession session;

    private VirtualActivityBridge(
            VirtualRuntimeCoordinator coordinator,
            VirtualActivitySession session) {
        this.coordinator = coordinator;
        this.session = session;
    }

    public static VirtualActivityBridge prepare(
            Activity host,
            VirtualRuntimeCoordinator.Prepared prepared) throws IOException {
        if (host == null) throw new IOException("Host Activity missing");
        if (prepared == null) throw new IOException("Virtual preparation missing");

        VirtualActivitySession session =
                VirtualActivitySession.open(host, prepared);

        return new VirtualActivityBridge(
                new VirtualRuntimeCoordinator(host),
                session);
    }

    public VirtualActivitySession getSession() {
        return session;
    }

    public Intent buildHostIntent() {
        Intent intent = coordinator.buildHostIntent(session.getPrepared());
        intent.putExtra(EXTRA_BRIDGE_READY, true);
        return intent;
    }

    public Bundle buildBridgeState() {
        Bundle state = session.buildInitialState();
        state.putBoolean(EXTRA_BRIDGE_READY, true);
        state.putString(
                "calcvault_bridge_activity",
                session.getActivityClassName());
        return state;
    }
}
