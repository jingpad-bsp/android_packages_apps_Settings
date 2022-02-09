/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.settings.display;

import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemProperties;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DarkUIPreferenceController extends TogglePreferenceController implements
        LifecycleObserver, OnStart, OnStop {

    public static final String DARK_MODE_PREFS = "dark_mode_prefs";
    public static final String PREF_DARK_MODE_DIALOG_SEEN = "dark_mode_dialog_seen";
    public static final int DIALOG_SEEN = 1;
    //bug 1113470: modify summary of dark theme when Power saving mode is turned on
    private final boolean isSupportSprdPowerManager = (1 == SystemProperties.getInt("persist.sys.pwctl.enable", 1));

    @VisibleForTesting
    SwitchPreference mPreference;

    private UiModeManager mUiModeManager;
    private PowerManager mPowerManager;
    private Context mContext;

    private Fragment mFragment;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateEnabledStateIfNeeded();
        }
    };

    public DarkUIPreferenceController(Context context, String key) {
        super(context, key);
        mContext = context;
        mUiModeManager = context.getSystemService(UiModeManager.class);
        mPowerManager = context.getSystemService(PowerManager.class);
    }

    @Override
    public boolean isChecked() {
        return mUiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        updateEnabledStateIfNeeded();
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        final boolean dialogSeen =
                Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.DARK_MODE_DIALOG_SEEN, 0) == DIALOG_SEEN;
        if (!dialogSeen && isChecked) {
            showDarkModeDialog();
            return false;
        }

        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.schedule(new Runnable() {
            @Override
            public void run() {
                mUiModeManager.setNightMode(isChecked
                ? UiModeManager.MODE_NIGHT_YES
                : UiModeManager.MODE_NIGHT_NO);
            }
        }, 300, TimeUnit.MILLISECONDS);

        return true;
    }

    private void showDarkModeDialog() {
        final DarkUIInfoDialogFragment frag = new DarkUIInfoDialogFragment();
        if (mFragment.getFragmentManager() != null) {
            frag.show(mFragment.getFragmentManager(), getClass().getName());
        }
    }

    @VisibleForTesting
    void updateEnabledStateIfNeeded() {
        if (mPreference == null) {
            return;
        }
        boolean isBatterySaver = isPowerSaveMode();
        mPreference.setEnabled(!isBatterySaver);
        if (isBatterySaver) {
            //bug 1113470: modify summary of dark theme when Power saving mode is turned on
            if (isSupportSprdPowerManager) {
                int stringId = R.string.unisoc_dark_ui_mode_disabled_summary_dark_theme_on;
                mPreference.setSummary(mContext.getString(stringId));
            } else {
                int stringId = R.string.dark_ui_mode_disabled_summary_dark_theme_on;
                mPreference.setSummary(mContext.getString(stringId));
            }
        } else {
            mPreference.setSummary(null);
        }
    }

    @VisibleForTesting
    boolean isPowerSaveMode() {
        return mPowerManager.isPowerSaveMode();
    }

    @VisibleForTesting
    void setUiModeManager(UiModeManager uiModeManager) {
        mUiModeManager = uiModeManager;
    }

    public void setParentFragment(Fragment fragment) {
        mFragment = fragment;
    }

    @Override
    public void onStart() {
        mContext.registerReceiver(mReceiver,
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
    }

    @Override
    public void onStop() {
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }
}
