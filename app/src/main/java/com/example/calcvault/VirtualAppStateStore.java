package com.example.calcvault;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.json.JSONObject;

/**
 * Persists the virtual-instance state separately from the original APK record.
 * This is bookkeeping for the future virtual runtime; it never installs an APK.
 */
public final class VirtualAppStateStore {
    public static final String STATE_READY = "ready";
    public static final String STATE_BLOCKED = "blocked";
    public static final String STATE_FAILED = "failed";

    private final Context context;

    public VirtualAppStateStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void save(
            VirtualAppContainer.Instance instance,
            VirtualAppInspector.Inspection inspection,
            String state,
            String message) throws IOException {
        if (instance == null) throw new IOException("Virtual instance missing");

        try {
            JSONObject json = new JSONObject();
            json.put("cloneId", instance.clone.id);
            json.put("originalPackageName", instance.clone.packageName);
            json.put("virtualPackageName", instance.virtualPackageName);
            json.put("state", state == null ? STATE_READY : state);
            json.put("message", message == null ? "" : message);

            if (inspection != null) {
                json.put("manifestPackageName", inspection.packageName);
                json.put("applicationClass", inspection.applicationClass);
                json.put("launchActivity",
                        inspection.launchActivity == null ? "" : inspection.launchActivity);
                json.put("activities", inspection.activities);
                json.put("services", inspection.services);
                json.put("receivers", inspection.receivers);
                json.put("providers", inspection.providers);
            }

            json.put("updatedAt", System.currentTimeMillis());
            write(new File(instance.root, "state.json"), json.toString());
        } catch (Exception e) {
            throw new IOException("Could not save virtual app state", e);
        }
    }

    public synchronized JSONObject load(VirtualAppContainer.Instance instance) throws IOException {
        if (instance == null) throw new IOException("Virtual instance missing");
        File file = new File(instance.root, "state.json");
        if (!file.isFile()) return null;

        try {
            return new JSONObject(read(file));
        } catch (Exception e) {
            throw new IOException("Could not read virtual app state", e);
        }
    }

    private static void write(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }
}
