package com.example.calcvault;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.IOException;

public final class ApkCloneImporter {
    private final Context context;
    private final ApkCloneStore store;

    public ApkCloneImporter(Context context) {
        this.context = context.getApplicationContext();
        this.store = new ApkCloneStore(this.context);
    }

    public ApkCloneStore.CloneRecord importInstalledPackage(String packageName) throws IOException {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            String source = info.sourceDir;
            if (source == null) throw new IOException("APK path unavailable");

            ApkCloneStore.CloneRecord record = store.importApk(
                    packageName,
                    label == null ? packageName : label.toString(),
                    new File(source)
            );

            // Prepare the private runtime immediately after the APK is copied.
            // This validates the APK, discovers a launchable activity, and creates
            // the clone's isolated data/cache/files directories.
            new CloneRuntime(context).prepare(record);

            return record;
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("Installed app not found", e);
        }
    }

    public ApkCloneStore getStore() {
        return store;
    }
}
