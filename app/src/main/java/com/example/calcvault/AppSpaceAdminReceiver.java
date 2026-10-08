package com.example.calcvault;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class AppSpaceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        Toast.makeText(context, "CalcVault Private App Space is ready.", Toast.LENGTH_LONG).show();
    }
}
