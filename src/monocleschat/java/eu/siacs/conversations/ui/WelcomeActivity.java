package eu.siacs.conversations.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.UUID;

import de.monocles.chat.RegisterMonoclesActivity;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityWelcomeBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.InstallReferrerUtils;
import eu.siacs.conversations.utils.SignupUtils;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;

import static eu.siacs.conversations.AppSettings.ALLOW_SCREENSHOTS;
import static eu.siacs.conversations.AppSettings.BROADCAST_LAST_ACTIVITY;
import static eu.siacs.conversations.AppSettings.CONFIRM_MESSAGES;
import static eu.siacs.conversations.AppSettings.DANE_ENFORCED;
import static eu.siacs.conversations.AppSettings.LOAD_PROVIDERS_EXTERNAL;
import static eu.siacs.conversations.AppSettings.SECURE_TLS;
import static eu.siacs.conversations.AppSettings.SHOW_LINK_PREVIEWS;
import static eu.siacs.conversations.AppSettings.SHOW_MAPS_INSIDE;
import static eu.siacs.conversations.AppSettings.UNENCRYPTED_REACTIONS;
import static eu.siacs.conversations.AppSettings.BLIND_TRUST_BEFORE_VERIFICATION;
import static eu.siacs.conversations.AppSettings.SEND_CRASH_REPORTS;
import static eu.siacs.conversations.AppSettings.USE_CACHE_STORAGE;
import static eu.siacs.conversations.utils.PermissionUtils.allGranted;
import static eu.siacs.conversations.utils.PermissionUtils.writeGranted;
import static eu.siacs.conversations.xml.Namespace.CHAT_STATES;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class WelcomeActivity extends XmppActivity implements XmppConnectionService.OnAccountCreated, XmppConnectionService.OnAccountUpdate, KeyChainAliasCallback {

    private static final int REQUEST_IMPORT_BACKUP = 0x63fb;

    // Default settings screen
    static final int LOADPROVIDERSEXTERNAL = 0;
    static final int ALLOWSCREENSHOTS = 1;
    static final int SHOWWEBLINKS = 2;
    static final int SHOWMAPPREVIEW = 3;
    static final int UNENCRYPTEDREACTIONS = 4;
    static final int CHATSTATES = 5;
    static final int CONFIRMMESSAGES = 6;
    static final int LASTSEEN = 7;
    static final int BLINDTRUST = 8;
    static final int ENFORCEDANE = 9;
    static final int USESECURETLSCIPHERS = 10;
    static final int USECACHESTORAGE = 11;
    static final int SENDCRASHREPORTS = 12;

    private XmppUri inviteUri;
    private Account onboardingAccount = null;
    private ActivityWelcomeBinding binding = null;

    public static void launch(AppCompatActivity activity) {
        Intent intent = new Intent(activity, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

    public void onInstallReferrerDiscovered(final Uri referrer) {
        Log.d(Config.LOGTAG, "welcome activity: on install referrer discovered " + referrer);
        if ("xmpp".equalsIgnoreCase(referrer.getScheme())) {
            final XmppUri xmppUri = new XmppUri(referrer);
            runOnUiThread(() -> processXmppUri(xmppUri));
        } else {
            Log.i(Config.LOGTAG, "install referrer was not an XMPP uri");
        }
    }

    private void processXmppUri(final XmppUri xmppUri) {
        if (!xmppUri.isValidJid()) {
            return;
        }
        final String preAuth = xmppUri.getParameter(XmppUri.PARAMETER_PRE_AUTH);
        final Jid jid = xmppUri.getJid();
        final Intent intent;
        if (xmppUri.isAction(XmppUri.ACTION_REGISTER)) {
            intent = SignupUtils.getTokenRegistrationIntent(this, jid, preAuth);
        } else if (xmppUri.isAction(XmppUri.ACTION_ROSTER) && "y".equals(xmppUri.getParameter(XmppUri.PARAMETER_IBR))) {
            intent = SignupUtils.getTokenRegistrationIntent(this, jid.getDomain(), preAuth);
            intent.putExtra(StartConversationActivity.EXTRA_INVITE_URI, xmppUri.toString());
        } else {
            intent = null;
        }
        if (intent != null) {
            startActivity(intent);
            finish();
            return;
        }
        this.inviteUri = xmppUri;
    }

    @Override
    protected synchronized void refreshUiReal() {
        if (onboardingAccount == null) return;
        if (onboardingAccount.getStatus() != Account.State.ONLINE) return;

        Intent intent = new Intent(this, StartConversationActivity.class);
        intent.putExtra("init", true);
        intent.putExtra(EXTRA_ACCOUNT, onboardingAccount.getJid().asBareJid().toString());
        onboardingAccount = null;
        startActivity(intent);
        finish();
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    protected void onBackendConnected() {
        if (xmppConnectionService.isOnboarding()) {
            binding.registerNewAccount.setText("Working...");
            binding.registerNewAccount.setEnabled(false);
            binding.slideshowPager.setCurrentItem(4);
            onboardingAccount = xmppConnectionService.getAccounts().get(0);
            xmppConnectionService.reconnectAccountInBackground(onboardingAccount);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        new InstallReferrerUtils(this);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent != null) {
            setIntent(intent);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        super.onCreate(savedInstanceState);
        getPreferences().edit().putStringSet("pstn_gateways", new HashSet<>()).apply();
        binding = DataBindingUtil.setContentView(this, R.layout.activity_welcome);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        binding.slideshowPager.setAdapter(new WelcomePagerAdapter(binding.slideshowPager));
        binding.dotsIndicator.setViewPager(binding.slideshowPager);
        binding.slideshowPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            public void onPageScrollStateChanged(int state) {
                setSettings();
            }
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { }

            public void onPageSelected(int position) {
                binding.buttonNext.setVisibility(position > 2 ? View.GONE : View.VISIBLE);
                binding.buttonPrivacy.setVisibility(position < 3 ? View.GONE : View.VISIBLE);
                if (position > 2) {
                    setSettings();
                }
            }
        });
        binding.buttonNext.setOnClickListener((v) ->
            binding.slideshowPager.setCurrentItem(binding.slideshowPager.getCurrentItem() + 1)
        );
        binding.buttonPrivacy.setOnClickListener((v) ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://monocles.eu/legal-privacy/#policies-section")))
        );
        configureActionBar(getSupportActionBar(), false);
        binding.registerNewAccount.setOnClickListener(v -> {
            final Intent intent = new Intent(this, RegisterMonoclesActivity.class);
            addInviteUri(intent);
            startActivity(intent);
            /* // TODO: Better Onboarding later
            if (hasInviteUri()) {
                final Intent intent = new Intent(this, MagicCreateActivity.class);
                addInviteUri(intent);
                startActivity(intent);
            } else {
                binding.registerNewAccount.setText("Working...");
                binding.registerNewAccount.setEnabled(false);
                onboardingAccount = new Account(Jid.ofLocalAndDomain(UUID.randomUUID().toString(), Config.ONBOARDING_DOMAIN.toString()), CryptoHelper.createPassword(new SecureRandom()));
                onboardingAccount.setOption(Account.OPTION_REGISTER, true);
                onboardingAccount.setOption(Account.OPTION_FIXED_USERNAME, true);
                xmppConnectionService.createAccount(onboardingAccount);
            }
             */
        });
        binding.useExisting.setOnClickListener(v -> {
            final List<Account> accounts = xmppConnectionService.getAccounts();
            Intent intent = new Intent(WelcomeActivity.this, EditAccountActivity.class);
            intent.putExtra(EditAccountActivity.EXTRA_FORCE_REGISTER, false);
            if (accounts.size() == 1) {
                intent.putExtra("jid", accounts.get(0).getJid().asBareJid().toString());
                intent.putExtra("init", true);
            } else if (accounts.size() >= 1) {
                intent = new Intent(WelcomeActivity.this, ManageAccountActivity.class);
            }
            addInviteUri(intent);
            startActivity(intent);
        });
        binding.useSnikket.setOnClickListener(v -> {
            final List<Account> accounts = xmppConnectionService.getAccounts();
            Intent intent = new Intent(WelcomeActivity.this, EditAccountActivity.class);
            intent.putExtra(EditAccountActivity.EXTRA_FORCE_REGISTER, false);
            intent.putExtra("snikket", true);
            if (accounts.size() == 1) {
                intent.putExtra("jid", accounts.get(0).getJid().asBareJid().toString());
                intent.putExtra("init", true);
            } else if (accounts.size() >= 1) {
                intent = new Intent(WelcomeActivity.this, ManageAccountActivity.class);
            }
            addInviteUri(intent);
            startActivity(intent);
        });

        binding.useBackup.setOnClickListener(v -> {
            if (hasStoragePermission(REQUEST_IMPORT_BACKUP)) {
                startActivity(new Intent(this, ImportBackupActivity.class));
            }
        });
        getDefaults();
        createInfoMenu();
    }

    private void createInfoMenu() {
        this.binding.actionInfoLoadProvidersListExternal.setOnClickListener(string -> showInfo(LOADPROVIDERSEXTERNAL));
        this.binding.actionInfoAllowScreenshots.setOnClickListener(string -> showInfo(ALLOWSCREENSHOTS));
        this.binding.actionInfoShowWeblinks.setOnClickListener(string -> showInfo(SHOWWEBLINKS));
        this.binding.actionInfoShowMapPreviews.setOnClickListener(string -> showInfo(SHOWMAPPREVIEW));
        this.binding.actionInfoAllowUnencryptedReactions.setOnClickListener(string -> showInfo(UNENCRYPTEDREACTIONS));
        this.binding.actionInfoChatStates.setOnClickListener(string -> showInfo(CHATSTATES));
        this.binding.actionInfoConfirmMessages.setOnClickListener(string -> showInfo(CONFIRMMESSAGES));
        this.binding.actionInfoLastSeen.setOnClickListener(string -> showInfo(LASTSEEN));
        this.binding.actionInfoBlindTrust.setOnClickListener(string -> showInfo(BLINDTRUST));
        this.binding.actionInfoDane.setOnClickListener(string -> showInfo(ENFORCEDANE));
        this.binding.actionInfoUseSecureTls.setOnClickListener(string -> showInfo(USESECURETLSCIPHERS));
        this.binding.actionInfoStoreInCache.setOnClickListener(string -> showInfo(USECACHESTORAGE));
        this.binding.actionInfoSendCrashReports.setOnClickListener(string -> showInfo(SENDCRASHREPORTS));
    }

    private void getDefaults() {
        this.binding.switchLoadProvidersListExternal.setChecked(getResources().getBoolean(R.bool.load_providers_list_external));
        this.binding.allowScreenshots.setChecked(getResources().getBoolean(R.bool.allow_screenshots));
        this.binding.showLinks.setChecked(getResources().getBoolean(R.bool.show_link_previews));
        this.binding.showMappreview.setChecked(getResources().getBoolean(R.bool.show_maps_inside));
        this.binding.allowUnencryptedReactions.setChecked(getResources().getBoolean(R.bool.allow_unencrypted_reactions));
        this.binding.chatStates.setChecked(getResources().getBoolean(R.bool.chat_states));
        this.binding.confirmMessages.setChecked(getResources().getBoolean(R.bool.confirm_messages));
        this.binding.lastSeen.setChecked(getResources().getBoolean(R.bool.last_activity));
        this.binding.blindTrust.setChecked(getResources().getBoolean(R.bool.btbv));
        this.binding.dane.setChecked(getResources().getBoolean(R.bool.enforce_dane));
        this.binding.useSecureTls.setChecked(getResources().getBoolean(R.bool.secure_tls));
        this.binding.storeInCache.setChecked(getResources().getBoolean(R.bool.default_store_media_in_cache));
        this.binding.sendCrashReports.setChecked(getResources().getBoolean(R.bool.send_crash_reports));
    }


    private void showInfo(int setting) {
        String title;
        String message;
        switch (setting) {
            case LOADPROVIDERSEXTERNAL:
                title = getString(R.string.pref_load_providers_list_external);
                message = getString(R.string.pref_load_providers_list_external_summary);
                break;
            case ALLOWSCREENSHOTS:
                title = getString(R.string.pref_allow_screenshots);
                message = getString(R.string.pref_allow_screenshots_summary);
                break;
            case SHOWWEBLINKS:
                title = getString(R.string.show_link_previews);
                message = getString(R.string.show_link_previews_summary);
                break;
            case SHOWMAPPREVIEW:
                title = getString(R.string.pref_show_mappreview_inside);
                message = getString(R.string.pref_show_mappreview_inside_summary);
                break;
            case UNENCRYPTEDREACTIONS:
                title = getString(R.string.pref_allow_unencrypted_reactions);
                message = getString(R.string.pref_allow_unencrypted_reactions_summary);
                break;
            case CHATSTATES:
                title = getString(R.string.pref_chat_states);
                message = getString(R.string.pref_chat_states_summary);
                break;
            case CONFIRMMESSAGES:
                title = getString(R.string.pref_confirm_messages);
                message = getString(R.string.pref_confirm_messages_summary);
                break;
            case LASTSEEN:
                title = getString(R.string.pref_broadcast_last_activity);
                message = getString(R.string.pref_broadcast_last_activity_summary);
                break;
            case BLINDTRUST:
                title = getString(R.string.pref_blind_trust_before_verification);
                message = getString(R.string.blindly_trusted_omemo_keys);
                break;
            case ENFORCEDANE:
                title = getString(R.string.pref_enforce_dane);
                message = getString(R.string.pref_enforce_dane_summary);
                break;
            case USESECURETLSCIPHERS:
                title = getString(R.string.pref_secure_tls);
                message = getString(R.string.pref_secure_tls_summary);
                break;
            case USECACHESTORAGE:
                title = getString(R.string.store_media_only_in_cache);
                message = getString(R.string.pref_store_media_in_cache);
                break;
            case SENDCRASHREPORTS:
                title = getString(R.string.pref_send_crash_reports);
                message = getString(R.string.pref_never_send_crash_summary);
                break;
            default:
                title = getString(R.string.error);
                message = getString(R.string.error);
        }
        Log.d(Config.LOGTAG, "STRING value " + title);
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setNeutralButton(getString(R.string.ok), null);
        builder.create().show();
    }


    private void setSettings() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putBoolean(LOAD_PROVIDERS_EXTERNAL, this.binding.switchLoadProvidersListExternal.isChecked()).apply();
        preferences.edit().putBoolean(ALLOW_SCREENSHOTS, this.binding.allowScreenshots.isChecked()).apply();
        preferences.edit().putBoolean(SHOW_LINK_PREVIEWS, this.binding.showLinks.isChecked()).apply();
        preferences.edit().putBoolean(SHOW_MAPS_INSIDE, this.binding.showMappreview.isChecked()).apply();
        preferences.edit().putBoolean(UNENCRYPTED_REACTIONS, this.binding.allowUnencryptedReactions.isChecked()).apply();
        preferences.edit().putBoolean(CHAT_STATES, this.binding.chatStates.isChecked()).apply();
        preferences.edit().putBoolean(CONFIRM_MESSAGES, this.binding.confirmMessages.isChecked()).apply();
        preferences.edit().putBoolean(BROADCAST_LAST_ACTIVITY, this.binding.lastSeen.isChecked()).apply();
        preferences.edit().putBoolean(BLIND_TRUST_BEFORE_VERIFICATION, this.binding.blindTrust.isChecked()).apply();
        preferences.edit().putBoolean(DANE_ENFORCED, this.binding.dane.isChecked()).apply();
        preferences.edit().putBoolean(SECURE_TLS, this.binding.useSecureTls.isChecked()).apply();
        preferences.edit().putBoolean(USE_CACHE_STORAGE, this.binding.storeInCache.isChecked()).apply();
        preferences.edit().putBoolean(SEND_CRASH_REPORTS, this.binding.sendCrashReports.isChecked()).apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.welcome_menu, menu);
        final MenuItem scan = menu.findItem(R.id.action_scan_qr_code);
        scan.setVisible(Compatibility.hasFeatureCamera(this));
        return super.onCreateOptionsMenu(menu);
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_import_backup:
                if (hasStoragePermission(REQUEST_IMPORT_BACKUP)) {
                    startActivity(new Intent(this, ImportBackupActivity.class));
                }
                break;
            case R.id.action_scan_qr_code:
                UriHandlerActivity.scan(this, true);
                break;
            case R.id.action_add_account_with_cert:
                addAccountFromKey();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addAccountFromKey() {
        try {
            KeyChain.choosePrivateKeyAlias(this, this, null, null, null, -1, null);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.device_does_not_support_certificates, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void alias(final String alias) {
        if (alias != null) {
            xmppConnectionService.createAccountFromKey(alias, this);
        }
    }

    @Override
    public void onAccountCreated(final Account account) {
        final Intent intent = new Intent(this, EditAccountActivity.class);
        intent.putExtra("jid", account.getJid().asBareJid().toString());
        intent.putExtra("init", true);
        addInviteUri(intent);
        startActivity(intent);
    }

    @Override
    public void informUser(final int r) {
        runOnUiThread(() -> Toast.makeText(this, r, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        UriHandlerActivity.onRequestPermissionResult(this, requestCode, grantResults);
        if (grantResults.length > 0) {
            if (allGranted(grantResults)) {
                switch (requestCode) {
                    case REQUEST_IMPORT_BACKUP:
                        startActivity(new Intent(this, ImportBackupActivity.class));
                        break;
                }
            } else if (Arrays.asList(permissions).contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
            }
        }
        if (writeGranted(grantResults, permissions)) {
            if (xmppConnectionService != null) {
                xmppConnectionService.restartFileObserver();
            }
        }
    }

    protected boolean hasInviteUri() {
        final Intent from = getIntent();
        if (from != null && from.hasExtra(StartConversationActivity.EXTRA_INVITE_URI)) return true;
        return this.inviteUri != null;
    }

    public void addInviteUri(Intent to) {
        final Intent from = getIntent();
        if (from != null && from.hasExtra(StartConversationActivity.EXTRA_INVITE_URI)) {
            final String invite = from.getStringExtra(StartConversationActivity.EXTRA_INVITE_URI);
            to.putExtra(StartConversationActivity.EXTRA_INVITE_URI, invite);
        } else if (this.inviteUri != null) {
            Log.d(Config.LOGTAG, "injecting referrer uri into on-boarding flow");
            to.putExtra(StartConversationActivity.EXTRA_INVITE_URI, this.inviteUri.toString());
        }
    }

    class WelcomePagerAdapter extends PagerAdapter {
        protected View[] pages;

        public WelcomePagerAdapter(ViewPager p) {
            super();
            pages = new View[]{ p.getChildAt(0), p.getChildAt(1), p.getChildAt(2), p.getChildAt(3) };
            for (View v : pages) {
                p.removeView(v);
            }
        }

        @Override
        public int getCount() {
            return 4;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            container.addView(pages[position]);
            return pages[position];
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
            return view == o;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, Object o) {
            container.removeView(pages[position]);
        }
    }
}
