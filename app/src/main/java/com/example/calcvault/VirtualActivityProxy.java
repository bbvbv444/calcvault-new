package com.example.calcvault;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Host Activity reserved for the virtual runtime.
 *
 * This is intentionally only a host/proxy shell. It does not claim to execute
 * an arbitrary third-party APK. The next runtime layer will attach the selected
 * virtual instance here after Android component calls are redirected.
 */
public final class VirtualActivityProxy extends Activity {
    public static final String EXTRA_CLONE_ID = "calcvault_virtual_clone_id";
    public static final String EXTRA_VIRTUAL_PACKAGE = "calcvault_virtual_package";
    public static final String EXTRA_APK = "calcvault_virtual_apk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView view = new TextView(this);
        view.setGravity(Gravity.CENTER);
        view.setPadding(48, 48, 48, 48);
        view.setText("Virtual app runtime is preparing…");
        setContentView(view);
    }
}
