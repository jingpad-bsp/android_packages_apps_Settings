package com.android.settings.network.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

import android.util.Log;

import android.preference.ListPreference;
import android.telephony.SubscriptionManager;

import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.internal.telephony.TelephonyIntents;
import com.android.sprd.telephony.RadioInteractor;

import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.settings.R;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

/**
 * Preference controller for "Stand Alone"
 */
public class MobileNetworkStandAloneController extends TelephonyTogglePreferenceController
        implements  OnStart, OnStop, LifecycleObserver, MobileNetworkStandAloneDialogFragment.MobileNetworkStandAloneDialogListener {

    private static final String DIALOG_TAG = "NetworkStandAloneDialog";
    private static final String LOG_TAG = "NetworkStandAloneController";

    private static final int READ_SA_ONLY = 258;
    private static final int READ_SA_NSA = 66;

    private int mSubId;
    private Context mContext;
    private RadioInteractor mRadioInteractor;
    private SwitchPreference mSwitchPreference;
    private PreferenceScreen mPreferenceScreen;
    private FragmentManager mFragmentManager;

    private final BroadcastReceiver mPhoneChangeReceiver = new PhoneChangeReceiver();

    public void init(FragmentManager fragmentManager, int subId) {
        mFragmentManager = fragmentManager;
        mSubId = subId;
    }

    public MobileNetworkStandAloneController(Context context, String key) {
        super(context, key);
        mContext = context;
        mRadioInteractor = new RadioInteractor(context);
    }

    @Override
    public void onStart() {
        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        mContext.registerReceiver(mPhoneChangeReceiver, intentFilter);
    }

    @Override
    public void onStop() {
        mContext.unregisterReceiver(mPhoneChangeReceiver);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreferenceScreen = screen;
        mSwitchPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        final SwitchPreference switchPreference = (SwitchPreference) preference;
        switchPreference.setVisible(getAvailabilityStatus(mSubId) == AVAILABLE);
        switchPreference.setEnabled(true);
    }

    @Override
    public boolean isChecked() {
        return isStandAloneOn(mSubId);
    }

    private boolean isStandAloneOn(int subId) {
        if (mRadioInteractor != null) {
            int standAloneStatus = mRadioInteractor.getStandAlone(SubscriptionManager.getPhoneId(mSubId));
            Log.d(LOG_TAG, "standAloneStatus : " + standAloneStatus + ", subId: " + mSubId);
            return (standAloneStatus == READ_SA_NSA || standAloneStatus == READ_SA_ONLY);
        }
        return false;
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        boolean isDefaultDataSubId = (mSubId == SubscriptionManager.getDefaultDataSubscriptionId());
        boolean visible = isDefaultDataSubId ? true : false;
        Log.d(LOG_TAG, "getAvailabilityStatus subId: " + mSubId + "isDefaultDataSubId: " + isDefaultDataSubId);
        return visible ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        showDialog(isChecked);
        return false;
    }

    private void showDialog(boolean isChecked) {
        mSwitchPreference.setEnabled(false);
        final MobileNetworkStandAloneDialogFragment dialogFragment = MobileNetworkStandAloneDialogFragment.newInstance(mSubId, isChecked);
        dialogFragment.setController(this);
        dialogFragment.show(mFragmentManager, DIALOG_TAG);
    }

    public void onDialogDismiss(InstrumentedDialogFragment dialog) {
        mSwitchPreference.setEnabled(true);
    }

    private class PhoneChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(LOG_TAG, "onReceive");
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED.equals(action)) {
                updateState(mSwitchPreference);
                displayPreference(mPreferenceScreen);
            }
        }
    }
}
