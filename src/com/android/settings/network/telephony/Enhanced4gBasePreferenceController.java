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
 * limitations under the License.
 */

package com.android.settings.network.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import android.os.PersistableBundle;
import android.util.Log;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.RadioAccessFamily;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.ims.ImsManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyIntents;
import com.android.ims.internal.ImsManagerEx;
import com.android.internal.telephony.RILConstants;
import com.android.internal.util.ArrayUtils;
import com.android.settings.R;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.ArrayList;
import java.util.List;

/**
 * Preference controller for "Enhanced 4G LTE"
 */
public class Enhanced4gBasePreferenceController extends TelephonyTogglePreferenceController
        implements LifecycleObserver, OnStart, OnStop, BroadcastReceiverChanged.BroadcastReceiverChangedClient {

    private static final String LOG_TAG = "Enhanced4gBasePreferenceController";
    private Preference mPreference;
    private TelephonyManager mTelephonyManager;
    private CarrierConfigManager mCarrierConfigManager;
    private PersistableBundle mCarrierConfig;
    @VisibleForTesting
    ImsManager mImsManager;
    private PhoneCallStateListener mPhoneStateListener;
    private final List<On4gLteUpdateListener> m4gLteListeners;

    protected static final int MODE_NONE = -1;
    protected static final int MODE_VOLTE = 0;
    protected static final int MODE_ADVANCED_CALL = 1;
    protected static final int MODE_4G_CALLING = 2;
    // UNISOC: add for Bug 1194931
    protected static final int MSG_VOLTE_SETTINGS = 3;
    private static final long UPDATE_VOLTE_AVOID_FREQUENTLY = 1000;
    private int m4gCurrentMode = MODE_NONE;

    ContentObserver mContentObserver;
    // UNISOC: fix for bug 1139650
    private BroadcastReceiverChanged mBroadcastReceiverChanged;
    private PreferenceScreen mPreferenceScreen;

    private String mVolteTitle = null;

    public Enhanced4gBasePreferenceController(Context context, String key) {
        super(context, key);
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        m4gLteListeners = new ArrayList<>();
        mPhoneStateListener = new PhoneCallStateListener(Looper.getMainLooper());
        // UNISOC: fix for bug 1139650
        mBroadcastReceiverChanged = new BroadcastReceiverChanged(context,this);
        mIntentFilter = new IntentFilter();
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        init(subId);
        if (!isModeMatched()) {
            return CONDITIONALLY_UNAVAILABLE;
        }
        final boolean isVisible = subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && isEnhance4gLteEnabled(subId);
        return isVisible
                ? (is4gLtePrefEnabled() ? AVAILABLE : AVAILABLE_UNSEARCHABLE)
                : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreferenceScreen = screen;
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onStart() {
        mPhoneStateListener.register(mSubId);
        // UNISOC: fix for bug 1139650
        mBroadcastReceiverChanged.start();
        mIntentFilter.addAction(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        mContext.registerReceiver(mIntentReceiver, mIntentFilter);

        final ContentResolver cr = mContext.getContentResolver();
        mContentObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                Log.i(LOG_TAG,"onChange");
                /* UNISOC: fix for bug 1194931@{ */
                if (mHandler.hasMessages(MSG_VOLTE_SETTINGS, mSubId)) {
                    mHandler.removeMessages(MSG_VOLTE_SETTINGS, mSubId);
                }
                mHandler.sendMessageDelayed(
                      mHandler.obtainMessage(MSG_VOLTE_SETTINGS, mSubId),
                      UPDATE_VOLTE_AVOID_FREQUENTLY);
                /* @} */
            }
        };

        cr.registerContentObserver(MobileNetworkUtils.getNotifyContentUri(
                SubscriptionManager.ADVANCED_CALLING_ENABLED_CONTENT_URI, true, mSubId),
                true, mContentObserver); //UNISOC:fix for bug 1104230

        cr.registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.PREFERRED_NETWORK_MODE + mSubId),
                false /* notifyForDescendants */, mContentObserver /* observer */);
    }

    @Override
    public void onStop() {
        mPhoneStateListener.unregister();
        mContext.unregisterReceiver(mIntentReceiver);
        if (mContentObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        }
        // UNISOC: fix for bug 1139650
        mBroadcastReceiverChanged.stop();
    }

    /* UNISOC: fix for bug 1516594@{ */
    private IntentFilter mIntentFilter;
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
                if (mHandler.hasMessages(MSG_VOLTE_SETTINGS, mSubId)) {
                    mHandler.removeMessages(MSG_VOLTE_SETTINGS, mSubId);
                }
                mHandler.sendMessageDelayed(
                        mHandler.obtainMessage(MSG_VOLTE_SETTINGS, mSubId),
                        UPDATE_VOLTE_AVOID_FREQUENTLY);
            }
        }
    };
    /* @} */

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (!(preference instanceof SwitchPreference)) {
            return;
        }

        final SwitchPreference switchPreference = (SwitchPreference) preference;
        switchPreference.setVisible(getAvailabilityStatus(mSubId) == AVAILABLE);
        mVolteTitle = switchPreference.getTitle().toString();
        switchPreference.setEnabled(is4gLtePrefEnabled() && isSimStateLoaded(mSubId));
        switchPreference.setChecked(mImsManager.isEnhanced4gLteModeSettingEnabledByUser()
                && mImsManager.isNonTtyOrTtyOnVolteEnabled());
    }

    @Override
    public void onPhoneStateChanged() {
        Log.d(LOG_TAG,"onPhoneStateChanged");
        updateState(mPreference);
    }

    /* UNISOC: fix for bug 1146093 @{ */
    @Override
    public void onCarrierConfigChanged(int phoneId) {
        if(SubscriptionManager.isValidPhoneId(phoneId) && SubscriptionManager.getPhoneId(mSubId) == phoneId) {
            if(SubscriptionManager.getSimStateForSlotIndex(phoneId) ==TelephonyManager.SIM_STATE_LOADED) {
                Log.i(LOG_TAG,"onCarrierConfigChanged");
                mCarrierConfig = mCarrierConfigManager.getConfigForSubId(mSubId);
                if(mPreferenceScreen != null) {
                    this.displayPreference(mPreferenceScreen);
                    updateState(mPreference);
                }
            }
        }
    }
    /* @} */

    @Override
    public boolean setChecked(boolean isChecked) {
        /* UNISOC: fix for bug 1073296@{ */
        boolean isDefaultDataSubId = (mSubId == SubscriptionManager.getDefaultDataSubscriptionId());
        if (isDefaultDataSubId && !isImsTurnOffAllowed() && !isChecked ) {
            //UNISOC: fix for bug 1122730
            String turnOffImsError = String.format(mContext.getResources().getString(R.string.turn_off_ims_error),mVolteTitle);
            Toast.makeText(mContext, turnOffImsError,Toast.LENGTH_LONG).show();
            return false;
        }

        /* @} */
        Log.d(LOG_TAG, "setChecked mSubId = " + mSubId);
        mImsManager.setEnhanced4gLteModeSetting(isChecked);
        for (final On4gLteUpdateListener lsn : m4gLteListeners) {
            lsn.on4gLteUpdated();
        }
        return true;
    }

    @Override
    public boolean isChecked() {
        return mImsManager.isEnhanced4gLteModeSettingEnabledByUser();
    }

    public Enhanced4gBasePreferenceController init(int subId) {
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && mSubId == subId) {
            return this;
        }
        mSubId = subId;
        mTelephonyManager = TelephonyManager.from(mContext).createForSubscriptionId(mSubId);
        mCarrierConfig = mCarrierConfigManager.getConfigForSubId(mSubId);
        Log.d(LOG_TAG, "init mSubId = " + mSubId
                + ", phoneId = " + SubscriptionManager.getPhoneId(mSubId));
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            mImsManager = ImsManager.getInstance(mContext, SubscriptionManager.getPhoneId(mSubId));
        }

        final boolean show4GForLTE = mCarrierConfig.getBoolean(
                CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL);
        m4gCurrentMode = mCarrierConfig.getInt(
                CarrierConfigManager.KEY_ENHANCED_4G_LTE_TITLE_VARIANT_INT);
        if (m4gCurrentMode != MODE_ADVANCED_CALL) {
            m4gCurrentMode = show4GForLTE ? MODE_4G_CALLING : MODE_VOLTE;
        }

        return this;
    }

    private boolean isEnhance4gLteEnabled(int subId) {
        final ImsManager imsManager = subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                ? ImsManager.getInstance(mContext, SubscriptionManager.getPhoneId(subId)) : null;
                final PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(subId);
                return imsManager != null && carrierConfig != null
                        && imsManager.isVolteEnabledByPlatform()
                        && imsManager.isVolteProvisionedOnDevice()
                        && MobileNetworkUtils.isImsServiceStateReady(mImsManager)
                        && isSupportLTE()
                        && !carrierConfig.getBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL);
    }

    public Enhanced4gBasePreferenceController addListener(On4gLteUpdateListener lsn) {
        m4gLteListeners.add(lsn);
        return this;
    }

    protected int getMode() {
        return MODE_NONE;
    }

    private boolean isModeMatched() {
        return m4gCurrentMode == getMode();
    }

    /* UNISOC: fix for bug 1073296@{ */
    private boolean isImsTurnOffAllowed() {
        return !ImsManagerEx.synSettingForWFCandVoLTE(mContext) || (!ImsManager.isWfcEnabledByPlatform(mContext)
                || !ImsManager.isWfcEnabledByUser(mContext));
    }
    /* @} */

    private boolean is4gLtePrefEnabled() {
        //UNISOC:fix for bug 1109840
        return mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && !isInCall()
                && mImsManager != null
                && mImsManager.isNonTtyOrTtyOnVolteEnabled()
                && mCarrierConfig.getBoolean(
                CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL);
    }

    private boolean isSupportLTE() {
        boolean isSupportLTE = true;
        final int settingsNetworkMode = Settings.Global.getInt(
                mContext.getContentResolver(),
                Settings.Global.PREFERRED_NETWORK_MODE + mSubId,
                Phone.PREFERRED_NT_MODE);
        if ((settingsNetworkMode == RILConstants.NETWORK_MODE_NR_LTE_GSM_WCDMA)
                && (mSubId == SubscriptionManager.getDefaultDataSubscriptionId())) {
            isSupportLTE = false;
        }

        return isSupportLTE;
    }

    private class PhoneCallStateListener extends PhoneStateListener {

        public PhoneCallStateListener(Looper looper) {
            super(looper);
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            updateState(mPreference);
        }

        public void register(int subId) {
            mSubId = subId;
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_CALL_STATE);
        }

        public void unregister() {
            mTelephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
        }
    }

    /**
     * Update other preferences when 4gLte state is changed
     */
    public interface On4gLteUpdateListener {
        void on4gLteUpdated();
    }

    /*UNISOC: Add for bug 1109840 @{ */
    private boolean isInCall() {
        return getTelecommManager().isInCall();
    }

    private TelecomManager getTelecommManager() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }
    /* @} */

    private boolean isSimStateLoaded(int subId) {
        int phoneId = SubscriptionManager.getPhoneId(subId);
        if(SubscriptionManager.isValidPhoneId(phoneId)) {
            return SubscriptionManager.getSimStateForSlotIndex(phoneId) ==TelephonyManager.SIM_STATE_LOADED;
        }

        return false;
    }

    /* UNISOC: fix for bug 1194931@{ */
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
         @Override
         public void handleMessage(Message msg) {
             switch (msg.what) {
                 case MSG_VOLTE_SETTINGS:
                     if(mPreferenceScreen != null) {
                         displayPreference(mPreferenceScreen);//UNISOC:fix for bug 1442142
                         updateState(mPreference);
                         /*UNISOC: Add for bug 1583543 @{ */
                         for (final On4gLteUpdateListener lsn : m4gLteListeners) {
                             lsn.on4gLteUpdated();
                         }
                         /* @} */
                     }
                     break;
                 default:
                     break;
            }
        }
    };
    /* @} */
}