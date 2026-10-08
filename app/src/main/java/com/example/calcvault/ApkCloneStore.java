package com.example.calcvault;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ApkCloneStore {
    public static final class CloneRecord {
        public final String id, packageName, label, apkPath;
        public final long createdAt;
        public CloneRecord(String id,String packageName,String label,String apkPath,long createdAt){
            this.id=id; this.packageName=packageName; this.label=label; this.apkPath=apkPath; this.createdAt=createdAt;
        }
        @Override public boolean equals(Object o){
            return o instanceof CloneRecord && id.equals(((CloneRecord)o).id);
        }
        @Override public int hashCode(){return id.hashCode();}
    }


    private final Context context;
    private final File root;
    private final File indexFile;

    public ApkCloneStore(Context context) {
        this.context = context.getApplicationContext();
        root = new File(this.context.getFilesDir(), "app_clones");
        indexFile = new File(root, "index.json");
        if (!root.exists()) root.mkdirs();
    }

    public synchronized CloneRecord importApk(String packageName, String label, File sourceApk) throws IOException {
        if (sourceApk == null || !sourceApk.isFile()) throw new IOException("APK not found");
        String id = UUID.randomUUID().toString();
        File dir = new File(root, id);
        if (!dir.mkdirs()) throw new IOException("Could not create clone folder");
        File target = new File(dir, "base.apk");
        copy(sourceApk, target);

        CloneRecord record = new CloneRecord(
                id,
                packageName == null ? "" : packageName,
                label == null ? packageName : label,
                target.getAbsolutePath(),
                System.currentTimeMillis()
        );
        List<CloneRecord> records = readRecords();
        records.add(record);
        writeRecords(records);
        return record;
    }

    public synchronized List<CloneRecord> list() {
        return readRecords();
    }

    public synchronized void delete(String id) {
        List<CloneRecord> records = readRecords();
        CloneRecord found = null;
        for (CloneRecord r : records) if (r.id.equals(id)) { found = r; break; }
        if (found != null) {
            File dir = new File(root, id);
            deleteTree(dir);
            records.remove(found);
            writeRecords(records);
        }
    }

    public File getRoot() {
        return root;
    }

    private List<CloneRecord> readRecords() {
        ArrayList<CloneRecord> out = new ArrayList<>();
        if (!indexFile.isFile()) return out;
        try {
            String text = readText(indexFile);
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                out.add(new CloneRecord(
                        o.optString("id"),
                        o.optString("packageName"),
                        o.optString("label"),
                        o.optString("apkPath"),
                        o.optLong("createdAt")
                ));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void writeRecords(List<CloneRecord> records) {
        JSONArray array = new JSONArray();
        for (CloneRecord r : records) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", r.id);
                o.put("packageName", r.packageName);
                o.put("label", r.label);
                o.put("apkPath", r.apkPath);
                o.put("createdAt", r.createdAt);
                array.put(o);
            } catch (Exception ignored) {}
        }
        try {
            writeText(indexFile, array.toString(2));
        } catch (IOException ignored) {}
    }

    private static void copy(File source, File target) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
    }

    private static String readText(File file) throws IOException {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void writeText(File file, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }
}
