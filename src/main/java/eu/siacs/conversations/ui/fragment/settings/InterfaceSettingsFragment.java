package eu.siacs.conversations.ui.fragment.settings;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.preference.Preference;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.DynamicColors;

import java.io.File;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.activity.SettingsActivity;
import eu.siacs.conversations.ui.util.SettingsUtils;
import eu.siacs.conversations.utils.ChatBackgroundHelper;
import eu.siacs.conversations.utils.ThemeHelper;

public class InterfaceSettingsFragment extends XmppPreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_interface, rootKey);
        final var themePreference = findPreference("theme");
        final var dynamicColors = findPreference("dynamic_colors");
        if (themePreference == null || dynamicColors == null) {
            throw new IllegalStateException(
                    "The preference resource file did not contain theme or color preferences");
        }
        themePreference.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    if (newValue instanceof final String theme) {
                        requireSettingsActivity().recreate();
                    }
                    updateCustomVisibility("custom".equals(newValue));
                    return true;
                });
        updateCustomVisibility(getString(R.string.pref_theme_custom).equals(themePreference.getSummary()));
        dynamicColors.setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    requireSettingsActivity().setDynamicColors(Boolean.TRUE.equals(newValue));
                    return true;
                });

        // Set custom Background globally
        final var importBackgroundPreference = findPreference("import_background");
        if (importBackgroundPreference != null) {
            importBackgroundPreference.setSummary(getString(R.string.pref_chat_background_summary));
            importBackgroundPreference.setOnPreferenceClickListener(preference -> {
                if (requireSettingsActivity().hasStoragePermission(ChatBackgroundHelper.REQUEST_IMPORT_BACKGROUND)) {
                    ChatBackgroundHelper.openBGPicker(this);
                }
                return true;
            });
        }

        final var deleteBackgroundPreference = findPreference("delete_background");
        if (deleteBackgroundPreference != null) {
            deleteBackgroundPreference.setSummary(getString(R.string.pref_delete_background_summary));
            deleteBackgroundPreference.setOnPreferenceClickListener(preference -> {
                try {
                    File bgfile =  ChatBackgroundHelper.getBgFile(requireSettingsActivity(), null);
                    if (bgfile.exists()) {
                        bgfile.delete();
                        Toast.makeText(requireSettingsActivity(),R.string.delete_background_success,Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireSettingsActivity(),R.string.no_background_set,Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(requireSettingsActivity(),R.string.delete_background_failed,Toast.LENGTH_LONG).show();
                    throw new RuntimeException(e);
                }
                return true;
            });
        }
    }

    protected void updateCustomVisibility(boolean custom) {
        custom = custom && (Build.VERSION.SDK_INT >= 30);

        final var dark = requireSettingsActivity().isDark();
        final var sharedPreferences = getPreferenceManager().getSharedPreferences();
        findPreference("custom_theme_automatic").setVisible(custom);
        findPreference("custom_theme_dark").setVisible(custom && !sharedPreferences.getBoolean("custom_theme_automatic", true));
        findPreference("custom_theme_color_match").setVisible(custom);

        findPreference("custom_theme_primary").setVisible(custom && !dark);
        findPreference("custom_theme_primary_dark").setVisible(custom && !dark);
        findPreference("custom_theme_accent").setVisible(custom && !dark);
        findPreference("custom_theme_background_primary").setVisible(custom && !dark);

        findPreference("custom_dark_theme_primary").setVisible(custom && dark);
        findPreference("custom_dark_theme_primary_dark").setVisible(custom && dark);
        findPreference("custom_dark_theme_accent").setVisible(custom && dark);
        findPreference("custom_dark_theme_background_primary").setVisible(custom && dark);

        findPreference("dynamic_colors").setVisible(DynamicColors.isDynamicColorAvailable() && !custom);
    }

    @Override
    protected void onSharedPreferenceChanged(@NonNull String key) {
        super.onSharedPreferenceChanged(key);
        if (key.equals(AppSettings.ALLOW_SCREENSHOTS)) {
            SettingsUtils.applyScreenshotSetting(requireActivity());
        }

        if (
            key.equals("custom_theme_automatic") ||
            key.equals("custom_theme_dark") ||
            key.equals("custom_theme_color_match") ||
            key.equals("custom_theme_primary") ||
            key.equals("custom_theme_primary_dark") ||
            key.equals("custom_theme_accent") ||
            key.equals("custom_theme_background_primary") ||
            key.equals("custom_dark_theme_primary") ||
            key.equals("custom_dark_theme_primary_dark") ||
            key.equals("custom_dark_theme_accent") ||
            key.equals("custom_dark_theme_background_primary"))
        {
            ThemeHelper.applyCustomColors(requireService());
            new Thread(() -> runOnUiThread(() -> requireActivity().recreate())).start();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_title_interface);
    }

    public SettingsActivity requireSettingsActivity() {
        final var activity = requireActivity();
        if (activity instanceof SettingsActivity settingsActivity) {
            return settingsActivity;
        }
        throw new IllegalStateException(
                String.format(
                        "%s is not %s",
                        activity.getClass().getName(), SettingsActivity.class.getName()));
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        ChatBackgroundHelper.onActivityResult(requireSettingsActivity(), requestCode, resultCode, data, null);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0)
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                ChatBackgroundHelper.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
            } else {
                Toast.makeText(
                                requireSettingsActivity(),
                                getString(
                                        R.string.no_storage_permission,
                                        getString(R.string.app_name)),
                                Toast.LENGTH_SHORT)
                        .show();
            }
    }
}
