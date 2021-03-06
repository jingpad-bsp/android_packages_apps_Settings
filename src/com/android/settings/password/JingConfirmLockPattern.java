package com.android.settings.password;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toolbar;

import com.android.internal.widget.LinearLayoutWithDefaultTouchRecepient;
import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;
import com.android.internal.widget.LockPatternView.Cell;
import com.android.settings.R;
import com.android.settingslib.animation.AppearAnimationCreator;
import com.android.settingslib.animation.AppearAnimationUtils;
import com.android.settingslib.animation.DisappearAnimationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by xty on 2021/6/28.
 */
public class JingConfirmLockPattern extends ConfirmDeviceCredentialBaseActivity {
    private static final String TAG = "JingConfirmLockPattern";

    public static class InternalActivity extends JingConfirmLockPattern {
    }

    private enum Stage {
        NeedToUnlock,
        NeedToUnlockWrong,
        LockedOut
    }

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, ConfirmLockPatternFragment.class.getName());
        return modIntent;
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setTitle(getText(R.string.jingos_pattern_lock_toolbar));
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (ConfirmLockPatternFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    public static class ConfirmLockPatternFragment extends ConfirmDeviceCredentialBaseFragment
            implements AppearAnimationCreator<Object>, CredentialCheckResultTracker.Listener {

        private static final String FRAGMENT_TAG_CHECK_LOCK_RESULT = "check_lock_result";

        private LockPatternView mLockPatternView;
        private AsyncTask<?, ?, ?> mPendingLockCheck;
        private CredentialCheckResultTracker mCredentialCheckResultTracker;
        private boolean mDisappearing = false;
        private CountDownTimer mCountdownTimer;

        private TextView mPatternPasswdSummaryTv;
        private LinearLayout mPatternBgLl;

        private AppearAnimationUtils mAppearAnimationUtils;
        private DisappearAnimationUtils mDisappearAnimationUtils;

        // required constructor for fragments
        public ConfirmLockPatternFragment() {

        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            /* UNISOC: Modify for Bug 1147820 {@ */
            View view = inflater.inflate(R.layout.jing_choose_lock_pattern, container, false);

            initData(view);
            mLockPatternView.setTactileFeedbackEnabled(
                    mLockPatternUtils.isTactileFeedbackEnabled());
            mLockPatternView.setInStealthMode(!mLockPatternUtils.isVisiblePatternEnabled(
                    mEffectiveUserId));
            mLockPatternView.setOnPatternListener(mConfirmExistingLockPatternListener);
            updateStage(Stage.NeedToUnlock);

            if (savedInstanceState == null) {
                // on first launch, if no lock pattern is set, then finish with
                // success (don't want user to get stuck confirming something that
                // doesn't exist).
                // Don't do this check for FRP though, because the pattern is not stored
                // in a way that isLockPatternEnabled is aware of for that case.
                // TODO(roosa): This block should no longer be needed since we removed the
                //              ability to disable the pattern in L. Remove this block after
                //              ensuring it's safe to do so. (Note that ConfirmLockPassword
                //              doesn't have this).
                if (!mFrp && !mLockPatternUtils.isLockPatternEnabled(mEffectiveUserId)) {
                    getActivity().setResult(Activity.RESULT_OK);
                    getActivity().finish();
                }
            }
            mAppearAnimationUtils = new AppearAnimationUtils(getContext(),
                    AppearAnimationUtils.DEFAULT_APPEAR_DURATION, 2f /* translationScale */,
                    1.3f /* delayScale */, AnimationUtils.loadInterpolator(
                    getContext(), android.R.interpolator.linear_out_slow_in));
            mDisappearAnimationUtils = new DisappearAnimationUtils(getContext(),
                    125, 4f /* translationScale */,
                    0.3f /* delayScale */, AnimationUtils.loadInterpolator(
                    getContext(), android.R.interpolator.fast_out_linear_in),
                    new AppearAnimationUtils.RowTranslationScaler() {
                        @Override
                        public float getRowTranslationScale(int row, int numRows) {
                            return (float) (numRows - row) / numRows;
                        }
                    });

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
            mErrorTextView = (TextView) view.findViewById(R.id.errorText);
            mErrorTextView.setVisibility(View.VISIBLE);
            mPatternBgLl = view.findViewById(R.id.pattern_bg_ll);
            mPatternBgLl.setVisibility(View.GONE);
            mLockPatternView = (LockPatternView) view.findViewById(R.id.pattern_password_lp);
            mPatternPasswdSummaryTv = (TextView) view.findViewById(R.id.pattern_password_summary_tv);
            mPatternPasswdSummaryTv.setVisibility(View.GONE);

        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            // deliberately not calling super since we are managing this in full
        }

        @Override
        public void onPause() {
            super.onPause();

            if (mCountdownTimer != null) {
                mCountdownTimer.cancel();
            }
            mCredentialCheckResultTracker.setListener(null);
            Log.d(TAG, "onPause");
        }

        @Override
        public int getMetricsCategory() {
            return SettingsEnums.CONFIRM_LOCK_PATTERN;
        }

        @Override
        public void onResume() {
            super.onResume();

            // if the user is currently locked out, enforce it.
            long deadline = mLockPatternUtils.getLockoutAttemptDeadline(mEffectiveUserId);
            if (deadline != 0) {
                mCredentialCheckResultTracker.clearResult();
                handleAttemptLockout(deadline);
            } else if (!mLockPatternView.isEnabled()) {
                // The deadline has passed, but the timer was cancelled. Or the pending lock
                // check was cancelled. Need to clean up.
                updateStage(Stage.NeedToUnlock);
            }
            mCredentialCheckResultTracker.setListener(this);
            Log.d(TAG, "onResume");
        }

        @Override
        protected void onShowError() {
        }

        @Override
        public void prepareEnterAnimation() {
            super.prepareEnterAnimation();
            mLockPatternView.setAlpha(0f);
        }

        private Object[][] getActiveViews() {
            ArrayList<ArrayList<Object>> result = new ArrayList<>();
            LockPatternView.CellState[][] cellStates = mLockPatternView.getCellStates();
            for (int i = 0; i < cellStates.length; i++) {
                ArrayList<Object> row = new ArrayList<>();
                for (int j = 0; j < cellStates[i].length; j++) {
                    row.add(cellStates[i][j]);
                }
                result.add(row);
            }
            Object[][] resultArr = new Object[result.size()][cellStates[0].length];
            for (int i = 0; i < result.size(); i++) {
                ArrayList<Object> row = result.get(i);
                for (int j = 0; j < row.size(); j++) {
                    resultArr[i][j] = row.get(j);
                }
            }
            return resultArr;
        }

        @Override
        public void startEnterAnimation() {
            super.startEnterAnimation();
            mLockPatternView.setAlpha(1f);
            mAppearAnimationUtils.startAnimation2d(getActiveViews(), null, this);
        }

        private void updateStage(Stage stage) {
            switch (stage) {
                case NeedToUnlock:
                    mErrorTextView.setText("");
                    updateErrorMessage(
                            mLockPatternUtils.getCurrentFailedPasswordAttempts(mEffectiveUserId));

                    mLockPatternView.setEnabled(true);
                    mLockPatternView.enableInput();
                    mLockPatternView.clearPattern();
                    break;
                case NeedToUnlockWrong:
                    showError(R.string.lockpattern_need_to_unlock_wrong,
                            CLEAR_WRONG_ATTEMPT_TIMEOUT_MS);

                    mLockPatternView.setDisplayMode(LockPatternView.DisplayMode.Wrong);
                    mLockPatternView.setEnabled(true);
                    mLockPatternView.enableInput();
                    break;
                case LockedOut:
                    mLockPatternView.clearPattern();
                    // enabled = false means: disable input, and have the
                    // appearance of being disabled.
                    mLockPatternView.setEnabled(false); // appearance of being disabled
                    break;
            }
        }

        private Runnable mClearPatternRunnable = new Runnable() {
            public void run() {
                mLockPatternView.clearPattern();
            }
        };

        // clear the wrong pattern unless they have started a new one
        // already
        private void postClearPatternRunnable() {
            mLockPatternView.removeCallbacks(mClearPatternRunnable);
            mLockPatternView.postDelayed(mClearPatternRunnable, CLEAR_WRONG_ATTEMPT_TIMEOUT_MS);
        }

        @Override
        protected void authenticationSucceeded() {
            Log.d(TAG, "authenticationSucceeded");
            mCredentialCheckResultTracker.setResult(true, new Intent(), 0, mEffectiveUserId);
        }

        private void startDisappearAnimation(final Intent intent) {
            if (mDisappearing) {
                return;
            }
            mDisappearing = true;

            final JingConfirmLockPattern activity = (JingConfirmLockPattern) getActivity();
            // Bail if there is no active activity.
            if (activity == null || activity.isFinishing()) {
                return;
            }
            if (activity.getConfirmCredentialTheme() == ConfirmCredentialTheme.DARK) {
                mLockPatternView.clearPattern();
                mDisappearAnimationUtils.startAnimation2d(getActiveViews(),
                        () -> {
                            activity.setResult(RESULT_OK, intent);
                            activity.finish();
                            activity.overridePendingTransition(
                                    R.anim.confirm_credential_close_enter,
                                    R.anim.confirm_credential_close_exit);
                        }, this);
            } else {
                activity.setResult(RESULT_OK, intent);
                activity.finish();
            }
        }

        /**
         * The pattern listener that responds according to a user confirming
         * an existing lock pattern.
         */
        private LockPatternView.OnPatternListener mConfirmExistingLockPatternListener
                = new LockPatternView.OnPatternListener() {

            public void onPatternStart() {
                Log.d(TAG, "onPatternStart");
                mLockPatternView.removeCallbacks(mClearPatternRunnable);
            }

            public void onPatternCleared() {
                Log.d(TAG, "onPatternCleared");
                mLockPatternView.removeCallbacks(mClearPatternRunnable);
            }

            public void onPatternCellAdded(List<Cell> pattern) {

            }

            public void onPatternDetected(List<LockPatternView.Cell> pattern) {
                if (mPendingLockCheck != null || mDisappearing) {
                    return;
                }
                Log.d(TAG, "onPatternDetected");

                mLockPatternView.setEnabled(false);

                final boolean verifyChallenge = getActivity().getIntent().getBooleanExtra(
                        ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, false);
                Intent intent = new Intent();
                if (verifyChallenge) {
                    if (isInternalActivity()) {
                        startVerifyPattern(pattern, intent);
                        return;
                    }
                } else {
                    startCheckPattern(pattern, intent);
                    return;
                }

                mCredentialCheckResultTracker.setResult(false, intent, 0, mEffectiveUserId);
            }

            private boolean isInternalActivity() {
                return getActivity() instanceof JingConfirmLockPattern.InternalActivity;
            }

            private void startVerifyPattern(final List<LockPatternView.Cell> pattern,
                                            final Intent intent) {
                final int localEffectiveUserId = mEffectiveUserId;
                final int localUserId = mUserId;
                long challenge = getActivity().getIntent().getLongExtra(
                        ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, 0);
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
                                Log.d(TAG, "onVerified matched:" + matched);
                            }
                        };
                mPendingLockCheck = (localEffectiveUserId == localUserId)
                        ? LockPatternChecker.verifyPattern(
                        mLockPatternUtils, pattern, challenge, localUserId,
                        onVerifyCallback)
                        : LockPatternChecker.verifyTiedProfileChallenge(
                        mLockPatternUtils, LockPatternUtils.patternToByteArray(pattern),
                        true, challenge, localUserId, onVerifyCallback);
            }

            private void startCheckPattern(final List<LockPatternView.Cell> pattern,
                                           final Intent intent) {
                if (pattern.size() < LockPatternUtils.MIN_PATTERN_REGISTER_FAIL) {
                    // Pattern size is less than the minimum, do not count it as an fail attempt.
                    onPatternChecked(false, intent, 0, mEffectiveUserId, false /* newResult */);
                    return;
                }

                final int localEffectiveUserId = mEffectiveUserId;
                mPendingLockCheck = LockPatternChecker.checkPattern(
                        mLockPatternUtils,
                        pattern,
                        localEffectiveUserId,
                        new LockPatternChecker.OnCheckCallback() {
                            @Override
                            public void onChecked(boolean matched, int timeoutMs) {
                                mPendingLockCheck = null;
                                if (matched && isInternalActivity() && mReturnCredentials) {
                                    intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_TYPE,
                                            StorageManager.CRYPT_TYPE_PATTERN);
                                    intent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD,
                                            LockPatternUtils.patternToByteArray(pattern));
                                }
                                mCredentialCheckResultTracker.setResult(matched, intent, timeoutMs,
                                        localEffectiveUserId);
                                Log.d(TAG, "onChecked matched:" + matched);
                            }
                        });
            }
        };

        private void onPatternChecked(boolean matched, Intent intent, int timeoutMs,
                                      int effectiveUserId, boolean newResult) {
            Log.d(TAG, "onPatternChecked matched:" + matched);
            mLockPatternView.setEnabled(true);
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
                    updateStage(Stage.NeedToUnlockWrong);
                    postClearPatternRunnable();
                }
                if (newResult) {
                    reportFailedAttempt();
                }
            }
        }

        @Override
        public void onCredentialChecked(boolean matched, Intent intent, int timeoutMs,
                                        int effectiveUserId, boolean newResult) {
            onPatternChecked(matched, intent, timeoutMs, effectiveUserId, newResult);
        }

        @Override
        protected int getLastTryErrorMessage(int userType) {
            switch (userType) {
                case USER_TYPE_PRIMARY:
                    return R.string.lock_last_pattern_attempt_before_wipe_device;
                case USER_TYPE_MANAGED_PROFILE:
                    return R.string.lock_last_pattern_attempt_before_wipe_profile;
                case USER_TYPE_SECONDARY:
                    return R.string.lock_last_pattern_attempt_before_wipe_user;
                default:
                    throw new IllegalArgumentException("Unrecognized user type:" + userType);
            }
        }

        private void handleAttemptLockout(long elapsedRealtimeDeadline) {
            updateStage(Stage.LockedOut);
            //Unisoc: fix for bug 899978
            resetErrorRunnableTimeout();
            long elapsedRealtime = SystemClock.elapsedRealtime();
            mCountdownTimer = new CountDownTimer(
                    elapsedRealtimeDeadline - elapsedRealtime,
                    LockPatternUtils.FAILED_ATTEMPT_COUNTDOWN_INTERVAL_MS) {

                @Override
                public void onTick(long millisUntilFinished) {
                    // UNISOC: Fix for bug 1213187
                    final int secondsCountdown = (int) (millisUntilFinished / 1000) + 1;
                    String errorText = getResources().getQuantityString(
                            R.plurals.lockpattern_too_many_failed_confirmation_attempts,
                            secondsCountdown, secondsCountdown);
                    mErrorTextView.setText(errorText);
                }

                @Override
                public void onFinish() {
                    updateStage(Stage.NeedToUnlock);
                }
            }.start();
        }

        @Override
        public void createAnimation(Object obj, long delay,
                                    long duration, float translationY, final boolean appearing,
                                    Interpolator interpolator,
                                    final Runnable finishListener) {
            if (obj instanceof LockPatternView.CellState) {
                final LockPatternView.CellState animatedCell = (LockPatternView.CellState) obj;
                mLockPatternView.startCellStateAnimation(animatedCell,
                        1f, appearing ? 1f : 0f, /* alpha */
                        appearing ? translationY : 0f, /* startTranslation */
                        appearing ? 0f : translationY, /* endTranslation */
                        appearing ? 0f : 1f, 1f /* scale */,
                        delay, duration, interpolator, finishListener);
            } else {
                mAppearAnimationUtils.createAnimation((View) obj, delay, duration, translationY,
                        appearing, interpolator, finishListener);
            }
        }
    }
}
