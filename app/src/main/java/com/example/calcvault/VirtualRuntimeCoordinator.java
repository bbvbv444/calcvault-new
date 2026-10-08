package com.example.calcvault;

import android.content.Context;
import android.content.Intent;
import java.io.IOException;

/**
 * Coordinates the preparation of one copied APK for the virtual runtime.
 *
 * This is the first integration point between the container, manifest
 * inspection, state store, component resolver and Activity host. It does not
 * attempt to bypass Android's package manager or execute arbitrary APK code.
 */
public final class VirtualRuntimeCoordinator {
    public static final class Prepared {
        public final VirtualAppContainer.Instance instance;
        public final VirtualAppInspector.Inspection inspection;
        public final VirtualLaunchPlan launchPlan;

        Prepared(
                VirtualAppContainer.Instance instance,
                VirtualAppInspector.Inspection inspection,
                VirtualLaunchPlan launchPlan) {
            this.instance = instance;
            this.inspection = inspection;
            this.launchPlan = launchPlan;
        }
    }

    private final Context context;
    private final VirtualAppContainer container;
    private final VirtualAppInspector inspector;
    private final VirtualAppStateStore stateStore;
    private final VirtualComponentResolver resolver;

    public VirtualRuntimeCoordinator(Context context) {
        this.context = context.getApplicationContext();
        this.container = new VirtualAppContainer(this.context);
        this.inspector = new VirtualAppInspector(this.context);
        this.stateStore = new VirtualAppStateStore(this.context);
        this.resolver = new VirtualComponentResolver(this.context);
    }

    public synchronized Prepared prepare(ApkCloneStore.CloneRecord clone) throws IOException {
        if (clone == null) throw new IOException("Clone record missing");

        VirtualAppContainer.Instance instance = container.prepare(clone);

        try {
            VirtualAppInspector.Inspection inspection = inspector.inspect(instance);
            resolver.resolveLaunchActivity(instance);

            VirtualLaunchPlan plan =
                    VirtualLaunchPlan.create(context, instance, inspection);

            stateStore.save(
                    instance,
                    inspection,
                    VirtualAppStateStore.STATE_READY,
                    "Virtual launch plan prepared");

            return new Prepared(instance, inspection, plan);
        } catch (IOException e) {
            try {
                stateStore.save(
                        instance,
                        null,
                        VirtualAppStateStore.STATE_FAILED,
                        e.getMessage() == null ? "Virtual preparation failed" : e.getMessage());
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    /**
     * Creates the explicit host Activity Intent for the future runtime layer.
     * The host receives only CalcVault-owned metadata; the copied package is
     * not registered with Android.
     */
    public Intent buildHostIntent(Prepared prepared) throws IOException {
        if (prepared == null || prepared.instance == null) {
            throw new IOException("Virtual runtime preparation missing");
        }

        Intent intent = new Intent(context, VirtualActivityProxy.class);
        intent.putExtra(VirtualActivityProxy.EXTRA_CLONE_ID, prepared.instance.clone.id);
        intent.putExtra(
                VirtualActivityProxy.EXTRA_VIRTUAL_PACKAGE,
                prepared.instance.virtualPackageName);
        intent.putExtra(
                VirtualActivityProxy.EXTRA_APK,
                prepared.instance.apkFile.getAbsolutePath());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public void delete(ApkCloneStore.CloneRecord clone) {
        container.delete(clone);
    }
}
