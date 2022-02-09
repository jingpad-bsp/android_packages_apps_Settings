package com.android.settings.notification;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.RestrictedRadioButton;
import com.android.settings.SettingsActivity;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.SetupRedactionInterstitial;
import com.android.settings.SetupWizardUtils;
import com.android.settings.Utils;
import com.android.settingslib.RestrictedLockUtilsInternal;

import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS;
import static android.app.admin.DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS;
import static android.provider.Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS;
import static android.provider.Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS;
import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

/**
 * Created by xty on 2021/6/25.
 */
public class JingRedactionInterstitial extends SettingsActivity {
    private static final String TAG = "JingRedactionInterstitial";

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, JingRedactionFragment.class.getName());
        return modIntent;
    }

    @Override
    protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
        resid = SetupWizardUtils.getTheme(getIntent());
        super.onApplyThemeResource(theme, resid, first);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return JingRedactionFragment.class.getName().equals(fragmentName);
    }

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        findViewById(R.id.content_parent).setFitsSystemWindows(false);
    }

    /**
     * Create an intent for launching RedactionInterstitial.
     *
     * @return An intent to launch the activity is if is available, @null if the activity is not
     * available to be launched.
     */
    public static Intent createStartIntent(Context ctx, int userId) {
        return new Intent(ctx, JingRedactionInterstitial.class)
                .putExtra(EXTRA_SHOW_FRAGMENT_TITLE_RESID,
                        UserManager.get(ctx).isManagedProfile(userId)
                                ? R.string.lock_screen_notifications_interstitial_title_profile
                                : R.string.lock_screen_notifications_interstitial_title)
                .putExtra(Intent.EXTRA_USER_ID, userId);
    }


    public static class JingRedactionFragment extends SettingsPreferenceFragment
            implements RadioGroup.OnCheckedChangeListener {

        private RadioGroup mRadioGroup;
        private RestrictedRadioButton mShowAllButton;
        private RestrictedRadioButton mRedactSensitiveButton;
        private int mUserId;
        private TextView mRedactOkTv;

        @Override
        public int getMetricsCategory() {
            return SettingsEnums.NOTIFICATION_REDACTION;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            return inflater.inflate(R.layout.jing_redaction_interstitial, container, false);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            mRadioGroup = (RadioGroup) view.findViewById(R.id.radio_group);
            mShowAllButton = (RestrictedRadioButton) view.findViewById(R.id.redact_show_all_rb);
            mRedactOkTv = (TextView) view.findViewById(R.id.redact_ok_tv);
            mRedactSensitiveButton =
                    (RestrictedRadioButton) view.findViewById(R.id.redact_sensitive_rb);

            mRadioGroup.setOnCheckedChangeListener(this);
            mUserId = Utils.getUserIdFromBundle(
                    getContext(), getActivity().getIntent().getExtras());
            if (UserManager.get(getContext()).isManagedProfile(mUserId)) {
                ((TextView) view.findViewById(R.id.sud_layout_description))
                        .setText(R.string.lock_screen_notifications_interstitial_message_profile);
                mShowAllButton.setText(R.string.lock_screen_notifications_summary_show_profile);
                mRedactSensitiveButton
                        .setText(R.string.lock_screen_notifications_summary_hide_profile);

                ((RadioButton) view.findViewById(R.id.redact_hide_all_rb)).setVisibility(View.GONE);
            }

            mRedactOkTv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SetupRedactionInterstitial.setEnabled(getContext(), false);
                    final JingRedactionInterstitial activity = (JingRedactionInterstitial) getActivity();
                    if (activity != null) {
                        activity.setResult(RESULT_OK, null);
                        finish();
                    }
                }
            });
        }

        @Override
        public void onResume() {
            super.onResume();
            // Disable buttons according to policy.

            checkNotificationFeaturesAndSetDisabled(mShowAllButton,
                    KEYGUARD_DISABLE_SECURE_NOTIFICATIONS |
                            KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS);
            checkNotificationFeaturesAndSetDisabled(mRedactSensitiveButton,
                    KEYGUARD_DISABLE_SECURE_NOTIFICATIONS);
            loadFromSettings();
        }

        private void checkNotificationFeaturesAndSetDisabled(RestrictedRadioButton button,
                                                             int keyguardNotifications) {
            EnforcedAdmin admin = RestrictedLockUtilsInternal.checkIfKeyguardFeaturesDisabled(
                    getActivity(), keyguardNotifications, mUserId);
            button.setDisabledByAdmin(admin);
        }

        private void loadFromSettings() {
            final boolean managedProfile = UserManager.get(getContext()).isManagedProfile(mUserId);
            // Hiding all notifications is device-wide setting, managed profiles can only set
            // whether their notifications are show in full or redacted.
            final boolean showNotifications = managedProfile || Settings.Secure.getIntForUser(
                    getContentResolver(), LOCK_SCREEN_SHOW_NOTIFICATIONS, 0, mUserId) != 0;
            final boolean showUnredacted = Settings.Secure.getIntForUser(
                    getContentResolver(), LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, 1, mUserId) != 0;

            int checkedButtonId = R.id.redact_hide_all_rb;
            if (showNotifications) {
                if (showUnredacted && !mShowAllButton.isDisabledByAdmin()) {
                    checkedButtonId = R.id.redact_show_all_rb;
                } else if (!mRedactSensitiveButton.isDisabledByAdmin()) {
                    checkedButtonId = R.id.redact_sensitive_rb;
                }
            }

            mRadioGroup.check(checkedButtonId);
        }

        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            final boolean show = (checkedId == R.id.redact_show_all_rb);
            final boolean enabled = (checkedId != R.id.redact_hide_all_rb);

            Settings.Secure.putIntForUser(getContentResolver(),
                    LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, show ? 1 : 0, mUserId);
            Settings.Secure.putIntForUser(getContentResolver(),
                    LOCK_SCREEN_SHOW_NOTIFICATIONS, enabled ? 1 : 0, mUserId);

        }
    }
}
