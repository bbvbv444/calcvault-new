package com.example.calcvault;

import android.app.Activity;
import java.io.IOException;

/**
 * Defines the safe boundary between code CalcVault can prepare itself and
 * lifecycle work that only Android can perform.
 */
public final class VirtualExecutionBoundary {
    public enum State {
        PREPARED,
        FRAMEWORK_ATTACHMENT_REQUIRED
    }

    private VirtualExecutionBoundary() {}

    public static State inspect(
            VirtualActivityExecutor.Resolution resolution) throws IOException {
        if (resolution == null || resolution.activityClass == null) {
            throw new IOException("Virtual Activity resolution is missing");
        }

        if (!Activity.class.isAssignableFrom(resolution.activityClass)) {
            throw new IOException("Virtual component is not an Activity");
        }

        return State.FRAMEWORK_ATTACHMENT_REQUIRED;
    }

    public static String explain(State state) {
        if (state == State.FRAMEWORK_ATTACHMENT_REQUIRED) {
            return "Android framework Activity attachment is required before lifecycle execution";
        }
        return "Virtual component is prepared";
    }
}
