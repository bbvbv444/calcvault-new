package com.example.calcvault;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.net.URLConnection;

public class PrivateFileProvider extends ContentProvider {
    private static final String AUTH="com.example.calcvault.privatefiles";

    @Override public boolean onCreate(){return true;}

    @Override public String getType(Uri uri){
        File f=fileFor(uri); String n=f.getName().toLowerCase();
        if(n.endsWith(".jpg")||n.endsWith(".jpeg"))return "image/jpeg";
        if(n.endsWith(".png"))return "image/png";
        if(n.endsWith(".webp"))return "image/webp";
        if(n.endsWith(".gif"))return "image/gif";
        if(n.endsWith(".pdf"))return "application/pdf";
        if(n.endsWith(".txt"))return "text/plain";
        if(n.endsWith(".m4a"))return "audio/mp4";
        if(n.endsWith(".mp3"))return "audio/mpeg";
        if(n.endsWith(".wav"))return "audio/wav";
        if(n.endsWith(".aac"))return "audio/aac";
        return "application/octet-stream";
    }

    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode)&&!"rwt".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(fileFor(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        File f=fileFor(uri);
        MatrixCursor c=new MatrixCursor(projection==null?new String[]{"_display_name","_size"}:projection);
        if(f.exists()){
            Object[] row=new Object[projection==null?2:projection.length];
            if(projection==null){row[0]=f.getName();row[1]=f.length();}
            else for(int i=0;i<projection.length;i++){if("_display_name".equals(projection[i]))row[i]=f.getName();else if("_size".equals(projection[i]))row[i]=f.length();}
            c.addRow(row);
        }
        return c;
    }

    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}

    private File fileFor(Uri uri){
        String path=uri.getPath();
        if(path==null)throw new IllegalArgumentException("Missing file");
        String clean=path.startsWith("/")?path.substring(1):path;
        File base;
        if(clean.startsWith("file/"))base=new File(getContext().getFilesDir(),"vault_files");
        else if(clean.startsWith("backup/"))base=new File(getContext().getFilesDir(),"vault_backups");
        else throw new IllegalArgumentException("Invalid path");
        String name=uri.getLastPathSegment();
        if(name==null||name.contains("..")||name.contains("/"))throw new IllegalArgumentException("Invalid file");
        File f=new File(base,name);
        try {
            if(!f.getCanonicalPath().startsWith(base.getCanonicalPath()+File.separator)) {
                throw new IllegalArgumentException("Invalid file");
            }
        } catch(IOException e) {
            throw new IllegalArgumentException("Invalid file", e);
        }
        return f;
    }
}