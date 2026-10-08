package com.example.calcvault;

public final class PrivateAppRecord {
    public final String id;
    public final String packageName;
    public final String label;
    public final String apkPath;
    public final long createdAt;

    public PrivateAppRecord(String id, String packageName, String label, String apkPath, long createdAt) {
        this.id=id;
        this.packageName=packageName;
        this.label=label;
        this.apkPath=apkPath;
        this.createdAt=createdAt;
    }
}
