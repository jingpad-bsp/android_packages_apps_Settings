package com.android.settings.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.PhoneConstants;

public class SimLoadedListener {
    /* unisoc: add for bug1450229 @{ */
    private final static String TAG = "SimLoadedListener";
    private Context mContext;
    private SimLoadedListenerClient mClient;

    public interface SimLoadedListenerClient {
        void onSimLoaded(int subId);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive()");
            int subId = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY,
                    SubscriptionManager.INVALID_PHONE_INDEX);
            String state = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            if (state != null) {
                if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(state)) {
                    Log.d(TAG, "state equals" + state);
                    onSimLoadedCallback(subId);
                }
            }
        }
    };

    public SimLoadedListener(Context context, SimLoadedListenerClient client) {
        mContext = context;
        mClient = client;
    }

    public void start() {
        final IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    public void stop() {
        mContext.unregisterReceiver(mReceiver);
    }

    public void onSimLoadedCallback(int subId) {
        mClient.onSimLoaded(subId);
    }
    /* unisoc: add for bug1450229 @} */
}
