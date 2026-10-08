package com.example.calcvault;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class AppSpaceAdminReceiver extends DeviceAdminReceiver {
    private void configureProfile(Context context, Intent intent) {
        DevicePolicyManager dpm=(DevicePolicyManager)context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin=new ComponentName(context,AppSpaceAdminReceiver.class);

        try{
            dpm.setProfileName(admin,"CalcVault Private Apps");
        }catch(Exception ignored){}

        String pkg=null;
        Bundle extras=intent==null?null:intent.getBundleExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        if(extras!=null)pkg=extras.getString("calcvault_clone_package");

        if(pkg!=null&&!pkg.equals(context.getPackageName())&&android.os.Build.VERSION.SDK_INT>=28){
            try{
                dpm.installExistingPackage(admin,pkg);
            }catch(Exception ignored){}
        }

        try{
            dpm.setApplicationHidden(admin,context.getPackageName(),true);
        }catch(Exception ignored){}
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        configureProfile(context,intent);
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context,intent);
        configureProfile(context,intent);
    }
}
