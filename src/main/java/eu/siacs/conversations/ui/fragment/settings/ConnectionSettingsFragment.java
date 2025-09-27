package eu.siacs.conversations.ui.fragment.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.google.common.base.Strings;

import java.io.File;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.ui.activity.SettingsActivity;
import eu.siacs.conversations.utils.ChatBackgroundHelper;
import eu.siacs.conversations.utils.Resolver;
import java.util.Arrays;

public class ConnectionSettingsFragment extends XmppPreferenceFragment {

    private static final String GROUPS_AND_CONFERENCES = "groups_and_conferences";

    public static boolean hideChannelDiscovery() {
        return QuickConversationsService.isQuicksy()
                || QuickConversationsService.isPlayStoreFlavor()
                || Strings.isNullOrEmpty(Config.CHANNEL_DISCOVERY);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_connection, rootKey);
        final var connectionOptions = findPreference(AppSettings.SHOW_CONNECTION_OPTIONS);
        final var channelDiscovery = findPreference(AppSettings.CHANNEL_DISCOVERY_METHOD);
        final var groupsAndConferences = findPreference(GROUPS_AND_CONFERENCES);
        if (connectionOptions == null || channelDiscovery == null || groupsAndConferences == null) {
            throw new IllegalStateException();
        }
        if (QuickConversationsService.isQuicksy()) {
            connectionOptions.setVisible(false);
        }
        if (hideChannelDiscovery()) {
            groupsAndConferences.setVisible(false);
            channelDiscovery.setVisible(false);
        }

        final var resetDNSServerPreference = findPreference("reset_dns_server");
        if (resetDNSServerPreference != null) {
            resetDNSServerPreference.setOnPreferenceClickListener(preference -> {

                final var dnsv4Server = (EditTextPreference) findPreference("dns_server_ipv4");
                if (dnsv4Server != null) {
                    dnsv4Server.setText("194.242.2.2");
                }

                final var dnsv6Server = (EditTextPreference) findPreference("dns_server_ipv6");
                if (dnsv6Server != null) {
                    dnsv6Server.setText("[2a07:e340::2]");
                }

                Toast.makeText(requireSettingsActivity(),R.string.dns_server_reset,Toast.LENGTH_LONG).show();
                return true;
            });
        }
    }

    @Override
    protected void onSharedPreferenceChanged(@NonNull String key) {
        super.onSharedPreferenceChanged(key);
        switch (key) {
            case AppSettings.USE_TOR -> {
                final var appSettings = new AppSettings(requireContext());
                if (appSettings.isUseTor()) {
                    runOnUiThread(
                            () ->
                                    Toast.makeText(
                                                    requireActivity(),
                                                    R.string.audio_video_disabled_tor,
                                                    Toast.LENGTH_LONG)
                                            .show());
                }
                reconnectAccounts();
                requireService().reinitializeMuclumbusService();
            }
            case AppSettings.USE_I2P -> {
                final var appSettings = new AppSettings(requireContext());
                if (appSettings.isUseI2P()) {
                    runOnUiThread(
                            () ->
                                    Toast.makeText(
                                                    requireActivity(),
                                                    R.string.audio_video_disabled_i2p,
                                                    Toast.LENGTH_LONG)
                                            .show());
                }
                reconnectAccounts();
                requireService().reinitializeMuclumbusService();
            }
            case AppSettings.SHOW_CONNECTION_OPTIONS, AppSettings.PREFER_IPV6 -> {
                reconnectAccounts();
            }
        }
        if (Arrays.asList(AppSettings.USE_TOR, AppSettings.SHOW_CONNECTION_OPTIONS).contains(key)) {
            final var appSettings = new AppSettings(requireContext());
            if (appSettings.isUseTor() || appSettings.isExtendedConnectionOptions()) {
                return;
            }
            resetUserDefinedHostname();
        }
    }

    private void resetUserDefinedHostname() {
        final var service = requireService();
        for (final Account account : service.getAccounts()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid() + ": resetting hostname and port to defaults");
            account.setHostname(null);
            account.setPort(Resolver.XMPP_PORT_STARTTLS);
            service.databaseBackend.updateAccount(account);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_connection_options);
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
}
