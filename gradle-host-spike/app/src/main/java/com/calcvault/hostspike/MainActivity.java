package com.calcvault.hostspike;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView message = new TextView(this);
        message.setText("CalcVault isolated Gradle host\n\nBuild-path test only. No app cloning is enabled.");
        message.setTextSize(20);
        message.setTextColor(Color.WHITE);
        message.setGravity(Gravity.CENTER);
        message.setPadding(24, 24, 24, 24);
        message.setBackgroundColor(Color.rgb(12, 20, 38));
        setContentView(message);
    }
}
