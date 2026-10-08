package com.example.calcvault;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/**
 * First-stage virtual-app container.
 *
 * This class deliberately does NOT use Android managed profiles and does NOT
 * modify CloneRuntime. It owns the private filesystem identity for a copied APK
 * and records the information needed by a future component-based virtual
 * launcher.
 *
 * It is not an Android package installer and must not be presented as one.
 */
public final class VirtualAppContainer {
    public static final class Instance {
        public final ApkCloneStore.CloneRecord clone;
        public final String virtualPackageName;
        public final File root;
        public final File dataDir;
        public final File cacheDir;
        public final File filesDir;
        public final File apkFile;

        private Instance(
                ApkCloneStore.CloneRecord clone,
                String virtualPackageName,
                File root,
                File dataDir,
                File cacheDir,
                File filesDir,
                File apkFile) {
            this.clone = clone;
            this.virtualPackageName = virtualPackageName;
            this.root = root;
            this.dataDir = dataDir;
            this.cacheDir = cacheDir;
            this.filesDir = filesDir;
            this.apkFile = apkFile;
        }
    }

    private final Context context;
    private final ApkCloneStore store;

    public VirtualAppContainer(Context context) {
        this.context = context.getApplicationContext();
        this.store = new ApkCloneStore(this.context);
    }

    /**
     * Creates or repairs the private filesystem identity for a clone.
     *
     * The virtual package name is intentionally different from the real
     * installed package name. Nothing is installed into Android's package
     * manager by this method.
     */
    public synchronized Instance prepare(ApkCloneStore.CloneRecord clone) throws IOException {
        if (clone == null || clone.id == null || clone.id.trim().isEmpty()) {
            throw new IOException("Clone record missing");
        }

        File sourceApk = new File(clone.apkPath);
        if (!sourceApk.isFile()) {
            throw new IOException("Private APK is missing");
        }

        File root = new File(store.getRoot(), clone.id + "/virtual");
        File data = new File(root, "data");
        File cache = new File(root, "cache");
        File files = new File(root, "files");
        File apk = new File(root, "apk/base.apk");

        makeDir(root, "virtual container");
        makeDir(data, "virtual data");
        makeDir(cache, "virtual cache");
        makeDir(files, "virtual files");
        makeDir(apk.getParentFile(), "virtual APK folder");

        if (!apk.isFile() || apk.length() != sourceApk.length()) {
            copy(sourceApk, apk);
        }

        String virtualPackage = buildVirtualPackageName(clone);

        JSONObject meta = new JSONObject();
        try {
            meta.put("cloneId", clone.id);
            meta.put("originalPackageName", clone.packageName);
            meta.put("virtualPackageName", virtualPackage);
            meta.put("label", clone.label == null ? "" : clone.label);
            meta.put("apkPath", apk.getAbsolutePath());
            meta.put("createdAt", clone.createdAt);
            meta.put("preparedAt", System.currentTimeMillis());
        } catch (Exception e) {
            throw new IOException("Could not create virtual metadata", e);
        }

        write(new File(root, "virtual.json"), meta.toString());
        write(new File(root, "READY"), "virtual-container-ready\n");

        return new Instance(
                clone,
                virtualPackage,
                root,
                data,
                cache,
                files,
                apk
        );
    }

    public synchronized void delete(ApkCloneStore.CloneRecord clone) {
        if (clone == null) return;
        File root = new File(store.getRoot(), clone.id + "/virtual");
        deleteTree(root);
    }

    public static String buildVirtualPackageName(ApkCloneStore.CloneRecord clone) {
        String id = clone == null ? "unknown" : clone.id;
        String safe = id.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        if (safe.length() > 20) safe = safe.substring(0, 20);
        if (safe.isEmpty()) safe = "instance";
        return "com.example.calcvault.virtual." + safe;
    }

    private static void makeDir(File dir, String what) throws IOException {
        if (dir == null) throw new IOException("Missing " + what);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create " + what);
        }
    }

    private static void copy(File source, File target) throws IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(source);
             java.io.OutputStream out = new java.io.FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        }
    }

    private static void write(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteTree(child);
            }
        }
        file.delete();
    }
}
