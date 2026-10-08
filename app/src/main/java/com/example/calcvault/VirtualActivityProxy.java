package com.example.calcvault;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import java.io.IOException;

public final class VirtualActivityProxy extends Activity {
    public static final String EXTRA_CLONE_ID = "calcvault_virtual_clone_id";
    public static final String EXTRA_VIRTUAL_PACKAGE = "calcvault_virtual_package";
    public static final String EXTRA_APK = "calcvault_virtual_apk";

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        statusView = new TextView(this);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(48, 48, 48, 48);
        statusView.setText("Preparing virtual app…");
        setContentView(statusView);

        prepareBridge();
    }

    private void prepareBridge() {
        String cloneId = getIntent().getStringExtra(EXTRA_CLONE_ID);
        if (cloneId == null || cloneId.trim().isEmpty()) {
            showError("Virtual copy ID is missing");
            return;
        }

        try {
            ApkCloneStore store = new ApkCloneStore(this);
            ApkCloneStore.CloneRecord clone = findClone(store, cloneId);
            if (clone == null) {
                showError("Virtual copy was not found");
                return;
            }

            VirtualRuntimeCoordinator coordinator =
                    new VirtualRuntimeCoordinator(this);
            VirtualRuntimeCoordinator.Prepared prepared =
                    coordinator.prepare(clone);

            VirtualIntentRouter router = new VirtualIntentRouter(this);
            Intent routedIntent = router.routeLaunch(prepared);

            VirtualActivityBridge bridge =
                    VirtualActivityBridge.prepare(this, prepared);

            statusView.setText(
                    "Virtual runtime ready\n\n" +
                    "Activity: " + bridge.getSession().getActivityClassName() +
                    "\n\n" +
                    "Virtual component: " +
                    String.valueOf(routedIntent.getComponent()));
        } catch (IOException e) {
            showError(e.getMessage() == null
                    ? "Virtual runtime preparation failed"
                    : e.getMessage());
        } catch (RuntimeException e) {
            showError("Virtual runtime could not be prepared");
        }
    }

    private ApkCloneStore.CloneRecord findClone(
            ApkCloneStore store,
            String cloneId) {
        for (ApkCloneStore.CloneRecord record : store.list()) {
            if (record != null && cloneId.equals(record.id)) {
                return record;
            }
        }
        return null;
    }

    private void showError(String message) {
        if (statusView != null) {
            statusView.setText(message);
        }
    }
}
