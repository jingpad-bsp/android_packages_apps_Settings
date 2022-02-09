package com.android.settings.password;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManager.PasswordComplexity;
import android.app.admin.PasswordMetrics;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.Theme;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.EncryptionInterstitial;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.Utils;
import com.android.settings.core.InstrumentedFragment;
import com.android.settings.notification.JingRedactionInterstitial;
import com.android.settings.password.JingLockPasswordUtils.SaveAndFinishWorker;

import java.util.ArrayList;
import java.util.Arrays;

import androidx.fragment.app.Fragment;

import static android.app.admin.DevicePolicyManager.PASSWORD_COMPLEXITY_NONE;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX;
import static com.android.settings.password.ChooseLockSettingsHelper.EXTRA_KEY_REQUESTED_MIN_COMPLEXITY;
import static com.android.settings.password.JingLockPasswordUtils.CONFIRM_EXISTING_REQUEST;
import static com.android.settings.password.JingLockPasswordUtils.CONTAIN_INVALID_CHARACTERS;
import static com.android.settings.password.JingLockPasswordUtils.CONTAIN_NON_DIGITS;
import static com.android.settings.password.JingLockPasswordUtils.CONTAIN_SEQUENTIAL_DIGITS;
import static com.android.settings.password.JingLockPasswordUtils.FRAGMENT_TAG_SAVE_AND_FINISH;
import static com.android.settings.password.JingLockPasswordUtils.KEY_CURRENT_PASSWORD;
import static com.android.settings.password.JingLockPasswordUtils.KEY_FIRST_PIN;
import static com.android.settings.password.JingLockPasswordUtils.KEY_UI_STAGE;
import static com.android.settings.password.JingLockPasswordUtils.NOT_ENOUGH_DIGITS;
import static com.android.settings.password.JingLockPasswordUtils.NOT_ENOUGH_LETTER;
import static com.android.settings.password.JingLockPasswordUtils.NOT_ENOUGH_LOWER_CASE;
import static com.android.settings.password.JingLockPasswordUtils.NOT_ENOUGH_NON_LETTER;
import static com.android.settings.password.JingLockPasswordUtils.NOT_ENOUGH_SYMBOLS;
import static com.android.settings.password.JingLockPasswordUtils.NOT_ENOUGH_UPPER_CASE;
import static com.android.settings.password.JingLockPasswordUtils.NO_ERROR;
import static com.android.settings.password.JingLockPasswordUtils.RECENTLY_USED;
import static com.android.settings.password.JingLockPasswordUtils.TOO_LONG;
import static com.android.settings.password.JingLockPasswordUtils.TOO_SHORT;

/**
 * Created by xty on 2021/6/21.
 */
public class JingSimpleLockPassword extends SettingsActivity {
    private static final String TAG = "JingSimpleLockPassword";
    private InputKeyListener mInputKeyListener;

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

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (JingSimplePasswordFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    /* package */ Class<? extends Fragment> getFragmentClass() {
        return JingSimplePasswordFragment.class;
    }

    public void setInputKeyListener(InputKeyListener listener) {
        mInputKeyListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setTitle(getText(R.string.jingos_passwd_actionbar));
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

    public static class JingSimplePasswordFragment extends InstrumentedFragment
            implements SaveAndFinishWorker.Listener, View.OnClickListener, InputKeyListener {

        private byte[] mCurrentPassword;
        private byte[] mChosenPassword;
        private boolean mHasChallenge;
        private long mChallenge;
        private int mPasswordMinLength = LockPatternUtils.MIN_LOCK_PASSWORD_SIZE;
        private int mPasswordMaxLength = 16;
        private int mPasswordMinLetters = 0;
        private int mPasswordMinUpperCase = 0;
        private int mPasswordMinLowerCase = 0;
        private int mPasswordMinSymbols = 0;
        private int mPasswordMinNumeric = 0;
        private int mPasswordMinNonLetter = 0;
        private int mPasswordMinLengthToFulfillAllPolicies = 0;
        private boolean mPasswordNumSequenceAllowed = true;
        @PasswordComplexity
        private int mRequestedMinComplexity = PASSWORD_COMPLEXITY_NONE;
        protected int mUserId;
        private byte[] mPasswordHistoryHashFactor;

        private LockPatternUtils mLockPatternUtils;
        private SaveAndFinishWorker mSaveAndFinishWorker;
        private int mRequestedQuality = DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
        private ChooseLockSettingsHelper mChooseLockSettingsHelper;
        protected Stage mUiStage = Stage.Introduction;
        protected boolean mForFingerprint;
        protected boolean mForFace;

        private byte[] mFirstPin;

        static final int RESULT_FINISHED = RESULT_FIRST_USER;

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

        @Override
        public void inputKeyDown(int keyCode) {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                updateUI();
                handleNext();
            } else if (keyCode == KeyEvent.KEYCODE_DEL) {
                delPasswd();
            } else {
                inputPasswd(keyCode);
            }
        }

        /**
         * Keep track internally of where the user is in choosing a pattern.
         */
        protected enum Stage {
            Introduction,
            NeedToConfirm,
            ConfirmWrong;
        }

        // required constructor for fragments
        public JingSimplePasswordFragment() {

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if ((getActivity() instanceof JingSimpleLockPassword)) {
                ((JingSimpleLockPassword) getActivity()).setInputKeyListener(this);
            }
            mLockPatternUtils = new LockPatternUtils(getActivity());
            Intent intent = getActivity().getIntent();
            if (!(getActivity() instanceof JingSimpleLockPassword)) {
                throw new SecurityException("Fragment contained in wrong activity");
            }
            // Only take this argument into account if it belongs to the current profile.
            mUserId = Utils.getUserIdFromBundle(getActivity(), intent.getExtras());
            mForFingerprint = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_FOR_FINGERPRINT, false);
            mForFace = intent.getBooleanExtra(ChooseLockSettingsHelper.EXTRA_KEY_FOR_FACE, false);
            mRequestedMinComplexity = intent.getIntExtra(
                    EXTRA_KEY_REQUESTED_MIN_COMPLEXITY, PASSWORD_COMPLEXITY_NONE);
            mRequestedQuality = Math.max(
                    intent.getIntExtra(LockPatternUtils.PASSWORD_TYPE_KEY, mRequestedQuality),
                    mLockPatternUtils.getRequestedPasswordQuality(mUserId));

            loadDpmPasswordRequirements();
            mChooseLockSettingsHelper = new ChooseLockSettingsHelper(getActivity());

            if (intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_FOR_CHANGE_CRED_REQUIRED_FOR_BOOT, false)) {
                SaveAndFinishWorker w = new SaveAndFinishWorker();
                final boolean required = getActivity().getIntent().getBooleanExtra(
                        EncryptionInterstitial.EXTRA_REQUIRE_PASSWORD, true);
                byte[] currentBytes = intent.getByteArrayExtra(
                        ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);

                w.setBlocking(true);
                w.setListener(this);
                w.start(mChooseLockSettingsHelper.utils(), required, false, 0,
                        currentBytes, currentBytes, mRequestedQuality, mUserId);
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.jing_simple_lock_password, container, false);
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
            numberDelTv = view.findViewById(R.id.tv_number_del);
            numberNextOrConfirmTv = view.findViewById(R.id.tv_number_next);

            numberIv1 = view.findViewById(R.id.iv_number1);
            numberIv2 = view.findViewById(R.id.iv_number2);
            numberIv3 = view.findViewById(R.id.iv_number3);
            numberIv4 = view.findViewById(R.id.iv_number4);
            numberIv5 = view.findViewById(R.id.iv_number5);
            numberIv6 = view.findViewById(R.id.iv_number6);
            mPasswdTitleTv = view.findViewById(R.id.tv_input_passwd_title);
            mPasswordSummaryTv = view.findViewById(R.id.tv_password_summary);
            mPasswdSb = new StringBuilder(8);
            mImageViewArrayList = new ArrayList<ImageView>();
            mImageViewArrayList.add(numberIv1);
            mImageViewArrayList.add(numberIv2);
            mImageViewArrayList.add(numberIv3);
            mImageViewArrayList.add(numberIv4);
            mImageViewArrayList.add(numberIv5);
            mImageViewArrayList.add(numberIv6);
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
                    updateUI();
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
                if (mUiStage == Stage.ConfirmWrong) {
                    mPasswordSummaryTv.setText(R.string.jingos_passwd_summary_confirm);
                    mPasswordSummaryTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
                }
            } else {
                mImageViewArrayList.get(number).setImageResource(R.drawable.number_circle_de);
            }
        }

        private void resetCircleImageView() {
            for (int i = 0; i < mImageViewArrayList.size(); i++) {
                mImageViewArrayList.get(i).setImageResource(R.drawable.number_circle_de);
            }
        }

        private void updateUI() {
            mPasswdTitleTv.setText(R.string.jingos_passwd_title_confirm);
            mPasswordSummaryTv.setText(R.string.jingos_passwd_summary_confirm);
            mPasswordSummaryTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
            numberNextOrConfirmTv.setText(R.string.jingos_passwd_ok);
            numberNextOrConfirmTv.setTextColor(getResources().getColor(R.color.jingos_passwd_clear_de));
            numberDelTv.setVisibility(View.INVISIBLE);
            resetCircleImageView();
            if (mUiStage == Stage.ConfirmWrong) {
                mUiStage = Stage.NeedToConfirm;
            }
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            Intent intent = getActivity().getIntent();
            final boolean confirmCredentials = intent.getBooleanExtra(
                    ChooseLockGeneric.CONFIRM_CREDENTIALS, true);
            mCurrentPassword = intent.getByteArrayExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);
            mHasChallenge = intent.getBooleanExtra(
                    ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, false);
            mChallenge = intent.getLongExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE, 0);
            if (savedInstanceState == null) {
                updateStage(Stage.Introduction);
                if (confirmCredentials) {
                    mChooseLockSettingsHelper.launchConfirmationActivity(CONFIRM_EXISTING_REQUEST,
                            getString(R.string.unlock_set_unlock_launch_picker_title), true,
                            mUserId);
                }
            } else {

                // restore from previous state
                mFirstPin = savedInstanceState.getByteArray(KEY_FIRST_PIN);
                final String state = savedInstanceState.getString(KEY_UI_STAGE);
                if (state != null) {
                    mUiStage = Stage.valueOf(state);
                    updateStage(mUiStage);
                }

                if (mCurrentPassword == null) {
                    mCurrentPassword = savedInstanceState.getByteArray(KEY_CURRENT_PASSWORD);
                }

                // Re-attach to the exiting worker if there is one.
                mSaveAndFinishWorker = (SaveAndFinishWorker) getFragmentManager().findFragmentByTag(
                        FRAGMENT_TAG_SAVE_AND_FINISH);
            }
        }

        protected int getStageType() {
            return mForFingerprint ? ChooseLockPassword.ChooseLockPasswordFragment.Stage.TYPE_FINGERPRINT :
                    mForFace ? ChooseLockPassword.ChooseLockPasswordFragment.Stage.TYPE_FACE :
                            ChooseLockPassword.ChooseLockPasswordFragment.Stage.TYPE_NONE;
        }

        @Override
        public int getMetricsCategory() {
            return SettingsEnums.CHOOSE_LOCK_PASSWORD;
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
            if (mSaveAndFinishWorker != null) {
                mSaveAndFinishWorker.setListener(null);
            }
            super.onPause();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(KEY_UI_STAGE, mUiStage.name());
            outState.putByteArray(KEY_FIRST_PIN, mFirstPin);
            outState.putByteArray(KEY_CURRENT_PASSWORD, mCurrentPassword);
        }

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
                        mCurrentPassword = data.getByteArrayExtra(
                                ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD);
                    }
                    break;
            }
        }

        protected Intent getRedactionInterstitialIntent(Context context) {
            return JingRedactionInterstitial.createStartIntent(context, mUserId);
        }

        protected void updateStage(Stage stage) {
            mUiStage = stage;
        }

        /**
         * Read the requirements from {@link DevicePolicyManager} and intent and aggregate them.
         */
        private void loadDpmPasswordRequirements() {
            final int dpmPasswordQuality = mLockPatternUtils.getRequestedPasswordQuality(mUserId);
            if (dpmPasswordQuality == PASSWORD_QUALITY_NUMERIC_COMPLEX) {
                mPasswordNumSequenceAllowed = false;
            }
            mPasswordMinLength = Math.max(LockPatternUtils.MIN_LOCK_PASSWORD_SIZE,
                    mLockPatternUtils.getRequestedMinimumPasswordLength(mUserId));
            mPasswordMaxLength = mLockPatternUtils.getMaximumPasswordLength(mRequestedQuality);
            mPasswordMinLetters = mLockPatternUtils.getRequestedPasswordMinimumLetters(mUserId);
            mPasswordMinUpperCase = mLockPatternUtils.getRequestedPasswordMinimumUpperCase(mUserId);
            mPasswordMinLowerCase = mLockPatternUtils.getRequestedPasswordMinimumLowerCase(mUserId);
            mPasswordMinNumeric = mLockPatternUtils.getRequestedPasswordMinimumNumeric(mUserId);
            mPasswordMinSymbols = mLockPatternUtils.getRequestedPasswordMinimumSymbols(mUserId);
            mPasswordMinNonLetter = mLockPatternUtils.getRequestedPasswordMinimumNonLetter(mUserId);

            // Modify the value based on dpm policy
            switch (dpmPasswordQuality) {
                case PASSWORD_QUALITY_ALPHABETIC:
                    if (mPasswordMinLetters == 0) {
                        mPasswordMinLetters = 1;
                    }
                    break;
                case PASSWORD_QUALITY_ALPHANUMERIC:
                    if (mPasswordMinLetters == 0) {
                        mPasswordMinLetters = 1;
                    }
                    if (mPasswordMinNumeric == 0) {
                        mPasswordMinNumeric = 1;
                    }
                    break;
                case PASSWORD_QUALITY_COMPLEX:
                    // Reserve all the requirements.
                    break;
                default:
                    mPasswordMinNumeric = 0;
                    mPasswordMinLetters = 0;
                    mPasswordMinUpperCase = 0;
                    mPasswordMinLowerCase = 0;
                    mPasswordMinSymbols = 0;
                    mPasswordMinNonLetter = 0;
            }

            mPasswordMinLengthToFulfillAllPolicies = getMinLengthToFulfillAllPolicies();
        }

        private void mergeMinComplexityAndDpmRequirements(int userEnteredPasswordQuality) {
            if (mRequestedMinComplexity == PASSWORD_COMPLEXITY_NONE) {
                // dpm requirements are dominant if min complexity is none
                return;
            }

            // reset dpm requirements
            loadDpmPasswordRequirements();

            PasswordMetrics minMetrics = PasswordMetrics.getMinimumMetrics(
                    mRequestedMinComplexity, userEnteredPasswordQuality, mRequestedQuality,
                    requiresNumeric(), requiresLettersOrSymbols());
            mPasswordNumSequenceAllowed = mPasswordNumSequenceAllowed
                    && minMetrics.quality != PASSWORD_QUALITY_NUMERIC_COMPLEX;
            mPasswordMinLength = Math.max(mPasswordMinLength, minMetrics.length);
            mPasswordMinLetters = Math.max(mPasswordMinLetters, minMetrics.letters);
            mPasswordMinUpperCase = Math.max(mPasswordMinUpperCase, minMetrics.upperCase);
            mPasswordMinLowerCase = Math.max(mPasswordMinLowerCase, minMetrics.lowerCase);
            mPasswordMinNumeric = Math.max(mPasswordMinNumeric, minMetrics.numeric);
            mPasswordMinSymbols = Math.max(mPasswordMinSymbols, minMetrics.symbols);
            mPasswordMinNonLetter = Math.max(mPasswordMinNonLetter, minMetrics.nonLetter);

            if (minMetrics.quality == PASSWORD_QUALITY_ALPHABETIC) {
                if (!requiresLettersOrSymbols()) {
                    mPasswordMinLetters = 1;
                }
            }
            if (minMetrics.quality == PASSWORD_QUALITY_ALPHANUMERIC) {
                if (!requiresLettersOrSymbols()) {
                    mPasswordMinLetters = 1;
                }
                if (!requiresNumeric()) {
                    mPasswordMinNumeric = 1;
                }
            }

            mPasswordMinLengthToFulfillAllPolicies = getMinLengthToFulfillAllPolicies();
        }

        private boolean requiresLettersOrSymbols() {
            // This is the condition for the password to be considered ALPHABETIC according to
            // PasswordMetrics.computeForPassword()
            return mPasswordMinLetters + mPasswordMinUpperCase
                    + mPasswordMinLowerCase + mPasswordMinSymbols + mPasswordMinNonLetter > 0;
        }

        private boolean requiresNumeric() {
            return mPasswordMinNumeric > 0;
        }

        /**
         * Validates PIN/Password and returns the validation result.
         *
         * @param password the raw password the user typed in
         * @return the validation result.
         */
        @VisibleForTesting
        int validatePassword(byte[] password) {
            int errorCode = NO_ERROR;
            final PasswordMetrics metrics = PasswordMetrics.computeForPassword(password);
            mergeMinComplexityAndDpmRequirements(metrics.quality);

            if (password == null || password.length < mPasswordMinLength) {
                if (mPasswordMinLength > mPasswordMinLengthToFulfillAllPolicies) {
                    errorCode |= TOO_SHORT;
                }
            } else if (password.length > mPasswordMaxLength) {
                errorCode |= TOO_LONG;
            } else {
                // The length requirements are fulfilled.
                if (!mPasswordNumSequenceAllowed
                        && !requiresLettersOrSymbols()
                        && metrics.numeric == password.length) {
                    // Check for repeated characters or sequences (e.g. '1234', '0000', '2468')
                    // if DevicePolicyManager or min password complexity requires a complex numeric
                    // password. There can be two cases in the UI: 1. User chooses to enroll a
                    // PIN, 2. User chooses to enroll a password but enters a numeric-only pin. We
                    // should carry out the sequence check in both cases.
                    //
                    // Conditions for the !requiresLettersOrSymbols() to be necessary:
                    // - DPM requires NUMERIC_COMPLEX
                    // - min complexity not NONE, user picks PASSWORD type so ALPHABETIC or
                    // ALPHANUMERIC is required
                    // Imagine user has entered "12345678", if we don't skip the sequence check, the
                    // validation result would show both "requires a letter" and "sequence not
                    // allowed", while the only requirement the user needs to know is "requires a
                    // letter" because once the user has fulfilled the alphabetic requirement, the
                    // password would not be containing only digits so this check would not be
                    // performed anyway.
                    final int sequence = PasswordMetrics.maxLengthSequence(password);
                    if (sequence > PasswordMetrics.MAX_ALLOWED_SEQUENCE) {
                        errorCode |= CONTAIN_SEQUENTIAL_DIGITS;
                    }
                }
                // Is the password recently used?
                if (mLockPatternUtils.checkPasswordHistory(password, getPasswordHistoryHashFactor(),
                        mUserId)) {
                    errorCode |= RECENTLY_USED;
                }
            }

            // Allow non-control Latin-1 characters only.
            for (int i = 0; i < password.length; i++) {
                char c = (char) password[i];
                if (c < 32 || c > 127) {
                    errorCode |= CONTAIN_INVALID_CHARACTERS;
                    break;
                }
            }

            // Ensure no non-digits if we are requesting numbers. This shouldn't be possible unless
            // user finds some way to bring up soft keyboard.
            if (mRequestedQuality == PASSWORD_QUALITY_NUMERIC
                    || mRequestedQuality == PASSWORD_QUALITY_NUMERIC_COMPLEX) {
                if (metrics.letters > 0 || metrics.symbols > 0) {
                    errorCode |= CONTAIN_NON_DIGITS;
                }
            }

            if (metrics.letters < mPasswordMinLetters) {
                errorCode |= NOT_ENOUGH_LETTER;
            }
            if (metrics.upperCase < mPasswordMinUpperCase) {
                errorCode |= NOT_ENOUGH_UPPER_CASE;
            }
            if (metrics.lowerCase < mPasswordMinLowerCase) {
                errorCode |= NOT_ENOUGH_LOWER_CASE;
            }
            if (metrics.symbols < mPasswordMinSymbols) {
                errorCode |= NOT_ENOUGH_SYMBOLS;
            }
            if (metrics.numeric < mPasswordMinNumeric) {
                errorCode |= NOT_ENOUGH_DIGITS;
            }
            if (metrics.nonLetter < mPasswordMinNonLetter) {
                errorCode |= NOT_ENOUGH_NON_LETTER;
            }
            return errorCode;
        }

        /**
         * Lazily compute and return the history hash factor of the current user (mUserId), used for
         * password history check.
         */
        private byte[] getPasswordHistoryHashFactor() {
            if (mPasswordHistoryHashFactor == null) {
                mPasswordHistoryHashFactor = mLockPatternUtils.getPasswordHistoryHashFactor(
                        mCurrentPassword, mUserId);
            }
            return mPasswordHistoryHashFactor;
        }

        public void handleNext() {
            if (mSaveAndFinishWorker != null) return;
            mChosenPassword = LockPatternUtils.charSequenceToByteArray(mPasswdSb);
            if (mChosenPassword == null || mChosenPassword.length == 0) {
                return;
            }
            if (mUiStage == Stage.Introduction) {
                if (validatePassword(mChosenPassword) == NO_ERROR) {
                    mFirstPin = mChosenPassword;
                    mPasswdSb.delete(0, mPasswdSb.length());
                    updateStage(Stage.NeedToConfirm);
                } else {
                    Arrays.fill(mChosenPassword, (byte) 0);
                }
            } else if (mUiStage == Stage.NeedToConfirm) {
                if (Arrays.equals(mFirstPin, mChosenPassword)) {
                    startSaveAndFinish();
                } else {
                    mPasswdSb.delete(0, mPasswdSb.length());
                    mPasswordSummaryTv.setText(R.string.jingos_passwd_error);
                    mPasswordSummaryTv.setTextColor(getResources().getColor(R.color.jingos_passwd_error));
                    resetCircleImageView();
                    updateStage(Stage.ConfirmWrong);
                    Arrays.fill(mChosenPassword, (byte) 0);
                }
            }
        }

        private int getMinLengthToFulfillAllPolicies() {
            final int minLengthForLetters = Math.max(mPasswordMinLetters,
                    mPasswordMinUpperCase + mPasswordMinLowerCase);
            final int minLengthForNonLetters = Math.max(mPasswordMinNonLetter,
                    mPasswordMinSymbols + mPasswordMinNumeric);
            return minLengthForLetters + minLengthForNonLetters;
        }

        protected int toVisibility(boolean visibleOrGone) {
            return visibleOrGone ? View.VISIBLE : View.GONE;
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
            mSaveAndFinishWorker.start(mLockPatternUtils, required, mHasChallenge, mChallenge,
                    mChosenPassword, mCurrentPassword, mRequestedQuality, mUserId);
        }

        @Override
        public void onChosenLockSaveFinished(boolean wasSecureBefore, Intent resultData) {
            getActivity().setResult(RESULT_FINISHED, resultData);

            if (mChosenPassword != null) {
                Arrays.fill(mChosenPassword, (byte) 0);
            }
            if (mCurrentPassword != null) {
                Arrays.fill(mCurrentPassword, (byte) 0);
            }
            if (mFirstPin != null) {
                Arrays.fill(mFirstPin, (byte) 0);
            }
            mPasswdSb.delete(0, mPasswdSb.length());

            if (!wasSecureBefore) {
                Intent intent = getRedactionInterstitialIntent(getActivity());
                if (intent != null) {
                    startActivity(intent);
                }
            }
            getActivity().finish();
        }
    }

    public interface InputKeyListener{
        void inputKeyDown(int keyCode);
    }
}
