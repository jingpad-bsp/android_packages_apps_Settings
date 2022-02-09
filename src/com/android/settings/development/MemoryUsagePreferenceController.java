/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.settings.development;

import android.content.Context;
import android.text.format.Formatter;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.applications.ProcStatsData;
import com.android.settings.applications.ProcessStatsBase;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;
import com.android.settingslib.utils.ThreadUtils;

import android.os.SystemProperties;
import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemoryUsagePreferenceController extends DeveloperOptionsPreferenceController implements
        PreferenceControllerMixin {

    private static final String MEMORY_USAGE_KEY = "memory";
    private static final String TAG = "MemoryUsagePreferenceController";

    private ProcStatsData mProcStatsData;

    public MemoryUsagePreferenceController(Context context) {
        super(context);
    }

    @Override
    public String getPreferenceKey() {
        return MEMORY_USAGE_KEY;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);

        mProcStatsData = getProcStatsData();
        setDuration();
    }

    @Override
    public void updateState(Preference preference) {
        // This is posted on the background thread to speed up fragment launch time for dev options
        // mProcStasData.refreshStats(true) takes ~20ms to run.
        ThreadUtils.postOnBackgroundThread(() -> {
            mProcStatsData.refreshStats(true);
            final ProcStatsData.MemInfo memInfo = mProcStatsData.getMemInfo();
            final String usedResult = Formatter.formatShortFileSize(mContext,
                    (long) memInfo.realUsedRam);
            final String totalResult = Formatter.formatShortFileSize(mContext,
                    (long) memInfo.realTotalRam);
            ThreadUtils.postOnMainThread(
                    () -> mPreference.setSummary(mContext.getString(R.string.memory_summary,
                            usedResult, getRamSizeFromProperty())));
        });
    }

    @VisibleForTesting
    void setDuration() {
        mProcStatsData.setDuration(ProcessStatsBase.sDurations[0] /* 3 hours */);
    }

    @VisibleForTesting
    ProcStatsData getProcStatsData() {
        return new ProcStatsData(mContext, false);
    }

    public String getRamSizeFromProperty() {
        String size = SystemProperties.get(SPRD_RAM_SIZE, "unconfig");
        if ("unconfig".equals(size)) {
            Log.d(TAG, "can not get ram size from "+SPRD_RAM_SIZE);
            return "8 GB";
        } else {
            Log.d(TAG, "property value is:" + size);
            String regEx="[^0-9]";
            Pattern p = Pattern.compile(regEx);
            Matcher m = p.matcher(size);
            size = m.replaceAll("").trim();
            long ramSize = Long.parseLong(size);
            return Formatter.formatShortFileSize(mContext, covertUnitsToSI(ramSize));
        }
    }

    private static final String SPRD_RAM_SIZE = "ro.boot.ddrsize";
    private static final int SI_UNITS = 1000;
    private static final int IEC_UNITS = 1024;
    
    /**
     * SI_UNITS = 1000bytes; IEC_UNITS = 1024bytes
     * 512MB = 512 * 1000 * 1000
     * 2048MB = 2048/1024 * 1000 * 1000 * 1000
     * 2000MB = 2000 * 1000 * 1000
     */
    private long covertUnitsToSI(long size) {
        if (size > SI_UNITS && size % IEC_UNITS == 0) {
            return size / IEC_UNITS * SI_UNITS * SI_UNITS * SI_UNITS;
        }
        return size * SI_UNITS * SI_UNITS;
    }

}
