package com.android.settings.password;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.Theme;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternUtils.RequestThrottledException;
import com.android.internal.widget.LockPatternView;
import com.android.internal.widget.LockPatternView.Cell;
import com.android.internal.widget.LockPatternView.DisplayMode;
import com.android.settings.EncryptionInterstitial;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import com.android.settings.core.InstrumentedFragment;
import com.android.settings.notification.JingRedactionInterstitial;
import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.fragment.app.Fragment;

/**
 * Created by xty on 2021/6/23.
 */
public class JingChooseLockPattern extends SettingsActivity {
    private static final String TAG = "JingChooseLockPattern";

    static final int RESULT_FINISHED = RESULT_FIRST_USER;

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, getFragmentClass().getName());
        return modIntent;
    }

    @Override
    protected void onApplyThemeResource(Theme theme, int resid, boolean first) {
        super.onApplyThemeResource(theme, resid, first);
    }

    public static class IntentBuilder {
        private final Intent mIntent;

        public IntentBuilder(Context context) {
            mIntent = new Intent(context, JingChooseLockPattern.class);
            mIntent.putExtra(EncryptionInterstitial.EXTRA_REQUIRE_PASSWORD, false);
            mIntent.putExtra(ChooseLockGeneric.CONFIRM_CREDENTIALS, false);
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, false);
        }

        public IntentBuilder setUserId(int userId) {
            mIntent.putExtra(Intent.EXTRA_USER_ID, userId);
            return this;
        }

        public IntentBuilder setChallenge(long challenge) {
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, true);
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, challenge);
            return this;
        }

        public IntentBuilder setPattern(byte[] pattern) {
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, pattern);
            return this;
        }

        public IntentBuilder setForFingerprint(boolean forFingerprint) {
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_FOR_FINGERPRINT, forFingerprint);
            return this;
        }

        public IntentBuilder setForFace(boolean forFace) {
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_FOR_FACE, forFace);
            return this;
        }

        public Intent build() {
            return mIntent;
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (JingChooseLockPatternFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    /* package */ Class<? extends Fragment> getFragmentClass() {
        return JingChooseLockPatternFragment.class;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.jingos_pattern_passwd_actionbar));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // *** TODO ***
        return super.onKeyDown(keyCode, event);
    }

    public static class JingChooseLockPatternFragment extends InstrumentedFragment
            implements SaveAndFinishWorker.Listener, View.OnClickListener {

        public static final int CONFIRM_EXISTING_REQUEST = 55;

        // how long after a confirmation message is shown before moving on
        static final int INFORMATION_MSG_TIMEOUT_MS = 3000;

        // how long we wait to clear a wrong pattern
        private static final int WRONG_PATTERN_CLEAR_TIMEOUT_MS = 2000;

        protected static final int ID_EMPTY_MESSAGE = -1;

        private static final String FRAGMENT_TAG_SAVE_AND_FINISH = "save_and_finish_worker";

        private byte[] mCurrentPattern;
        private boolean mHasChallenge;
        private long mChallenge;
        protected LockPatternView mLockPatternView;
        protected List<LockPatternView.Cell> mChosenPattern = null;

        private TextView mPatternPasswdTitleTv;
        private TextView mPatternPasswdSummaryTv;
        private TextView mPatternPasswdClearTv;
        private TextView mPatternPasswdNextConfirmTv;

        /**
         * The patten used during the help screen to show how to draw a pattern.
         */
        private final List<LockPatternView.Cell> mAnimatePattern =
                Collections.unmodifiableList(Lists.newArrayList(
                        LockPatternView.Cell.of(0, 0),
                        LockPatternView.Cell.of(0, 1),
                        LockPatternView.Cell.of(1, 1),
                        LockPatternView.Cell.of(2, 1)
                ));

        @Override
        public void onActivityResult(int requestCode, int resultCode,
                                     Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            switch (requestCode) {
                case CONFIRM_EXISTING_REQUEST:
                    if (resultCode != Activity.RESULT_OK) {
                        getActivity().setResult(RESULT_FINISHED);
                        getActivity().finish();
                    } else {
                        mCurrentPattern = data.getByteArrayExtra(
                                ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);
                    }

                    updateStage(Stage.Introduction);
                    break;
            }
        }

        /**
         * The pattern listener that responds according to a user choosing a new
         * lock pattern.
         */
        protected LockPatternView.OnPatternListener mChooseNewLockPatternListener =
                new LockPatternView.OnPatternListener() {

                    public void onPatternStart() {
                        mLockPatternView.removeCallbacks(mClearPatternRunnable);
                        patternInProgress();
                    }

                    public void onPatternCleared() {
                        mLockPatternView.removeCallbacks(mClearPatternRunnable);
                    }

                    public void onPatternDetected(List<LockPatternView.Cell> pattern) {
                        if (mUiStage == Stage.NeedToConfirm || mUiStage == Stage.ConfirmWrong) {
                            if (mChosenPattern == null) throw new IllegalStateException(
                                    "null chosen pattern in stage 'need to confirm");
                            if (mChosenPattern.equals(pattern)) {
                                updateStage(Stage.ChoiceConfirmed);
                            } else {
                                updateStage(Stage.ConfirmWrong);
                                mPatternPasswdSummaryTv.setText(R.string.jingos_pattern_passwd_error);
                                mPatternPasswdSummaryTv.setTextColor(getResources().getColor(R.color.jingos_passwd_error));
                            }
                        } else if (mUiStage == Stage.Introduction || mUiStage == Stage.ChoiceTooShort){
                            if (pattern.size() < LockPatternUtils.MIN_LOCK_PATTERN_SIZE) {
                                updateStage(Stage.ChoiceTooShort);
                                mPatternPasswdSummaryTv.setText(R.string.jingos_pattern_passwd_summary_four);
                                mPatternPasswdSummaryTv.setTextColor(getResources().getColor(R.color.jingos_passwd_error));
                            } else {
                                mChosenPattern = new ArrayList<LockPatternView.Cell>(pattern);
                                updateStage(Stage.FirstChoiceValid);
                                mPatternPasswdSummaryTv.setText(R.string.jingos_pattern_passwd_record);
                                mPatternPasswdSummaryTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
                            }
                        } else {
                            throw new IllegalStateException("Unexpected stage " + mUiStage + " when "
                                    + "entering the pattern.");
                        }
                    }

                    public void onPatternCellAdded(List<Cell> pattern) {

                    }

                    private void patternInProgress() {
                    }
                };

        @Override
        public int getMetricsCategory() {
            return SettingsEnums.CHOOSE_LOCK_PATTERN;
        }

        private void resetPatternNextColor() {
            mPatternPasswdClearTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
            mPatternPasswdNextConfirmTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
        }

        protected enum Stage {
            Introduction(LeftButtonMode.Gone, RightButtonMode.ContinueDisabled,true),
            HelpScreen(LeftButtonMode.Gone, RightButtonMode.Ok,false),
            ChoiceTooShort(LeftButtonMode.Retry, RightButtonMode.ContinueDisabled,true),
            FirstChoiceValid(LeftButtonMode.Retry, RightButtonMode.Continue,false),
            NeedToConfirm(LeftButtonMode.Gone, RightButtonMode.ConfirmDisabled,true),
            ConfirmWrong(LeftButtonMode.Gone, RightButtonMode.ConfirmDisabled,true),
            ChoiceConfirmed(LeftButtonMode.Gone, RightButtonMode.Confirm,false);

            Stage(LeftButtonMode leftMode,
                  RightButtonMode rightMode,
                  boolean patternEnabled) {
                this.leftMode = leftMode;
                this.rightMode = rightMode;
                this.patternEnabled = patternEnabled;
            }

            final LeftButtonMode leftMode;
            final RightButtonMode rightMode;
            final boolean patternEnabled;
        }

        private Stage mUiStage = Stage.Introduction;

        private Runnable mClearPatternRunnable = new Runnable() {
            public void run() {
                if (getActivity() != null) {
                    mLockPatternView.clearPattern();
                    resetPatternNextColor();
                    if (mUiStage == Stage.ConfirmWrong) {
                        mPatternPasswdSummaryTv.setText(R.string.jingos_pattern_passwd_summary_confirm);
                        mPatternPasswdSummaryTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
                    }
                }
            }
        };

        private ChooseLockSettingsHelper mChooseLockSettingsHelper;
        private SaveAndFinishWorker mSaveAndFinishWorker;
        protected int mUserId;
        protected boolean mForFingerprint;
        protected boolean mForFace;

        private static final String KEY_UI_STAGE = "uiStage";
        private static final String KEY_PATTERN_CHOICE = "chosenPattern";
        private static final String KEY_CURRENT_PATTERN = "currentPattern";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());
            if (!(getActivity() instanceof JingChooseLockPattern)) {
                throw new SecurityException("Fragment contained in wrong activity");
            }
            Intent intent = getActivity().getIntent();
            // Only take this argument into account if it belongs to the current profile.
            mUserId = Utils.getUserIdFromBundle(getActivity(), intent.getExtras());

            if (intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_FOR_CHANGE_CRED_REQUIRED_FOR_BOOT, false)) {
                SaveAndFinishWorker w = new SaveAndFinishWorker();
                final boolean required = getActivity().getIntent().getBooleanExtra(
                        EncryptionInterstitial.EXTRA_REQUIRE_PASSWORD, true);
                byte[] current = intent.getByteArrayExtra(
                        ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);
                w.setBlocking(true);
                w.setListener(this);
                w.start(mChooseLockSettingsHelper.utils(), required,
                        false, 0, LockPatternUtils.byteArrayToPattern(current), current, mUserId);
            }
            mForFingerprint = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_FOR_FINGERPRINT, false);
            mForFace = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_FOR_FACE, false);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            return inflater.inflate(R.layout.jing_choose_lock_pattern, container, false);
        }

        /**
         * The states of the left footer button.
         */
        enum LeftButtonMode {
            Retry,
            RetryDisabled,
            Gone;
        }

        /**
         * The states of the right button.
         */
        enum RightButtonMode {
            Continue,
            ContinueDisabled,
            Confirm,
            ConfirmDisabled,
            Ok;
        }

        private void initData(View view) {
            mLockPatternView = (LockPatternView) view.findViewById(R.id.pattern_password_lp);
            mPatternPasswdTitleTv = (TextView) view.findViewById(R.id.pattern_password_title_tv);
            mPatternPasswdSummaryTv = (TextView) view.findViewById(R.id.pattern_password_summary_tv);
            mPatternPasswdClearTv = (TextView) view.findViewById(R.id.pattern_password_clear_tv);
            mPatternPasswdNextConfirmTv = (TextView) view.findViewById(R.id.pattern_next_tv);
        }

        private void initListener() {
            mPatternPasswdClearTv.setOnClickListener(this);
            mPatternPasswdNextConfirmTv.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.pattern_password_clear_tv:
                    handleLeftButton();
                    break;
                case R.id.pattern_next_tv:
                    handleRightButton();
                    break;
            }
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            initData(view);
            initListener();
            mLockPatternView.setOnPatternListener(mChooseNewLockPatternListener);
            mLockPatternView.setTactileFeedbackEnabled(
                    mChooseLockSettingsHelper.utils().isTactileFeedbackEnabled());
            mLockPatternView.setFadePattern(false);

            final boolean confirmCredentials = getActivity().getIntent()
                    .getBooleanExtra(ChooseLockGeneric.CONFIRM_CREDENTIALS, true);
            Intent intent = getActivity().getIntent();
            mCurrentPattern =
                    intent.getByteArrayExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);
            mHasChallenge = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, false);
            mChallenge = intent.getLongExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, 0);

            if (savedInstanceState == null) {
                if (confirmCredentials) {
                    // first launch. As a security measure, we're in NeedToConfirm mode until we
                    // know there isn't an existing password or the user confirms their password.
                    updateStage(Stage.NeedToConfirm);
                    boolean launchedConfirmationActivity =
                            mChooseLockSettingsHelper.launchConfirmationActivity(
                                    CONFIRM_EXISTING_REQUEST,
                                    getString(R.string.unlock_set_unlock_launch_picker_title), true,
                                    mUserId);
                    if (!launchedConfirmationActivity) {
                        updateStage(Stage.Introduction);
                    }
                } else {
                    updateStage(Stage.Introduction);
                }
            } else {
                // restore from previous state
                final byte[] pattern = savedInstanceState.getByteArray(KEY_PATTERN_CHOICE);
                if (pattern != null) {
                    mChosenPattern = LockPatternUtils.byteArrayToPattern(pattern);
                }

                if (mCurrentPattern == null) {
                    mCurrentPattern = savedInstanceState.getByteArray(KEY_CURRENT_PATTERN);
                }
                updateStage(Stage.values()[savedInstanceState.getInt(KEY_UI_STAGE)]);

                // Re-attach to the exiting worker if there is one.
                mSaveAndFinishWorker = (SaveAndFinishWorker) getFragmentManager().findFragmentByTag(
                        FRAGMENT_TAG_SAVE_AND_FINISH);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            updateStage(mUiStage);

            if (mSaveAndFinishWorker != null) {
                mSaveAndFinishWorker.setListener(this);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            if (mSaveAndFinishWorker != null) {
                mSaveAndFinishWorker.setListener(null);
            }
        }

        protected Intent getRedactionInterstitialIntent(Context context) {
            return JingRedactionInterstitial.createStartIntent(context, mUserId);
        }

        public void handleLeftButton() {
            if (mUiStage.leftMode == LeftButtonMode.Retry) {
                mChosenPattern = null;
                mLockPatternView.clearPattern();
                resetPatternNextColor();
                updateStage(Stage.Introduction);
                mPatternPasswdSummaryTv.setText(R.string.jingos_pattern_passwd_summary_confirm);
                mPatternPasswdSummaryTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
            }
        }

        public void handleRightButton() {
            if (mUiStage.rightMode == RightButtonMode.Continue) {
                if (mUiStage != Stage.FirstChoiceValid) {
                    throw new IllegalStateException("expected ui stage "
                            + Stage.FirstChoiceValid + " when button is "
                            + RightButtonMode.Continue);
                }
                updateStage(Stage.NeedToConfirm);
                mPatternPasswdTitleTv.setText(R.string.jingos_pattern_passwd_title_again);
                mPatternPasswdSummaryTv.setText(R.string.jingos_pattern_passwd_summary_confirm);
                mPatternPasswdSummaryTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
                mPatternPasswdClearTv.setVisibility(View.INVISIBLE);
                mPatternPasswdNextConfirmTv.setText(R.string.jingos_passwd_ok);
            } else if (mUiStage.rightMode == RightButtonMode.Confirm) {
                if (mUiStage != Stage.ChoiceConfirmed) {
                    throw new IllegalStateException("expected ui stage " + Stage.ChoiceConfirmed
                            + " when button is " + RightButtonMode.Confirm);
                }
                startSaveAndFinish();
            } else if (mUiStage.rightMode == RightButtonMode.Ok) {
                if (mUiStage != Stage.HelpScreen) {
                    throw new IllegalStateException("Help screen is only mode with ok button, "
                            + "but stage is " + mUiStage);
                }
                mLockPatternView.clearPattern();
                mLockPatternView.setDisplayMode(DisplayMode.Correct);
                updateStage(Stage.Introduction);
            }
        }

        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
                if (mUiStage == Stage.HelpScreen) {
                    updateStage(Stage.Introduction);
                    return true;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_MENU && mUiStage == Stage.Introduction) {
                updateStage(Stage.HelpScreen);
                return true;
            }
            return false;
        }

        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);

            outState.putInt(KEY_UI_STAGE, mUiStage.ordinal());
            if (mChosenPattern != null) {
                outState.putByteArray(KEY_PATTERN_CHOICE,
                        LockPatternUtils.patternToByteArray(mChosenPattern));
            }

            if (mCurrentPattern != null) {
                outState.putByteArray(KEY_CURRENT_PATTERN, mCurrentPattern);
            }
        }

        /**
         * Updates the messages and buttons appropriate to what stage the user
         * is at in choosing a view.  This doesn't handle clearing out the pattern;
         * the pattern is expected to be in the right state.
         * @param stage
         */
        protected void updateStage(Stage stage) {
            mUiStage = stage;

            // header text, footer text, visibility and
            // enabled state all known from the stage

            // same for whether the pattern is enabled
            if (stage.patternEnabled) {
                mLockPatternView.enableInput();
            } else {
                mLockPatternView.disableInput();
            }

            // the rest of the stuff varies enough that it is easier just to handle
            // on a case by case basis.
            mLockPatternView.setDisplayMode(DisplayMode.Correct);

            switch (mUiStage) {
                case Introduction:
                    mLockPatternView.clearPattern();
                    break;
                case HelpScreen:
                    mLockPatternView.setPattern(DisplayMode.Animate, mAnimatePattern);
                    break;
                case ChoiceTooShort:
                    mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                    postClearPatternRunnable();
                    break;
                case FirstChoiceValid:
                    mPatternPasswdClearTv.setTextColor(getResources().getColor(R.color.jingos_passwd_next_blue));
                    mPatternPasswdNextConfirmTv.setTextColor(getResources().getColor(R.color.jingos_passwd_next_blue));
                    break;
                case NeedToConfirm:
                    mLockPatternView.clearPattern();
                    resetPatternNextColor();
                    break;
                case ConfirmWrong:
                    mLockPatternView.setDisplayMode(DisplayMode.Wrong);
                    postClearPatternRunnable();
                    break;
                case ChoiceConfirmed:
                    mPatternPasswdSummaryTv.setText(R.string.jingos_pattern_passwd_summary_new);
                    mPatternPasswdSummaryTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
                    mPatternPasswdNextConfirmTv.setTextColor(getResources().getColor(R.color.jingos_passwd_next_blue));
                    break;
            }
        }


        // clear the wrong pattern unless they have started a new one
        // already
        private void postClearPatternRunnable() {
            mLockPatternView.removeCallbacks(mClearPatternRunnable);
            mLockPatternView.postDelayed(mClearPatternRunnable, WRONG_PATTERN_CLEAR_TIMEOUT_MS);
        }

        private void startSaveAndFinish() {
            if (mSaveAndFinishWorker != null) {
                Log.w(TAG, "startSaveAndFinish with an existing SaveAndFinishWorker.");
                return;
            }

            mSaveAndFinishWorker = new SaveAndFinishWorker();
            mSaveAndFinishWorker.setListener(this);

            getFragmentManager().beginTransaction().add(mSaveAndFinishWorker,
                    FRAGMENT_TAG_SAVE_AND_FINISH).commit();
            getFragmentManager().executePendingTransactions();

            final boolean required = getActivity().getIntent().getBooleanExtra(
                    EncryptionInterstitial.EXTRA_REQUIRE_PASSWORD, true);
            mSaveAndFinishWorker.start(mChooseLockSettingsHelper.utils(), required,
                    mHasChallenge, mChallenge, mChosenPattern, mCurrentPattern, mUserId);
        }

        @Override
        public void onChosenLockSaveFinished(boolean wasSecureBefore, Intent resultData) {
            getActivity().setResult(RESULT_FINISHED, resultData);

            if (mCurrentPattern != null) {
                Arrays.fill(mCurrentPattern, (byte) 0);
            }

            if (!wasSecureBefore) {
                Intent intent = getRedactionInterstitialIntent(getActivity());
                if (intent != null) {
                    startActivity(intent);
                }
            }
            getActivity().finish();
        }
    }

    public static class SaveAndFinishWorker extends SaveChosenLockWorkerBase {

        private List<LockPatternView.Cell> mChosenPattern;
        private byte[] mCurrentPattern;
        private boolean mLockVirgin;

        public void start(LockPatternUtils utils, boolean credentialRequired,
                          boolean hasChallenge, long challenge,
                          List<LockPatternView.Cell> chosenPattern, byte[] currentPattern, int userId) {
            prepare(utils, credentialRequired, hasChallenge, challenge, userId);

            mCurrentPattern = currentPattern;
            mChosenPattern = chosenPattern;
            mUserId = userId;

            mLockVirgin = !mUtils.isPatternEverChosen(mUserId);

            start();
        }

        @Override
        protected Pair<Boolean, Intent> saveAndVerifyInBackground() {
            final int userId = mUserId;
            final boolean success = mUtils.saveLockPattern(mChosenPattern, mCurrentPattern, userId);
            Intent result = null;
            if (success && mHasChallenge) {
                byte[] token;
                try {
                    token = mUtils.verifyPattern(mChosenPattern, mChallenge, userId);
                } catch (RequestThrottledException e) {
                    token = null;
                }

                if (token == null) {
                    Log.e(TAG, "critical: no token returned for known good pattern");
                }

                result = new Intent();
                result.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, token);
            }
            return Pair.create(success, result);
        }

        @Override
        protected void finish(Intent resultData) {
            if (mLockVirgin) {
                mUtils.setVisiblePatternEnabled(true, mUserId);
            }
            super.finish(resultData);
        }
    }
}
