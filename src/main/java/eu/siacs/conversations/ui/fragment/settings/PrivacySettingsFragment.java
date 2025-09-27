package eu.siacs.conversations.ui.fragment.settings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.R;

public class PrivacySettingsFragment extends XmppPreferenceFragment {

    // Original implementation by Upendra Shah: https://stackoverflow.com/questions/47673127
    private boolean hasNotificationAccess() {
        Context context = requireContext();
        ContentResolver contentResolver = context.getContentResolver();
        String enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
        String packageName = context.getPackageName();

        // check to see if the enabledNotificationListeners String contains our package name
        return !(enabledNotificationListeners == null || !enabledNotificationListeners.contains(packageName));
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_privacy, rootKey);
        try {
            Class.forName("io.sentry.Sentry");
            final var neverSend = findPreference("send_crash_reports");
            neverSend.setVisible(false);
            final var appCat = findPreference("category_application");
            appCat.setVisible(false);
        } catch (final ClassNotFoundException e) { }

        final ListPreference updateTracksLongerThan = findPreference("update_track_longer_than");
        if (updateTracksLongerThan == null) {
            throw new IllegalStateException("The preference resource file is missing preferences");
        }
        setValues(
                updateTracksLongerThan,
                R.array.track_duration_values,
                value -> {
                    if (value <= 0) {
                        return getString(R.string.ignore);
                    } else {
                        return getResources().getQuantityString(R.plurals.seconds, value, value);
                    }
                });

        final SwitchPreferenceCompat updateNowPlaying = findPreference("load_now_playing_from_system");
        if (updateNowPlaying == null) {
            throw new IllegalStateException("The preference resource file is missing preferences");
        }

        updateNowPlaying.setOnPreferenceChangeListener((pref, newVal) -> {
            boolean turningOn = (Boolean) newVal;

            if (turningOn && !hasNotificationAccess()) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
            }

            return true;
        });
    }

    @Override
    protected void onSharedPreferenceChanged(@NonNull String key) {
        super.onSharedPreferenceChanged(key);
        switch (key) {
            case AppSettings.AWAY_WHEN_SCREEN_IS_OFF, AppSettings.MANUALLY_CHANGE_PRESENCE -> {
                requireService().toggleScreenEventReceiver();
                requireService().refreshAllPresences();
            }
            case AppSettings.CUSTOM_RESOURCE_NAME -> {
                reconnectAccounts();
            }
            case AppSettings.CONFIRM_MESSAGES,
                    AppSettings.BROADCAST_LAST_ACTIVITY,
                    AppSettings.ALLOW_MESSAGE_CORRECTION,
                    AppSettings.DND_ON_SILENT_MODE,
                    AppSettings.TREAT_VIBRATE_AS_SILENT -> {
                requireService().refreshAllPresences();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_privacy);
    }

    @Override
    public void onResume() {
        super.onResume();

        final SwitchPreferenceCompat updateNowPlaying = findPreference("load_now_playing_from_system");
        if (updateNowPlaying == null) {
            throw new IllegalStateException("The preference resource file is missing preferences");
        }

        if (updateNowPlaying.isChecked() && !hasNotificationAccess()) {
            updateNowPlaying.setChecked(false);
        }
    }
}
