package com.example.calcvault;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class CloneRuntime {
    public static final class PreparedRuntime {
        public final ApkCloneStore.CloneRecord clone;
        public final String launchActivity;
        public final File runtimeRoot;
        public PreparedRuntime(ApkCloneStore.CloneRecord clone,String launchActivity,File runtimeRoot){
            this.clone=clone;
            this.launchActivity=launchActivity;
            this.runtimeRoot=runtimeRoot;
        }
    }

    private final Context context;

    public CloneRuntime(Context context){
        this.context=context.getApplicationContext();
    }

    public PreparedRuntime prepare(ApkCloneStore.CloneRecord clone) throws IOException {
        if(clone==null) throw new IOException("Clone record missing");
        File apk=new File(clone.apkPath);
        if(!apk.isFile()) throw new IOException("Private APK is missing");

        PackageManager pm=context.getPackageManager();
        android.content.pm.PackageInfo info=pm.getPackageArchiveInfo(
                apk.getAbsolutePath(),
                PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA
        );
        if(info==null || info.applicationInfo==null){
            throw new IOException("Private APK could not be inspected");
        }

        String launchActivity=findLaunchActivity(pm,apk.getAbsolutePath());
        if(launchActivity==null || launchActivity.isEmpty()){
            throw new IOException("No launchable activity found");
        }

        File root=new File(context.getFilesDir(),"app_clones/"+clone.id+"/runtime");
        File data=new File(root,"data");
        File cache=new File(root,"cache");
        File files=new File(root,"files");
        if(!root.exists() && !root.mkdirs()) throw new IOException("Could not create runtime folder");
        if(!data.exists() && !data.mkdirs()) throw new IOException("Could not create runtime data folder");
        if(!cache.exists() && !cache.mkdirs()) throw new IOException("Could not create runtime cache folder");
        if(!files.exists() && !files.mkdirs()) throw new IOException("Could not create runtime files folder");

        String metadata="{\n"
                +"  \"packageName\": \""+escape(clone.packageName)+"\",\n"
                +"  \"label\": \""+escape(clone.label)+"\",\n"
                +"  \"launchActivity\": \""+escape(launchActivity)+"\",\n"
                +"  \"preparedAt\": "+System.currentTimeMillis()+"\n"
                +"}\n";
        write(new File(root,"runtime.json"),metadata);

        return new PreparedRuntime(clone,launchActivity,root);
    }

    private String findLaunchActivity(PackageManager pm,String apkPath){
        try{
            android.content.pm.PackageInfo info=pm.getPackageArchiveInfo(apkPath,PackageManager.GET_ACTIVITIES);
            if(info!=null && info.activities!=null){
                for(ActivityInfo a:info.activities){
                    if(a==null || a.name==null)continue;
                    if(a.exported)return a.name;
                }
            }
        }catch(Exception ignored){}
        return null;
    }

    private static String escape(String s){
        if(s==null)return "";
        return s.replace("\\","\\\\").replace("\"","\\\"");
    }

    private static void write(File file,String text)throws IOException{
        try(FileOutputStream out=new FileOutputStream(file)){
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
