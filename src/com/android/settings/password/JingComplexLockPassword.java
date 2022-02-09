package com.android.settings.password;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManager.PasswordComplexity;
import android.app.admin.PasswordMetrics;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.Theme;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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

import java.util.Arrays;

import androidx.annotation.StringRes;
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

import android.text.method.DigitsKeyListener;
import android.text.InputType;

/**
 * Created by xty on 2021/6/22.
 */
public class JingComplexLockPassword extends SettingsActivity {
    private static final String TAG = "JingComplexLockPassword";

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
        if (JingComplexPasswordFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    /* package */ Class<? extends Fragment> getFragmentClass() {
        return JingComplexPasswordFragment.class;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(getText(R.string.jingos_complex_passwd_actionbar));
    }

    public static class JingComplexPasswordFragment extends InstrumentedFragment
            implements SaveAndFinishWorker.Listener, View.OnClickListener {

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

        private EditText mPasswdEt;
        private ImageView mPasswdVisibleIv;
        private TextView mComplexPasswdTitleTv;
        private TextView mComplexPasswdSummaryTv;
        private TextView mPasswdNextTv;
        private boolean isHidePasswd;

        /**
         * Keep track internally of where the user is in choosing a pattern.
         */
        protected enum Stage {

            Introduction(
                    R.string.lockpassword_choose_your_screen_lock_header, // password
                    R.string.lockpassword_choose_your_password_header_for_fingerprint,
                    R.string.lockpassword_choose_your_password_header_for_face,
                    R.string.lockpassword_choose_your_screen_lock_header, // pin
                    R.string.lockpassword_choose_your_pin_header_for_fingerprint,
                    R.string.lockpassword_choose_your_pin_header_for_face,
                    R.string.lockpassword_choose_your_password_message, // added security message
                    R.string.lock_settings_picker_biometrics_added_security_message,
                    R.string.lockpassword_choose_your_pin_message,
                    R.string.lock_settings_picker_biometrics_added_security_message,
                    R.string.next_label),

            NeedToConfirm(
                    R.string.lockpassword_confirm_your_password_header,
                    R.string.lockpassword_confirm_your_password_header,
                    R.string.lockpassword_confirm_your_password_header,
                    R.string.lockpassword_confirm_your_pin_header,
                    R.string.lockpassword_confirm_your_pin_header,
                    R.string.lockpassword_confirm_your_pin_header,
                    0,
                    0,
                    0,
                    0,
                    R.string.lockpassword_confirm_label),

            ConfirmWrong(
                    R.string.lockpassword_confirm_passwords_dont_match,
                    R.string.lockpassword_confirm_passwords_dont_match,
                    R.string.lockpassword_confirm_passwords_dont_match,
                    R.string.lockpassword_confirm_pins_dont_match,
                    R.string.lockpassword_confirm_pins_dont_match,
                    R.string.lockpassword_confirm_pins_dont_match,
                    0,
                    0,
                    0,
                    0,
                    R.string.lockpassword_confirm_label);

            Stage(int hintInAlpha, int hintInAlphaForFingerprint, int hintInAlphaForFace,
                  int hintInNumeric, int hintInNumericForFingerprint, int hintInNumericForFace,
                  int messageInAlpha, int messageInAlphaForBiometrics,
                  int messageInNumeric, int messageInNumericForBiometrics,
                  int nextButtonText) {
                this.alphaHint = hintInAlpha;
                this.alphaHintForFingerprint = hintInAlphaForFingerprint;
                this.alphaHintForFace = hintInAlphaForFace;

                this.numericHint = hintInNumeric;
                this.numericHintForFingerprint = hintInNumericForFingerprint;
                this.numericHintForFace = hintInNumericForFace;

                this.alphaMessage = messageInAlpha;
                this.alphaMessageForBiometrics = messageInAlphaForBiometrics;
                this.numericMessage = messageInNumeric;
                this.numericMessageForBiometrics = messageInNumericForBiometrics;
                this.buttonText = nextButtonText;
            }

            public static final int TYPE_NONE = 0;
            public static final int TYPE_FINGERPRINT = 1;
            public static final int TYPE_FACE = 2;

            // Password
            public final int alphaHint;
            public final int alphaHintForFingerprint;
            public final int alphaHintForFace;

            // PIN
            public final int numericHint;
            public final int numericHintForFingerprint;
            public final int numericHintForFace;

            public final int alphaMessage;
            public final int alphaMessageForBiometrics;
            public final int numericMessage;
            public final int numericMessageForBiometrics;
            public final int buttonText;

            public @StringRes
            int getHint(boolean isAlpha, int type) {
                if (isAlpha) {
                    if (type == TYPE_FINGERPRINT) {
                        return alphaHintForFingerprint;
                    } else if (type == TYPE_FACE) {
                        return alphaHintForFace;
                    } else {
                        return alphaHint;
                    }
                } else {
                    if (type == TYPE_FINGERPRINT) {
                        return numericHintForFingerprint;
                    } else if (type == TYPE_FACE) {
                        return numericHintForFace;
                    } else {
                        return numericHint;
                    }
                }
            }

            public @StringRes
            int getMessage(boolean isAlpha, int type) {
                if (isAlpha) {
                    return type != TYPE_NONE ? alphaMessageForBiometrics : alphaMessage;
                } else {
                    return type != TYPE_NONE ? numericMessageForBiometrics : numericMessage;
                }
            }
        }

        // required constructor for fragments
        public JingComplexPasswordFragment() {

        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mLockPatternUtils = new LockPatternUtils(getActivity());
            Intent intent = getActivity().getIntent();
            if (!(getActivity() instanceof JingComplexLockPassword)) {
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
            View view = inflater.inflate(R.layout.jing_complex_lock_password, container, false);
            initData(view);
            initListener();
            return view;
        }

        private void initData(View view) {
            mPasswdEt = view.findViewById(R.id.complex_passwd_input_et);
            mPasswdVisibleIv = view.findViewById(R.id.complex_passwd_visible_iv);
            mComplexPasswdTitleTv = view.findViewById(R.id.complex_passwd_title_tv);
            mComplexPasswdSummaryTv = view.findViewById(R.id.complex_passwd_summary_tv);
            mPasswdNextTv = view.findViewById(R.id.complex_passwd_next_tv);
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
                        if (mUiStage == Stage.Introduction) {
                            mComplexPasswdSummaryTv.setText(R.string.jingos_complex_passwd_summary);
                        }
                        mPasswdNextTv.setBackgroundResource(R.drawable.passwd_next_btn_hl);
                        mPasswdNextTv.setEnabled(true);
                    } else if (length > 16) {
                        mComplexPasswdSummaryTv.setText(R.string.jingos_complex_passwd_long);
                        mPasswdNextTv.setBackgroundResource(R.drawable.passwd_next_btn_de);
                        mPasswdNextTv.setEnabled(false);
                    } else {
                        if (mUiStage == Stage.ConfirmWrong) {
                            mComplexPasswdSummaryTv.setText(R.string.jingos_passwd_summary_confirm);
                            mComplexPasswdSummaryTv.setTextColor(Color.parseColor("#4D000000"));
                        }
                        mPasswdNextTv.setBackgroundResource(R.drawable.passwd_next_btn_de);
                        mPasswdNextTv.setEnabled(false);
                    }
                }
            });

            mPasswdEt.setKeyListener(new DigitsKeyListener() {
                @Override
                public int getInputType() {
                    return InputType.TYPE_TEXT_VARIATION_PASSWORD;
                }
                @Override
                protected char[] getAcceptedChars() {
                    char[] data = "qwertyuioplkjhgfdsazxcvbnmQWERTYUIOPLKJHGFDSAZXCVBNM1234567890".toCharArray();   
                    return data;
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
                    updateUI();
                    handleNext();
                    break;
            }
        }

        private void initListener() {
            mPasswdVisibleIv.setOnClickListener(this);
            mPasswdNextTv.setOnClickListener(this);
        }

        private void updateUI() {
            mComplexPasswdTitleTv.setText(R.string.jingos_complex_passwd_verify);
            mComplexPasswdSummaryTv.setText(R.string.jingos_passwd_summary_confirm);
            mPasswdNextTv.setText(R.string.jingos_passwd_ok);
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
            } else {
                mPasswdEt.requestFocus();
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
            mChosenPassword = LockPatternUtils.charSequenceToByteArray(mPasswdEt.getText());
            if (mChosenPassword == null || mChosenPassword.length == 0) {
                return;
            }
            if (mUiStage == Stage.Introduction) {
                if (validatePassword(mChosenPassword) == NO_ERROR) {
                    mFirstPin = mChosenPassword;
                    mPasswdEt.setText("");
                    updateStage(Stage.NeedToConfirm);
                } else {
                    Arrays.fill(mChosenPassword, (byte) 0);
                }
            } else if (mUiStage == Stage.NeedToConfirm) {
                if (Arrays.equals(mFirstPin, mChosenPassword)) {
                    startSaveAndFinish();
                } else {
                    mPasswdEt.setText("");
                    mComplexPasswdSummaryTv.setText(R.string.jingos_passwd_error);
                    mComplexPasswdSummaryTv.setTextColor(Color.parseColor("#E95B4E"));
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
            mPasswdEt.setText("");
            if (!wasSecureBefore) {
                Intent intent = getRedactionInterstitialIntent(getActivity());
                if (intent != null) {
                    startActivity(intent);
                }
            }
            getActivity().finish();
        }
    }
}
