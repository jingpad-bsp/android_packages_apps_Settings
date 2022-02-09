package com.android.settings.password;

import android.app.admin.DevicePolicyManager.PasswordComplexity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternUtils.RequestThrottledException;
import com.android.settings.EncryptionInterstitial;

import static com.android.settings.password.ChooseLockSettingsHelper.EXTRA_KEY_REQUESTED_MIN_COMPLEXITY;

/**
 * Created by xty on 2021/6/23.
 */
public class JingLockPasswordUtils {
    private static final String TAG = "JingLockPasswordUtils";

    public static final String KEY_FIRST_PIN = "first_pin";
    public static final String KEY_UI_STAGE = "ui_stage";
    public static final String KEY_CURRENT_PASSWORD = "current_password";
    public static final String FRAGMENT_TAG_SAVE_AND_FINISH = "save_and_finish_worker";

    public static final int NO_ERROR = 0;
    public static final int CONTAIN_INVALID_CHARACTERS = 1 << 0;
    public static final int TOO_SHORT = 1 << 1;
    public static final int TOO_LONG = 1 << 2;
    public static final int CONTAIN_NON_DIGITS = 1 << 3;
    public static final int CONTAIN_SEQUENTIAL_DIGITS = 1 << 4;
    public static final int RECENTLY_USED = 1 << 5;
    public static final int NOT_ENOUGH_LETTER = 1 << 6;
    public static final int NOT_ENOUGH_UPPER_CASE = 1 << 7;
    public static final int NOT_ENOUGH_LOWER_CASE = 1 << 8;
    public static final int NOT_ENOUGH_DIGITS = 1 << 9;
    public static final int NOT_ENOUGH_SYMBOLS = 1 << 10;
    public static final int NOT_ENOUGH_NON_LETTER = 1 << 11;

    public static final int CONFIRM_EXISTING_REQUEST = 58;

    public static class IntentBuilder {

        private final Intent mIntent;

        public IntentBuilder(Context context, boolean quality) {
            mIntent = new Intent(context, quality ? JingComplexLockPassword.class : JingSimpleLockPassword.class);
            mIntent.putExtra(ChooseLockGeneric.CONFIRM_CREDENTIALS, false);
            mIntent.putExtra(EncryptionInterstitial.EXTRA_REQUIRE_PASSWORD, false);
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_HAS_CHALLENGE, false);
        }

        public IntentBuilder setPasswordQuality(int quality) {
            mIntent.putExtra(LockPatternUtils.PASSWORD_TYPE_KEY, quality);
            return this;
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

        public IntentBuilder setPassword(byte[] password) {
            mIntent.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_PASSWORD, password);
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

        public IntentBuilder setRequestedMinComplexity(@PasswordComplexity int level) {
            mIntent.putExtra(EXTRA_KEY_REQUESTED_MIN_COMPLEXITY, level);
            return this;
        }

        public Intent build() {
            return mIntent;
        }
    }

    public static class SaveAndFinishWorker extends SaveChosenLockWorkerBase {

        private byte[] mChosenPassword;
        private byte[] mCurrentPassword;
        private int mRequestedQuality;

        public void start(LockPatternUtils utils, boolean required,
                          boolean hasChallenge, long challenge,
                          byte[] chosenPassword, byte[] currentPassword, int requestedQuality, int userId) {
            prepare(utils, required, hasChallenge, challenge, userId);

            mChosenPassword = chosenPassword;
            mCurrentPassword = currentPassword;
            mRequestedQuality = requestedQuality;
            mUserId = userId;

            start();
        }

        @Override
        protected Pair<Boolean, Intent> saveAndVerifyInBackground() {
            final boolean success = mUtils.saveLockPassword(
                    mChosenPassword, mCurrentPassword, mRequestedQuality, mUserId);
            Intent result = null;
            if (success && mHasChallenge) {
                byte[] token;
                try {
                    token = mUtils.verifyPassword(mChosenPassword, mChallenge, mUserId);
                } catch (RequestThrottledException e) {
                    token = null;
                }

                if (token == null) {
                    Log.e(TAG, "critical: no token returned for known good password.");
                }

                result = new Intent();
                result.putExtra(ChooseLockSettingsHelper.EXTRA_KEY_CHALLENGE_TOKEN, token);
            }
            return Pair.create(success, result);
        }
    }
}
