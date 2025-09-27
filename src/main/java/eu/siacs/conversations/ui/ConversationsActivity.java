/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.ui;

import static eu.siacs.conversations.ui.ConversationFragment.REQUEST_DECRYPT_PGP;
import static eu.siacs.conversations.utils.AccountUtils.MANAGE_ACCOUNT_ACTIVITY;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.drawerlayout.widget.DrawerLayout;

import de.monocles.chat.DownloadDefaultStickers;
import de.monocles.chat.FinishOnboarding;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.common.collect.ImmutableList;

import de.monocles.chat.pinnedmessage.PinnedMessageRepository;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.widget.AvatarView;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import io.michaelrocks.libphonenumber.android.NumberParseException;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.MaterialColors;

import net.java.otr4j.session.SessionStatus;

import org.openintents.openpgp.util.OpenPgpApi;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.databinding.ActivityConversationsBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.ListItem.Tag;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.interfaces.OnBackendConnected;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationRead;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.interfaces.OnConversationsListItemUpdated;
import eu.siacs.conversations.ui.util.ActivityResult;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.ConversationMenuConfigurator;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.ToolbarUtils;
import eu.siacs.conversations.ui.util.SendButtonTool;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import eu.siacs.conversations.utils.SignupUtils;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import me.drakeet.support.toast.ToastCompat;
import p32929.easypasscodelock.Utils.EasyLock;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openintents.openpgp.util.OpenPgpApi;

public class ConversationsActivity extends XmppActivity
        implements OnConversationSelected,
        OnConversationArchived,
        OnConversationsListItemUpdated,
        OnConversationRead,
        XmppConnectionService.OnAccountUpdate,
        XmppConnectionService.OnConversationUpdate,
        XmppConnectionService.OnRosterUpdate,
        OnUpdateBlocklist,
        XmppConnectionService.OnShowErrorToast,
        XmppConnectionService.OnAffiliationChanged {

    public static final String ACTION_VIEW_CONVERSATION = "eu.siacs.conversations.action.VIEW";
    public static final String EXTRA_CONVERSATION = "conversationUuid";
    public static final String EXTRA_DOWNLOAD_UUID = "eu.siacs.conversations.download_uuid";
    public static final String EXTRA_AS_QUOTE = "eu.siacs.conversations.as_quote";
    public static final String EXTRA_NICK = "nick";
    public static final String EXTRA_IS_PRIVATE_MESSAGE = "pm";
    public static final String EXTRA_DO_NOT_APPEND = "do_not_append";
    public static final String EXTRA_POST_INIT_ACTION = "post_init_action";
    public static final String POST_ACTION_RECORD_VOICE = "record_voice";
    public static final String EXTRA_THREAD = "threadId";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_NODE = "node";
    public static final String EXTRA_JID = "jid";
    public static final String EXTRA_MESSAGE_UUID = "messageUuid";

    private static final List<String> VIEW_AND_SHARE_ACTIONS =
            Arrays.asList(
                    ACTION_VIEW_CONVERSATION, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE);

    public static final int REQUEST_OPEN_MESSAGE = 0x9876;
    public static final int REQUEST_PLAY_PAUSE = 0x5432;
    public static final int REQUEST_MICROPHONE = 0x5432f;
    public static final int DIALLER_INTEGRATION = 0x5432ff;
    public static final int REQUEST_DOWNLOAD_STICKERS = 0xbf8702;

    public static final long DRAWER_ALL_CHATS = 1;
    public static final long DRAWER_UNREAD_CHATS = 2;
    public static final long DRAWER_DIRECT_MESSAGES = 3;
    public static final long DRAWER_MANAGE_ACCOUNT = 4;
    public static final long DRAWER_ADD_ACCOUNT = 5;
    public static final long DRAWER_MANAGE_PHONE_ACCOUNTS = 6;
    public static final long DRAWER_CHANNELS = 7;
    public static final long DRAWER_CHAT_REQUESTS = 8;
    public static final long DRAWER_SETTINGS = 9;
    public static final long DRAWER_START_CHAT = 10;
    public static final long DRAWER_START_CHAT_CONTACT = 11;
    public static final long DRAWER_START_CHAT_NEW = 12;
    public static final long DRAWER_START_CHAT_GROUP = 13;
    public static final long DRAWER_START_CHAT_PUBLIC = 14;
    public static final long DRAWER_START_CHAT_DISCOVER = 15;

    // secondary fragment (when holding the conversation, must be initialized before refreshing the
    // overview fragment
    private static final @IdRes int[] FRAGMENT_ID_NOTIFICATION_ORDER = {
            R.id.secondary_fragment, R.id.main_fragment
    };
    private final PendingItem<Intent> pendingViewIntent = new PendingItem<>();
    private final PendingItem<ActivityResult> postponedActivityResult = new PendingItem<>();
    private ActivityConversationsBinding binding;
    private boolean mActivityPaused = true;
    private final AtomicBoolean mRedirectInProcess = new AtomicBoolean(false);
    private boolean refreshForNewCaps = false;
    private Set<Jid> newCapsJids = new HashSet<>();
    private int mRequestCode = -1;
    private boolean showLastSeen = false;
    private com.mikepenz.materialdrawer.widget.AccountHeaderView accountHeader;
    private Bundle savedState = null;
    private HashSet<Tag> selectedTag = new HashSet<>();
    private long mainFilter = DRAWER_ALL_CHATS;
    private boolean refreshAccounts = true;

    private static boolean isViewOrShareIntent(Intent i) {
        Log.d(Config.LOGTAG, "action: " + (i == null ? null : i.getAction()));
        return i != null
                && VIEW_AND_SHARE_ACTIONS.contains(i.getAction())
                && i.hasExtra(EXTRA_CONVERSATION);
    }

    private static Intent createLauncherIntent(Context context) {
        final Intent intent = new Intent(context, ConversationsActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        return intent;
    }

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
            refreshFragment(id);
        }
        refreshForNewCaps = false;
        newCapsJids.clear();
        invalidateActionBarTitle();
        if (binding.drawer != null && binding.drawer.getDrawerLayout() != null && !getBooleanPreference("show_nav_drawer", R.bool.show_nav_drawer)) {
            binding.drawer.getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        } else if (binding.drawer != null && binding.drawer.getDrawerLayout() != null) {
            binding.drawer.getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
        if (accountHeader == null) return;

        // Show badge for unread message in bottom nav
        int unreadCount = xmppConnectionService.unreadCount();
        BottomNavigationView bottomnav = findViewById(R.id.bottom_navigation);
        var bottomBadge = bottomnav.getOrCreateBadge(R.id.chats);
        bottomBadge.setNumber(unreadCount);
        bottomBadge.setVisible(unreadCount > 0);
        bottomBadge.setHorizontalOffset(20);

        final var chatRequestsPref = xmppConnectionService.getStringPreference("chat_requests", R.string.default_chat_requests);
        final var accountUnreads = new HashMap<Account, Integer>();
        binding.drawer.apply(dr -> {
            final var items = binding.drawer.getItemAdapter().getAdapterItems();
            final var tags = new TreeMap<Tag, Integer>();
            final var conversations = new ArrayList<Conversation>();
            var totalUnread = 0;
            var dmUnread = 0;
            var channelUnread = 0;
            var chatRequests = 0;
            final var selectedAccount = selectedAccount();
            populateWithOrderedConversations(conversations, false, false);
            for (final var c : conversations) {
                final var unread = c.unreadCount(xmppConnectionService);
                if (selectedAccount == null || selectedAccount.getUuid().equals(c.getAccount().getUuid())) {
                    if (c.isChatRequest(chatRequestsPref)) {
                        chatRequests++;
                    } else {
                        totalUnread += unread;
                        if (c.getMode() == Conversation.MODE_MULTI) {
                            channelUnread += unread;
                        } else {
                            dmUnread += unread;
                        }
                    }
                }
                var accountUnread = accountUnreads.get(c.getAccount());
                if (accountUnread == null) accountUnread = 0;
                accountUnreads.put(c.getAccount(), accountUnread + unread);
            }
            filterByMainFilter(conversations);
            for (final var c : conversations) {
                if (selectedAccount == null || selectedAccount.getUuid().equals(c.getAccount().getUuid())) {
                    final var unread = c.unreadCount(xmppConnectionService);
                    for (final var tag : c.getTags(this)) {
                        if ("Channel".equals(tag.getName())) continue;
                        var count = tags.get(tag);
                        if (count == null) count = 0;
                        tags.put(tag, count + unread);
                    }
                }
            }

            com.mikepenz.materialdrawer.util.MaterialDrawerSliderViewExtensionsKt.updateBadge(
                    binding.drawer,
                    DRAWER_UNREAD_CHATS,
                    new com.mikepenz.materialdrawer.holder.StringHolder(totalUnread > 0 ? new Integer(totalUnread).toString() : null)
            );

            com.mikepenz.materialdrawer.util.MaterialDrawerSliderViewExtensionsKt.updateBadge(
                    binding.drawer,
                    DRAWER_DIRECT_MESSAGES,
                    new com.mikepenz.materialdrawer.holder.StringHolder(dmUnread > 0 ? new Integer(dmUnread).toString() : null)
            );

            com.mikepenz.materialdrawer.util.MaterialDrawerSliderViewExtensionsKt.updateBadge(
                    binding.drawer,
                    DRAWER_CHANNELS,
                    new com.mikepenz.materialdrawer.holder.StringHolder(channelUnread > 0 ? new Integer(channelUnread).toString() : null)
            );

            if (chatRequests > 0) {
                if (binding.drawer.getItemAdapter().getAdapterPosition(DRAWER_CHAT_REQUESTS) < 0) {
                    final var color = MaterialColors.getColor(binding.drawer, com.google.android.material.R.attr.colorPrimaryContainer);
                    final var textColor = MaterialColors.getColor(binding.drawer, com.google.android.material.R.attr.colorOnPrimaryContainer);
                    final var requests = new com.mikepenz.materialdrawer.model.PrimaryDrawerItem();
                    requests.setIdentifier(DRAWER_CHAT_REQUESTS);
                    com.mikepenz.materialdrawer.model.interfaces.NameableKt.setNameText(requests, getString(R.string.chat_requests));
                    com.mikepenz.materialdrawer.model.interfaces.IconableKt.setIconRes(requests, R.drawable.ic_person_add_24dp);
                    requests.setBadgeStyle(new com.mikepenz.materialdrawer.holder.BadgeStyle(com.mikepenz.materialdrawer.R.drawable.material_drawer_badge, color, color, textColor));
                    binding.drawer.getItemAdapter().add(binding.drawer.getItemAdapter().getGlobalPosition(binding.drawer.getItemAdapter().getAdapterPosition(DRAWER_CHANNELS) + 1), requests);
                }
                com.mikepenz.materialdrawer.util.MaterialDrawerSliderViewExtensionsKt.updateBadge(
                        binding.drawer,
                        DRAWER_CHAT_REQUESTS,
                        new com.mikepenz.materialdrawer.holder.StringHolder(chatRequests > 0 ? new Integer(chatRequests).toString() : null)
                );
            } else {
                binding.drawer.getItemAdapter().removeByIdentifier(DRAWER_CHAT_REQUESTS);
            }

            final var endOfMainFilters = chatRequests > 0 ? 6 : 5;
            long id = 1000;
            final var inDrawer = new HashMap<Tag, Long>();
            for (final var item : ImmutableList.copyOf(items)) {
                if (item.getIdentifier() >= 1000 && !tags.containsKey(item.getTag())) {
                    com.mikepenz.materialdrawer.util.MaterialDrawerSliderViewExtensionsKt.removeItems(binding.drawer, item);
                } else if (item.getIdentifier() >= 1000) {
                    inDrawer.put((Tag)item.getTag(), item.getIdentifier());
                    id = item.getIdentifier() + 1;
                }
            }

            for (final var entry : tags.entrySet()) {
                final var badge = entry.getValue() > 0 ? entry.getValue().toString() : null;
                if (inDrawer.containsKey(entry.getKey())) {
                    com.mikepenz.materialdrawer.util.MaterialDrawerSliderViewExtensionsKt.updateBadge(
                            binding.drawer,
                            inDrawer.get(entry.getKey()),
                            new com.mikepenz.materialdrawer.holder.StringHolder(badge)
                    );
                } else {
                    final var item = new com.mikepenz.materialdrawer.model.SecondaryDrawerItem();
                    item.setIdentifier(id++);
                    item.setTag(entry.getKey());
                    com.mikepenz.materialdrawer.model.interfaces.NameableKt.setNameText(item, entry.getKey().getName());
                    if (badge != null) com.mikepenz.materialdrawer.model.interfaces.BadgeableKt.setBadgeText(item, badge);
                    final var color = MaterialColors.getColor(binding.drawer, com.google.android.material.R.attr.colorPrimaryContainer);
                    final var textColor = MaterialColors.getColor(binding.drawer, com.google.android.material.R.attr.colorOnPrimaryContainer);
                    item.setBadgeStyle(new com.mikepenz.materialdrawer.holder.BadgeStyle(com.mikepenz.materialdrawer.R.drawable.material_drawer_badge, color, color, textColor));
                    binding.drawer.getItemAdapter().add(binding.drawer.getItemAdapter().getGlobalPosition(endOfMainFilters), item);
                }
            }

            items.subList(endOfMainFilters, Math.min(endOfMainFilters + tags.size(), items.size())).sort((x, y) -> x.getTag() == null ? -1 : ((Comparable) x.getTag()).compareTo(y.getTag()));
            binding.drawer.getItemAdapter().getFastAdapter().notifyDataSetChanged();
            return kotlin.Unit.INSTANCE;
        });


        accountHeader.apply(ah -> {
            //if (!refreshAccounts) return kotlin.Unit.INSTANCE;
            refreshAccounts = false;
            final var accounts = xmppConnectionService.getAccounts();
            final var inHeader = new HashMap<Account, com.mikepenz.materialdrawer.model.ProfileDrawerItem>();
            long id = 101;
            for (final var p : ImmutableList.copyOf(accountHeader.getProfiles())) {
                if (p instanceof com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem) continue;
                final var pId = p.getIdentifier();
                if (id < pId) id = pId;
                if (accounts.contains(p.getTag()) || (accounts.size() > 1 && p.getTag() == null)) {
                    inHeader.put((Account) p.getTag(), (com.mikepenz.materialdrawer.model.ProfileDrawerItem) p);
                } else {
                    accountHeader.removeProfile(p);
                }
            }

            if (accounts.size() > 1 && !inHeader.containsKey(null)) {
                final var all = new com.mikepenz.materialdrawer.model.ProfileDrawerItem();
                all.setIdentifier(100);
                com.mikepenz.materialdrawer.model.interfaces.DescribableKt.setDescriptionText(all, getString(R.string.all_accounts));
                com.mikepenz.materialdrawer.model.interfaces.IconableKt.setIconRes(all, R.drawable.main_logo);
                accountHeader.addProfile(all, 0);
            }

            accountHeader.removeProfileByIdentifier(DRAWER_MANAGE_PHONE_ACCOUNTS);
            final var hasPhoneAccounts = accounts.stream().anyMatch(a -> a.getGateways("pstn").size() > 0);
            if (hasPhoneAccounts) {
                final var phoneAccounts = new com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem();
                phoneAccounts.setIdentifier(DRAWER_MANAGE_PHONE_ACCOUNTS);
                com.mikepenz.materialdrawer.model.interfaces.NameableKt.setNameText(phoneAccounts, "Manage Phone Accounts");
                com.mikepenz.materialdrawer.model.interfaces.IconableKt.setIconRes(phoneAccounts, R.drawable.ic_call_24dp);
                accountHeader.addProfile(phoneAccounts, accountHeader.getProfiles().size() - 2);
            }

            final boolean nightMode = Activities.isNightMode(this);
            for (final var a : accounts) {
                final var size = (int) getResources().getDimension(R.dimen.avatar_on_drawer);
                final var avatar = xmppConnectionService.getAvatarService().get(a, size, true);
                if (avatar == null) {
                    final var task = new AvatarWorkerTask(this, R.dimen.avatar_on_drawer);
                    try { task.execute(a); } catch (final RejectedExecutionException ignored) { }
                    refreshAccounts = true;
                }
                final var alreadyInHeader = inHeader.get(a);
                final com.mikepenz.materialdrawer.model.ProfileDrawerItem p;
                if (alreadyInHeader == null) {
                    p = new com.mikepenz.materialdrawer.model.ProfileDrawerItem();
                    p.setIdentifier(id++);
                    p.setTag(a);
                } else {
                    p = alreadyInHeader;
                }
                com.mikepenz.materialdrawer.model.interfaces.NameableKt.setNameText(p, a.getDisplayName() == null ? "" : a.getDisplayName());
                com.mikepenz.materialdrawer.model.interfaces.DescribableKt.setDescriptionText(p, a.getJid().asBareJid().toString());
                if (avatar != null) com.mikepenz.materialdrawer.model.interfaces.IconableKt.setIconBitmap(p, FileBackend.drawDrawable(avatar).copy(Bitmap.Config.ARGB_8888, false));
                var color = SendButtonTool.getSendButtonColor(binding.drawer, a.getPresenceStatus());
                if (!a.isOnlineAndConnected()) {
                    if (a.getStatus().isError()) {
                        color = MaterialColors.harmonizeWithPrimary(this, ContextCompat.getColor(this, nightMode ? R.color.red_300 : R.color.red_800));
                    } else {
                        color = MaterialColors.getColor(binding.drawer, com.google.android.material.R.attr.colorOnSurface);
                    }
                }
                final var textColor = MaterialColors.getColor(binding.drawer, com.google.android.material.R.attr.colorOnPrimary);
                p.setBadgeStyle(new com.mikepenz.materialdrawer.holder.BadgeStyle(com.mikepenz.materialdrawer.R.drawable.material_drawer_badge, color, color, textColor));
                final var badgeNumber = accountUnreads.get(a);
                p.setBadge(new com.mikepenz.materialdrawer.holder.StringHolder(badgeNumber == null || badgeNumber < 1 ? " " : badgeNumber.toString()));
                if (alreadyInHeader == null) {
                    accountHeader.addProfile(p, accountHeader.getProfiles().size() - (hasPhoneAccounts ? 3 : 2));
                } else {
                    accountHeader.updateProfile(p);
                }
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    @Override
    protected void onBackendConnected() {
        final var useSavedState = savedState;
        savedState = null;
        if (performRedirectIfNecessary(true)) {
            return;
        }
        xmppConnectionService.getNotificationService().setIsInForeground(true);
        var intent = pendingViewIntent.pop();
        if (intent != null) {
            if (processViewIntent(intent)) {
                if (binding.secondaryFragment != null) {
                    notifyFragmentOfBackendConnected(R.id.main_fragment);
                }
            } else {
                intent = null;
            }
        }

        if (intent == null) {
            for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
                notifyFragmentOfBackendConnected(id);
            }

            final ActivityResult activityResult = postponedActivityResult.pop();
            if (activityResult != null) {
                handleActivityResult(activityResult);
            }
            if (binding.secondaryFragment != null
                    && ConversationFragment.getConversation(this) == null) {
                Conversation conversation = ConversationsOverviewFragment.getSuggestion(this);
                if (conversation != null) {
                    openConversation(conversation, null);
                }
            }
            showDialogsIfMainIsOverview();
        }
        invalidateActionBarTitle();

        if (accountHeader != null || binding == null || binding.drawer == null) {
            refreshUiReal();
            return;
        }

        accountHeader = new com.mikepenz.materialdrawer.widget.AccountHeaderView(this);
        final var manageAccount = new com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem();
        manageAccount.setIdentifier(DRAWER_MANAGE_ACCOUNT);
        com.mikepenz.materialdrawer.model.interfaces.NameableKt.setNameText(manageAccount, getResources().getString(R.string.action_accounts));
        com.mikepenz.materialdrawer.model.interfaces.IconableKt.setIconRes(manageAccount, R.drawable.ic_settings_24dp);
        accountHeader.addProfiles(manageAccount);

        final var addAccount = new com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem();
        addAccount.setIdentifier(DRAWER_ADD_ACCOUNT);
        com.mikepenz.materialdrawer.model.interfaces.NameableKt.setNameText(addAccount, getResources().getString(R.string.action_add_account));
        com.mikepenz.materialdrawer.model.interfaces.IconableKt.setIconRes(addAccount, R.drawable.ic_add_24dp);
        accountHeader.addProfiles(addAccount);

        final var color = MaterialColors.getColor(binding.drawer, com.google.android.material.R.attr.colorPrimaryContainer);
        final var textColor = MaterialColors.getColor(binding.drawer, com.google.android.material.R.attr.colorOnPrimaryContainer);

        final var allChats = new com.mikepenz.materialdrawer.model.PrimaryDrawerItem();
        allChats.setIdentifier(DRAWER_ALL_CHATS);
        com.mikepenz.materialdrawer.model.interfaces.NameableKt.setNameText(allChats, getString(R.string.all_chats));
        com.mikepenz.materialdrawer.model.interfaces.IconableKt.setIconRes(allChats, R.drawable.ic_chat_24dp);

        final var unreadChats = new com.mikepenz.materialdrawer.model.PrimaryDrawerItem();
        unreadChats.setIdentifier(DRAWER_UNREAD_CHATS);
        com.mikepenz.materialdrawer.model.interfaces.NameableKt.setNameText(unreadChats, getString(R.string.unread_chats));
        com.mikepenz.materialdrawer.model.interfaces.IconableKt.setIconRes(unreadChats, R.drawable.chat_unread_24dp);
        unreadChats.setBadgeStyle(new com.mikepenz.materialdrawer.holder.BadgeStyle(com.mikepenz.materialdrawer.R.drawable.material_drawer_badge, color, color, textColor));

        final var directMessages = new com.mikepenz.materialdrawer.model.PrimaryDrawerItem();
        directMessages.setIdentifier(DRAWER_DIRECT_MESSAGES);
        com.mikepenz.materialdrawer.model.interfaces.NameableKt.setNameText(directMessages, getString(R.string.direct_messages));
        com.mikepenz.materialdrawer.model.interfaces.IconableKt.setIconRes(directMessages, R.drawable.ic_person_24dp);
        directMessages.setBadgeStyle(new com.mikepenz.materialdrawer.holder.BadgeStyle(com.mikepenz.materialdrawer.R.drawable.material_drawer_badge, color, color, textColor));

        final var channels = new com.mikepenz.materialdrawer.model.PrimaryDrawerItem();
        channels.setIdentifier(DRAWER_CHANNELS);
        com.mikepenz.materialdrawer.model.interfaces.NameableKt.setNameText(channels, getString(R.string.channels));
        com.mikepenz.materialdrawer.model.interfaces.IconableKt.setIconRes(channels, R.drawable.ic_group_24dp);
        channels.setBadgeStyle(new com.mikepenz.materialdrawer.holder.BadgeStyle(com.mikepenz.materialdrawer.R.drawable.material_drawer_badge, color, color, textColor));

        binding.drawer.getItemAdapter().add(
                allChats,
                unreadChats,
                directMessages,
                channels,
                new com.mikepenz.materialdrawer.model.DividerDrawerItem()
        );

        final var settings = new com.mikepenz.materialdrawer.model.PrimaryDrawerItem();
        settings.setIdentifier(DRAWER_SETTINGS);
        settings.setSelectable(false);
        com.mikepenz.materialdrawer.model.interfaces.NameableKt.setNameText(settings, getString(R.string.action_settings));
        com.mikepenz.materialdrawer.model.interfaces.IconableKt.setIconRes(settings, R.drawable.ic_settings_24dp);
        com.mikepenz.materialdrawer.util.MaterialDrawerSliderViewExtensionsKt.addStickyDrawerItems(binding.drawer, settings);

        if (useSavedState != null) {
            mainFilter = useSavedState.getLong("mainFilter", DRAWER_ALL_CHATS);
            selectedTag = (HashSet<Tag>) useSavedState.getSerializable("selectedTag");
        }
        refreshUiReal();
        if (useSavedState != null) binding.drawer.setSavedInstance(useSavedState);
        accountHeader.attachToSliderView(binding.drawer);
        if (useSavedState != null) accountHeader.withSavedInstance(useSavedState);

        if (mainFilter == DRAWER_ALL_CHATS && selectedTag.isEmpty()) {
            binding.drawer.setSelectedItemIdentifier(DRAWER_ALL_CHATS);
        }

        binding.drawer.setOnDrawerItemClickListener((v, drawerItem, pos) -> {
            final var id = drawerItem.getIdentifier();
            if (id != DRAWER_START_CHAT) binding.drawer.getExpandableExtension().collapse(false);
            if (id == DRAWER_SETTINGS) {
                startActivity(new Intent(this, eu.siacs.conversations.ui.activity.SettingsActivity.class));
                return false;
            } else if (id == DRAWER_START_CHAT_CONTACT) {
                launchStartConversation();
            } else if (id == DRAWER_START_CHAT_NEW) {
                launchStartConversation(R.id.create_contact);
            } else if (id == DRAWER_START_CHAT_GROUP) {
                launchStartConversation(R.id.create_private_group_chat);
            } else if (id == DRAWER_START_CHAT_PUBLIC) {
                launchStartConversation(R.id.create_public_channel);
            } else if (id == DRAWER_START_CHAT_DISCOVER) {
                launchStartConversation(R.id.discover_public_channels);
            } else if (id == DRAWER_ALL_CHATS || id == DRAWER_UNREAD_CHATS || id == DRAWER_DIRECT_MESSAGES || id == DRAWER_CHANNELS || id == DRAWER_CHAT_REQUESTS) {
                selectedTag.clear();
                mainFilter = id;
                binding.drawer.getSelectExtension().deselect();
            } else if (id >= 1000) {
                selectedTag.clear();
                selectedTag.add((Tag) drawerItem.getTag());
                binding.drawer.getSelectExtension().deselect();
                binding.drawer.getSelectExtension().select(pos, false, true);
            }
            binding.drawer.getSelectExtension().selectByIdentifier(mainFilter, false, true);

            final var fm = getFragmentManager();
            while (fm.getBackStackEntryCount() > 0) {
                try {
                    fm.popBackStackImmediate();
                } catch (IllegalStateException e) {
                    break;
                }
            }

            refreshUi();
            return false;
        });

        binding.drawer.setOnDrawerItemLongClickListener((v, drawerItem, pos) -> {
            final var id = drawerItem.getIdentifier();
            if (id == DRAWER_ALL_CHATS || id == DRAWER_UNREAD_CHATS || id == DRAWER_DIRECT_MESSAGES || id == DRAWER_CHANNELS || id == DRAWER_CHAT_REQUESTS) {
                selectedTag.clear();
                mainFilter = id;
                binding.drawer.getSelectExtension().deselect();
            } else if (id >= 1000) {
                final var tag = (Tag) drawerItem.getTag();
                if (selectedTag.contains(tag)) {
                    selectedTag.remove(tag);
                    binding.drawer.getSelectExtension().deselect(pos);
                } else {
                    selectedTag.add(tag);
                    binding.drawer.getSelectExtension().select(pos, false, true);
                }
            }
            binding.drawer.getSelectExtension().selectByIdentifier(mainFilter, false, true);

            refreshUi();
            return true;
        });

        accountHeader.setOnAccountHeaderListener((v, profile, isCurrent) -> {
            final var id = profile.getIdentifier();
            if (isCurrent) return false; // Ignore switching to already selected profile

            if (id == DRAWER_MANAGE_ACCOUNT) {
                AccountUtils.launchManageAccounts(this);
                return false;
            }

            if (id == DRAWER_ADD_ACCOUNT) {
                startActivity(new Intent(this, EditAccountActivity.class));
                return false;
            }

            if (id == DRAWER_MANAGE_PHONE_ACCOUNTS) {
                final String[] permissions;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissions = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT};
                } else {
                    permissions = new String[]{Manifest.permission.RECORD_AUDIO};
                }
                requestPermissions(permissions, REQUEST_MICROPHONE);
                return false;
            }

            final var fm = getFragmentManager();
            while (fm.getBackStackEntryCount() > 0) {
                try {
                    fm.popBackStackImmediate();
                } catch (IllegalStateException e) {
                    break;
                }
            }

            refreshUi();

            return false;
        });

        accountHeader.setOnAccountHeaderProfileImageListener((v, profile, isCurrent) -> {
            if (isCurrent) {
                final Account account = (Account) accountHeader.getActiveProfile().getTag();
                if (account == null) {
                    AccountUtils.launchManageAccounts(this);
                } else {
                    switchToAccount(account);
                }
            }
            return false;
        });
    }

    @Override
    public boolean colorCodeAccounts() {
        if (accountHeader != null) {
            final var active = accountHeader.getActiveProfile();
            if (active != null && active.getTag() != null) return false;
        }
        return super.colorCodeAccounts();
    }

    @Override
    public void populateWithOrderedConversations(List<Conversation> list) {
        populateWithOrderedConversations(list, true, true);
    }

    public void populateWithOrderedConversations(List<Conversation> list, final boolean filter, final boolean sort) {
        if (sort) {
            super.populateWithOrderedConversations(list);
        } else {
            list.addAll(xmppConnectionService.getConversations());
        }

        if (!filter) return;
        filterByMainFilter(list);

        final var selectedAccount = selectedAccount();

        for (final var c : ImmutableList.copyOf(list)) {
            if (selectedAccount != null && !selectedAccount.getUuid().equals(c.getAccount().getUuid())) {
                list.remove(c);
            } else if (!selectedTag.isEmpty()) {
                final var tags = new HashSet<>(c.getTags(this));
                tags.retainAll(selectedTag);
                if (tags.isEmpty()) list.remove(c);
            }
        }
    }

    protected Account selectedAccount() {
        if (accountHeader == null || accountHeader.getActiveProfile() == null) return null;
        return (Account) accountHeader.getActiveProfile().getTag();
    }

    protected void filterByMainFilter(List<Conversation> list) {
        final var chatRequests = xmppConnectionService.getStringPreference("chat_requests", R.string.default_chat_requests);
        for (final var c : ImmutableList.copyOf(list)) {
            if (mainFilter == DRAWER_CHANNELS && c.getMode() != Conversation.MODE_MULTI) {
                list.remove(c);
            } else if (mainFilter == DRAWER_DIRECT_MESSAGES && c.getMode() == Conversation.MODE_MULTI) {
                list.remove(c);
            } else if (mainFilter == DRAWER_UNREAD_CHATS && c.unreadCount(xmppConnectionService) < 1) {
                list.remove(c);
            } else if (mainFilter == DRAWER_CHAT_REQUESTS && !c.isChatRequest(chatRequests)) {
                list.remove(c);
            }
            if (mainFilter != DRAWER_CHAT_REQUESTS && c.isChatRequest(chatRequests)) {
                list.remove(c);
            }
        }
    }

    @Override
    public void launchStartConversation() {
        launchStartConversation(0);
    }

    public void launchStartConversation(int goTo) {
        StartConversationActivity.launch(this, accountHeader == null ? null : (Account) accountHeader.getActiveProfile().getTag(), selectedTag.stream().map(tag -> tag.getName()).collect(Collectors.joining(", ")), goTo);
    }

    private boolean performRedirectIfNecessary(boolean noAnimation) {
        return performRedirectIfNecessary(null, noAnimation);
    }

    private boolean performRedirectIfNecessary(
            final Conversation ignore, final boolean noAnimation) {
        if (xmppConnectionService == null) {
            return false;
        }

        boolean isConversationsListEmpty = xmppConnectionService.isConversationsListEmpty(ignore);
        if (isConversationsListEmpty && mRedirectInProcess.compareAndSet(false, true)) {
            final Intent intent = SignupUtils.getRedirectionIntent(this);
            if (noAnimation) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            }
            runOnUiThread(
                    () -> {
                        startActivity(intent);
                        if (noAnimation) {
                            overridePendingTransition(0, 0);
                        }
                    });
        }
        return mRedirectInProcess.get();
    }

    private void showDialogsIfMainIsOverview() {
        Pair<Account, Account> incomplete = null;
        if (xmppConnectionService != null && (incomplete = xmppConnectionService.onboardingIncomplete()) != null) {
            FinishOnboarding.finish(xmppConnectionService, this, incomplete.first, incomplete.second);
        }
        if (xmppConnectionService == null || xmppConnectionService.isOnboarding()) {
            return;
        }
        final Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment) {
            if (ExceptionHelper.checkForCrash(this)) return;
            if (offerToSetupDiallerIntegration()) return;
            if (offerToDownloadStickers()) return;
            if (openBatteryOptimizationDialogIfNeeded()) return;
            if (requestNotificationPermissionIfNeeded()) return;
            if (askAboutNomedia()) return;
            xmppConnectionService.rescanStickers();
        }
    }

    private String getBatteryOptimizationPreferenceKey() {
        @SuppressLint("HardwareIds")
        String device = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return "show_battery_optimization" + (device == null ? "" : device);
    }

    private void setNeverAskForBatteryOptimizationsAgain() {
        getPreferences().edit().putBoolean(getBatteryOptimizationPreferenceKey(), false).apply();
    }

    private boolean openBatteryOptimizationDialogIfNeeded() {
        if (isOptimizingBattery()
                && getPreferences().getBoolean(getBatteryOptimizationPreferenceKey(), true)) {
            final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle(R.string.battery_optimizations_enabled);
            builder.setMessage(
                    getString(
                            R.string.battery_optimizations_enabled_dialog,
                            getString(R.string.app_name)));
            builder.setPositiveButton(
                    R.string.next,
                    (dialog, which) -> {
                        final Intent intent =
                                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        final Uri uri = Uri.parse("package:" + getPackageName());
                        intent.setData(uri);
                        try {
                            startActivityForResult(intent, REQUEST_BATTERY_OP);
                        } catch (final ActivityNotFoundException e) {
                            Toast.makeText(
                                            this,
                                            R.string.device_does_not_support_battery_op,
                                            Toast.LENGTH_SHORT)
                                    .show();
                        }
                    });
            builder.setOnDismissListener(dialog -> setNeverAskForBatteryOptimizationsAgain());
            final AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            return true;
        }
        return false;
    }

    private boolean requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_POST_NOTIFICATION);
            return true;
        }
        return false;
    }

    private boolean askAboutNomedia() {
        if (getPreferences().contains("nomedia")) return false;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.show_media_title);
        builder.setMessage(R.string.show_media_summary);
        builder.setPositiveButton(R.string.yes, (dialog, which) -> {
            getPreferences().edit().putBoolean("nomedia", false).apply();
        });
        builder.setNegativeButton(R.string.no, (dialog, which) -> {
            getPreferences().edit().putBoolean("nomedia", true).apply();
        });
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        return true;
    }

    private boolean offerToDownloadStickers() {
        int offered = getPreferences().getInt("default_stickers_offered", 0);
        if (offered > 0) return false;
        getPreferences().edit().putInt("default_stickers_offered", 1).apply();

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(getString(R.string.download_stickers_title));
        builder.setMessage(getString(R.string.download_stickers_text));
        builder.setPositiveButton(R.string.yes, (dialog, which) -> {
            if (hasStoragePermission(REQUEST_DOWNLOAD_STICKERS)) {
                downloadStickers();
            }
        });
        builder.setNegativeButton(R.string.no, (dialog, which) -> {
            showDialogsIfMainIsOverview();
        });
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        return true;
    }

    private boolean offerToSetupDiallerIntegration() {
        if (mRequestCode == DIALLER_INTEGRATION) {
            mRequestCode = -1;
            return true;
        }
        if (Build.VERSION.SDK_INT < 23) return false;
        if (Build.VERSION.SDK_INT >= 33) {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELECOM) && !getPackageManager().hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) return false;
        } else {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE)) return false;
        }

        Set<String> pstnGateways = xmppConnectionService.getAccounts().stream()
                .flatMap(a -> a.getGateways("pstn").stream())
                .map(a -> a.getJid().asBareJid().toString()).collect(Collectors.toSet());

        if (pstnGateways.size() < 1) return false;
        Set<String> fromPrefs = getPreferences().getStringSet("pstn_gateways", Set.of("UPGRADE"));
        getPreferences().edit().putStringSet("pstn_gateways", pstnGateways).apply();
        pstnGateways.removeAll(fromPrefs);
        if (pstnGateways.size() < 1) return false;

        if (fromPrefs.contains("UPGRADE")) return false;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(getString(R.string.dialler_integration_title));
        builder.setMessage("monocles Android is able to integrate with your system's dialler app to allow dialling calls via your configured gateway " + String.join(", ", pstnGateways) + ".\n\nEnabling this integration will require granting microphone permission to the app.  Would you like to enable it now?");
        builder.setPositiveButton(R.string.yes, (dialog, which) -> {
            final String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT};
            } else {
                permissions = new String[]{Manifest.permission.RECORD_AUDIO};
            }
            requestPermissions(permissions, REQUEST_MICROPHONE);
        });
        builder.setNegativeButton(R.string.no, (dialog, which) -> {
            showDialogsIfMainIsOverview();
        });
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        return true;
    }

    private void notifyFragmentOfBackendConnected(@IdRes int id) {
        final Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment instanceof OnBackendConnected callback) {
            callback.onBackendConnected();
        }
    }

    private void refreshFragment(@IdRes int id) {
        final Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment instanceof XmppFragment xmppFragment) {
            xmppFragment.refresh();
            if (refreshForNewCaps) xmppFragment.refreshForNewCaps(newCapsJids);
        }
    }

    private boolean processViewIntent(final Intent intent) {
        final String uuid = intent.getStringExtra(EXTRA_CONVERSATION);
        final Conversation conversation =
                uuid != null ? xmppConnectionService.findConversationByUuidReliable(uuid) : null;
        if (conversation == null) {
            Log.d(Config.LOGTAG, "unable to view conversation with uuid:" + uuid);
            return false;
        }
        openConversation(conversation, intent.getExtras());
        return true;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        UriHandlerActivity.onRequestPermissionResult(this, requestCode, grantResults);
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switch (requestCode) {
                    case REQUEST_OPEN_MESSAGE:
                        refreshUiReal();
                        ConversationFragment.openPendingMessage(this);
                        break;
                    case REQUEST_PLAY_PAUSE:
                        ConversationFragment.startStopPending(this);
                        break;
                    case REQUEST_MICROPHONE:
                        Intent intent = new Intent();
                        intent.setComponent(new ComponentName("com.android.server.telecom",
                                "com.android.server.telecom.settings.EnableAccountPreferenceActivity"));
                        try {
                            startActivityForResult(intent, DIALLER_INTEGRATION);
                        } catch (ActivityNotFoundException e) {
                            displayToast("Dialler integration not available on your OS");
                        }
                        break;
                    case REQUEST_DOWNLOAD_STICKERS:
                        downloadStickers();
                        break;
                }
            } else {
                if (requestCode != REQUEST_POST_NOTIFICATION) showDialogsIfMainIsOverview();
            }
        } else {
            if (requestCode != REQUEST_POST_NOTIFICATION) showDialogsIfMainIsOverview();
        }
    }

    private void downloadStickers() {
        Intent intent = new Intent(this, DownloadDefaultStickers.class);
        intent.putExtra("tor", xmppConnectionService.useTorToConnect());
        intent.putExtra("i2p", xmppConnectionService.useI2PToConnect());
        ContextCompat.startForegroundService(this, intent);
        displayToast("Sticker download started");
        showDialogsIfMainIsOverview();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == DIALLER_INTEGRATION) {
            mRequestCode = requestCode;
            try {
                startActivity(new Intent(android.telecom.TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS));
            } catch (ActivityNotFoundException e) {
                displayToast("Dialler integration not available on your OS");
            }
            return;
        }

        ActivityResult activityResult = ActivityResult.of(requestCode, resultCode, data);
        if (xmppConnectionService != null) {
            handleActivityResult(activityResult);
        } else {
            this.postponedActivityResult.push(activityResult);
        }
    }

    private void handleActivityResult(final ActivityResult activityResult) {
        if (activityResult.resultCode == Activity.RESULT_OK) {
            handlePositiveActivityResult(activityResult.requestCode, activityResult.data);
        } else {
            handleNegativeActivityResult(activityResult.requestCode);
        }
        if (activityResult.requestCode == REQUEST_BATTERY_OP) {
            // the result code is always 0 even when battery permission were granted
            requestNotificationPermissionIfNeeded();
            XmppConnectionService.toggleForegroundService(xmppConnectionService);
        }
    }

    private void handleNegativeActivityResult(int requestCode) {
        Conversation conversation = ConversationFragment.getConversationReliable(this);
        switch (requestCode) {
            case REQUEST_DECRYPT_PGP:
                if (conversation == null) {
                    break;
                }
                conversation.getAccount().getPgpDecryptionService().giveUpCurrentDecryption();
                break;
            case REQUEST_BATTERY_OP:
                setNeverAskForBatteryOptimizationsAgain();
                break;
        }
    }

    private void handlePositiveActivityResult(int requestCode, final Intent data) {
        Conversation conversation = ConversationFragment.getConversationReliable(this);
        if (conversation == null) {
            Log.d(Config.LOGTAG, "conversation not found");
            return;
        }
        switch (requestCode) {
            case REQUEST_DECRYPT_PGP:
                conversation.getAccount().getPgpDecryptionService().continueDecryption(data);
                break;
            case REQUEST_CHOOSE_PGP_ID:
                long id = data.getLongExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, 0);
                if (id != 0) {
                    conversation.getAccount().setPgpSignId(id);
                    announcePgp(conversation.getAccount(), null, null, onOpenPGPKeyPublished);
                } else {
                    choosePgpSignId(conversation.getAccount());
                }
                break;
            case REQUEST_ANNOUNCE_PGP:
                announcePgp(conversation.getAccount(), conversation, data, onOpenPGPKeyPublished);
                break;
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        // Check if lock is set
        if (getBooleanPreference("app_lock_enabled", R.bool.app_lock_enabled)) {
            EasyLock.setBackgroundColor(getColor(R.color.black26));
            EasyLock.checkPassword(this);
            EasyLock.forgotPassword(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Toast.makeText(ConversationsActivity.this, R.string.app_lock_forgot_password, Toast.LENGTH_LONG).show();
                }
            });
        }
        super.onCreate(savedInstanceState);
        savedState = savedInstanceState;
        ConversationMenuConfigurator.reloadFeatures(this);
        OmemoSetting.load(this);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_conversations);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        this.getFragmentManager().addOnBackStackChangedListener(this::invalidateActionBarTitle);
        this.getFragmentManager().addOnBackStackChangedListener(this::showDialogsIfMainIsOverview);
        this.initializeFragments();
        this.invalidateActionBarTitle();
        final Intent intent;
        if (savedInstanceState == null) {
            intent = getIntent();
        } else {
            intent = savedInstanceState.getParcelable("intent");
        }
        if (isViewOrShareIntent(intent)) {
            pendingViewIntent.push(intent);
            setIntent(createLauncherIntent(this));
        }

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setBackgroundColor(Color.TRANSPARENT);
        bottomNavigationView.setOnItemSelectedListener(item -> {

            switch (item.getItemId()) {
                case R.id.chats -> {
                    return true;
                }
                case R.id.contactslist -> {
                    Intent i = new Intent(getApplicationContext(), StartConversationActivity.class);
                    i.putExtra("show_nav_bar", true);
                    startActivity(i);

                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                    return true;
                }
                case R.id.manageaccounts -> {
                    Intent i = new Intent(getApplicationContext(), MANAGE_ACCOUNT_ACTIVITY);
                    i.putExtra("show_nav_bar", true);
                    startActivity(i);
                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                    return true;
                }
                default ->
                        throw new IllegalStateException("Unexpected value: " + item.getItemId());
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_conversations, menu);
        final MenuItem qrCodeScanMenuItem = menu.findItem(R.id.action_scan_qr_code);
        final var reportSpamItem = menu.findItem(R.id.action_report_spam);
        final var fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        final var overview = fragment instanceof ConversationsOverviewFragment;
        if (qrCodeScanMenuItem != null) {
            if (isCameraFeatureAvailable() && (xmppConnectionService == null || !xmppConnectionService.isOnboarding())) {
                final var visible = getResources().getBoolean(R.bool.show_qr_code_scan) && overview;
                qrCodeScanMenuItem.setVisible(visible);
            } else {
                qrCodeScanMenuItem.setVisible(false);
            }
        }
        reportSpamItem.setVisible(overview && mainFilter == DRAWER_CHAT_REQUESTS);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        clearPendingViewIntent();
        if (ConversationFragment.getConversation(this) == conversation) {
            Log.d(
                    Config.LOGTAG,
                    "ignore onConversationSelected() because conversation is already open");
            return;
        }
        openConversation(conversation, null);
    }

    public void clearPendingViewIntent() {
        if (pendingViewIntent.clear()) {
            Log.e(Config.LOGTAG, "cleared pending view intent");
        }
    }


    public boolean navigationBarVisible() {
        return findViewById(R.id.bottom_navigation).getVisibility() == View.VISIBLE;
    }

    public boolean showNavigationBar() {
        if (!getBooleanPreference("show_nav_bar", R.bool.show_nav_bar)) {
            findViewById(R.id.bottom_navigation).setVisibility(View.GONE);
            return false;
        }

        findViewById(R.id.bottom_navigation).setVisibility(View.VISIBLE);
        return true;
    }

    public void hideNavigationBar() {
        findViewById(R.id.bottom_navigation).setVisibility(View.GONE);
    }


    private void displayToast(final String msg) {
        runOnUiThread(
                () -> Toast.makeText(ConversationsActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAffiliationChangedSuccessful(Jid jid) {}

    @Override
    public void onAffiliationChangeFailed(Jid jid, int resId) {
        displayToast(getString(resId, jid.asBareJid().toString()));
    }

    private void openConversation(Conversation conversation, Bundle extras) {
        final FragmentManager fragmentManager = getFragmentManager();
        executePendingTransactions(fragmentManager);
        ConversationFragment conversationFragment =
                (ConversationFragment) fragmentManager.findFragmentById(R.id.secondary_fragment);
        final boolean mainNeedsRefresh;
        if (conversationFragment == null) {
            mainNeedsRefresh = false;
            final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
            if (mainFragment instanceof ConversationFragment) {
                conversationFragment = (ConversationFragment) mainFragment;
            } else {
                conversationFragment = new ConversationFragment();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.main_fragment, conversationFragment);
                fragmentTransaction.addToBackStack(null);
                try {
                    fragmentTransaction.commit();
                } catch (IllegalStateException e) {
                    Log.w(Config.LOGTAG, "state loss while opening conversation", e);
                    // allowing state loss is probably fine since view intents et all are already
                    // stored and a click can probably be 'ignored'
                    return;
                }
            }
        } else {
            mainNeedsRefresh = true;
        }
        conversationFragment.reInit(conversation, extras == null ? new Bundle() : extras);
        if (mainNeedsRefresh) {
            refreshFragment(R.id.main_fragment);
        }
        if (findViewById(R.id.textinput) != null) findViewById(R.id.textinput).requestFocus();
        invalidateActionBarTitle();
    }

    private static void executePendingTransactions(final FragmentManager fragmentManager) {
        try {
            fragmentManager.executePendingTransactions();
        } catch (final Exception e) {
            Log.e(Config.LOGTAG, "unable to execute pending fragment transactions");
        }
    }

    public boolean onXmppUriClicked(Uri uri) {
        XmppUri xmppUri = new XmppUri(uri);
        if (xmppUri.isValidJid() && !xmppUri.hasFingerprints()) {
            final Conversation conversation =
                    xmppConnectionService.findUniqueConversationByJid(xmppUri);
            if (conversation != null) {
                if (xmppUri.getParameter("password") != null) {
                    xmppConnectionService.providePasswordForMuc(conversation, xmppUri.getParameter("password"));
                }
                if (xmppUri.isAction("command")) {
                    startCommand(conversation.getAccount(), xmppUri.getJid(), xmppUri.getParameter("node"));
                } else {
                    Bundle extras = new Bundle();
                    extras.putString(Intent.EXTRA_TEXT, xmppUri.getBody());
                    if (xmppUri.isAction("message")) extras.putString(EXTRA_POST_INIT_ACTION, "message");
                    openConversation(conversation, extras);
                }
                return true;
            }
        }
        return false;
    }

    public boolean onTelUriClicked(Uri uri, Account acct) {
        final String tel;
        try {
            tel = PhoneNumberUtilWrapper.normalize(this, uri.getSchemeSpecificPart());
        } catch (final IllegalArgumentException | NumberParseException | NullPointerException e) {
            return false;
        }

        Set<String> gateways = (acct == null ? xmppConnectionService.getAccounts().stream() : List.of(acct).stream()).flatMap(account ->
                Stream.concat(
                        account.getGateways("pstn").stream(),
                        account.getGateways("sms").stream()
                )
        ).map(a -> a.getJid().asBareJid().toString()).collect(Collectors.toSet());

        for (String gateway : gateways) {
            if (onXmppUriClicked(Uri.parse("xmpp:" + tel + "@" + gateway))) return true;
        }

        if (gateways.size() == 1 && acct != null) {
            openConversation(xmppConnectionService.findOrCreateConversation(acct, Jid.ofLocalAndDomain(tel, gateways.iterator().next()), false, true), null);
            return true;
        }

        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (item.getItemId()) {
            case android.R.id.home:
                FragmentManager fm = getFragmentManager();
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    Fragment f = fm.getFragments().get(fm.getFragments().size() - 1);
                    if (f != null && f instanceof ConversationFragment) {
                        if (((ConversationFragment) f).onBackPressed()) {
                            return true;
                        }
                    }
                }
                if (fm.getBackStackEntryCount() > 0) {
                    try {
                        fm.popBackStack();
                    } catch (IllegalStateException e) {
                        Log.w(Config.LOGTAG, "Unable to pop back stack after pressing home button");
                    }
                } else {
                    if (getBooleanPreference("show_nav_drawer", R.bool.show_nav_drawer)) {
                        if (binding.drawer != null && binding.drawer.getDrawerLayout() != null) {
                            binding.drawer.getDrawerLayout().openDrawer(binding.drawer);
                        }
                    }
                }
                return true;
            case R.id.action_scan_qr_code:
                UriHandlerActivity.scan(this);
                return true;
            case R.id.action_search_all_conversations:
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            case R.id.action_search_this_conversation: {
                final Conversation conversation = ConversationFragment.getConversation(this);
                if (conversation == null) {
                    return true;
                }
                final Intent intent = new Intent(this, SearchActivity.class);
                intent.putExtra(SearchActivity.EXTRA_CONVERSATION_UUID, conversation.getUuid());
                startActivity(intent);
                return true;
            }
            case R.id.action_report_spam: {
                final var list = new ArrayList<Conversation>();
                populateWithOrderedConversations(list, true, false);
                new AlertDialog.Builder(this)
                    .setTitle(R.string.report_spam)
                    .setMessage(R.string.block_user_and_spam_question)
                    .setPositiveButton(R.string.yes, (dialog, whichButton) -> {
                        for (final var conversation : list) {
                            final var m = conversation.getLatestMessage();
                            xmppConnectionService.sendBlockRequest(conversation, true, m == null ? null : m.getServerMsgId());
                        }
                    })
                    .setNegativeButton(R.string.no, null).show();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent keyEvent) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && keyEvent.isCtrlPressed()) {
            final ConversationFragment conversationFragment = ConversationFragment.get(this);
            if (conversationFragment != null && conversationFragment.onArrowUpCtrlPressed()) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, keyEvent);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        final Intent pendingIntent = pendingViewIntent.peek();
        savedInstanceState.putParcelable("intent", pendingIntent != null ? pendingIntent : getIntent());
        savedInstanceState.putLong("mainFilter", mainFilter);
        savedInstanceState.putSerializable("selectedTag", selectedTag);
        if (binding.drawer != null) savedInstanceState = binding.drawer.saveInstanceState(savedInstanceState);
        if (accountHeader != null) savedInstanceState = accountHeader.saveInstanceState(savedInstanceState);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        mRedirectInProcess.set(false);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setSelectedItemId(R.id.chats);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.showLastSeen = preferences.getBoolean("last_activity", getResources().getBoolean(R.bool.last_activity));
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (isViewOrShareIntent(intent)) {
            if (xmppConnectionService != null) {
                clearPendingViewIntent();
                processViewIntent(intent);
            } else {
                pendingViewIntent.push(intent);
            }
        }
        setIntent(createLauncherIntent(this));
    }

    @Override
    public void onPause() {
        this.mActivityPaused = true;
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.mActivityPaused = false;
    }

    private void initializeFragments() {
        final FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
        final Fragment secondaryFragment =
                fragmentManager.findFragmentById(R.id.secondary_fragment);
        if (mainFragment != null) {
            if (binding.secondaryFragment != null) {
                if (mainFragment instanceof ConversationFragment) {
                    getFragmentManager().popBackStack();
                    transaction.remove(mainFragment);
                    transaction.commit();
                    fragmentManager.executePendingTransactions();
                    transaction = fragmentManager.beginTransaction();
                    transaction.replace(R.id.secondary_fragment, mainFragment);
                    transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
                    transaction.commit();
                    return;
                }
            } else {
                if (secondaryFragment instanceof ConversationFragment) {
                    transaction.remove(secondaryFragment);
                    transaction.commit();
                    getFragmentManager().executePendingTransactions();
                    transaction = fragmentManager.beginTransaction();
                    transaction.replace(R.id.main_fragment, secondaryFragment);
                    transaction.addToBackStack(null);
                    transaction.commit();
                    return;
                }
            }
        } else {
            transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
        }
        if (binding.secondaryFragment != null && secondaryFragment == null) {
            transaction.replace(R.id.secondary_fragment, new ConversationFragment());
        }
        transaction.commit();
    }

    private void invalidateActionBarTitle() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        actionBar.setHomeAsUpIndicator(0);
        final FragmentManager fragmentManager = getFragmentManager();
        final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
        if (mainFragment instanceof ConversationFragment conversationFragment) {
            final Conversation conversation = conversationFragment.getConversation();
            if (conversation != null) {
                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    if (!conversation.withSelf()) {
                        ChatState state = conversation.getIncomingChatState();
                        if (state == ChatState.COMPOSING) {
                            binding.toolbarSubtitle.setText(R.string.is_typing);
                            binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                        } else {
                            if (showLastSeen && conversation.getContact().getLastseen() > 0 && conversation.getContact().getPresences().allOrNonSupport(Namespace.IDLE)) {
                                binding.toolbarSubtitle.setText(UIHelper.lastseen(getApplicationContext(), conversation.getContact().isActive(), conversation.getContact().getLastseen()));
                                binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                            } else {
                                binding.toolbarSubtitle.setVisibility(View.GONE);
                            }
                            binding.toolbarSubtitle.setSelected(true);
                        }
                    } else {
                        binding.toolbarSubtitle.setVisibility(View.GONE);
                    }
                } else {
                    ChatState state = ChatState.COMPOSING;
                    List<MucOptions.User> userWithChatStates = conversation.getMucOptions().getUsersWithChatState(state, 5);
                    if (userWithChatStates.isEmpty()) {
                        state = ChatState.PAUSED;
                        userWithChatStates = conversation.getMucOptions().getUsersWithChatState(state, 5);
                    }
                    List<MucOptions.User> users = conversation.getMucOptions().getUsers(true);
                    if (state == ChatState.COMPOSING) {
                        if (!userWithChatStates.isEmpty()) {
                            if (userWithChatStates.size() == 1) {
                                MucOptions.User user = userWithChatStates.get(0);
                                binding.toolbarSubtitle.setText(getString(R.string.contact_is_typing, UIHelper.getDisplayName(user)));
                                binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                            } else {
                                StringBuilder builder = new StringBuilder();
                                for (MucOptions.User user : userWithChatStates) {
                                    if (builder.length() != 0) {
                                        builder.append(", ");
                                    }
                                    builder.append(UIHelper.getDisplayName(user));
                                }
                                binding.toolbarSubtitle.setText(getString(R.string.contacts_are_typing, builder.toString()));
                                binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                            }
                        }
                    } else {
                        if (users.isEmpty()) {
                            binding.toolbarSubtitle.setText(getString(R.string.one_participant));
                            binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                        } else {
                            int size = users.size();
                            binding.toolbarSubtitle.setText(getString(R.string.more_participants, size));
                            binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                        }
                    }
                    binding.toolbarSubtitle.setSelected(true);
                }
                AvatarWorkerTask.loadAvatar(conversation, binding.toolbarAvatar, R.dimen.muc_avatar_actionbar);
                binding.toolbarAvatar.setVisibility(View.VISIBLE);
                binding.toolbarTitle.setText(conversation.getName());
                actionBar.setDisplayHomeAsUpEnabled(!xmppConnectionService.isOnboarding() || !conversation.getJid().equals(Jid.of("cheogram.com")));
                binding.toolbar.setOnClickListener((v) -> { if(!xmppConnectionService.isOnboarding()) openConversationDetails(conversation); });
                ToolbarUtils.setActionBarOnClickListener(
                        binding.toolbar,
                        (v) -> { if(!xmppConnectionService.isOnboarding()) openConversationDetails(conversation); }
                );
                return;
            }
        } else {
            binding.toolbarSubtitle.setVisibility(View.GONE);
            binding.toolbarAvatar.setVisibility(View.GONE);
        }
        final Fragment secondaryFragment =
                fragmentManager.findFragmentById(R.id.secondary_fragment);
        if (secondaryFragment instanceof ConversationFragment conversationFragment) {
            final Conversation conversation = conversationFragment.getConversation();
            if (conversation != null) {
                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    if (!conversation.withSelf()) {
                        ChatState state = conversation.getIncomingChatState();
                        if (state == ChatState.COMPOSING) {
                            binding.toolbarSubtitle.setText(getString(R.string.is_typing));
                            binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                            binding.toolbarSubtitle.setTypeface(null, Typeface.BOLD_ITALIC);
                            binding.toolbarSubtitle.setSelected(true);
                        } else {
                            if (showLastSeen && conversation.getContact().getLastseen() > 0 && conversation.getContact().getPresences().allOrNonSupport(Namespace.IDLE)) {
                                binding.toolbarSubtitle.setText(UIHelper.lastseen(getApplicationContext(), conversation.getContact().isActive(), conversation.getContact().getLastseen()));
                                binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                            } else {
                                binding.toolbarSubtitle.setVisibility(View.GONE);
                            }
                            binding.toolbarSubtitle.setSelected(true);
                        }
                    } else {
                        binding.toolbarSubtitle.setVisibility(View.GONE);
                    }
                } else {
                    ChatState state = ChatState.COMPOSING;
                    List<MucOptions.User> userWithChatStates = conversation.getMucOptions().getUsersWithChatState(state, 5);
                    if (userWithChatStates.isEmpty()) {
                        state = ChatState.PAUSED;
                        userWithChatStates = conversation.getMucOptions().getUsersWithChatState(state, 5);
                    }
                    List<MucOptions.User> users = conversation.getMucOptions().getUsers(true);
                    if (state == ChatState.COMPOSING) {
                        if (!userWithChatStates.isEmpty()) {
                            if (userWithChatStates.size() == 1) {
                                MucOptions.User user = userWithChatStates.get(0);
                                binding.toolbarSubtitle.setText(getString(R.string.contact_is_typing, UIHelper.getDisplayName(user)));
                                binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                            } else {
                                StringBuilder builder = new StringBuilder();
                                for (MucOptions.User user : userWithChatStates) {
                                    if (builder.length() != 0) {
                                        builder.append(", ");
                                    }
                                    builder.append(UIHelper.getDisplayName(user));
                                }
                                binding.toolbarSubtitle.setText(getString(R.string.contacts_are_typing, builder.toString()));
                                binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                            }
                        }
                    } else {
                        if (users.isEmpty()) {
                            binding.toolbarSubtitle.setText(getString(R.string.one_participant));
                            binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                        } else {
                            int size = users.size();
                            binding.toolbarSubtitle.setText(getString(R.string.more_participants, size));
                            binding.toolbarSubtitle.setVisibility(View.VISIBLE);
                        }
                    }
                    binding.toolbarSubtitle.setSelected(true);
                }
                AvatarWorkerTask.loadAvatar(conversation, binding.toolbarAvatar, R.dimen.muc_avatar_actionbar);
                binding.toolbarAvatar.setVisibility(View.VISIBLE);
                binding.toolbarTitle.setText(conversation.getName());
                binding.toolbar.setOnClickListener((v) -> { if(!xmppConnectionService.isOnboarding()) openConversationDetails(conversation); });
                ToolbarUtils.setActionBarOnClickListener(
                        binding.toolbar,
                        (v) -> { if(!xmppConnectionService.isOnboarding()) openConversationDetails(conversation); }
                );
            } else {
                binding.toolbarTitle.setText(R.string.app_name);
                binding.toolbar.setOnClickListener(null);
            }
        } else {
            binding.toolbarAvatar.setVisibility(View.GONE);
            binding.toolbarTitle.setText(R.string.app_name);
            binding.toolbar.setOnClickListener(null);
        }
        actionBar.setTitle(null);
        actionBar.setDisplayHomeAsUpEnabled(false);
        if (getBooleanPreference("show_nav_drawer", R.bool.show_nav_drawer)) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.menu_24dp);
            ToolbarUtils.resetActionBarOnClickListeners(binding.toolbar);
            ToolbarUtils.setActionBarOnClickListener(
                    binding.toolbar,
                    (v) -> {
                        if (binding.drawer != null && binding.drawer.getDrawerLayout() != null) {
                            binding.drawer.getDrawerLayout().openDrawer(binding.drawer);
                        }
                    }
            );
        }
    }

    private void openConversationDetails(final Conversation conversation) {
        if (conversation.getMode() == Conversational.MODE_MULTI) {
            ConferenceDetailsActivity.open(this, conversation);
        } else {
            final Contact contact = conversation.getContact();
            if (contact.isSelf()) {
                switchToAccount(conversation.getAccount());
            } else {
                switchToContactDetails(contact);
            }
        }
    }

    public void verifyOtrSessionDialog(final Conversation conversation, View view) {
        if (!conversation.hasValidOtrSession() || conversation.getOtrSession().getSessionStatus() != SessionStatus.ENCRYPTED) {
            ToastCompat.makeText(this, R.string.otr_session_not_started, Toast.LENGTH_LONG).show();
            return;
        }
        if (view == null) {
            return;
        }
        PopupMenu popup = new PopupMenu(this, view);
        popup.inflate(R.menu.verification_choices);
        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.blind_trust) {
                conversation.verifyOtrFingerprint();
                xmppConnectionService.syncRosterToDisk(conversation.getAccount());
                refreshUiReal();
                return true;
            }

            Intent intent = new Intent(ConversationsActivity.this, VerifyOTRActivity.class);
            intent.setAction(VerifyOTRActivity.ACTION_VERIFY_CONTACT);
            intent.putExtra("contact", conversation.getContact().getJid().asBareJid().toString());
            intent.putExtra("counterpart", conversation.getNextCounterpart().toString());
            intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
            switch (menuItem.getItemId()) {
                case R.id.ask_question:
                    intent.putExtra("mode", VerifyOTRActivity.MODE_ASK_QUESTION);
                    break;
            }
            startActivity(intent);
            overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
            return true;
        });
        popup.show();
    }

    @Override
    public void onConversationArchived(Conversation conversation) {
        if (performRedirectIfNecessary(conversation, false)) {
            return;
        }
        final FragmentManager fragmentManager = getFragmentManager();
        final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
        if (mainFragment instanceof ConversationFragment) {
            try {
                fragmentManager.popBackStack();
            } catch (final IllegalStateException e) {
                Log.w(
                        Config.LOGTAG,
                        "state loss while popping back state after archiving conversation",
                        e);
                // this usually means activity is no longer active; meaning on the next open we will
                // run through this again
            }
            return;
        }
        final Fragment secondaryFragment =
                fragmentManager.findFragmentById(R.id.secondary_fragment);
        if (secondaryFragment instanceof ConversationFragment) {
            if (((ConversationFragment) secondaryFragment).getConversation() == conversation) {
                Conversation suggestion =
                        ConversationsOverviewFragment.getSuggestion(this, conversation);
                if (suggestion != null) {
                    openConversation(suggestion, null);
                }
            }
        }
    }

    @Override
    public void onConversationsListItemUpdated() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment) {
            ((ConversationsOverviewFragment) fragment).refresh();
        }
    }

    @Override
    public void switchToConversation(Conversation conversation) {
        Log.d(Config.LOGTAG, "override");
        openConversation(conversation, null);
    }

    @Override
    public void onConversationRead(Conversation conversation, String upToUuid) {
        if (!mActivityPaused && pendingViewIntent.peek() == null) {
            xmppConnectionService.sendReadMarker(conversation, upToUuid);
        } else {
            Log.d(Config.LOGTAG, "ignoring read callback. mActivityPaused=" + mActivityPaused);
        }
    }

    @Override
    public void onAccountUpdate() {
        refreshAccounts = true;
        this.refreshUi();
    }

    @Override
    public void onConversationUpdate(boolean newCaps) {
        if (performRedirectIfNecessary(false)) {
            return;
        }
        refreshForNewCaps = newCaps;
        this.refreshUi();
    }

    @Override
    public void onRosterUpdate(final XmppConnectionService.UpdateRosterReason reason, final Contact contact) {
        if (reason != XmppConnectionService.UpdateRosterReason.AVATAR) {
            refreshForNewCaps = true;
            if (contact != null) newCapsJids.add(contact.getJid().asBareJid());
        }
        this.refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(OnUpdateBlocklist.Status status) {
        this.refreshUi();
    }

    @Override
    public void onShowErrorToast(int resId) {
        runOnUiThread(() -> Toast.makeText(this, resId, Toast.LENGTH_SHORT).show());
    }

    public PinnedMessageRepository getPinnedMessageRepository() {
        return new PinnedMessageRepository(this);
    }
}
