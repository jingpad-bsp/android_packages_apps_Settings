package com.android.settings.password;

import android.app.admin.DevicePolicyManager;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toolbar;

import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;

import java.util.ArrayList;

import androidx.fragment.app.Fragment;

/**
 * Created by xty on 2021/6/26.
 */
public class JingSimpleConfirmLockPassword extends ConfirmDeviceCredentialBaseActivity{
    private static final String TAG = "JingSimpleConfirmLockPassword";
    private InputKeyListener mInputKeyListener;

    public static class InternalActivity extends JingSimpleConfirmLockPassword {
    }

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, ConfirmLockPasswordFragment.class.getName());
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (ConfirmLockPasswordFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }


    @Override
    protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        super.onApplyThemeResource(theme, resid, first);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_content);
        if (fragment != null && fragment instanceof ConfirmLockPasswordFragment) {
            ((ConfirmLockPasswordFragment)fragment).onWindowFocusChanged(hasFocus);
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setTitle(getText(R.string.jingos_lock_toolbar));
    }

    public void setInputKeyListener(InputKeyListener listener) {
        mInputKeyListener = listener;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_0:
                mInputKeyListener.inputKeyDown(0);
                break;
            case KeyEvent.KEYCODE_1:
                mInputKeyListener.inputKeyDown(1);
                break;
            case KeyEvent.KEYCODE_2:
                mInputKeyListener.inputKeyDown(2);
                break;
            case KeyEvent.KEYCODE_3:
                mInputKeyListener.inputKeyDown(3);
                break;
            case KeyEvent.KEYCODE_4:
                mInputKeyListener.inputKeyDown(4);
                break;
            case KeyEvent.KEYCODE_5:
                mInputKeyListener.inputKeyDown(5);
                break;
            case KeyEvent.KEYCODE_6:
                mInputKeyListener.inputKeyDown(6);
                break;
            case KeyEvent.KEYCODE_7:
                mInputKeyListener.inputKeyDown(7);
                break;
            case KeyEvent.KEYCODE_8:
                mInputKeyListener.inputKeyDown(8);
                break;
            case KeyEvent.KEYCODE_9:
                mInputKeyListener.inputKeyDown(9);
                break;
            case KeyEvent.KEYCODE_ENTER:
                mInputKeyListener.inputKeyDown(KeyEvent.KEYCODE_ENTER);
                break;
            case KeyEvent.KEYCODE_DEL:
                mInputKeyListener.inputKeyDown(KeyEvent.KEYCODE_DEL);
                break;

        }
        return super.onKeyDown(keyCode, event);
    }

    public static class ConfirmLockPasswordFragment extends ConfirmDeviceCredentialBaseFragment
            implements View.OnClickListener, CredentialCheckResultTracker.Listener, InputKeyListener {
        private static final String FRAGMENT_TAG_CHECK_LOCK_RESULT = "check_lock_result";
        private AsyncTask<?, ?, ?> mPendingLockCheck;
        private CredentialCheckResultTracker mCredentialCheckResultTracker;
        private boolean mDisappearing = false;
        private CountDownTimer mCountdownTimer;
        private boolean mIsAlpha;

        private TextView numberTv1;
        private TextView numberTv2;
        private TextView numberTv3;
        private TextView numberTv4;
        private TextView numberTv5;
        private TextView numberTv6;
        private TextView numberTv7;
        private TextView numberTv8;
        private TextView numberTv9;
        private TextView numberTv0;
        private TextView numberDelTv;
        private TextView numberNextOrConfirmTv;
        private TextView mPasswordSummaryTv;
        private TextView mPasswdTitleTv;
        private StringBuilder mPasswdSb;
        private ImageView numberIv1;
        private ImageView numberIv2;
        private ImageView numberIv3;
        private ImageView numberIv4;
        private ImageView numberIv5;
        private ImageView numberIv6;
        private ArrayList<ImageView> mImageViewArrayList;

        // required constructor for fragments
        public ConfirmLockPasswordFragment() {

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if ((getActivity() instanceof JingSimpleConfirmLockPassword)) {
                ((JingSimpleConfirmLockPassword) getActivity()).setInputKeyListener(this);
            }
        }

        @Override
        public void inputKeyDown(int keyCode) {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                handleNext();
            } else if (keyCode == KeyEvent.KEYCODE_DEL) {
                delPasswd();
            } else {
                inputPasswd(keyCode);
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            final int storedQuality = mLockPatternUtils.getKeyguardStoredPasswordQuality(
                    mEffectiveUserId);

            View view = inflater.inflate(R.layout.jing_simple_lock_password, container, false);

            mIsAlpha = DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC == storedQuality
                    || DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC == storedQuality
                    || DevicePolicyManager.PASSWORD_QUALITY_COMPLEX == storedQuality
                    || DevicePolicyManager.PASSWORD_QUALITY_MANAGED == storedQuality;
            mCredentialCheckResultTracker = (CredentialCheckResultTracker) getFragmentManager()
                    .findFragmentByTag(FRAGMENT_TAG_CHECK_LOCK_RESULT);
            if (mCredentialCheckResultTracker == null) {
                mCredentialCheckResultTracker = new CredentialCheckResultTracker();
                getFragmentManager().beginTransaction().add(mCredentialCheckResultTracker,
                        FRAGMENT_TAG_CHECK_LOCK_RESULT).commit();
            }
            initData(view);
            initListener();
            return view;
        }

        private void initData(View view) {
            numberTv0 = view.findViewById(R.id.tv_number0);
            numberTv1 = view.findViewById(R.id.tv_number1);
            numberTv2 = view.findViewById(R.id.tv_number2);
            numberTv3 = view.findViewById(R.id.tv_number3);
            numberTv4 = view.findViewById(R.id.tv_number4);
            numberTv5 = view.findViewById(R.id.tv_number5);
            numberTv6 = view.findViewById(R.id.tv_number6);
            numberTv7 = view.findViewById(R.id.tv_number7);
            numberTv8 = view.findViewById(R.id.tv_number8);
            numberTv9 = view.findViewById(R.id.tv_number9);
            numberTv0 = view.findViewById(R.id.tv_number0);

            numberDelTv = view.findViewById(R.id.tv_number_del);
            numberNextOrConfirmTv = view.findViewById(R.id.tv_number_next);
            mErrorTextView = (TextView) view.findViewById(R.id.errorText);
            mErrorTextView.setVisibility(View.VISIBLE);

            numberIv1 = view.findViewById(R.id.iv_number1);
            numberIv2 = view.findViewById(R.id.iv_number2);
            numberIv3 = view.findViewById(R.id.iv_number3);
            numberIv4 = view.findViewById(R.id.iv_number4);
            numberIv5 = view.findViewById(R.id.iv_number5);
            numberIv6 = view.findViewById(R.id.iv_number6);
            mPasswdTitleTv = view.findViewById(R.id.tv_input_passwd_title);
            mPasswordSummaryTv = view.findViewById(R.id.tv_password_summary);
            mPasswordSummaryTv.setVisibility(View.GONE);
            mPasswdSb = new StringBuilder(8);
            mImageViewArrayList = new ArrayList<ImageView>();
            mImageViewArrayList.add(numberIv1);
            mImageViewArrayList.add(numberIv2);
            mImageViewArrayList.add(numberIv3);
            mImageViewArrayList.add(numberIv4);
            mImageViewArrayList.add(numberIv5);
            mImageViewArrayList.add(numberIv6);

            mPasswdTitleTv.setText(R.string.jingos_lock_summary_confirm);
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.tv_number0:
                    inputPasswd(0);
                    break;
                case R.id.tv_number1:
                    inputPasswd(1);
                    break;
                case R.id.tv_number2:
                    inputPasswd(2);
                    break;
                case R.id.tv_number3:
                    inputPasswd(3);
                    break;
                case R.id.tv_number4:
                    inputPasswd(4);
                    break;
                case R.id.tv_number5:
                    inputPasswd(5);
                    break;
                case R.id.tv_number6:
                    inputPasswd(6);
                    break;
                case R.id.tv_number7:
                    inputPasswd(7);
                    break;
                case R.id.tv_number8:
                    inputPasswd(8);
                    break;
                case R.id.tv_number9:
                    inputPasswd(9);
                    break;
                case R.id.tv_number_del:
                    delPasswd();
                    break;
                case R.id.tv_number_next:
                    if (mPasswdSb.length() < 6) return;
                    handleNext();
                    break;
            }
        }

        private void initListener() {
            numberTv0.setOnClickListener(this);
            numberTv1.setOnClickListener(this);
            numberTv2.setOnClickListener(this);
            numberTv3.setOnClickListener(this);
            numberTv4.setOnClickListener(this);
            numberTv5.setOnClickListener(this);
            numberTv6.setOnClickListener(this);
            numberTv7.setOnClickListener(this);
            numberTv8.setOnClickListener(this);
            numberTv9.setOnClickListener(this);
            numberDelTv.setOnClickListener(this);
            numberNextOrConfirmTv.setOnClickListener(this);
        }

        private void inputPasswd(int passwd) {
            if (mPasswdSb.length() > 5) return;
            mPasswdSb.append(passwd);
            updateImageviewCircle(true);
        }

        private void delPasswd() {
            if (TextUtils.isEmpty(mPasswdSb)) return;
            mPasswdSb.deleteCharAt(mPasswdSb.length() - 1);
            updateImageviewCircle(false);
        }

        private void updateImageviewCircle(boolean plus) {
            int number = mPasswdSb.length();
            if (number == 0) {
                numberDelTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
            } else if (number == 6) {
                numberNextOrConfirmTv.setEnabled(true);
                numberNextOrConfirmTv.setTextColor(getResources().getColor(R.color.jingos_passwd_next_blue));
            } else {
                numberDelTv.setTextColor(getResources().getColor(R.color.jingos_passwd_next_blue));
                numberNextOrConfirmTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
            }
            if (plus) {
                mImageViewArrayList.get(number - 1).setImageResource(R.drawable.number_circle_hl);
            } else {
                mImageViewArrayList.get(number).setImageResource(R.drawable.number_circle_de);
            }
        }

        private void resetCircleImageView() {
            for (int i = 0; i < mImageViewArrayList.size(); i++) {
                mImageViewArrayList.get(i).setImageResource(R.drawable.number_circle_de);
            }
        }

        private int getErrorMessage() {
            return mIsAlpha ? R.string.lockpassword_invalid_password
                    : R.string.lockpassword_invalid_pin;
        }

        @Override
        protected int getLastTryErrorMessage(int userType) {
            switch (userType) {
                case USER_TYPE_PRIMARY:
                    return mIsAlpha ? R.string.lock_last_password_attempt_before_wipe_device
                            : R.string.lock_last_pin_attempt_before_wipe_device;
                case USER_TYPE_MANAGED_PROFILE:
                    return mIsAlpha ? R.string.lock_last_password_attempt_before_wipe_profile
                            : R.string.lock_last_pin_attempt_before_wipe_profile;
                case USER_TYPE_SECONDARY:
                    return mIsAlpha ? R.string.lock_last_password_attempt_before_wipe_user
                            : R.string.lock_last_pin_attempt_before_wipe_user;
                default:
                    throw new IllegalArgumentException("Unrecognized user type:" + userType);
            }
        }

        @Override
        public void prepareEnterAnimation() {
            super.prepareEnterAnimation();
        }

        @Override
        public void startEnterAnimation() {
            super.startEnterAnimation();
        }

        @Override
        public void onPause() {
            super.onPause();
            if (mCountdownTimer != null) {
                mCountdownTimer.cancel();
                mCountdownTimer = null;
            }
            mCredentialCheckResultTracker.setListener(null);
        }

        @Override
        public int getMetricsCategory() {
            return SettingsEnums.CONFIRM_LOCK_PASSWORD;
        }

        @Override
        public void onResume() {
            super.onResume();
            long deadline = mLockPatternUtils.getLockoutAttemptDeadline(mEffectiveUserId);
            if (deadline != 0) {
                mCredentialCheckResultTracker.clearResult();
                handleAttemptLockout(deadline);
            } else {
                updateErrorMessage(
                        mLockPatternUtils.getCurrentFailedPasswordAttempts(mEffectiveUserId));
            }
            mCredentialCheckResultTracker.setListener(this);
        }

        @Override
        protected void authenticationSucceeded() {
            mCredentialCheckResultTracker.setResult(true, new Intent(), 0, mEffectiveUserId);
        }

        public void onWindowFocusChanged(boolean hasFocus) {
            if (!hasFocus) {
                return;
            }
        }

        private void handleNext() {
            if (mPendingLockCheck != null || mDisappearing) {
                return;
            }

            // TODO(b/120484642): This is a point of entry for passwords from the UI
            final byte[] pin = LockPatternUtils.charSequenceToByteArray(mPasswdSb.toString());
            if (pin == null || pin.length == 0) {
                return;
            }

            final boolean verifyChallenge = getActivity().getIntent().getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, false);

            Intent intent = new Intent();
            if (verifyChallenge)  {
                if (isInternalActivity()) {
                    startVerifyPassword(pin, intent);
                    return;
                }
            } else {
                startCheckPassword(pin, intent);
                return;
            }

            mCredentialCheckResultTracker.setResult(false, intent, 0, mEffectiveUserId);
        }

        private boolean isInternalActivity() {
            return getActivity() instanceof JingSimpleConfirmLockPassword.InternalActivity;
        }

        private void startVerifyPassword(final byte[] pin, final Intent intent) {
            long challenge = getActivity().getIntent().getLongExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, 0);
            final int localEffectiveUserId = mEffectiveUserId;
            final int localUserId = mUserId;
            final LockPatternChecker.OnVerifyCallback onVerifyCallback =
                    new LockPatternChecker.OnVerifyCallback() {
                        @Override
                        public void onVerified(byte[] token, int timeoutMs) {
                            mPendingLockCheck = null;
                            boolean matched = false;
                            if (token != null) {
                                matched = true;
                                if (mReturnCredentials) {
                                    intent.putExtra(
                                            ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN,
                                            token);
                                }
                            }
                            mCredentialCheckResultTracker.setResult(matched, intent, timeoutMs,
                                    localEffectiveUserId);
                        }
                    };
            mPendingLockCheck = (localEffectiveUserId == localUserId)
                    ? LockPatternChecker.verifyPassword(
                    mLockPatternUtils, pin, challenge, localUserId, onVerifyCallback)
                    : LockPatternChecker.verifyTiedProfileChallenge(
                    mLockPatternUtils, pin, false, challenge, localUserId,
                    onVerifyCallback);
        }

        private void startCheckPassword(final byte[] pin, final Intent intent) {
            final int localEffectiveUserId = mEffectiveUserId;
            mPendingLockCheck = LockPatternChecker.checkPassword(
                    mLockPatternUtils,
                    pin,
                    localEffectiveUserId,
                    new LockPatternChecker.OnCheckCallback() {
                        @Override
                        public void onChecked(boolean matched, int timeoutMs) {
                            mPendingLockCheck = null;
                            if (matched && isInternalActivity() && mReturnCredentials) {
                                intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_TYPE,
                                        mIsAlpha ? StorageManager.CRYPT_TYPE_PASSWORD
                                                : StorageManager.CRYPT_TYPE_PIN);
                                intent.putExtra(
                                        ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, pin);
                            }
                            mCredentialCheckResultTracker.setResult(matched, intent, timeoutMs,
                                    localEffectiveUserId);
                        }
                    });
        }

        private void startDisappearAnimation(final Intent intent) {
            if (mDisappearing) {
                return;
            }
            mDisappearing = true;

            final JingSimpleConfirmLockPassword activity = (JingSimpleConfirmLockPassword) getActivity();
            // Bail if there is no active activity.
            if (activity == null || activity.isFinishing()) {
                return;
            }
            activity.setResult(RESULT_OK, intent);
            activity.finish();
        }

        private void onPasswordChecked(boolean matched, Intent intent, int timeoutMs,
                                       int effectiveUserId, boolean newResult) {
            if (matched) {
                if (newResult) {
                    ConfirmDeviceCredentialUtils.reportSuccessfulAttempt(mLockPatternUtils,
                            mUserManager, mEffectiveUserId);
                }
                mBiometricManager.onConfirmDeviceCredentialSuccess();
                startDisappearAnimation(intent);
                ConfirmDeviceCredentialUtils.checkForPendingIntent(getActivity());
            } else {
                if (timeoutMs > 0) {
                    refreshLockScreen();
                    long deadline = mLockPatternUtils.setLockoutAttemptDeadline(
                            effectiveUserId, timeoutMs);
                    handleAttemptLockout(deadline);
                } else {
                    showError(getErrorMessage(), CLEAR_WRONG_ATTEMPT_TIMEOUT_MS);
                    resetCircleImageView();
                }
                if (newResult) {
                    reportFailedAttempt();
                }
            }
        }

        @Override
        public void onCredentialChecked(boolean matched, Intent intent, int timeoutMs,
                                        int effectiveUserId, boolean newResult) {
            onPasswordChecked(matched, intent, timeoutMs, effectiveUserId, newResult);
        }

        @Override
        protected void onShowError() {
            mPasswdSb.delete(0, mPasswdSb.length());
        }

        private void handleAttemptLockout(long elapsedRealtimeDeadline) {
            mCountdownTimer = new CountDownTimer(
                    elapsedRealtimeDeadline - SystemClock.elapsedRealtime(),
                    LockPatternUtils.FAILED_ATTEMPT_COUNTDOWN_INTERVAL_MS) {

                @Override
                public void onTick(long millisUntilFinished) {
                    // UNISOC: Fix for bug 1213187
                    final int secondsCountdown = (int) (millisUntilFinished / 1000) + 1;
                    String errorText = getResources().getQuantityString(
                            R.plurals.lockpattern_too_many_failed_confirmation_attempts,
                            secondsCountdown, secondsCountdown);
                    showError(errorText, 0);
                }

                @Override
                public void onFinish() {
                    mErrorTextView.setText("");
                    updateErrorMessage(
                            mLockPatternUtils.getCurrentFailedPasswordAttempts(mEffectiveUserId));
                }
            }.start();
        }
    }

    public interface InputKeyListener{
        void inputKeyDown(int keyCode);
    }
}
