package com.example.calcvault;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.io.IOException;

/**
 * Host-side lifecycle state for a virtual Activity.
 *
 * Android owns the real Activity lifecycle, so this class keeps the copied
 * Activity's virtual identity, context, class loader and routed intent together
 * without pretending that a foreign Activity can be attached by reflection.
 */
public final class VirtualActivityHost {
    public enum State {
        CREATED,
        RESOLVED,
        READY,
        FAILED
    }

    private final Activity host;
    private final VirtualActivitySession session;
    private final ClassLoader classLoader;
    private final Intent routedIntent;
    private State state;
    private String error;
    private VirtualExecutionBoundary.State executionState;

    private VirtualActivityHost(
            Activity host,
            VirtualActivitySession session,
            ClassLoader classLoader,
            Intent routedIntent) {
        this.host = host;
        this.session = session;
        this.classLoader = classLoader;
        this.routedIntent = routedIntent;
        this.state = State.CREATED;
    }

    public static VirtualActivityHost create(
            Activity host,
            VirtualRuntimeCoordinator.Prepared prepared) throws IOException {
        if (host == null) throw new IOException("Host Activity missing");
        if (prepared == null) throw new IOException("Virtual preparation missing");

        VirtualActivitySession session =
                VirtualActivitySession.open(host, prepared);

        VirtualIntentRouter router = new VirtualIntentRouter(host);
        Intent routed = router.routeLaunch(prepared);

        VirtualActivityHost result = new VirtualActivityHost(
                host,
                session,
                session.getResolution().classLoader,
                routed);
        result.state = State.RESOLVED;
        result.executionState = VirtualExecutionBoundary.inspect(session.getResolution());
        return result;
    }

    public void markReady() {
        if (state != State.FAILED) {
            state = State.READY;
        }
    }

    public void markFailed(String message) {
        state = State.FAILED;
        error = message == null ? "Virtual Activity failed" : message;
    }

    public Activity getHost() {
        return host;
    }

    public VirtualActivitySession getSession() {
        return session;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public Intent getRoutedIntent() {
        return new Intent(routedIntent);
    }

    public State getState() {
        return state;
    }

    public VirtualExecutionBoundary.State getExecutionState() {
        return executionState;
    }

    public String getExecutionMessage() {
        return VirtualExecutionBoundary.explain(executionState);
    }

    public String getError() {
        return error;
    }

    public Bundle toBundle() {
        Bundle result = session.buildInitialState();
        result.putString("calcvault_virtual_host_state", state.name());
        if (executionState != null) {
            result.putString("calcvault_virtual_execution_state", executionState.name());
            result.putString("calcvault_virtual_execution_message", getExecutionMessage());
        }
        result.putString(
                "calcvault_virtual_component",
                String.valueOf(routedIntent.getComponent()));
        if (error != null) {
            result.putString("calcvault_virtual_error", error);
        }
        return result;
    }
}
