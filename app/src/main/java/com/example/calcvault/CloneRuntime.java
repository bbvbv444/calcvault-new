package com.example.calcvault;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import dalvik.system.DexClassLoader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CloneRuntime {
    public static final class PreparedRuntime {
        public final ApkCloneStore.CloneRecord clone;
        public final String launchActivity;
        public final String applicationClass;
        public final File runtimeRoot;
        public final File dexRoot;
        public final DexClassLoader classLoader;

        PreparedRuntime(ApkCloneStore.CloneRecord clone,String launchActivity,String applicationClass,
                        File runtimeRoot,File dexRoot,DexClassLoader classLoader){
            this.clone=clone;
            this.launchActivity=launchActivity;
            this.applicationClass=applicationClass;
            this.runtimeRoot=runtimeRoot;
            this.dexRoot=dexRoot;
            this.classLoader=classLoader;
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

        File root=new File(context.getFilesDir(),"app_clones/"+clone.id+"/runtime");
        File data=new File(root,"data");
        File cache=new File(root,"cache");
        File files=new File(root,"files");
        File dexRoot=new File(root,"dex");
        File apkRoot=new File(root,"apk");
        makeDir(root,"runtime folder");
        makeDir(data,"runtime data folder");
        makeDir(cache,"runtime cache folder");
        makeDir(files,"runtime files folder");
        makeDir(dexRoot,"runtime dex folder");
        makeDir(apkRoot,"runtime APK folder");

        File runtimeApk=new File(apkRoot,"base.apk");
        if(!runtimeApk.isFile() || runtimeApk.length()!=apk.length()){
            copyFile(apk,runtimeApk);
        }

        PackageManager pm=context.getPackageManager();
        android.content.pm.PackageInfo info=pm.getPackageArchiveInfo(
                runtimeApk.getAbsolutePath(),
                PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA
        );
        if(info==null || info.applicationInfo==null){
            throw new IOException("Private APK could not be inspected");
        }

        String launchActivity=findLaunchActivity(info);
        if(launchActivity==null || launchActivity.isEmpty()){
            throw new IOException("No launchable activity found");
        }

        extractDexFiles(runtimeApk,dexRoot);

        DexClassLoader loader=new DexClassLoader(
                runtimeApk.getAbsolutePath(),
                dexRoot.getAbsolutePath(),
                null,
                context.getClassLoader()
        );

        String applicationClass=info.applicationInfo.className;
        if(applicationClass==null || applicationClass.trim().isEmpty()){
            applicationClass="android.app.Application";
        }

        // Compatibility probe only: load the clone's Application class without
        // initializing it. This does not start or modify the original app.
        try{
            Class.forName(applicationClass,false,loader);
        }catch(Throwable e){
            throw new IOException("Clone classes could not be loaded: "+e.getClass().getSimpleName(),e);
        }

        String metadata="{\n"
                +"  \\"packageName\\": \\""+escape(clone.packageName)+"\\",\\n"
                +"  \\"label\\": \\""+escape(clone.label)+"\\",\\n"
                +"  \\"launchActivity\\": \\""+escape(launchActivity)+"\\",\\n"
                +"  \\"applicationClass\\": \\""+escape(applicationClass)+"\\",\\n"
                +"  \\"preparedAt\\": "+System.currentTimeMillis()+"\\n"
                +"}\n";

        write(new File(root,"runtime.json"),metadata);
        write(new File(root,"READY"),"prepared\n");

        return new PreparedRuntime(clone,launchActivity,applicationClass,root,dexRoot,loader);
    }

    public String probe(ApkCloneStore.CloneRecord clone) throws IOException {
        PreparedRuntime runtime=prepare(clone);
        return "Runtime prepared successfully for "+runtime.clone.label+
                ". Launch activity: "+runtime.launchActivity;
    }

    private String findLaunchActivity(android.content.pm.PackageInfo info){
        if(info.activities!=null){
            for(ActivityInfo a:info.activities){
                if(a==null || a.name==null)continue;
                if(a.exported)return a.name;
            }
        }
        return null;
    }

    private void extractDexFiles(File apk,File dexRoot)throws IOException{
        File[] existing=dexRoot.listFiles();
        if(existing!=null){
            for(File f:existing)if(f.isFile())f.delete();
        }

        boolean found=false;
        try(ZipInputStream zin=new ZipInputStream(new BufferedInputStream(new FileInputStream(apk)))){
            ZipEntry entry;
            byte[] buffer=new byte[8192];
            while((entry=zin.getNextEntry())!=null){
                String name=entry.getName();
                if(entry.isDirectory() || !name.matches("classes([2-9][0-9]*|[0-9]+)?\\.dex")){
                    continue;
                }
                File out=new File(dexRoot,new File(name).getName());
                try(FileOutputStream fos=new FileOutputStream(out)){
                    int n;
                    while((n=zin.read(buffer))!=-1)fos.write(buffer,0,n);
                }
                found=true;
            }
        }
        if(!found)throw new IOException("Clone APK contains no dex code");
    }

    private static void copyFile(File source,File target)throws IOException{
        File parent=target.getParentFile();
        if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Could not create APK folder");
        try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(target)){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        }
    }

    private static void makeDir(File dir,String what)throws IOException{
        if(!dir.exists() && !dir.mkdirs())throw new IOException("Could not create "+what);
    }

    private static String escape(String s){
        if(s==null)return "";
        return s.replace("\\\\","\\\\\\\\").replace("\"","\\\\\"");
    }

    private static void write(File file,String text)throws IOException{
        try(FileOutputStream out=new FileOutputStream(file)){
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
