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
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toolbar;

import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;

import androidx.fragment.app.Fragment;
/**
 * Created by xty on 2021/6/26.
 */
public class JingComplexConfirmLockPassword extends ConfirmDeviceCredentialBaseActivity{
    private static final String TAG = "JingComplexConfirmLockPassword";

    public static class InternalActivity extends JingComplexConfirmLockPassword {
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
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_content);
        if (fragment != null && fragment instanceof ConfirmLockPasswordFragment) {
            ((ConfirmLockPasswordFragment)fragment).onWindowFocusChanged(hasFocus);
        }
    }

    @Override
    protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        super.onApplyThemeResource(theme, resid, first);
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setTitle(getText(R.string.jingos_lock_toolbar));
    }

    public static class ConfirmLockPasswordFragment extends ConfirmDeviceCredentialBaseFragment
            implements View.OnClickListener, CredentialCheckResultTracker.Listener {
        private static final String FRAGMENT_TAG_CHECK_LOCK_RESULT = "check_lock_result";
        private AsyncTask<?, ?, ?> mPendingLockCheck;
        private CredentialCheckResultTracker mCredentialCheckResultTracker;
        private boolean mDisappearing = false;
        private CountDownTimer mCountdownTimer;
        private boolean mIsAlpha;

        private EditText mPasswdEt;
        private ImageView mPasswdVisibleIv;
        private TextView mComplexPasswdTitleTv;
        private TextView mComplexPasswdSummaryTv;
        private TextView mPasswdNextTv;
        private boolean isHidePasswd;

        // required constructor for fragments
        public ConfirmLockPasswordFragment() {

        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            final int storedQuality = mLockPatternUtils.getKeyguardStoredPasswordQuality(
                    mEffectiveUserId);

            View view = inflater.inflate(R.layout.jing_complex_lock_password, container, false);
            initData(view);
            initListener();
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

            return view;
        }


        private void initData(View view) {
            mPasswdEt = view.findViewById(R.id.complex_passwd_input_et);
            mPasswdVisibleIv = view.findViewById(R.id.complex_passwd_visible_iv);
            mComplexPasswdTitleTv = view.findViewById(R.id.complex_passwd_title_tv);
            mComplexPasswdSummaryTv = view.findViewById(R.id.complex_passwd_summary_tv);
            mComplexPasswdSummaryTv.setVisibility(View.GONE);
            mPasswdNextTv = view.findViewById(R.id.complex_passwd_next_tv);
            mErrorTextView = (TextView) view.findViewById(R.id.errorText);
            mErrorTextView.setVisibility(View.VISIBLE);
            mPasswdEt.requestFocus();
            mPasswdEt.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    int length = s.toString().length();
                    if (length >= 4 && length <= 16) {
                        mPasswdNextTv.setBackgroundResource(R.drawable.passwd_next_btn_hl);
                        mPasswdNextTv.setEnabled(true);
                    } else {
                        mPasswdNextTv.setBackgroundResource(R.drawable.passwd_next_btn_de);
                        mPasswdNextTv.setEnabled(false);
                    }
                }
            });
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.complex_passwd_visible_iv:
                    if (isHidePasswd) {
                        mPasswdVisibleIv.setImageResource(R.mipmap.remove_red_eye_black_24dp);
                        mPasswdEt.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        isHidePasswd = false;
                    } else {
                        mPasswdVisibleIv.setImageResource(R.mipmap.visibility_off_black_24dp);
                        mPasswdEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        isHidePasswd = true;
                    }
                    // 光标的位置
                    mPasswdEt.setSelection(mPasswdEt.getText().toString().length());
                    break;

                case R.id.complex_passwd_next_tv:
                    handleNext();
                    break;
            }
        }

        private void initListener() {
            mPasswdVisibleIv.setOnClickListener(this);
            mPasswdNextTv.setOnClickListener(this);
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
            final byte[] pin = LockPatternUtils.charSequenceToByteArray(mPasswdEt.getText());
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
            return getActivity() instanceof JingComplexConfirmLockPassword.InternalActivity;
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

            final JingComplexConfirmLockPassword activity = (JingComplexConfirmLockPassword) getActivity();
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
            mPasswdEt.setText(null);
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
                    updateErrorMessage(
                            mLockPatternUtils.getCurrentFailedPasswordAttempts(mEffectiveUserId));
                }
            }.start();
        }
    }
}
