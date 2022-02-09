package com.android.settings.network.telephony;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.sprd.telephony.RadioInteractor;

/**
 * A dialog fragment that asks the user if they need to restart the phone to switch the networking mode
 */
public class MobileNetworkStandAloneDialogFragment extends InstrumentedDialogFragment implements OnClickListener {

    public interface MobileNetworkStandAloneDialogListener {
        void onDialogDismiss(InstrumentedDialogFragment dialog);
    }

    private static final String LOG_TAG = "NetworkStandAloneDialog";

    public static final String SUB_ID_KEY = "sub_id_key";
    public static final String IS_CHECKED_KEY = "is_checked_key";

    private boolean mIsChecked;
    private int mSubId;
    private MobileNetworkStandAloneDialogListener mListener;
    private PowerManager mPowerManager;
    private RadioInteractor mRadioInteractor;

    private static final int SET_SA_NSA = 132;
    private static final int SET_NSA_ONLY = 260;

    public static MobileNetworkStandAloneDialogFragment newInstance(int subId, boolean isChecked) {
        final MobileNetworkStandAloneDialogFragment dialogFragment = new MobileNetworkStandAloneDialogFragment();
        Bundle args = new Bundle();
        args.putInt(SUB_ID_KEY, subId);
        args.putBoolean(IS_CHECKED_KEY, isChecked);
        dialogFragment.setArguments(args);

        return dialogFragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Bundle args = getArguments();
        mSubId = args.getInt(SUB_ID_KEY);
        mIsChecked = args.getBoolean(IS_CHECKED_KEY);
        Log.d(LOG_TAG, "mSubId: " + mSubId + ", mIsChecked: " + mIsChecked);
        mRadioInteractor = new RadioInteractor(context);
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        int title = android.R.string.dialog_alert_title;
        int message = R.string.sa_switch_dialog;
        builder.setMessage(getResources().getString(message))
                .setTitle(title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.yes, this)
                .setNegativeButton(android.R.string.no, this);
        return builder.create();
    }

    @Override
    public int getMetricsCategory() {
        return 0;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        // let the host know that the positive button has been clicked
        if (which == dialog.BUTTON_POSITIVE) {
            int standAloneSwitchMode = 0;
            boolean standAloneSwitch = mIsChecked;
            standAloneSwitchMode = standAloneSwitch ? SET_SA_NSA : SET_NSA_ONLY;
            if (mRadioInteractor != null) {
                Log.d(LOG_TAG, "standAloneSwitchMode : " + standAloneSwitchMode);
                int result = mRadioInteractor.setStandAlone(standAloneSwitchMode, SubscriptionManager.getPhoneId(mSubId));
                if (result == 0) {
                    mPowerManager.reboot("");
                } else {
                    Log.d(LOG_TAG, "exception in set stand alone mode");
                }
            }
        }
    }

    public void setController(MobileNetworkStandAloneController MobileNetworkStandAloneDialogListener){
        mListener = (MobileNetworkStandAloneDialogListener) MobileNetworkStandAloneDialogListener;
    }

    public void onDismiss(DialogInterface dialog) {
        if (mListener != null) {
            mListener.onDialogDismiss(this);
        }
        super.onDismiss(dialog);
    }
}
