/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.settings.applications;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Slog;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

/**
 * Screen layout controller
 *
 * @author flypig
 * @since 2021.08.21 21:18
 */
public class ScreenLayoutPreferenceController extends TogglePreferenceController {
    private static final String SCREEN_LAYOUT_PROPERTIES_KEY = "persist.sys.jingos.screen_layout";
    private static final String DEFAULT_VALUE = "1";
    private static final String TAG = "ScreenLayoutPreferenceController";
    private static final String SCREEN_LAYOUT_KEY = "screen_layout_mode";
    private static final boolean DEBUG = false;

    private PreferenceGroup mPreferenceGroup;

    public ScreenLayoutPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public boolean isChecked() {
        String preferenceKey = getPreferenceKey();
        String propVal = SystemProperties.get(SCREEN_LAYOUT_PROPERTIES_KEY, DEFAULT_VALUE);
        return preferenceKey.equals(propVal);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreferenceGroup = screen.findPreference(SCREEN_LAYOUT_KEY);
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        String preferenceKey = getPreferenceKey();
        int childCount = mPreferenceGroup.getPreferenceCount();
        if (DEBUG) {
            Slog.d(TAG,
                    "preferenceKey:" + preferenceKey + " isChecked:" + isChecked + " childCount:"
                            + childCount);
        }
        if (isChecked) {
            for (int i = 0; i < childCount; i++) {
                Preference preference = mPreferenceGroup.getPreference(i);
                if (preference instanceof TwoStatePreference) {
                    ((TwoStatePreference) preference).setChecked(false);
                }
            }
        }
        SystemProperties.set(SCREEN_LAYOUT_PROPERTIES_KEY, preferenceKey);
        return isChecked;
    }
}
