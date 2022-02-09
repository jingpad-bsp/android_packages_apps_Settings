package com.android.settings.gestures;

import android.content.Context;
import android.provider.Settings;

import com.android.settings.core.TogglePreferenceController;

public class TouchpadTapController extends TogglePreferenceController {

    private final int ON = 1;
    private final int OFF = 0;

    public TouchpadTapController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean isChecked() {
        return Settings.System.getInt(mContext.getContentResolver(), Settings.System.DISABLED_TOUCHPAD_TAP, ON) == ON;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        Settings.System.putInt(mContext.getContentResolver(), Settings.System.DISABLED_TOUCHPAD_TAP, isChecked ? ON : OFF);
        return true;
    }
}
