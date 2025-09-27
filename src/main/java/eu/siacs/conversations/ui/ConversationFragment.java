package eu.siacs.conversations.ui;

import static android.app.Activity.RESULT_CANCELED;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static eu.siacs.conversations.ui.XmppActivity.EXTRA_ACCOUNT;
import static eu.siacs.conversations.ui.XmppActivity.REQUEST_INVITE_TO_CONVERSATION;
import static eu.siacs.conversations.ui.util.SoftKeyboardUtils.hideSoftKeyboard;
import static eu.siacs.conversations.utils.Compatibility.hasStoragePermission;
import static eu.siacs.conversations.utils.PermissionUtils.allGranted;
import static eu.siacs.conversations.utils.PermissionUtils.audioGranted;
import static eu.siacs.conversations.utils.PermissionUtils.cameraGranted;
import static eu.siacs.conversations.utils.PermissionUtils.getFirstDenied;
import static eu.siacs.conversations.utils.PermissionUtils.writeGranted;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.icu.util.Calendar;
import android.icu.util.TimeZone;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.CycleInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.databinding.DataBindingUtil;
import androidx.documentfile.provider.DocumentFile;
import androidx.emoji2.emojipicker.EmojiPickerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;

import de.monocles.chat.BobTransfer;
import de.monocles.chat.EmojiSearch;
import de.monocles.chat.GifsAdapter;
import de.monocles.chat.KeyboardHeightProvider;
import de.monocles.chat.StickersAdapter;
import de.monocles.chat.StickersMigration;
import de.monocles.chat.WebxdcPage;
import de.monocles.chat.WebxdcStore;
import de.monocles.chat.EditMessageSelectionActionModeCallback;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.io.Files;

import com.otaliastudios.autocomplete.Autocomplete;
import com.otaliastudios.autocomplete.AutocompleteCallback;
import com.otaliastudios.autocomplete.AutocompletePresenter;
import com.otaliastudios.autocomplete.CharPolicy;
import com.otaliastudios.autocomplete.RecyclerViewPresenter;

import de.monocles.chat.pinnedmessage.PinnedMessageRepository;
import net.java.otr4j.session.SessionStatus;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Edit;
import eu.siacs.conversations.medialib.activities.EditActivity;
import eu.siacs.conversations.ui.util.QuoteHelper;
import eu.siacs.conversations.utils.ChatBackgroundHelper;
import eu.siacs.conversations.xmpp.pep.UserTune;
import io.ipfs.cid.Cid;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.databinding.FragmentConversationBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ReadByMarker;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.entities.TransferablePlaceholder;
import eu.siacs.conversations.http.HttpDownloadConnection;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.CallIntegrationConnectionService;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.CommandAdapter;
import eu.siacs.conversations.ui.adapter.MediaPreviewAdapter;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.ui.adapter.UserAdapter;
import eu.siacs.conversations.ui.util.ActivityResult;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.ConversationMenuConfigurator;
import eu.siacs.conversations.ui.util.DateSeparator;
import eu.siacs.conversations.ui.util.EditMessageActionModeCallback;
import eu.siacs.conversations.ui.util.ListViewUtils;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.PresenceSelector;
import eu.siacs.conversations.ui.util.ScrollState;
import eu.siacs.conversations.ui.util.SendButtonAction;
import eu.siacs.conversations.ui.util.SendButtonTool;
import eu.siacs.conversations.ui.util.ShareUtil;
import eu.siacs.conversations.ui.util.ViewUtil;
import eu.siacs.conversations.ui.widget.EditMessage;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.utils.NickValidityChecker;
import eu.siacs.conversations.utils.PermissionUtils;
import eu.siacs.conversations.utils.QuickLoader;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.JingleFileTransferConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.OngoingRtpSession;
import eu.siacs.conversations.xmpp.jingle.RtpCapability;

import im.conversations.android.xmpp.model.stanza.Iq;

import me.drakeet.support.toast.ToastCompat;

public class ConversationFragment extends XmppFragment
        implements EditMessage.KeyboardListener,
        MessageAdapter.OnContactPictureLongClicked,
        MessageAdapter.OnContactPictureClicked,
        MessageAdapter.OnInlineImageLongClicked {

    //Voice recorder
    private MediaRecorder mRecorder;
    private Integer oldOrientation;
    private int mStartTime = 0;
    private boolean recording = false;

    private CountDownLatch outputFileWrittenLatch = new CountDownLatch(1);

    private final Handler mHandler = new Handler();
    private final Runnable mTickExecutor = new Runnable() {
        @Override
        public void run() {
            tick();
            mHandler.postDelayed(mTickExecutor, 1000);
        }
    };

    private File mOutputFile;

    private FileObserver mFileObserver;


    public static final int REQUEST_TRUST_KEYS_NONE = 0x0;
    public static final int REQUEST_SEND_MESSAGE = 0x0201;
    public static final int REQUEST_DECRYPT_PGP = 0x0202;
    public static final int REQUEST_ENCRYPT_MESSAGE = 0x0207;
    public static final int REQUEST_TRUST_KEYS_TEXT = 0x0208;
    public static final int REQUEST_TRUST_KEYS_ATTACHMENTS = 0x0209;
    public static final int REQUEST_START_DOWNLOAD = 0x0210;
    public static final int REQUEST_ADD_EDITOR_CONTENT = 0x0211;
    public static final int REQUEST_COMMIT_ATTACHMENTS = 0x0212;
    public static final int REQUEST_START_AUDIO_CALL = 0x213;
    public static final int REQUEST_START_VIDEO_CALL = 0x214;
    public static final int REQUEST_WEBXDC_STORE = 0x216;
    public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x0301;
    public static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x0302;
    public static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 0x0303;
    public static final int ATTACHMENT_CHOICE_RECORD_VOICE = 0x0304;
    public static final int ATTACHMENT_CHOICE_LOCATION = 0x0305;
    public static final int ATTACHMENT_CHOICE_INVALID = 0x0306;
    public static final int ATTACHMENT_CHOICE_RECORD_VIDEO = 0x0307;
    public static final int ATTACHMENT_CHOICE_EDIT_PHOTO = 0x0308;

    public static final String RECENTLY_USED_QUICK_ACTION = "recently_used_quick_action";
    public static final String STATE_CONVERSATION_UUID =
            ConversationFragment.class.getName() + ".uuid";
    public static final String STATE_SCROLL_POSITION =
            ConversationFragment.class.getName() + ".scroll_position";
    public static final String STATE_PHOTO_URI =
            ConversationFragment.class.getName() + ".media_previews";
    public static final String STATE_MEDIA_PREVIEWS =
            ConversationFragment.class.getName() + ".take_photo_uri";
    private static final String STATE_LAST_MESSAGE_UUID = "state_last_message_uuid";
    private final List<Message> messageList = new ArrayList<>();
    private final PendingItem<ActivityResult> postponedActivityResult = new PendingItem<>();
    private final PendingItem<String> pendingConversationsUuid = new PendingItem<>();
    private final PendingItem<ArrayList<Attachment>> pendingMediaPreviews = new PendingItem<>();
    private final PendingItem<Bundle> pendingExtras = new PendingItem<>();
    private final PendingItem<Uri> pendingTakePhotoUri = new PendingItem<>();
    private final PendingItem<ScrollState> pendingScrollState = new PendingItem<>();
    private final PendingItem<String> pendingLastMessageUuid = new PendingItem<>();
    private final PendingItem<Message> pendingMessage = new PendingItem<>();
    public Uri mPendingEditorContent = null;
    protected ArrayList<WebxdcPage> extensions = new ArrayList<>();
    protected MessageAdapter messageListAdapter;
    protected CommandAdapter commandAdapter;
    private MediaPreviewAdapter mediaPreviewAdapter;
    private String lastMessageUuid = null;
    private Conversation conversation;
    private FragmentConversationBinding binding;
    private Toast messageLoaderToast;
    private ConversationsActivity activity;
    private boolean reInitRequiredOnStart = true;
    private File savingAsSticker = null;
    private EmojiSearch emojiSearch = null;
    File dirStickers;
    private String[] StickerfilesPaths;
    private String[] StickerfilesNames;
    private String[] GifsfilesPaths;
    private String[] GifsfilesNames;

    private Message previousClickedReply = null;

    private PinnedMessageRepository pinnedMessageRepository;
    private String currentDisplayedPinnedMessageUuid = null; // To track what's shown
    private Cid currentDisplayedPinnedMessageCid = null; // Store CID for potential actions

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    private KeyboardHeightProvider.KeyboardHeightListener keyboardHeightListener = null;
    private KeyboardHeightProvider keyboardHeightProvider = null;
    private static final String PINNED_MESSAGE_KEY_PREFIX = "pinned_message_";

    protected OnClickListener clickToVerify = new OnClickListener() {
        @Override
        public void onClick(View v) {
            activity.verifyOtrSessionDialog(conversation, v);
        }
    };

    private final OnClickListener clickToMuc =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    ConferenceDetailsActivity.open(activity, conversation);
                }
            };
    private final OnClickListener leaveMuc =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    activity.xmppConnectionService.archiveConversation(conversation);
                }
            };
    private final OnClickListener joinMuc =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    activity.xmppConnectionService.joinMuc(conversation);
                }
            };

    private final OnClickListener acceptJoin =
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    conversation.setAttribute("accept_non_anonymous", true);
                    activity.xmppConnectionService.updateConversation(conversation);
                    activity.xmppConnectionService.joinMuc(conversation);
                }
            };

    private final OnClickListener enterPassword =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    MucOptions muc = conversation.getMucOptions();
                    String password = muc.getPassword();
                    if (password == null) {
                        password = "";
                    }
                    activity.quickPasswordEdit(
                            password,
                            value -> {
                                activity.xmppConnectionService.providePasswordForMuc(
                                        conversation, value);
                                return null;
                            });
                }
            };


    private final OnClickListener meCommand = v -> Objects.requireNonNull(binding.textinput.getText()).insert(0, Message.ME_COMMAND + " ");
    private final OnClickListener quote = v -> insertQuote();
    private final OnClickListener boldText = v -> insertFormatting("bold");
    private final OnClickListener italicText = v -> insertFormatting("italic");
    private final OnClickListener monospaceText = v -> insertFormatting("monospace");
    private final OnClickListener strikethroughText = v -> insertFormatting("strikethrough");
    private final OnClickListener close = v -> closeFormatting();

    private void closeFormatting() {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(R.string.action_close);
        builder.setMessage(R.string.close_format_text);
        builder.setPositiveButton(getString(R.string.action_close),
                (dialog, which) -> {
                    final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
                    preferences.edit().putBoolean("showtextformatting", false).apply();
                    binding.textformat.setVisibility(GONE);
                    updateSendButton();
                });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.create().show();
    }

    private void insertFormatting(String format) {
        final String BOLD = "*";
        final String ITALIC = "_";
        final String MONOSPACE = "`";
        final String STRIKETHROUGH = "~";

        int selStart = this.binding.textinput.getSelectionStart();
        int selEnd = this.binding.textinput.getSelectionEnd();
        int min = 0;
        int max = this.binding.textinput.getText().length();
        if (this.binding.textinput.isFocused()) {
            selStart = this.binding.textinput.getSelectionStart();
            selEnd = this.binding.textinput.getSelectionEnd();
            min = Math.max(0, Math.min(selStart, selEnd));
            max = Math.max(0, Math.max(selStart, selEnd));
        }
        final CharSequence selectedText = this.binding.textinput.getText().subSequence(min, max);

        if (format.equals("bold")) {
            if (selectedText.length() != 0) {
                this.binding.textinput.getText().replace(Math.min(selStart, selEnd), Math.max(selStart, selEnd),
                        BOLD + selectedText + BOLD, 0, selectedText.length() + 2);
            } else {
                this.binding.textinput.getText().insert(this.binding.textinput.getSelectionStart(), (BOLD));
            }
            return;
        } else if (format.equals("italic")) {
            if (selectedText.length() != 0) {
                this.binding.textinput.getText().replace(Math.min(selStart, selEnd), Math.max(selStart, selEnd),
                        ITALIC + selectedText + ITALIC, 0, selectedText.length() + 2);
            } else {
                this.binding.textinput.getText().insert(this.binding.textinput.getSelectionStart(), (ITALIC));
            }
            return;
        } else if (format.equals("monospace")) {
            if (selectedText.length() != 0) {
                this.binding.textinput.getText().replace(Math.min(selStart, selEnd), Math.max(selStart, selEnd),
                        MONOSPACE + selectedText + MONOSPACE, 0, selectedText.length() + 2);
            } else {
                this.binding.textinput.getText().insert(this.binding.textinput.getSelectionStart(), (MONOSPACE));
            }
            return;
        } else if (format.equals("strikethrough")) {
            if (selectedText.length() != 0) {
                this.binding.textinput.getText().replace(Math.min(selStart, selEnd), Math.max(selStart, selEnd),
                        STRIKETHROUGH + selectedText + STRIKETHROUGH, 0, selectedText.length() + 2);
            } else {
                this.binding.textinput.getText().insert(this.binding.textinput.getSelectionStart(), (STRIKETHROUGH));
            }
            return;
        }
    }

    private void insertQuote() {
        int pos = 0;
        if (this.binding.textinput.getSelectionStart() == this.binding.textinput.getSelectionEnd()) {
            pos = this.binding.textinput.getSelectionStart();
        }
        if (pos == 0) {
            Objects.requireNonNull(binding.textinput.getText()).insert(0, QuoteHelper.QUOTE_CHAR + " ");
        } else {
            Objects.requireNonNull(binding.textinput.getText()).insert(pos, System.getProperty("line.separator") + QuoteHelper.QUOTE_CHAR + " ");
        }
    }


    private final OnScrollListener mOnScrollListener =
            new OnScrollListener() {

                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (AbsListView.OnScrollListener.SCROLL_STATE_IDLE == scrollState) {
                        fireReadEvent();
                    }
                }

                @Override
                public void onScroll(
                        final AbsListView view,
                        int firstVisibleItem,
                        int visibleItemCount,
                        int totalItemCount) {
                    toggleScrollDownButton(view);
                    synchronized (ConversationFragment.this.messageList) {
                        boolean paginateBackward = firstVisibleItem < 5;
                        boolean paginationForward = conversation != null && conversation.isInHistoryPart() && firstVisibleItem + visibleItemCount + 5 > totalItemCount;
                        loadMoreMessages(paginateBackward, paginationForward, view);
                    }
                }
            };

    private void loadMoreMessages(boolean paginateBackward, boolean paginationForward, AbsListView view) {
        if (paginateBackward && (conversation != null && !conversation.messagesLoaded.get())) {
            paginateBackward = false;
        }

        if (
                conversation != null &&
                        messageList.size() > 0 &&
                        ((paginateBackward && conversation.messagesLoaded.compareAndSet(true, false)) ||
                                (paginationForward && conversation.historyPartLoadedForward.compareAndSet(true, false)))
        ) {
            long timestamp;

            if (paginateBackward) {
                if (messageList.get(0).getType() == Message.TYPE_STATUS
                        && messageList.size() >= 2) {
                    timestamp = messageList.get(1).getTimeSent();
                } else {
                    timestamp = messageList.get(0).getTimeSent();
                }
            } else {
                if (messageList.get(messageList.size() - 1).getType() == Message.TYPE_STATUS
                        && messageList.size() >= 2) {
                    timestamp = messageList.get(messageList.size() - 2).getTimeSent();
                } else {
                    timestamp = messageList.get(messageList.size() - 1).getTimeSent();
                }
            }

            boolean finalPaginateBackward = paginateBackward;
            activity.xmppConnectionService.loadMoreMessages(
                    conversation,
                    timestamp,
                    !paginateBackward,
                    new XmppConnectionService.OnMoreMessagesLoaded() {
                        @Override
                        public void onMoreMessagesLoaded(
                                final int c, final Conversation conversation) {
                            if (ConversationFragment.this.conversation
                                    != conversation) {
                                conversation.messagesLoaded.set(true);
                                return;
                            }
                            runOnUiThread(
                                    () -> {
                                        synchronized (messageList) {
                                            final int oldPosition =
                                                    binding.messagesView
                                                            .getFirstVisiblePosition();
                                            Message message = null;
                                            int childPos;
                                            for (childPos = 0;
                                                 childPos + oldPosition
                                                         < messageList.size();
                                                 ++childPos) {
                                                message =
                                                        messageList.get(
                                                                oldPosition
                                                                        + childPos);
                                                if (message.getType()
                                                        != Message.TYPE_STATUS) {
                                                    break;
                                                }
                                            }
                                            final String uuid =
                                                    message != null
                                                            ? message.getUuid()
                                                            : null;
                                            View v =
                                                    binding.messagesView.getChildAt(
                                                            childPos);
                                            final int pxOffset =
                                                    (v == null) ? 0 : v.getTop();
                                            ConversationFragment.this.conversation
                                                    .populateWithMessages(
                                                            ConversationFragment
                                                                    .this
                                                                    .messageList,
                                                            activity == null ? null : activity.xmppConnectionService);
                                            try {
                                                updateStatusMessages();
                                            } catch (IllegalStateException e) {
                                                Log.d(
                                                        Config.LOGTAG,
                                                        "caught illegal state exception while updating status messages");
                                            }
                                            messageListAdapter
                                                    .notifyDataSetChanged();
                                            int pos =
                                                    Math.max(
                                                            getIndexOf(
                                                                    uuid,
                                                                    messageList),
                                                            0);
                                            binding.messagesView
                                                    .setSelectionFromTop(
                                                            pos, pxOffset);
                                            if (messageLoaderToast != null) {
                                                messageLoaderToast.cancel();
                                            }

                                            if (!finalPaginateBackward) {
                                                conversation.historyPartLoadedForward.set(true);
                                            } else {
                                                conversation.messagesLoaded.set(true);
                                            }
                                        }
                                    });
                        }

                        @Override
                        public void informUser(final int resId) {

                            runOnUiThread(
                                    () -> {
                                        if (messageLoaderToast != null) {
                                            messageLoaderToast.cancel();
                                        }
                                        if (ConversationFragment.this.conversation
                                                != conversation) {
                                            return;
                                        }
                                        messageLoaderToast =
                                                Toast.makeText(
                                                        view.getContext(),
                                                        resId,
                                                        Toast.LENGTH_LONG);
                                        messageLoaderToast.show();
                                    });
                        }
                    });
        }
    }
    private final EditMessage.OnCommitContentListener mEditorContentListener =
            new EditMessage.OnCommitContentListener() {
                @Override
                public boolean onCommitContent(
                        InputContentInfoCompat inputContentInfo,
                        int flags,
                        Bundle opts,
                        String[] contentMimeTypes) {
                    // try to get permission to read the image, if applicable
                    if ((flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION)
                            != 0) {
                        try {
                            inputContentInfo.requestPermission();
                        } catch (Exception e) {
                            Log.e(
                                    Config.LOGTAG,
                                    "InputContentInfoCompat#requestPermission() failed.",
                                    e);
                            Toast.makeText(
                                            activity,
                                            activity.getString(
                                                    R.string.no_permission_to_access_x,
                                                    inputContentInfo.getDescription()),
                                            Toast.LENGTH_LONG)
                                    .show();
                            return false;
                        }
                    }
                    if (hasPermissions(
                            REQUEST_ADD_EDITOR_CONTENT,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        attachEditorContentToConversation(inputContentInfo.getContentUri());
                    } else {
                        mPendingEditorContent = inputContentInfo.getContentUri();
                    }
                    return true;
                }
            };
    private Message selectedMessage;
    private final OnClickListener mEnableAccountListener =
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Account account = conversation == null ? null : conversation.getAccount();
                    if (account != null) {
                        account.setOption(Account.OPTION_SOFT_DISABLED, false);
                        account.setOption(Account.OPTION_DISABLED, false);
                        activity.xmppConnectionService.updateAccount(account);
                    }
                }
            };
    private final OnClickListener mUnblockClickListener =
            new OnClickListener() {
                @Override
                public void onClick(final View v) {
                    v.post(() -> v.setVisibility(View.INVISIBLE));
                    if (conversation.isDomainBlocked()) {
                        BlockContactDialog.show(activity, conversation);
                    } else {
                        unblockConversation(conversation);
                    }
                }
            };
    private final OnClickListener mBlockClickListener = this::showBlockSubmenu;
    private final OnClickListener mAddBackClickListener =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    final Contact contact = conversation == null ? null : conversation.getContact();
                    if (contact != null) {
                        activity.xmppConnectionService.createContact(contact, true);
                        activity.switchToContactDetails(contact);
                    }
                }
            };
    private final View.OnLongClickListener mLongPressBlockListener = this::showBlockSubmenu;
    private final OnClickListener mAllowPresenceSubscription =
            new OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Contact contact = conversation == null ? null : conversation.getContact();
                    if (contact != null) {
                        activity.xmppConnectionService.sendPresencePacket(
                                contact.getAccount(),
                                activity.xmppConnectionService
                                        .getPresenceGenerator()
                                        .sendPresenceUpdatesTo(contact));
                        hideSnackbar();
                    }
                }
            };

    private OnClickListener mAnswerSmpClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent(activity, VerifyOTRActivity.class);
            intent.setAction(VerifyOTRActivity.ACTION_VERIFY_CONTACT);
            intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
            intent.putExtra(VerifyOTRActivity.EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
            intent.putExtra("mode", VerifyOTRActivity.MODE_ANSWER_QUESTION);
            startActivity(intent);
            activity.overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
        }
    };

    protected OnClickListener clickToDecryptListener =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    PendingIntent pendingIntent =
                            conversation.getAccount().getPgpDecryptionService().getPendingIntent();
                    if (pendingIntent != null) {
                        try {
                            activity
                                    .startIntentSenderForResult(
                                            pendingIntent.getIntentSender(),
                                            REQUEST_DECRYPT_PGP,
                                            null,
                                            0,
                                            0,
                                            0,
                                            Compatibility.pgpStartIntentSenderOptions());
                        } catch (SendIntentException e) {
                            Toast.makeText(
                                            activity,
                                            R.string.unable_to_connect_to_keychain,
                                            Toast.LENGTH_SHORT)
                                    .show();
                            conversation
                                    .getAccount()
                                    .getPgpDecryptionService()
                                    .continueDecryption(true);
                        }
                    }
                    updateSnackBar(conversation);
                }
            };
    private final AtomicBoolean mSendingPgpMessage = new AtomicBoolean(false);
    private final OnEditorActionListener mEditorActionListener =
            (v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    InputMethodManager imm =
                            (InputMethodManager)
                                    activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null && imm.isFullscreenMode()) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                    sendMessage();
                    return true;
                } else {
                    return false;
                }
            };
    private final OnClickListener mScrollButtonListener =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    stopScrolling();

                    if (previousClickedReply != null) {
                        int lastVisiblePosition = binding.messagesView.getLastVisiblePosition();
                        Message lastVisibleMessage = messageListAdapter.getItem(lastVisiblePosition);
                        Message jump = previousClickedReply;
                        previousClickedReply = null;
                        if (lastVisibleMessage != null) {
                            if (jump.getTimeSent() > lastVisibleMessage.getTimeSent()) {
                                Runnable postSelectionRunnable = () -> highlightMessage(jump.getUuid());
                                updateSelection(jump.getUuid(), binding.messagesView.getHeight() / 2, postSelectionRunnable, false, false);
                                return;
                            }
                        }
                    }

                    if (conversation.isInHistoryPart()) {
                        conversation.jumpToLatest();
                        refresh(false);
                    }
                    setSelection(binding.messagesView.getCount() - 1, true);
                }
            };

    private final OnClickListener mTimerClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (recording && binding.recordingVoiceActivity.getVisibility() == VISIBLE) {
                pauseRecording();
            } else if (!recording && binding.recordingVoiceActivity.getVisibility() == VISIBLE) {
                resumeRecording();
            }
        }
    };

    private final OnClickListener mRecordVoiceButtonListener = v -> attachFile(ATTACHMENT_CHOICE_RECORD_VOICE);
    private final OnClickListener mtakePictureButtonListener = v -> attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
    private final OnClickListener mSendButtonListener =
            new OnClickListener() {

                @Override
                public void onClick(View v) {
                    Object tag = v.getTag();
                    if (tag instanceof SendButtonAction) {
                        SendButtonAction action = (SendButtonAction) tag;
                        switch (action) {
                            case TAKE_PHOTO:
                            case RECORD_VIDEO:
                            case SEND_LOCATION:
                            case RECORD_VOICE:
                            case CHOOSE_PICTURE:
                                attachFile(action.toChoice());
                                break;
                            case CANCEL:
                                if (conversation != null) {
                                    conversation.setUserSelectedThread(false);
                                    if (conversation.setCorrectingMessage(null)) {
                                        binding.textinput.setText("");
                                        binding.textinput.append(conversation.getDraftMessage());
                                        conversation.setDraftMessage(null);
                                    } else if (conversation.getMode() == Conversation.MODE_MULTI) {
                                        conversation.setNextCounterpart(null);
                                        binding.textinput.setText("");
                                    } else {
                                        binding.textinput.setText("");
                                    }
                                    binding.textinputSubject.setText("");
                                    binding.textinputSubject.setVisibility(View.GONE);
                                    updateChatMsgHint();
                                    updateSendButton();
                                    updateEditablity();
                                }
                                break;
                            default:
                                sendMessage();
                        }
                    } else {
                        sendMessage();
                    }
                }
            };


    private final OnClickListener mCancelVoiceRecord = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mHandler.removeCallbacks(mTickExecutor);
            stopRecording(false);
            activity.setResult(RESULT_CANCELED);
            //activity.finish();
            binding.recordingVoiceActivity.setVisibility(View.GONE);
        }
    };

    private final OnClickListener mShareVoiceRecord = new OnClickListener() {
        @Override
        public void onClick(View v) {
            binding.shareButton.setEnabled(false);        // TODO: Activate again
            // binding.shareButton.setText(R.string.please_wait);
            mHandler.removeCallbacks(mTickExecutor);
            mHandler.postDelayed(() -> stopRecording(true), 500);
        }
    };

    private OnBackPressedCallback backPressedLeaveSingleThread = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            conversation.setLockThread(false);
            this.setEnabled(false);
            conversation.setUserSelectedThread(false);
            setThread(null);
            refresh();
            updateThreadFromLastMessage();
        }
    };

    private final OnBackPressedCallback backPressedLeaveVoiceRecorder = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (binding.recordingVoiceActivity.getVisibility()==VISIBLE){
                mHandler.removeCallbacks(mTickExecutor);
                stopRecording(false);
                activity.setResult(RESULT_CANCELED);
                //activity.finish();
                binding.recordingVoiceActivity.setVisibility(View.GONE);
            }
            this.setEnabled(false);
            refresh();
        }
    };

    private int completionIndex = 0;
    private int lastCompletionLength = 0;
    private String incomplete;
    private int lastCompletionCursor;
    private boolean firstWord = false;
    private Message mPendingDownloadableMessage;
    private ProgressDialog fetchHistoryDialog;

    private static ConversationFragment findConversationFragment(Activity activity) {
        Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationFragment) {
            return (ConversationFragment) fragment;
        }
        fragment = activity.getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (fragment instanceof ConversationFragment) {
            return (ConversationFragment) fragment;
        }
        return null;
    }

    public static void startStopPending(Activity activity) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.messageListAdapter.startStopPending();
        }
    }

    public static void downloadFile(Activity activity, Message message) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.startDownloadable(message);
        }
    }

    public static void registerPendingMessage(Activity activity, Message message) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            fragment.pendingMessage.push(message);
        }
    }

    public static void openPendingMessage(Activity activity) {
        ConversationFragment fragment = findConversationFragment(activity);
        if (fragment != null) {
            Message message = fragment.pendingMessage.pop();
            if (message != null) {
                fragment.messageListAdapter.openDownloadable(message);
            }
        }
    }

    public static Conversation getConversation(Activity activity) {
        return getConversation(activity, R.id.secondary_fragment);
    }

    private static Conversation getConversation(Activity activity, @IdRes int res) {
        final Fragment fragment = activity.getFragmentManager().findFragmentById(res);
        if (fragment instanceof ConversationFragment) {
            return ((ConversationFragment) fragment).getConversation();
        } else {
            return null;
        }
    }

    public static ConversationFragment get(Activity activity) {
        FragmentManager fragmentManager = activity.getFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationFragment) {
            return (ConversationFragment) fragment;
        } else {
            fragment = fragmentManager.findFragmentById(R.id.secondary_fragment);
            return fragment instanceof ConversationFragment
                    ? (ConversationFragment) fragment
                    : null;
        }
    }

    public static Conversation getConversationReliable(Activity activity) {
        final Conversation conversation = getConversation(activity, R.id.secondary_fragment);
        if (conversation != null) {
            return conversation;
        }
        return getConversation(activity, R.id.main_fragment);
    }

    private static boolean scrolledToBottom(AbsListView listView) {
        final int count = listView.getCount();
        if (count == 0) {
            return true;
        } else if (listView.getLastVisiblePosition() == count - 1) {
            final View lastChild = listView.getChildAt(listView.getChildCount() - 1);
            return lastChild != null && lastChild.getBottom() <= listView.getHeight();
        } else {
            return false;
        }
    }

    private void toggleScrollDownButton() {
        toggleScrollDownButton(binding.messagesView);
    }

    private void toggleScrollDownButton(AbsListView listView) {
        if (conversation == null) {
            return;
        }
        if (scrolledToBottom(listView) && !conversation.isInHistoryPart()) {
            lastMessageUuid = null;
            hideUnreadMessagesCount();
        } else {
            binding.scrollToBottomButton.setEnabled(true);
            binding.scrollToBottomButton.show();
            if (lastMessageUuid == null) {
                lastMessageUuid = conversation.getLatestMessage().getUuid();
            }
            if (conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid) > 0) {
                binding.unreadCountCustomView.setVisibility(View.VISIBLE);
            }
        }
    }

    private int getIndexOf(String uuid, List<Message> messages) {
        if (uuid == null) {
            return messages.size() - 1;
        }
        for (int i = 0; i < messages.size(); ++i) {
            if (uuid.equals(messages.get(i).getUuid())) {
                return i;
            }
        }
        return -1;
    }

    private int getIndexOfExtended(String uuid, List<Message> messages) {
        if (uuid == null) {
            return messages.size() - 1;
        }
        for (int i = 0; i < messages.size(); ++i) {
            if (uuid.equals(messages.get(i).getServerMsgId())) {
                return i;
            }

            if (uuid.equals(messages.get(i).getRemoteMsgId())) {
                return i;
            }

            if (uuid.equals(messages.get(i).getUuid())) {
                return i;
            }
        }
        return -1;
    }

    private ScrollState getScrollPosition() {
        final ListView listView = this.binding == null ? null : this.binding.messagesView;
        if (listView == null
                || listView.getCount() == 0
                || listView.getLastVisiblePosition() == listView.getCount() - 1) {
            return null;
        } else {
            final int pos = listView.getFirstVisiblePosition();
            final View view = listView.getChildAt(0);
            if (view == null) {
                return null;
            } else {
                return new ScrollState(pos, view.getTop());
            }
        }
    }

    private void setScrollPosition(ScrollState scrollPosition, String lastMessageUuid) {
        if (scrollPosition != null) {

            this.lastMessageUuid = lastMessageUuid;
            if (lastMessageUuid != null) {
                binding.unreadCountCustomView.setUnreadCount(
                        conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid));
            }
            // TODO maybe this needs a 'post'
            this.binding.messagesView.setSelectionFromTop(
                    scrollPosition.position, scrollPosition.offset);
            toggleScrollDownButton();
        }
    }

    private void attachLocationToConversation(Conversation conversation, Uri uri) {
        if (conversation == null) {
            return;
        }
        final String subject = binding.textinputSubject.getText().toString();
        activity.xmppConnectionService.attachLocationToConversation(
                conversation,
                uri,
                subject,
                new UiCallback<Message>() {

                    @Override
                    public void success(Message message) {
                        messageSent();
                    }

                    @Override
                    public void error(int errorCode, Message object) {
                        // TODO show possible pgp error
                    }

                    @Override
                    public void userInputRequired(PendingIntent pi, Message object) {}
                });
    }

    private void attachFileToConversation(Conversation conversation, Uri uri, String type, Runnable next) {
        if (conversation == null) {
            return;
        }
        final String subject = binding.textinputSubject.getText().toString();
        if ("application/webxdc+zip".equals(type)) newSubThread();
        final Toast prepareFileToast =
                Toast.makeText(activity, getText(R.string.preparing_file), Toast.LENGTH_LONG);
        prepareFileToast.show();
        activity.delegateUriPermissionsToService(uri);
        activity.xmppConnectionService.attachFileToConversation(
                conversation,
                uri,
                type,
                subject,
                new UiInformableCallback<Message>() {
                    @Override
                    public void inform(final String text) {
                        hidePrepareFileToast(prepareFileToast);
                        runOnUiThread(() -> activity.replaceToast(text));
                    }

                    @Override
                    public void success(Message message) {
                        if (next == null) {
                            runOnUiThread(() -> {
                                activity.hideToast();
                                messageSent();
                            });
                        } else {
                            runOnUiThread(next);
                        }
                        hidePrepareFileToast(prepareFileToast);
                    }

                    @Override
                    public void error(final int errorCode, Message message) {
                        hidePrepareFileToast(prepareFileToast);
                        runOnUiThread(() -> activity.replaceToast(getString(errorCode)));
                    }

                    @Override
                    public void userInputRequired(PendingIntent pi, Message message) {
                        hidePrepareFileToast(prepareFileToast);
                    }
                });
    }

    public void attachEditorContentToConversation(Uri uri) {
        mediaPreviewAdapter.addMediaPreviews(
                Attachment.of(activity, uri, Attachment.Type.FILE));
        toggleInputMethod();
    }

    private void attachImageToConversation(Conversation conversation, Uri uri, String type, Runnable next) {
        if (conversation == null) {
            return;
        }
        final String subject = binding.textinputSubject.getText().toString();
        final Toast prepareFileToast =
                Toast.makeText(activity, getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareFileToast.show();
        activity.delegateUriPermissionsToService(uri);
        activity.xmppConnectionService.attachImageToConversation(
                conversation,
                uri,
                type,
                subject,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequired(PendingIntent pi, Message object) {
                        hidePrepareFileToast(prepareFileToast);
                    }

                    @Override
                    public void success(Message message) {
                        hidePrepareFileToast(prepareFileToast);
                        if (next == null) {
                            runOnUiThread(() -> messageSent());
                        } else {
                            runOnUiThread(next);
                        }
                    }

                    @Override
                    public void error(final int error, final Message message) {
                        hidePrepareFileToast(prepareFileToast);
                        final ConversationsActivity activity = ConversationFragment.this.activity;
                        if (activity == null) {
                            return;
                        }
                        activity.runOnUiThread(() -> activity.replaceToast(getString(error)));
                    }
                });
    }

    private void hidePrepareFileToast(final Toast prepareFileToast) {
        if (prepareFileToast != null && activity != null) {
            activity.runOnUiThread(prepareFileToast::cancel);
        }
    }

    private void sendMessage() {
        sendMessage((Long) null);
    }

    private void sendMessage(Long sendAt) {
        if (sendAt != null && sendAt < System.currentTimeMillis()) sendAt = null; // No sending in past plz
        Editable body = this.binding.textinput.getText();
        if (mediaPreviewAdapter.getItemCount() > 1 || (mediaPreviewAdapter.getItemCount() == 1 && body == null)) {
            commitAttachments();
            return;
        }
        if (body == null) body = new SpannableStringBuilder("");
        if (body.length() > Config.MAX_DISPLAY_MESSAGE_CHARS) {
            Toast.makeText(activity, activity.getString(R.string.message_is_too_long), Toast.LENGTH_SHORT).show();
            return;
        }
        final Conversation conversation = this.conversation;
        final boolean hasSubject = binding.textinputSubject.getText().length() > 0;
        if (conversation == null || (body.length() == 0 && mediaPreviewAdapter.getItemCount() == 0 && (conversation.getThread() == null || !hasSubject))) {
            if (Build.VERSION.SDK_INT >= 24) {
                binding.textSendButton.showContextMenu(0, 0);
            } else {
                binding.textSendButton.showContextMenu();
            }
            return;
        }
        if (trustKeysIfNeeded(conversation, REQUEST_TRUST_KEYS_TEXT)) {
            return;
        }
        final Message message;
        if (conversation.getCorrectingMessage() == null) {
            boolean attention = false;
            if (Pattern.compile("\\A@here\\s.*").matcher(body).find()) {
                attention = true;
                body.delete(0, 6);
                while (body.length() > 0 && Character.isWhitespace(body.charAt(0))) body.delete(0, 1);
            }
            if (conversation.getReplyTo() != null) {
                if (Emoticons.isEmoji(body.toString().replaceAll("\\s", "")) && conversation.getNextCounterpart() == null && !conversation.getReplyTo().isPrivateMessage()) {
                    final var aggregated = conversation.getReplyTo().getAggregatedReactions();
                    final ImmutableSet.Builder<String> reactionBuilder = new ImmutableSet.Builder<>();
                    reactionBuilder.addAll(aggregated.ourReactions);
                    reactionBuilder.add(body.toString().replaceAll("\\s", ""));
                    activity.xmppConnectionService.sendReactions(conversation.getReplyTo(), reactionBuilder.build());
                    messageSent();
                    return;
                } else {
                    message = conversation.getReplyTo().reply();
                    message.appendBody(body);
                }
                message.setEncryption(conversation.getNextEncryption());
            } else {
                message = new Message(conversation, body.toString(), conversation.getNextEncryption());
                message.setBody(hasSubject && body.length() == 0 ? null : body);
                if (message.bodyIsOnlyEmojis()) {
                    SpannableStringBuilder spannable = message.getSpannableBody(null, null);
                    ImageSpan[] imageSpans = spannable.getSpans(0, spannable.length(), ImageSpan.class);
                    for (ImageSpan span : imageSpans) {
                        final int start = spannable.getSpanStart(span);
                        final int end = spannable.getSpanEnd(span);
                        spannable.delete(start, end);
                    }
                    if (imageSpans.length == 1 && spannable.toString().replaceAll("\\s", "").length() < 1) {
                        // Only one inline image, so it's a sticker
                        String source = imageSpans[0].getSource();
                        if (source != null && source.length() > 0 && source.substring(0, 4).equals("cid:")) {
                            try {
                                final Cid cid = BobTransfer.cid(Uri.parse(source));
                                final String url = activity.xmppConnectionService.getUrlForCid(cid);
                                final File f = activity.xmppConnectionService.getFileForCid(cid);
                                if (url != null) {
                                    message.setBody("");
                                    message.setRelativeFilePath(f.getAbsolutePath());
                                    activity.xmppConnectionService.getFileBackend().updateFileParams(message);
                                }
                            } catch (final Exception e) { }
                        }
                    }
                }
                // Set caption when only one attachment
                if (mediaPreviewAdapter.getItemCount() == 1) {
                    conversation.setCaption(message);
                    commitAttachments();
                    return;
                }
            }
            if (hasSubject) message.setSubject(binding.textinputSubject.getText().toString());
            if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
                message.setThread(conversation.getThread());
            }
            if (attention) {
                message.addPayload(new Element("attention", "urn:xmpp:attention:0"));
            }
            Message.configurePrivateMessage(message);
        } else {
            message = conversation.getCorrectingMessage();
            if (hasSubject) message.setSubject(binding.textinputSubject.getText().toString());
            if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
                message.setThread(conversation.getThread());
            }
            if (conversation.getReplyTo() != null) {
                if (Emoticons.isEmoji(body.toString().replaceAll("\\s", ""))) {
                    message.updateReaction(conversation.getReplyTo(), body.toString().replaceAll("\\s", ""));
                } else {
                    message.updateReplyTo(conversation.getReplyTo(), body);
                }
            } else {
                message.clearReplyReact();
                message.setBody(hasSubject && body.length() == 0 ? null : body);
            }
            if (message.getStatus() == Message.STATUS_WAITING) {
                if (sendAt != null) message.setTime(sendAt);
                activity.xmppConnectionService.updateMessage(message);
                messageSent();
                return;
            } else {
                message.putEdited(message.getUuid(), message.getServerMsgId());
                message.setServerMsgId(null);
                message.setUuid(UUID.randomUUID().toString());
            }
        }
        if (sendAt != null) message.setTime(sendAt);
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            default:
                sendMessage(message);
        }
        setupReply(null);
    }

    public boolean requireTrustKeys() {
        return trustKeysIfNeeded(conversation, REQUEST_TRUST_KEYS_NONE);
    }

    private boolean trustKeysIfNeeded(final Conversation conversation, final int requestCode) {
        return conversation.getNextEncryption() == Message.ENCRYPTION_AXOLOTL
                && trustKeysIfNeeded(requestCode);
    }

    protected boolean trustKeysIfNeeded(int requestCode) {
        AxolotlService axolotlService = conversation.getAccount().getAxolotlService();
        if (axolotlService == null) return false;
        final List<Jid> targets = axolotlService.getCryptoTargets(conversation);
        boolean hasUnaccepted = !conversation.getAcceptedCryptoTargets().containsAll(targets);
        boolean hasUndecidedOwn =
                !axolotlService
                        .getKeysWithTrust(FingerprintStatus.createActiveUndecided())
                        .isEmpty();
        boolean hasUndecidedContacts =
                !axolotlService
                        .getKeysWithTrust(FingerprintStatus.createActiveUndecided(), targets)
                        .isEmpty();
        boolean hasPendingKeys = !axolotlService.findDevicesWithoutSession(conversation).isEmpty();
        boolean hasNoTrustedKeys = axolotlService.anyTargetHasNoTrustedKeys(targets);
        boolean downloadInProgress = axolotlService.hasPendingKeyFetches(targets);
        if (hasUndecidedOwn
                || hasUndecidedContacts
                || hasPendingKeys
                || hasNoTrustedKeys
                || hasUnaccepted
                || downloadInProgress) {
            axolotlService.createSessionsIfNeeded(conversation);
            Intent intent = new Intent(activity, TrustKeysActivity.class);
            String[] contacts = new String[targets.size()];
            for (int i = 0; i < contacts.length; ++i) {
                contacts[i] = targets.get(i).toString();
            }
            intent.putExtra("contacts", contacts);
            intent.putExtra(
                    EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
            intent.putExtra("conversation", conversation.getUuid());
            startActivityForResult(intent, requestCode);
            return true;
        } else {
            return false;
        }
    }

    public void updateChatMsgHint() {
        final boolean multi = conversation.getMode() == Conversation.MODE_MULTI;
        if (conversation.getCorrectingMessage() != null) {
            this.binding.textInputHint.setVisibility(View.GONE);
            this.binding.textinput.setHint(R.string.send_corrected_message);
            binding.conversationViewPager.setCurrentItem(0);
        } else if (multi && conversation.getNextCounterpart() != null) {
            this.binding.textinput.setHint(R.string.send_message);
            this.binding.textInputHint.setVisibility(View.VISIBLE);
            final MucOptions.User user = conversation.getMucOptions().findUserByName(conversation.getNextCounterpart().getResource());
            String nick = user == null ? null : user.getNick();
            if (nick == null) nick = conversation.getNextCounterpart().getResource();
            this.binding.textInputHint.setText(
                    getString(
                            R.string.send_private_message_to,
                            nick));
            binding.conversationViewPager.setCurrentItem(0);
        } else if (multi && !conversation.getMucOptions().participating()) {
            this.binding.textInputHint.setVisibility(View.GONE);
            this.binding.textinput.setHint(R.string.you_are_not_participating);
        } else {
            this.binding.textInputHint.setVisibility(View.GONE);
            if (activity == null) return;
            this.binding.textinput.setHint(UIHelper.getMessageHint(activity, conversation));
            activity.invalidateOptionsMenu();
        }

        binding.messagesView.post(this::updateThreadFromLastMessage);
    }

    public void setupIme() {
        this.binding.textinput.refreshIme();
    }

    private void handleActivityResult(ActivityResult activityResult) {
        if (activityResult.resultCode == Activity.RESULT_OK) {
            handlePositiveActivityResult(activityResult.requestCode, activityResult.data);
        } else {
            handleNegativeActivityResult(activityResult.requestCode);
        }
    }

    private void handlePositiveActivityResult(int requestCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_WEBXDC_STORE:
                mediaPreviewAdapter.addMediaPreviews(Attachment.of(activity, data.getData(), Attachment.Type.FILE));
                toggleInputMethod();
                break;
            case REQUEST_TRUST_KEYS_NONE:
                break;
            case REQUEST_TRUST_KEYS_TEXT:
                sendMessage();
                break;
            case REQUEST_TRUST_KEYS_ATTACHMENTS:
                commitAttachments();
                break;
            case REQUEST_START_AUDIO_CALL:
                triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
                break;
            case REQUEST_START_VIDEO_CALL:
                triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
                break;
            case ATTACHMENT_CHOICE_CHOOSE_IMAGE: {
                final Uri takePhotoUri = pendingTakePhotoUri.pop();
                if (takePhotoUri != null && (data == null || (data.getData() == null && data.getClipData() == null))) {
                    mediaPreviewAdapter.addMediaPreviews(
                            Attachment.of(activity, takePhotoUri, Attachment.Type.IMAGE));
                }
                final List<Attachment> imageUris =
                        Attachment.extractAttachments(activity, data, Attachment.Type.IMAGE);
                if (imageUris.size() == 1 && imageUris.get(0).getMime().startsWith("image/")
                        && !imageUris.get(0).getMime().equals("image/gif")) {
                    editImage(imageUris.get(0).getUri());
                } else {
                    mediaPreviewAdapter.addMediaPreviews(imageUris);
                    toggleInputMethod();
                }
                break;
            }
            case ATTACHMENT_CHOICE_TAKE_PHOTO: {
                final Uri takePhotoUri = pendingTakePhotoUri.pop();
                if (takePhotoUri != null) {
                    editImage(takePhotoUri);
                } else {
                    Log.d(Config.LOGTAG, "lost take photo uri. unable to to attach");
                }
                break;
            }
            case ATTACHMENT_CHOICE_EDIT_PHOTO:
                final Uri editedUriPhoto = data.getParcelableExtra(EditActivity.KEY_EDITED_URI);
                if (editedUriPhoto != null) {
                    mediaPreviewAdapter.replaceOrAddMediaPreview(data.getData(), editedUriPhoto, Attachment.Type.IMAGE);
                    toggleInputMethod();
                } else {
                    Log.d(Config.LOGTAG, "lost take photo uri. unable to to attach");
                }
                break;
            case ATTACHMENT_CHOICE_CHOOSE_FILE:
            case ATTACHMENT_CHOICE_RECORD_VIDEO:
            case ATTACHMENT_CHOICE_RECORD_VOICE:
                final Attachment.Type type =
                        requestCode == ATTACHMENT_CHOICE_RECORD_VOICE
                                ? Attachment.Type.RECORDING
                                : Attachment.Type.FILE;
                final List<Attachment> fileUris =
                        Attachment.extractAttachments(activity, data, type);
                mediaPreviewAdapter.addMediaPreviews(fileUris);
                toggleInputMethod();
                break;
            case ATTACHMENT_CHOICE_LOCATION:
                final double latitude = data.getDoubleExtra("latitude", 0);
                final double longitude = data.getDoubleExtra("longitude", 0);
                final int accuracy = data.getIntExtra("accuracy", 0);
                final Uri geo;
                if (accuracy > 0) {
                    geo = Uri.parse(String.format("geo:%s,%s;u=%s", latitude, longitude, accuracy));
                } else {
                    geo = Uri.parse(String.format("geo:%s,%s", latitude, longitude));
                }
                mediaPreviewAdapter.addMediaPreviews(
                        Attachment.of(activity, geo, Attachment.Type.LOCATION));
                toggleInputMethod();
                break;
            case REQUEST_INVITE_TO_CONVERSATION:
                XmppActivity.ConferenceInvite invite = XmppActivity.ConferenceInvite.parse(data);
                if (invite != null) {
                    if (invite.execute(activity)) {
                        activity.mToast =
                                Toast.makeText(
                                        activity, R.string.creating_conference, Toast.LENGTH_LONG);
                        activity.mToast.show();
                    }
                }
                break;
        }
    }

    public void editImage(Uri uri) {
        Intent intent = new Intent(activity, EditActivity.class);
        intent.setData(uri);
        intent.putExtra(EditActivity.KEY_CHAT_NAME, conversation.getName());
        startActivityForResult(intent, ATTACHMENT_CHOICE_EDIT_PHOTO);
    }

    private void commitAttachments() {
        final List<Attachment> attachments = mediaPreviewAdapter.getAttachments();
        if (anyNeedsExternalStoragePermission(attachments)
                && !hasPermissions(
                REQUEST_COMMIT_ATTACHMENTS, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return;
        }
        if (trustKeysIfNeeded(conversation, REQUEST_TRUST_KEYS_ATTACHMENTS)) {
            return;
        }
        final PresenceSelector.OnPresenceSelected callback =
                () -> {
                    final Iterator<Attachment> i = attachments.iterator();
                    final Runnable next = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (!i.hasNext()) return;
                                final Attachment attachment = i.next();
                                if (attachment.getType() == Attachment.Type.LOCATION) {
                                    attachLocationToConversation(conversation, attachment.getUri());
                                    if (i.hasNext()) runOnUiThread(this);
                                } else if (attachment.getType() == Attachment.Type.IMAGE) {
                                    Log.d(
                                            Config.LOGTAG,
                                            "ConversationsActivity.commitAttachments() - attaching image to conversations. CHOOSE_IMAGE");
                                    attachImageToConversation(conversation, attachment.getUri(), attachment.getMime(), this);
                                } else {
                                    Log.d(
                                            Config.LOGTAG,
                                            "ConversationsActivity.commitAttachments() - attaching file to conversations. CHOOSE_FILE/RECORD_VOICE/RECORD_VIDEO");
                                    attachFileToConversation(conversation, attachment.getUri(), attachment.getMime(), this);
                                }
                                i.remove();
                                if (!i.hasNext()) messageSent();
                            } catch (final java.util.ConcurrentModificationException e) {
                                // Abort, leave any unsent attachments alone for the user to try again
                                Toast.makeText(activity, "Sometimes went wrong with some attachments. Try again?", Toast.LENGTH_SHORT).show();
                            }
                            mediaPreviewAdapter.notifyDataSetChanged();
                            toggleInputMethod();
                        }
                    };
                    next.run();
                };
        if (conversation == null
                || conversation.getMode() == Conversation.MODE_MULTI
                || Attachment.canBeSendInBand(attachments)
                || (conversation.getAccount().httpUploadAvailable()
                && FileBackend.allFilesUnderSize(
                activity, attachments, getMaxHttpUploadSize(conversation)))) {
            callback.onPresenceSelected();
        } else {
            activity.selectPresence(conversation, callback);
        }
    }

    private static boolean anyNeedsExternalStoragePermission(
            final Collection<Attachment> attachments) {
        for (final Attachment attachment : attachments) {
            if (attachment.getType() != Attachment.Type.LOCATION) {
                return true;
            }
        }
        return false;
    }

    public void toggleInputMethod() {
        //Currently no caption possible when E2EE enabled
        if (conversation.getNextEncryption() == Message.ENCRYPTION_NONE && mediaPreviewAdapter.getItemCount() == 1) {
            binding.textinputLayoutNew.setVisibility(VISIBLE);
            binding.mediaPreview.setVisibility(View.VISIBLE);
        } else {
            boolean hasAttachments = mediaPreviewAdapter.hasAttachments();
            binding.textinputLayoutNew.setVisibility(hasAttachments ? View.GONE : View.VISIBLE);
            binding.mediaPreview.setVisibility(hasAttachments ? View.VISIBLE : View.GONE);
        }
        updateSendButton();
    }

    private void handleNegativeActivityResult(int requestCode) {
        switch (requestCode) {
            case ATTACHMENT_CHOICE_TAKE_PHOTO:
                if (pendingTakePhotoUri.clear()) {
                    Log.d(
                            Config.LOGTAG,
                            "cleared pending photo uri after negative activity result");
                }
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ActivityResult activityResult = ActivityResult.of(requestCode, resultCode, data);
        if (activity != null && activity.xmppConnectionService != null) {
            handleActivityResult(activityResult);
        } else {
            this.postponedActivityResult.push(activityResult);
        }
        if (conversation != null && conversation.getUuid() != null) ChatBackgroundHelper.onActivityResult(activity, requestCode, resultCode, data, conversation.getUuid());

        if (requestCode == ChatBackgroundHelper.REQUEST_IMPORT_BACKGROUND) {
            refresh();
        }
    }

    public void unblockConversation(final Blockable conversation) {
        activity.xmppConnectionService.sendUnblockRequest(conversation);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(Config.LOGTAG, "ConversationFragment.onAttach()");
        if (activity instanceof ConversationsActivity) {
            this.activity = (ConversationsActivity) activity;
        } else {
            throw new IllegalStateException(
                    "Trying to attach fragment to activity that is not the ConversationsActivity");
        }
        dirStickers = StickersMigration.getStickersDir(activity);
        StickersMigration.requireMigration(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.activity = null; // TODO maybe not a good idea since some callbacks really need it
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        activity.getOnBackPressedDispatcher().addCallback(this, backPressedLeaveSingleThread);
        activity.getOnBackPressedDispatcher().addCallback(this, backPressedLeaveVoiceRecorder);
        activity.getOnBackPressedDispatcher().addCallback(this, backPressedLeaveEmojiPicker);
        oldOrientation = activity.getRequestedOrientation();

        // Get repository instance
        if (activity != null) {

            pinnedMessageRepository = activity.getPinnedMessageRepository();
        }
        // Fallback if not available through activity
        if (pinnedMessageRepository == null && activity != null) {
            pinnedMessageRepository = new PinnedMessageRepository(activity.getApplicationContext());
        }
        if (savedInstanceState == null && conversation != null) {
            conversation.jumpToLatest();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        if (activity != null && activity.xmppConnectionService != null && activity.xmppConnectionService.isOnboarding()) return;

        menuInflater.inflate(R.menu.fragment_conversation, menu);
        final MenuItem menuMucDetails = menu.findItem(R.id.action_muc_details);
        final MenuItem menuMucParticipants = menu.findItem(R.id.action_muc_participants);
        final MenuItem menuContactDetails = menu.findItem(R.id.action_contact_details);
        final MenuItem menuInviteContact = menu.findItem(R.id.action_invite);
        final MenuItem menuMute = menu.findItem(R.id.action_mute);
        final MenuItem menuUnmute = menu.findItem(R.id.action_unmute);
        final MenuItem menuCall = menu.findItem(R.id.action_call);
        final MenuItem menuOngoingCall = menu.findItem(R.id.action_ongoing_call);
        final MenuItem menuVideoCall = menu.findItem(R.id.action_video_call);
        final MenuItem menuTogglePinned = menu.findItem(R.id.action_toggle_pinned);
        final MenuItem deleteCustomBg = menu.findItem(R.id.action_delete_custom_bg);

        if (conversation != null) {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                menuContactDetails.setVisible(false);
                menuInviteContact.setVisible(conversation.getMucOptions().canInvite());
                menuMucDetails.setTitle(
                        conversation.getMucOptions().isPrivateAndNonAnonymous()
                                ? R.string.action_muc_details
                                : R.string.channel_details);
                menuCall.setVisible(false);
                menuOngoingCall.setVisible(false);
            } else {
                menuMucParticipants.setVisible(false);
                final XmppConnectionService service =
                        activity == null ? null : activity.xmppConnectionService;
                final Optional<OngoingRtpSession> ongoingRtpSession =
                        service == null
                                ? Optional.absent()
                                : service.getJingleConnectionManager()
                                .getOngoingRtpConnection(conversation.getContact());
                if (ongoingRtpSession.isPresent()) {
                    menuOngoingCall.setVisible(true);
                    menuCall.setVisible(false);
                } else {
                    menuOngoingCall.setVisible(false);
                    final RtpCapability.Capability rtpCapability =
                            RtpCapability.check(conversation.getContact());
                    final boolean cameraAvailable =
                            activity != null && activity.isCameraFeatureAvailable();
                    menuCall.setVisible(true);
                    menuVideoCall.setVisible(rtpCapability != RtpCapability.Capability.AUDIO && cameraAvailable);
                }
                menuContactDetails.setVisible(!this.conversation.withSelf());
                menuMucDetails.setVisible(false);
                menuInviteContact.setVisible(
                        service != null
                                && service.findConferenceServer(conversation.getAccount()) != null);
            }
            if (conversation.isMuted()) {
                menuMute.setVisible(false);
            } else {
                menuUnmute.setVisible(false);
            }
            ConversationMenuConfigurator.configureAttachmentMenu(conversation, menu, TextUtils.isEmpty(binding.textinput.getText()));
            ConversationMenuConfigurator.configureEncryptionMenu(conversation, menu, activity);
            if (conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false)) {
                menuTogglePinned.setTitle(R.string.remove_from_favorites);
            } else {
                menuTogglePinned.setTitle(R.string.add_to_favorites);
            }
            deleteCustomBg.setVisible(ChatBackgroundHelper.getBgFile(activity, conversation.getUuid()).exists());
        }
        Fragment secondaryFragment = activity.getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (secondaryFragment instanceof ConversationFragment) {
            activity.showNavigationBar();
        } else {
            activity.hideNavigationBar();
        }
        super.onCreateOptionsMenu(menu, menuInflater);
    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.binding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_conversation, container, false);
        binding.getRoot().setOnClickListener(null); // TODO why the fuck did we do this?

        // Check if we should adjust the soft keyboard
        binding.conversationViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            public void onPageScrollStateChanged(int state) {}
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

            public void onPageSelected(int position) {
                if (position == 0) {
                    if (activity != null) {
                        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                    }
                } else {
                    if (activity != null) {
                        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                    }
                }
            }
        });

        backPressedLeaveEmojiPicker.setEnabled(binding.emojisStickerLayout.getHeight() > 100);

        binding.textinput.setOnEditorActionListener(mEditorActionListener);
        binding.textinput.setRichContentListener(new String[] {"image/*"}, mEditorContentListener);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        if (displayMetrics.heightPixels > 0) binding.textinput.setMaxHeight(displayMetrics.heightPixels / 4);
        final var appSettings = new AppSettings(activity);
        if (appSettings.isLargeFont()) {
            binding.textinput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        } else {
            binding.textinput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        }
        binding.textSendButton.setOnClickListener(this.mSendButtonListener);
        binding.textSendButton.setPadding(0, 0, 10, 0);
        binding.mucSubjectIcon.setPadding(0, 0, 10, 0);
        binding.statusMessageIcon.setPadding(0, 0, 10, 0);

        binding.cancelButton.setOnClickListener(this.mCancelVoiceRecord);
        binding.shareButton.setOnClickListener(this.mShareVoiceRecord);
        binding.contextPreviewCancel.setOnClickListener((v) -> {
            setThread(null);
            conversation.setUserSelectedThread(false);
            setupReply(null);
        });
        binding.requestVoice.setOnClickListener((v) -> {
            activity.xmppConnectionService.requestVoice(conversation.getAccount(), conversation.getJid());
            binding.requestVoice.setVisibility(View.GONE);
            Toast.makeText(activity, R.string.request_to_speak_send, Toast.LENGTH_SHORT).show();
        });
        binding.emojiButton.setOnClickListener(this.memojiButtonListener);
        binding.emojisButton.setOnClickListener(this.memojisButtonListener);
        binding.stickersButton.setOnClickListener(this.mstickersButtonListener);
        binding.gifsButton.setOnClickListener(this.mgifsButtonListener);
        binding.keyboardButton.setOnClickListener(this.mkeyboardButtonListener);
        binding.recordVoiceButton.setOnClickListener(this.mRecordVoiceButtonListener);
        binding.timer.setOnClickListener(this.mTimerClickListener);
        binding.takePictureButton.setOnClickListener(this.mtakePictureButtonListener);
        binding.scrollToBottomButton.setOnClickListener(this.mScrollButtonListener);
        binding.messagesView.setOnScrollListener(mOnScrollListener);
        binding.messagesView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        mediaPreviewAdapter = new MediaPreviewAdapter(this);
        binding.mediaPreview.setAdapter(mediaPreviewAdapter);
        messageListAdapter = new MessageAdapter((XmppActivity) activity, this.messageList);
        messageListAdapter.setOnContactPictureClicked(this);
        messageListAdapter.setOnContactPictureLongClicked(this);
        messageListAdapter.setOnInlineImageLongClicked(this);
        messageListAdapter.setConversationFragment(this);
        messageListAdapter.setReplyClickListener(this::scrollToReply);
        binding.messagesView.setAdapter(messageListAdapter);

        binding.textinput.addTextChangedListener(
                new StylingHelper.MessageEditorStyler(binding.textinput, messageListAdapter));

        registerForContextMenu(binding.messagesView);
        registerForContextMenu(binding.textSendButton);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.binding.textinput.setCustomInsertionActionModeCallback(
                    new EditMessageActionModeCallback(this.binding.textinput));
            this.binding.textinput.setCustomSelectionActionModeCallback(
                    new EditMessageSelectionActionModeCallback(this.binding.textinput));
        }

        messageListAdapter.setOnMessageBoxClicked(message -> {
            if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
                if (message.isPrivateMessage()) privateMessageWith(message.getCounterpart());
                setThread(message.getThread());
                conversation.setUserSelectedThread(true);
            }
        });

        binding.threadIdenticonLayout.setOnClickListener(v -> {
            boolean wasLocked = conversation.getLockThread();
            conversation.setLockThread(false);
            backPressedLeaveSingleThread.setEnabled(false);
            if (wasLocked) {
                setThread(null);
                conversation.setUserSelectedThread(false);
                refresh();
                updateThreadFromLastMessage();
            } else {
                newThread();
                conversation.setUserSelectedThread(true);
                newThreadTutorialToast(activity.getString(R.string.switch_to_new_thread));
            }
        });

        binding.threadIdenticonLayout.setOnLongClickListener(v -> {
            boolean wasLocked = conversation.getLockThread();
            conversation.setLockThread(false);
            backPressedLeaveSingleThread.setEnabled(false);
            setThread(null);
            conversation.setUserSelectedThread(true);
            if (wasLocked) refresh();
            newThreadTutorialToast(activity.getString(R.string.cleared_thread));
            return true;
        });
        if (activity != null && activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("message_autocomplete", R.bool.message_autocomplete)) {
            // After here should be only autocomplete setup stuff

            Autocomplete.<MucOptions.User>on(binding.textinput)
                    .with(activity.getDrawable(R.drawable.background_message_bubble))
                    .with(new CharPolicy('@'))
                    .with(new RecyclerViewPresenter<MucOptions.User>(activity) {
                        protected UserAdapter adapter;

                        @Override
                        protected Adapter instantiateAdapter() {
                            adapter = new UserAdapter(false) {
                                @Override
                                public void onBindViewHolder(UserAdapter.ViewHolder viewHolder, int position) {
                                    super.onBindViewHolder(viewHolder, position);
                                    final var item = getItem(position);
                                    viewHolder.binding.getRoot().setOnClickListener(v -> {
                                        dispatchClick(item);
                                    });
                                }
                            };
                            return adapter;
                        }

                        @Override
                        protected void onQuery(@Nullable CharSequence query) {
                            if (!activity.xmppConnectionService.getBooleanPreference("message_autocomplete", R.bool.message_autocomplete))
                                return;

                            final var allUsers = conversation.getMucOptions().getUsers();
                            if (!conversation.getMucOptions().getUsersByRole(MucOptions.Role.MODERATOR).isEmpty()) {
                                final var u = new MucOptions.User(conversation.getMucOptions(), null, "\0role:moderator", "Notify active moderators", new HashSet<>());
                                u.setRole("participant");
                                allUsers.add(u);
                            }
                            if (!allUsers.isEmpty() && conversation.getMucOptions().getSelf() != null && conversation.getMucOptions().getSelf().getAffiliation().ranks(MucOptions.Affiliation.MEMBER)) {
                                final var u = new MucOptions.User(conversation.getMucOptions(), null, "\0attention", "Notify active participants", new HashSet<>());
                                u.setRole("participant");
                                allUsers.add(u);
                            }
                            final String needle = query.toString().toLowerCase(Locale.getDefault());
                            if (getRecyclerView() != null) getRecyclerView().setItemAnimator(null);
                            adapter.submitList(
                                    Ordering.natural().immutableSortedCopy(Collections2.filter(
                                            allUsers,
                                            user -> {
                                                if ("mods".contains(needle) && "\0role:moderator".equals(user.getOccupantId()))
                                                    return true;
                                                if ("here".contains(needle) && "\0attention".equals(user.getOccupantId()))
                                                    return true;
                                                final String name = user.getNick();
                                                if (name == null) return false;
                                                for (final var hat : user.getHats()) {
                                                    if (hat.toString().toLowerCase(Locale.getDefault()).contains(needle))
                                                        return true;
                                                }
                                                for (final var hat : user.getPseudoHats(activity)) {
                                                    if (hat.toString().toLowerCase(Locale.getDefault()).contains(needle))
                                                        return true;
                                                }
                                                final Contact contact = user.getContact();
                                                return name.toLowerCase(Locale.getDefault()).contains(needle)
                                                        || contact != null
                                                        && contact.getDisplayName().toLowerCase(Locale.getDefault()).contains(needle);
                                            })));
                        }

                        @Override
                        protected AutocompletePresenter.PopupDimensions getPopupDimensions() {
                            final var dim = new AutocompletePresenter.PopupDimensions();
                            dim.width = displayMetrics.widthPixels * 4 / 5;
                            return dim;
                        }
                    })
                    .with(new AutocompleteCallback<MucOptions.User>() {
                        @Override
                        public boolean onPopupItemClicked(Editable editable, MucOptions.User user) {
                            int[] range = com.otaliastudios.autocomplete.CharPolicy.getQueryRange(editable);
                            if (range == null) return false;
                            range[0] -= 1;
                            if ("\0attention".equals(user.getOccupantId())) {
                                editable.delete(Math.max(0, range[0]), Math.min(editable.length(), range[1]));
                                editable.insert(0, "@here ");
                                return true;
                            }
                            int colon = editable.toString().indexOf(':');
                            final var beforeColon = range[0] < colon;
                            String prefix = "";
                            String suffix = " ";
                            if (beforeColon) suffix = ", ";
                            if (colon < 0 && range[0] == 0) suffix = ": ";
                            if (colon > 0 && colon == range[0] - 2) {
                                prefix = ", ";
                                suffix = ": ";
                                range[0] -= 2;
                            }
                            var insert = user.getNick();
                            if ("\0role:moderator".equals(user.getOccupantId())) {
                                insert = conversation.getMucOptions().getUsersByRole(MucOptions.Role.MODERATOR).stream().map(MucOptions.User::getNick).collect(Collectors.joining(", "));
                            }
                            editable.replace(Math.max(0, range[0]), Math.min(editable.length(), range[1]), prefix + insert + suffix);
                            return true;
                        }

                        @Override
                        public void onPopupVisibilityChanged(boolean shown) {
                        }
                    }).build();

            Handler emojiDebounce = new Handler(Looper.getMainLooper());
            setupEmojiSearch();
            Autocomplete.<EmojiSearch.Emoji>on(binding.textinput)
                    .with(activity.getDrawable(R.drawable.background_message_bubble))
                    .with(new CharPolicy(':'))
                    .with(new RecyclerViewPresenter<EmojiSearch.Emoji>(activity) {
                        protected EmojiSearch.EmojiSearchAdapter adapter;

                        @Override
                        protected Adapter instantiateAdapter() {
                            setupEmojiSearch();
                            adapter = emojiSearch.makeAdapter(item -> dispatchClick(item));
                            return adapter;
                        }

                        @Override
                        protected void onViewHidden() {
                            if (getRecyclerView() == null) return;
                            super.onViewHidden();
                        }

                        @Override
                        protected void onQuery(@Nullable CharSequence query) {
                            if (!activity.xmppConnectionService.getBooleanPreference("message_autocomplete", R.bool.message_autocomplete))
                                return;

                            emojiDebounce.removeCallbacksAndMessages(null);
                            emojiDebounce.postDelayed(() -> {
                                if (getRecyclerView() == null) return;
                                getRecyclerView().setItemAnimator(null);
                                adapter.search(activity, getRecyclerView(), query.toString());
                            }, 100L);
                        }
                    })
                    .with(new AutocompleteCallback<EmojiSearch.Emoji>() {
                        @Override
                        public boolean onPopupItemClicked(Editable editable, EmojiSearch.Emoji emoji) {
                            int[] range = com.otaliastudios.autocomplete.CharPolicy.getQueryRange(editable);
                            if (range == null) return false;
                            range[0] -= 1;
                            final var toInsert = emoji.toInsert();
                            toInsert.append(" ");
                            editable.replace(Math.max(0, range[0]), Math.min(editable.length(), range[1]), toInsert);
                            return true;
                        }

                        @Override
                        public void onPopupVisibilityChanged(boolean shown) {
                        }
                    }).build();
        }
        return binding.getRoot();
    }

    protected void setupEmojiSearch() {
        if (activity != null && activity.xmppConnectionService != null) {
            if (emojiSearch == null) {
                emojiSearch = activity.xmppConnectionService.emojiSearch();
            }
        }
    }

    protected void newThreadTutorialToast(String s) {
        if (activity == null) return;
        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        final int tutorialCount = p.getInt("thread_tutorial", 0);
        if (tutorialCount < 5) {
            Toast.makeText(activity, s, Toast.LENGTH_SHORT).show();
            p.edit().putInt("thread_tutorial", tutorialCount + 1).apply();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdownNow(); // Attempt to stop all actively executing tasks
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(Config.LOGTAG, "ConversationFragment.onDestroyView()");
        if (activity != null) {
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        messageListAdapter.setOnContactPictureClicked(null);
        messageListAdapter.setOnContactPictureLongClicked(null);
        messageListAdapter.setOnInlineImageLongClicked(null);
        messageListAdapter.setConversationFragment(null);
        messageListAdapter.setOnMessageBoxClicked(null);
        messageListAdapter.setReplyClickListener(null);
        binding.conversationViewPager.setAdapter(null);
        if (conversation != null) conversation.setupViewPager(null, null, false, null);
    }

    public void quoteText(String text) {
        if (binding.textinput.isEnabled()) {
            binding.textinput.insertAsQuote(text);
            binding.textinput.requestFocus();
            InputMethodManager inputMethodManager =
                    (InputMethodManager)
                            activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(
                        binding.textinput, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    public void quoteMessage(Message message) {
        if (message.isPrivateMessage()) privateMessageWith(message.getCounterpart());
        if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
            setThread(message.getThread());
        }
        conversation.setUserSelectedThread(true);
        if (!forkNullThread(message)) newThread();
        setupReply(message);
    }

    private boolean forkNullThread(Message message) {
        if (message.getThread() != null || conversation.getMode() != Conversation.MODE_MULTI) return true;
        for (final Message m : conversation.findReplies(message.getServerMsgId())) {
            final Element thread = m.getThread();
            if (thread != null) {
                if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
                    setThread(thread);
                }
                return true;
            }
        }

        return false;
    }

    private void setupReply(Message message) {
        /*          //Activate to disable reply to yourself
        if (message != null) {

            final var correcting = conversation.getCorrectingMessage();
            if (correcting != null && correcting.getUuid().equals(message.getUuid())) return;
        }
        */
        conversation.setReplyTo(message);
        if (message == null) {
            binding.contextPreview.setVisibility(View.GONE);
            return;
        }

        SpannableStringBuilder body = message.getSpannableBody(null, null);
        if ((message.isFileOrImage() || message.isOOb()) && binding.imageReplyPreview != null) {
            binding.imageReplyPreview.setVisibility(VISIBLE);
            Glide.with(activity).load(message.getRelativeFilePath()).placeholder(R.drawable.ic_image_24dp).thumbnail(0.2f).into(binding.imageReplyPreview);
        } else if (message.isGeoUri()) {
            if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_maps_inside", R.bool.show_maps_inside) && binding.imageReplyPreview != null) {
                final String url = GeoHelper.MapPreviewUri(message, activity);
                binding.imageReplyPreview.setVisibility(View.VISIBLE);
                Glide.with(activity)
                        .load(Uri.parse(url))
                        .placeholder(R.drawable.marker)
                        .error(R.drawable.marker)
                        .into(binding.imageReplyPreview);
            } else {
                Glide.with(activity).clear(binding.imageReplyPreview);
                binding.imageReplyPreview.setVisibility(GONE);
                body = SpannableStringBuilder.valueOf(message.getRawBody());
            }
        } else if (binding.imageReplyPreview != null) {
            Glide.with(activity).clear(binding.imageReplyPreview);
            binding.imageReplyPreview.setVisibility(GONE);
        }
        messageListAdapter.handleTextQuotes(binding.contextPreviewText, body);
        binding.contextPreviewText.setText(body);
        final var appSettings = new AppSettings(activity);
        if (appSettings.isLargeFont()) {
            binding.contextPreviewText.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            binding.contextPreviewText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        } else {
            binding.contextPreviewText.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        }
        binding.contextPreview.setVisibility(View.VISIBLE);
    }

    private void setThread(Element thread) {
        this.conversation.setThread(thread);
        binding.threadIdenticon.setAlpha(0f);
        binding.threadIdenticonLock.setVisibility(this.conversation.getLockThread() ? View.VISIBLE : View.GONE);
        if (thread != null) {
            final String threadId = thread.getContent();
            if (threadId != null) {
                binding.threadIdenticon.setAlpha(1f);
                binding.threadIdenticon.setColor(UIHelper.getColorForName(threadId));
                binding.threadIdenticon.setHash(UIHelper.identiconHash(threadId));
            }
        }
        updateSendButton();
    }

    // TODO: Use this to scroll to the reply
    private void scrollToReply(Message message) {
        Element reply = message.getReply();
        if (reply == null) return;

        String replyId = reply.getAttribute("id");

        if (replyId != null) {
            Runnable postSelectionRunnable = () -> highlightMessage(replyId);
            previousClickedReply = message;
            updateSelection(replyId, binding.messagesView.getHeight() / 2, postSelectionRunnable, true, false);
        }
    }

    private void highlightMessage(String uuid) {
        binding.messagesView.postDelayed(() -> {
            int actualIndex = getIndexOfExtended(uuid, messageList);

            if (actualIndex == -1) {
                return;
            }

            View view = ListViewUtils.getViewByPosition(actualIndex, binding.messagesView);
            View messageBox = view.findViewById(R.id.message_box);
            if (messageBox != null) {
                messageBox.animate()
                        .scaleX(1.10f)
                        .scaleY(1.10f)
                        .setInterpolator(new CycleInterpolator(0.5f))
                        .setDuration(400L)
                        .start();
            }
        }, 300L);
    }

    public void fadeOutMessage(String uuid) {
            int actualIndex = getIndexOfExtended(uuid, messageList);

            if (actualIndex == -1) {
                return;
            }

            View view = ListViewUtils.getViewByPosition(actualIndex, binding.messagesView);
            View messageBox = view.findViewById(R.id.message_box);
            if (messageBox != null) {
                messageBox.animate()
                        .translationX(50)
                        .alpha(0.1f)
                        .setDuration(400L)
                        .start();
            }
    }

    private void updateSelection(String uuid, Integer offsetFormTop, Runnable selectionUpdatedRunnable, boolean populateFromMam, boolean recursiveFetch) {
        if (recursiveFetch && (fetchHistoryDialog == null || !fetchHistoryDialog.isShowing())) return;

        int pos = getIndexOfExtended(uuid, messageList);

        Runnable updateSelectionRunnable = () -> {
            FragmentConversationBinding binding = ConversationFragment.this.binding;

            Runnable performRunnable = () -> {
                if (offsetFormTop != null) {
                    binding.messagesView.setSelectionFromTop(pos, offsetFormTop);
                    return;
                }

                binding.messagesView.setSelection(pos);
            };

            performRunnable.run();
            binding.messagesView.post(performRunnable);

            if (selectionUpdatedRunnable != null) {
                selectionUpdatedRunnable.run();
            }
        };

        if (pos != -1) {
            hideFetchHistoryDialog();
            updateSelectionRunnable.run();
        } else {
            activity.xmppConnectionService.jumpToMessage(conversation, uuid, new XmppConnectionService.JumpToMessageListener() {
                @Override
                public void onSuccess() {
                    activity.runOnUiThread(() -> {
                        refresh(false);
                        conversation.messagesLoaded.set(true);
                        conversation.historyPartLoadedForward.set(true);
                        toggleScrollDownButton();
                        updateSelection(uuid, binding.messagesView.getHeight() / 2, selectionUpdatedRunnable, populateFromMam, false);
                    });
                }

                @Override
                public void onNotFound() {
                    activity.runOnUiThread(() -> {
                        if (populateFromMam && conversation.hasMessagesLeftOnServer()) {
                            showFetchHistoryDialog();
                            loadMoreMessages(true, false, binding.messagesView);
                            binding.messagesView.postDelayed(() -> updateSelection(uuid, binding.messagesView.getHeight() / 2, selectionUpdatedRunnable, populateFromMam, true), 500L);
                        } else {
                            hideFetchHistoryDialog();
                        }
                    });
                }
            });
        }
    }

    private void showFetchHistoryDialog() {
        if (fetchHistoryDialog != null && fetchHistoryDialog.isShowing()) return;

        fetchHistoryDialog = new ProgressDialog(activity);
        fetchHistoryDialog.setIndeterminate(true);
        fetchHistoryDialog.setMessage(getString(R.string.please_wait));
        fetchHistoryDialog.setCancelable(true);
        fetchHistoryDialog.show();
    }

    private void hideFetchHistoryDialog() {
        if (fetchHistoryDialog != null && fetchHistoryDialog.isShowing()) {
            fetchHistoryDialog.hide();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        // This should cancel any remaining click events that would otherwise trigger links
        v.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));

        if (v == binding.textSendButton) {
            super.onCreateContextMenu(menu, v, menuInfo);
            try {
                java.lang.reflect.Method m = menu.getClass().getSuperclass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                m.setAccessible(true);
                m.invoke(menu, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
            Menu tmpMenu = new PopupMenu(activity, null).getMenu();
            activity.getMenuInflater().inflate(R.menu.fragment_conversation, tmpMenu);
            MenuItem attachMenu = tmpMenu.findItem(R.id.action_attach_file);
            for (int i = 0; i < attachMenu.getSubMenu().size(); i++) {
                MenuItem item = attachMenu.getSubMenu().getItem(i);
                MenuItem newItem = menu.add(item.getGroupId(), item.getItemId(), item.getOrder(), item.getTitle());
                newItem.setIcon(item.getIcon());
            }

            extensions.clear();
            final var xmppConnectionService = activity.xmppConnectionService;
            final var dir = new File(xmppConnectionService.getExternalFilesDir(null), "extensions");
            for (File file : Files.fileTraverser().breadthFirst(dir)) {
                if (file.isFile() && file.canRead()) {
                    final var dummy = new Message(conversation, null, conversation.getNextEncryption());
                    dummy.setStatus(Message.STATUS_DUMMY);
                    dummy.setThread(conversation.getThread());
                    dummy.setUuid(file.getName());
                    final var xdc = new WebxdcPage(activity, file, dummy);
                    extensions.add(xdc);
                    final var item = menu.add(0x1, extensions.size() - 1, 0, xdc.getName());
                    item.setIcon(xdc.getIcon(24));
                }
            }
            ConversationMenuConfigurator.configureAttachmentMenu(conversation, menu, TextUtils.isEmpty(binding.textinput.getText()));
            return;
        }

        synchronized (this.messageList) {
            super.onCreateContextMenu(menu, v, menuInfo);
            AdapterView.AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) menuInfo;
            if (acmi != null && acmi.position >= 0 && acmi.position < this.messageList.size()) {
                this.selectedMessage = this.messageList.get(acmi.position);
            }
            populateContextMenu(menu);
        }
    }

    private void populateContextMenu(final ContextMenu menu) {
        final Message m = this.selectedMessage;
        final Transferable t = m.getTransferable();
        if (m.getType() != Message.TYPE_STATUS && m.getType() != Message.TYPE_RTP_SESSION) {

            if (m.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE
                    || m.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
                return;
            }

            if (m.getStatus() == Message.STATUS_RECEIVED
                    && t != null
                    && (t.getStatus() == Transferable.STATUS_CANCELLED
                    || t.getStatus() == Transferable.STATUS_FAILED)) {
                return;
            }

            final boolean deleted = m.isDeleted();
            final boolean encrypted =
                    m.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED
                            || m.getEncryption() == Message.ENCRYPTION_PGP;
            final boolean receiving =
                    m.getStatus() == Message.STATUS_RECEIVED
                            && (t instanceof JingleFileTransferConnection
                            || t instanceof HttpDownloadConnection);
            activity.getMenuInflater().inflate(R.menu.message_context, menu);
            final MenuItem reportAndBlock = menu.findItem(R.id.action_report_and_block);
            MenuItem openWith = menu.findItem(R.id.open_with);
            MenuItem pinToTop = menu.findItem(R.id.pin_message_to_top);
            MenuItem copyMessage = menu.findItem(R.id.copy_message);
            MenuItem quoteMessage = menu.findItem(R.id.quote_message);
            MenuItem retryDecryption = menu.findItem(R.id.retry_decryption);
            MenuItem correctMessage = menu.findItem(R.id.correct_message);
            MenuItem retractMessage = menu.findItem(R.id.retract_message);
            MenuItem moderateMessage = menu.findItem(R.id.moderate_message);
            MenuItem onlyThisThread = menu.findItem(R.id.only_this_thread);
            MenuItem shareWith = menu.findItem(R.id.share_with);
            MenuItem sendAgain = menu.findItem(R.id.send_again);
            MenuItem copyUrl = menu.findItem(R.id.copy_url);
            MenuItem saveToDownloads = menu.findItem(R.id.save_to_downloads);
            MenuItem copyLink = menu.findItem(R.id.copy_link);
            MenuItem saveAsSticker = menu.findItem(R.id.save_as_sticker);
            MenuItem downloadFile = menu.findItem(R.id.download_file);
            MenuItem cancelTransmission = menu.findItem(R.id.cancel_transmission);
            MenuItem blockMedia = menu.findItem(R.id.block_media);
            MenuItem deleteFile = menu.findItem(R.id.delete_file);
            MenuItem showErrorMessage = menu.findItem(R.id.show_error_message);
            final MenuItem retryAsP2P = menu.findItem(R.id.send_again_as_p2p);
            onlyThisThread.setVisible(!conversation.getLockThread() && m.getThread() != null);
            final boolean unInitiatedButKnownSize = MessageUtils.unInitiatedButKnownSize(m);
            final boolean showError =
                    m.getStatus() == Message.STATUS_SEND_FAILED
                            && m.getErrorMessage() != null
                            && !Message.ERROR_MESSAGE_CANCELLED.equals(m.getErrorMessage());
            final Conversational conversational = m.getConversation();
            if (m.getStatus() == Message.STATUS_RECEIVED
                    && conversational instanceof Conversation c) {
                final XmppConnection connection = c.getAccount().getXmppConnection();
                if (c.isWithStranger()
                        && m.getServerMsgId() != null
                        && !c.isBlocked()
                        && connection != null
                        && connection.getFeatures().spamReporting()) {
                    reportAndBlock.setVisible(true);
                }
            }
            if (!m.isFileOrImage()
                    && !encrypted
                    && !m.isGeoUri()
                    && !m.treatAsDownloadable()
                    && !unInitiatedButKnownSize
                    && t == null) {
                copyMessage.setVisible(true);

                quoteMessage.setVisible(!showError && !MessageUtils.prepareQuote(m).isEmpty());
                final String scheme =
                        ShareUtil.getLinkScheme(new SpannableStringBuilder(m.getBody()));
                if ("xmpp".equals(scheme)) {
                    copyLink.setTitle(R.string.copy_jabber_id);
                    copyLink.setVisible(true);
                } else if (scheme != null) {
                    copyLink.setVisible(true);
                }
            }
            quoteMessage.setVisible(!encrypted && !showError);
            if (m.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED && !deleted) {
                retryDecryption.setVisible(true);
            }
            if (!showError
                    && m.getType() == Message.TYPE_TEXT
                    && m.isEditable()
                    && !m.isGeoUri()
                    && m.getConversation() instanceof Conversation) {
                correctMessage.setVisible(true);
                retractMessage.setVisible(true);
            }
            if ((m.isGeoUri() || m.isFileOrImage()) && m.getStatus() != Message.STATUS_RECEIVED) retractMessage.setVisible(true); //TODO also allow retraction when not yet downloaded
            if (m.getStatus() == Message.STATUS_WAITING) {
                correctMessage.setVisible(true);
                retractMessage.setVisible(true);
            }
            if (conversation.getMode() == Conversation.MODE_MULTI && m.getServerMsgId() != null && m.getModerated() == null && conversation.getMucOptions().getSelf().getRole().ranks(MucOptions.Role.MODERATOR) && conversation.getMucOptions().hasFeature("urn:xmpp:message-moderate:0")) {
                moderateMessage.setVisible(true);
            }
            if ((m.isFileOrImage() && !deleted && !receiving)
                    || (m.getType() == Message.TYPE_TEXT && !m.treatAsDownloadable())
                    && !unInitiatedButKnownSize
                    && t == null) {
                shareWith.setVisible(true);

                // pin to top
                pinToTop.setVisible(true);
            }
            if (m.getStatus() == Message.STATUS_SEND_FAILED) {
                sendAgain.setVisible(true);
                final var fileNotUploaded = m.isFileOrImage() && !m.hasFileOnRemoteHost();
                final var isPeerOnline =
                        conversational.getMode() == Conversation.MODE_SINGLE
                                && (conversational instanceof Conversation c)
                                && !c.getContact().getPresences().isEmpty();
                retryAsP2P.setVisible(fileNotUploaded && isPeerOnline);
            }
            if (m.getEncryption() == Message.ENCRYPTION_NONE && (
                    m.hasFileOnRemoteHost()
                    || m.isGeoUri()
                    || m.treatAsDownloadable()
                    || unInitiatedButKnownSize
                    || t instanceof HttpDownloadConnection)) {
                copyUrl.setVisible(true);
            }
            if (m.isFileOrImage() && deleted && m.hasFileOnRemoteHost()) {
                downloadFile.setVisible(true);
                downloadFile.setTitle(
                        activity.getString(
                                R.string.download_x_file,
                                UIHelper.getFileDescriptionString(activity, m)));
            }
            final boolean waitingOfferedSending =
                    m.getStatus() == Message.STATUS_WAITING
                            || m.getStatus() == Message.STATUS_UNSEND
                            || m.getStatus() == Message.STATUS_OFFERED;
            final boolean cancelable =
                    (t != null && !deleted) || waitingOfferedSending && m.needsUploading();
            if (cancelable) {
                cancelTransmission.setVisible(true);
            }
            if (m.isFileOrImage() && !deleted && !cancelable) {
                final String path = m.getRelativeFilePath();
                if (path != null) {
                    final var file = new File(path);
                    if (file.canRead()) saveAsSticker.setVisible(true);
                    blockMedia.setVisible(true);
                    if (file.canWrite()) deleteFile.setVisible(true);
                    deleteFile.setTitle(
                            activity.getString(
                                    R.string.delete_x_file,
                                    UIHelper.getFileDescriptionString(activity, m)));
                    saveToDownloads.setVisible(true);
                }
            }

            if (m.getFileParams() != null && !m.getFileParams().getThumbnails().isEmpty()) {
                // We might be showing a thumbnail worth blocking
                blockMedia.setVisible(true);
            }
            if (showError) {
                showErrorMessage.setVisible(true);
            }
            final String mime = m.isFileOrImage() ? m.getMimeType() : null;
            if ((m.isGeoUri() && GeoHelper.openInOsmAnd(activity, m))
                    || (mime != null && mime.startsWith("audio/"))) {
                openWith.setVisible(true);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.share_with:
                ShareUtil.share(activity, selectedMessage);
                return true;
            case R.id.correct_message:
                correctMessage(selectedMessage);
                return true;
            case R.id.retract_message:
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.retract_message)
                        .setMessage(R.string.do_you_really_want_to_retract_this_message)
                        .setPositiveButton(R.string.yes, (dialog, whichButton) -> {
                            final var message = selectedMessage;
                            fadeOutMessage(message.getUuid());
                            binding.messagesView.postDelayed(() -> {
                                final Message finalMessage = message;
                                if (message.getConversation() instanceof Conversation) {

                                    Message retractedMessage = finalMessage;
                                    retractedMessage.setDeleted(true);

                                    long time = System.currentTimeMillis();
                                    Message retractmessage = new Message(conversation,
                                            "This person attempted to retract a previous message, but it's unsupported by your client.",
                                            message.getEncryption(),        // Message.ENCRYPTION_NONE doesn't work for encrypted messages
                                            Message.STATUS_SEND);
                                    if (retractedMessage.getEditedList().size() > 0) {
                                        retractmessage.setRetractId(retractedMessage.getEditedList().get(0).getEditedId());
                                    } else {
                                        retractmessage.setRetractId(retractedMessage.getRemoteMsgId() != null ? retractedMessage.getRemoteMsgId() : retractedMessage.getUuid());
                                    }

                                    retractedMessage.putEdited(retractedMessage.getUuid(), retractedMessage.getServerMsgId());  //TODO: Maybe add: , retractedMessage.getBody(), retractedMessage.getTimeSent());
                                    retractedMessage.setBody(Message.DELETED_MESSAGE_BODY);
                                    retractedMessage.setServerMsgId(null);
                                    retractedMessage.setRemoteMsgId(message.getRemoteMsgId());
                                    retractedMessage.setDeleted(true);

                                    retractmessage.setType(Message.TYPE_TEXT);
                                    retractmessage.setCounterpart(message.getCounterpart());
                                    retractmessage.setTrueCounterpart(message.getTrueCounterpart());
                                    retractmessage.setTime(time);
                                    retractmessage.setUuid(UUID.randomUUID().toString());
                                    retractmessage.setCarbon(false);
                                    retractmessage.setOob(false);
                                    retractmessage.resetFileParams();   //TODO: Check if we need this
                                    retractmessage.setRemoteMsgId(retractmessage.getUuid());
                                    retractmessage.setDeleted(true);
                                    retractedMessage.setTime(time); //set new time here to keep original timestamps
                                    for (Edit itm : retractedMessage.getEditedList()) {
                                        Message tmpRetractedMessage = conversation.findMessageWithUuidOrRemoteId(itm.getEditedId());
                                        if (tmpRetractedMessage != null) {
                                            tmpRetractedMessage.setDeleted(true);
                                            activity.xmppConnectionService.updateMessage(tmpRetractedMessage, tmpRetractedMessage.getUuid());
                                        }
                                    }
                                    activity.xmppConnectionService.updateMessage(retractedMessage, retractedMessage.getUuid());
                                    if (finalMessage.getStatus() >= Message.STATUS_SEND) {
                                        //only send retraction messages for outgoing messages!
                                        sendMessage(retractmessage);
                                    }
                                    activity.xmppConnectionService.deleteMessage(retractedMessage);
                                    activity.xmppConnectionService.deleteMessage(retractmessage);
                                }
                                activity.xmppConnectionService.deleteMessage(message);
                                activity.onConversationsListItemUpdated();
                                refresh();
                            }, 300L);
                        })
                        .setNegativeButton(R.string.no, null).show();
                return true;
            case R.id.moderate_message:
                activity.quickEdit("Spam", (reason) -> {
                    activity.xmppConnectionService.moderateMessage(conversation.getAccount(), selectedMessage, reason);
                    return null;
                }, R.string.moderate_reason, false, false, true, true);
                return true;
            case R.id.pin_message_to_top:
                // store for each conversation
                pinMessage(selectedMessage);
                return true;
            case R.id.copy_message:
                ShareUtil.copyToClipboard(activity, selectedMessage);
                return true;
            case R.id.quote_message:
                quoteMessage(selectedMessage);
                return true;
            case R.id.send_again:
                resendMessage(selectedMessage, false);
                return true;
            case R.id.send_again_as_p2p:
                resendMessage(selectedMessage, true);
                return true;
            case R.id.copy_link:
                ShareUtil.copyLinkToClipboard(activity, selectedMessage);
                return true;
            case R.id.copy_url:
                ShareUtil.copyUrlToClipboard(activity, selectedMessage);
                return true;
            case R.id.save_as_sticker:
                saveAsSticker(selectedMessage);
                return true;
            case R.id.download_file:
                startDownloadable(selectedMessage);
                return true;
            case R.id.cancel_transmission:
                cancelTransmission(selectedMessage);
                return true;
            case R.id.retry_decryption:
                retryDecryption(selectedMessage);
                return true;
            case R.id.block_media:
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.block_media)
                        .setMessage(R.string.block_media_question)
                        .setPositiveButton(R.string.yes, (dialog, whichButton) -> {
                            List<Element> thumbs = selectedMessage.getFileParams() != null ? selectedMessage.getFileParams().getThumbnails() : null;
                            if (thumbs != null && !thumbs.isEmpty()) {
                                for (Element thumb : thumbs) {
                                    Uri uri = Uri.parse(thumb.getAttribute("uri"));
                                    if (uri.getScheme().equals("cid")) {
                                        Cid cid = BobTransfer.cid(uri);
                                        if (cid == null) continue;
                                        DownloadableFile f = activity.xmppConnectionService.getFileForCid(cid);
                                        activity.xmppConnectionService.blockMedia(f);
                                        activity.xmppConnectionService.evictPreview(f);
                                        f.delete();
                                    }
                                }
                            }
                            File f = activity.xmppConnectionService.getFileBackend().getFile(selectedMessage);
                            activity.xmppConnectionService.blockMedia(f);
                            activity.xmppConnectionService.getFileBackend().deleteFile(selectedMessage);
                            activity.xmppConnectionService.evictPreview(f);
                            activity.xmppConnectionService.updateMessage(selectedMessage, false);
                            activity.onConversationsListItemUpdated();
                            refresh();
                        })
                        .setNegativeButton(R.string.no, null).show();
                return true;
            case R.id.delete_file:
                deleteFile(selectedMessage);
                return true;
            case R.id.save_to_downloads:
                saveToDownloads(selectedMessage);
                return true;
            case R.id.show_error_message:
                showErrorMessage(selectedMessage);
                return true;
            case R.id.open_with:
                openWith(selectedMessage);
                return true;
            case R.id.only_this_thread:
                conversation.setLockThread(true);
                backPressedLeaveSingleThread.setEnabled(true);
                setThread(selectedMessage.getThread());
                refresh();
                return true;
            case R.id.action_report_and_block:
                reportMessage(selectedMessage);
                return true;
            default:
                return onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        } else if (conversation == null) {
            return super.onOptionsItemSelected(item);
        }
        if (item.getGroupId() == 0x1) {
            conversation.startWebxdc(extensions.get(item.getItemId()));
            return true;
        }
        switch (item.getItemId()) {
            case R.id.encryption_choice_axolotl:
            case R.id.encryption_choice_pgp:
            case R.id.encryption_choice_otr:
            case R.id.encryption_choice_none:
                handleEncryptionSelection(item);
                break;
            case R.id.attach_choose_picture:
            //case R.id.attach_take_picture:
            case R.id.attach_record_video:
            case R.id.attach_choose_file:
            //case R.id.attach_record_voice:
            case R.id.attach_location:
                handleAttachmentSelection(item);
                break;
            case R.id.attach_webxdc:
                final Intent intent = new Intent(activity, WebxdcStore.class);
                startActivityForResult(intent, REQUEST_WEBXDC_STORE);
                break;
            case R.id.attach_subject:
                binding.textinputSubject.setVisibility(binding.textinputSubject.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
                break;
            case R.id.attach_schedule:
                scheduleMessage();
                break;
            case R.id.action_search:
                startSearch();
                break;
            case R.id.action_archive:
                activity.xmppConnectionService.archiveConversation(conversation);
                break;
            case R.id.action_contact_details:
                activity.switchToContactDetails(conversation.getContact());
                break;
            case R.id.action_muc_details:
                ConferenceDetailsActivity.open(activity, conversation);
                break;
            case R.id.action_muc_participants:
                Intent intent_user = new Intent(activity, MucUsersActivity.class);
                intent_user.putExtra("uuid", conversation.getUuid());
                activity.startActivity(intent_user);
                break;
            case R.id.action_invite:
                startActivityForResult(
                        ChooseContactActivity.create(activity, conversation),
                        REQUEST_INVITE_TO_CONVERSATION);
                break;
            case R.id.action_clear_history:
                clearHistoryDialog(conversation);
                break;
            case R.id.action_mute:
                muteConversationDialog(conversation);
                break;
            case R.id.action_unmute:
                unMuteConversation(conversation);
                break;
            case R.id.action_set_custom_bg:
                if (activity.hasStoragePermission(ChatBackgroundHelper.REQUEST_IMPORT_BACKGROUND)) {
                    ChatBackgroundHelper.openBGPicker(this);
                }
                break;
            case R.id.action_delete_custom_bg:
                try {
                    File bgfile =  ChatBackgroundHelper.getBgFile(activity, conversation.getUuid());
                    if (bgfile.exists()) {
                        bgfile.delete();
                        Toast.makeText(activity, R.string.delete_background_success,Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(activity, R.string.no_background_set,Toast.LENGTH_LONG).show();
                    }

                    refresh();
                } catch (Exception e) {
                    Toast.makeText(activity, R.string.delete_background_failed,Toast.LENGTH_LONG).show();
                    throw new RuntimeException(e);
                }
                break;
            case R.id.action_block:
            case R.id.action_unblock:
                BlockContactDialog.show(activity, conversation);
                break;
            case R.id.action_audio_call:
                checkPermissionAndTriggerAudioCall();
                break;
            case R.id.action_video_call:
                checkPermissionAndTriggerVideoCall();
                break;
            case R.id.action_ongoing_call:
                returnToOngoingCall();
                break;
            case R.id.action_toggle_pinned:
                togglePinned();
                break;
            case R.id.action_add_shortcut:
                addShortcut();
                break;
            case R.id.action_block_avatar:
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.block_media)
                        .setMessage(R.string.block_avatar_question)
                        .setPositiveButton(R.string.yes, (dialog, whichButton) -> {
                            activity.xmppConnectionService.blockMedia(activity.xmppConnectionService.getFileBackend().getAvatarFile(conversation.getContact().getAvatarFilename()));
                            activity.xmppConnectionService.getFileBackend().getAvatarFile(conversation.getContact().getAvatarFilename()).delete();
                            activity.avatarService().clear(conversation);
                            conversation.getContact().setAvatar(null);
                            activity.xmppConnectionService.updateConversationUi();
                        })
                        .setNegativeButton(R.string.no, null).show();
            case R.id.action_refresh_feature_discovery:
                refreshFeatureDiscovery();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean onBackPressed() {
        boolean wasLocked = conversation.getLockThread();
        conversation.setLockThread(false);
        backPressedLeaveSingleThread.setEnabled(false);
        if (wasLocked) {
            setThread(null);
            conversation.setUserSelectedThread(false);
            refresh();
            updateThreadFromLastMessage();
            return true;
        }
        if (binding.emojisStickerLayout.getHeight() > 100){
            LinearLayout emojipickerview = binding.emojisStickerLayout;
            ViewGroup.LayoutParams params = emojipickerview.getLayoutParams();
            params.height = 0;
            emojipickerview.setLayoutParams(params);
            hideSoftKeyboard(activity);
            return false;
        }
        if (binding.recordingVoiceActivity.getVisibility()==VISIBLE){
            mHandler.removeCallbacks(mTickExecutor);
            stopRecording(false);
            activity.setResult(RESULT_CANCELED);
            //activity.finish();
            binding.recordingVoiceActivity.setVisibility(View.GONE);
            return false;
        }
        return false;
    }

    private void startSearch() {
        final Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra(SearchActivity.EXTRA_CONVERSATION_UUID, conversation.getUuid());
        startActivity(intent);
    }

    private void scheduleMessage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            final var datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Schedule Message")
                    .setSelection(com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
                    .setCalendarConstraints(
                            new com.google.android.material.datepicker.CalendarConstraints.Builder()
                                    .setStart(com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
                                    .build()
                    )
                    .build();
            datePicker.addOnPositiveButtonClickListener((date) -> {
                final Calendar now = Calendar.getInstance();
                final var timePicker = new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                        .setTitleText("Schedule Message")
                        .setHour(now.get(Calendar.HOUR_OF_DAY))
                        .setMinute(now.get(Calendar.MINUTE))
                        .setTimeFormat(android.text.format.DateFormat.is24HourFormat(activity) ? com.google.android.material.timepicker.TimeFormat.CLOCK_24H : com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                        .build();
                timePicker.addOnPositiveButtonClickListener((v2) -> {
                    final var dateCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    dateCal.setTimeInMillis(date);
                    final var time = Calendar.getInstance();
                    time.set(dateCal.get(Calendar.YEAR), dateCal.get(Calendar.MONTH), dateCal.get(Calendar.DAY_OF_MONTH), timePicker.getHour(), timePicker.getMinute(), 0);
                    final long timestamp = time.getTimeInMillis();
                    sendMessage(timestamp);
                    Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": scheduled message for " + timestamp);
                });
                timePicker.show(activity.getSupportFragmentManager(), "schedulMessageTime");
            });
            datePicker.show(activity.getSupportFragmentManager(), "schedulMessageDate");
        }
    }

    private void returnToOngoingCall() {
        final Optional<OngoingRtpSession> ongoingRtpSession =
                activity.xmppConnectionService
                        .getJingleConnectionManager()
                        .getOngoingRtpConnection(conversation.getContact());
        if (ongoingRtpSession.isPresent()) {
            final OngoingRtpSession id = ongoingRtpSession.get();
            final Intent intent = new Intent(activity, RtpSessionActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.putExtra(
                    RtpSessionActivity.EXTRA_ACCOUNT,
                    id.getAccount().getJid().asBareJid().toString());
            intent.putExtra(RtpSessionActivity.EXTRA_WITH, id.getWith().toString());
            if (id instanceof AbstractJingleConnection) {
                intent.putExtra(RtpSessionActivity.EXTRA_SESSION_ID, id.getSessionId());
                startActivity(intent);
            } else if (id instanceof JingleConnectionManager.RtpSessionProposal proposal) {
                if (Media.audioOnly(proposal.media)) {
                    intent.putExtra(
                            RtpSessionActivity.EXTRA_LAST_ACTION,
                            RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
                } else {
                    intent.putExtra(
                            RtpSessionActivity.EXTRA_LAST_ACTION,
                            RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
                }
                intent.putExtra(RtpSessionActivity.EXTRA_PROPOSED_SESSION_ID, proposal.sessionId);
                startActivity(intent);
            }
        }
    }

    private void refreshFeatureDiscovery() {
        Set<Map.Entry<String, Presence>> presences = conversation.getContact().getPresences().getPresencesMap().entrySet();
        if (presences.isEmpty()) {
            presences = new HashSet<>();
            presences.add(new AbstractMap.SimpleEntry("", null));
        }
        for (Map.Entry<String, Presence> entry : presences) {
            Jid jid = conversation.getContact().getJid();
            if (!entry.getKey().equals("")) jid = jid.withResource(entry.getKey());
            activity.xmppConnectionService.fetchCaps(conversation.getAccount(), jid, entry.getValue(), () -> {
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    refresh();
                    refreshCommands(true);
                });
            });
        }
    }

    private void addShortcut() {
        ShortcutInfoCompat info;
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            info = activity.xmppConnectionService.getShortcutService().getShortcutInfo(conversation.getMucOptions());
        } else {
            info = activity.xmppConnectionService.getShortcutService().getShortcutInfo(conversation.getContact());
        }
        ShortcutManagerCompat.requestPinShortcut(activity, info, null);
    }

    private void togglePinned() {
        final boolean pinned =
                conversation.getBooleanAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, false);
        conversation.setAttribute(Conversation.ATTRIBUTE_PINNED_ON_TOP, !pinned);
        activity.xmppConnectionService.updateConversation(conversation);
        activity.invalidateOptionsMenu();
    }

    private void checkPermissionAndTriggerAudioCall() {
        if (activity.mUseTor || conversation.getAccount().isOnion()) {
            Toast.makeText(activity, R.string.disable_tor_to_make_call, Toast.LENGTH_SHORT).show();
            return;
        }
        if (activity.mUseI2P || conversation.getAccount().isI2P()) {
            Toast.makeText(activity, R.string.no_i2p_calls, Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions =
                    Arrays.asList(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions = Collections.singletonList(Manifest.permission.RECORD_AUDIO);
        }
        if (hasPermissions(REQUEST_START_AUDIO_CALL, permissions)) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
        }
    }

    private void checkPermissionAndTriggerVideoCall() {
        if (activity.mUseTor || conversation.getAccount().isOnion()) {
            Toast.makeText(activity, R.string.disable_tor_to_make_call, Toast.LENGTH_SHORT).show();
            return;
        }
        if (activity.mUseI2P || conversation.getAccount().isI2P()) {
            Toast.makeText(activity, R.string.no_i2p_calls, Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions =
                    Arrays.asList(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.CAMERA,
                            Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions =
                    Arrays.asList(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA);
        }
        if (hasPermissions(REQUEST_START_VIDEO_CALL, permissions)) {
            triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
        }
    }

    private void triggerRtpSession(final String action) {
        if (activity.xmppConnectionService.getJingleConnectionManager().isBusy()) {
            Toast.makeText(activity, R.string.only_one_call_at_a_time, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        final Account account = conversation.getAccount();
        if (account.setOption(Account.OPTION_SOFT_DISABLED, false)) {
            activity.xmppConnectionService.updateAccount(account);
        }
        final Contact contact = conversation.getContact();
        if (Config.USE_JINGLE_MESSAGE_INIT && RtpCapability.jmiSupport(contact)) {
            triggerRtpSession(contact.getAccount(), contact.getJid().asBareJid(), action);
        } else {
            final RtpCapability.Capability capability;
            if (action.equals(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL)) {
                capability = RtpCapability.Capability.VIDEO;
            } else {
                capability = RtpCapability.Capability.AUDIO;
            }
            PresenceSelector.selectFullJidForDirectRtpConnection(
                    activity,
                    contact,
                    capability,
                    fullJid -> {
                        triggerRtpSession(contact.getAccount(), fullJid, action);
                    });
        }
    }

    private void triggerRtpSession(final Account account, final Jid with, final String action) {
        CallIntegrationConnectionService.placeCall(
                activity.xmppConnectionService,
                account,
                with,
                RtpSessionActivity.actionToMedia(action));
    }

    private void handleAttachmentSelection(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.attach_choose_picture:
                attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
                break;
                /*
            case R.id.attach_take_picture:
                attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
                break;

                 */
            case R.id.attach_record_video:
                attachFile(ATTACHMENT_CHOICE_RECORD_VIDEO);
                break;
            case R.id.attach_choose_file:
                attachFile(ATTACHMENT_CHOICE_CHOOSE_FILE);
                break;
                /*
            case R.id.attach_record_voice:
                attachFile(ATTACHMENT_CHOICE_RECORD_VOICE);
                break;

                 */
            case R.id.attach_location:
                attachFile(ATTACHMENT_CHOICE_LOCATION);
                break;
        }
    }

    private void handleEncryptionSelection(MenuItem item) {
        if (conversation == null) {
            return;
        }
        final boolean updated;
        switch (item.getItemId()) {
            case R.id.encryption_choice_none:
                updated = conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                item.setChecked(true);
                break;
            case R.id.encryption_choice_pgp:
                if (activity.hasPgp()) {
                    if (conversation.getAccount().getPgpSignature() != null) {
                        updated = conversation.setNextEncryption(Message.ENCRYPTION_PGP);
                        item.setChecked(true);
                    } else {
                        updated = false;
                        activity.announcePgp(
                                conversation.getAccount(),
                                conversation,
                                null,
                                activity.onOpenPGPKeyPublished);
                    }
                } else {
                    activity.showInstallPgpDialog();
                    updated = false;
                }
                break;
            case R.id.encryption_choice_axolotl:
                Log.d(
                        Config.LOGTAG,
                        AxolotlService.getLogprefix(conversation.getAccount())
                                + "Enabled axolotl for Contact "
                                + conversation.getContact().getJid());
                updated = conversation.setNextEncryption(Message.ENCRYPTION_AXOLOTL);
                item.setChecked(true);
                break;
            case R.id.encryption_choice_otr:
                updated = conversation.setNextEncryption(Message.ENCRYPTION_OTR);
                item.setChecked(true);
                break;
            default:
                updated = conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                break;
        }
        if (updated) {
            activity.xmppConnectionService.updateConversation(conversation);
        }
        updateChatMsgHint();
        toggleInputMethod();
        activity.invalidateOptionsMenu();
        activity.refreshUi();
    }

    public void attachFile(final int attachmentChoice) {
        attachFile(attachmentChoice, true, false);
    }

    public void attachFile(final int attachmentChoice, final boolean updateRecentlyUsed) {
        attachFile(attachmentChoice, updateRecentlyUsed, false);
    }

    public void attachFile(final int attachmentChoice, final boolean updateRecentlyUsed, final boolean fromPermissions) {
        if (attachmentChoice == ATTACHMENT_CHOICE_RECORD_VOICE) {
            if (!hasPermissions(
                    attachmentChoice,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO)) {
                return;
            }
        } else if (attachmentChoice == ATTACHMENT_CHOICE_TAKE_PHOTO
                || attachmentChoice == ATTACHMENT_CHOICE_RECORD_VIDEO
                || (attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_IMAGE && !fromPermissions)) {
            if (!hasPermissions(
                    attachmentChoice,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA)) {
                return;
            }
        } else if (attachmentChoice != ATTACHMENT_CHOICE_LOCATION) {
            if (!hasPermissions(attachmentChoice, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                return;
            }
        }
        if (updateRecentlyUsed) {
            storeRecentlyUsedQuickAction(attachmentChoice);
        }
        final int encryption = conversation.getNextEncryption();
        final int mode = conversation.getMode();
        if (encryption == Message.ENCRYPTION_PGP) {
            if (activity.hasPgp()) {
                if (mode == Conversation.MODE_SINGLE
                        && conversation.getContact().getPgpKeyId() != 0) {
                    activity.xmppConnectionService
                            .getPgpEngine()
                            .hasKey(
                                    conversation.getContact(),
                                    new UiCallback<Contact>() {

                                        @Override
                                        public void userInputRequired(
                                                PendingIntent pi, Contact contact) {
                                            startPendingIntent(pi, attachmentChoice);
                                        }

                                        @Override
                                        public void success(Contact contact) {
                                            invokeAttachFileIntent(attachmentChoice);
                                        }

                                        @Override
                                        public void error(int error, Contact contact) {
                                            activity.replaceToast(getString(error));
                                        }
                                    });
                } else if (mode == Conversation.MODE_MULTI
                        && conversation.getMucOptions().pgpKeysInUse()) {
                    if (!conversation.getMucOptions().everybodyHasKeys()) {
                        Toast warning =
                                Toast.makeText(
                                        activity,
                                        R.string.missing_public_keys,
                                        Toast.LENGTH_LONG);
                        warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                        warning.show();
                    }
                    invokeAttachFileIntent(attachmentChoice);
                } else {
                    showNoPGPKeyDialog(
                            false,
                            (dialog, which) -> {
                                conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                                activity.xmppConnectionService.updateConversation(conversation);
                                invokeAttachFileIntent(attachmentChoice);
                            });
                }
            } else {
                activity.showInstallPgpDialog();
            }
        } else {
            invokeAttachFileIntent(attachmentChoice);
        }
    }

    private void storeRecentlyUsedQuickAction(final int attachmentChoice) {
        try {
            activity.getPreferences()
                    .edit()
                    .putString(
                            RECENTLY_USED_QUICK_ACTION,
                            SendButtonAction.of(attachmentChoice).toString())
                    .apply();
        } catch (IllegalArgumentException e) {
            // just do not save
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        final PermissionUtils.PermissionResult permissionResult =
                PermissionUtils.removeBluetoothConnect(permissions, grantResults);
        if (grantResults.length > 0) {
            if (allGranted(permissionResult.grantResults) || requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
                switch (requestCode) {
                    case REQUEST_START_DOWNLOAD:
                        if (this.mPendingDownloadableMessage != null) {
                            startDownloadable(this.mPendingDownloadableMessage);
                        }
                        break;
                    case REQUEST_ADD_EDITOR_CONTENT:
                        if (this.mPendingEditorContent != null) {
                            attachEditorContentToConversation(this.mPendingEditorContent);
                        }
                        break;
                    case REQUEST_COMMIT_ATTACHMENTS:
                        commitAttachments();
                        break;
                    case REQUEST_START_AUDIO_CALL:
                        triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
                        break;
                    case REQUEST_START_VIDEO_CALL:
                        triggerRtpSession(RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
                        break;
                    default:
                        attachFile(requestCode, true, true);
                        break;
                }
            } else {
                @StringRes int res;
                String firstDenied =
                        getFirstDenied(permissionResult.grantResults, permissionResult.permissions);
                if (Manifest.permission.RECORD_AUDIO.equals(firstDenied)) {
                    res = R.string.no_microphone_permission;
                } else if (Manifest.permission.CAMERA.equals(firstDenied)) {
                    res = R.string.no_camera_permission;
                } else {
                    res = R.string.no_storage_permission;
                }
                Toast.makeText(
                                activity,
                                getString(res, getString(R.string.app_name)),
                                Toast.LENGTH_SHORT)
                        .show();
            }
            ChatBackgroundHelper.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
        }
        if (writeGranted(grantResults, permissions)) {
            if (activity != null && activity.xmppConnectionService != null) {
                activity.xmppConnectionService.getDrawableCache().evictAll();
                activity.xmppConnectionService.restartFileObserver();
            }
            refresh();
        }
        if (cameraGranted(grantResults, permissions) || audioGranted(grantResults, permissions)) {
            XmppConnectionService.toggleForegroundService(activity);
        }
    }

    private void updateChatBG() {
        if (activity != null && conversation != null) {
            if (activity.unicoloredBG()) {
                binding.conversationsFragment.setBackgroundResource(0);
            } else {
                if (activity != null && conversation != null && conversation.getUuid() != null) {
                    Uri uri = ChatBackgroundHelper.getBgUri(activity, conversation.getUuid());
                    if (uri != null) {
                        binding.backgroundImage.setImageURI(uri);
                        binding.backgroundImage.setVisibility(View.VISIBLE);
                    } else {
                        binding.backgroundImage.setVisibility(View.GONE);
                        binding.conversationsFragment.setBackground(ContextCompat.getDrawable(activity, R.drawable.chatbg));
                    }
                } else {
                    binding.conversationsFragment.setBackground(ContextCompat.getDrawable(activity, R.drawable.chatbg));
                }
            }
        }
    }

    public void startDownloadable(Message message) {
        if (!hasPermissions(REQUEST_START_DOWNLOAD, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            this.mPendingDownloadableMessage = message;
            return;
        }
        Transferable transferable = message.getTransferable();
        if (transferable != null) {
            if (transferable instanceof TransferablePlaceholder && message.hasFileOnRemoteHost()) {
                createNewConnection(message);
                return;
            }
            if (!transferable.start()) {
                Log.d(Config.LOGTAG, "type: " + transferable.getClass().getName());
                Toast.makeText(activity, R.string.not_connected_try_again, Toast.LENGTH_SHORT)
                        .show();
            }
        } else if (message.treatAsDownloadable()
                || message.hasFileOnRemoteHost()
                || MessageUtils.unInitiatedButKnownSize(message)) {
            createNewConnection(message);
        } else {
            message.setDeleted(true);
            Log.d(
                    Config.LOGTAG,
                    message.getConversation().getAccount() + ": unable to start downloadable");
        }
    }

    private void createNewConnection(final Message message) {
        if (!activity.xmppConnectionService.hasInternetConnection()) {
            Toast.makeText(activity, R.string.not_connected_try_again, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        if (message.getOob() != null && "cid".equalsIgnoreCase(message.getOob().getScheme())) {
            try {
                BobTransfer transfer = new BobTransfer.ForMessage(message, activity.xmppConnectionService);
                message.setTransferable(transfer);
                transfer.start();
            } catch (URISyntaxException e) {
                Log.d(Config.LOGTAG, "BobTransfer failed to parse URI");
            }
        } else {
            activity.xmppConnectionService
                    .getHttpConnectionManager()
                    .createNewDownloadConnection(message, true);
        }
    }

    @SuppressLint("InflateParams")
    protected void clearHistoryDialog(final Conversation conversation) {
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setTitle(R.string.clear_conversation_history);
        final View dialogView =
                requireActivity().getLayoutInflater().inflate(R.layout.dialog_clear_history, null);
        final CheckBox endConversationCheckBox =
                dialogView.findViewById(R.id.end_conversation_checkbox);
        builder.setView(dialogView);
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(
                getString(R.string.confirm),
                (dialog, which) -> {
                    this.activity.xmppConnectionService.clearConversationHistory(conversation);
                    if (endConversationCheckBox.isChecked()) {
                        this.activity.xmppConnectionService.archiveConversation(conversation);
                        this.activity.onConversationArchived(conversation);
                    } else {
                        activity.onConversationsListItemUpdated();
                        refresh();
                    }
                });
        builder.create().show();
    }

    protected void muteConversationDialog(final Conversation conversation) {
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setTitle(R.string.disable_notifications);
        final int[] durations = activity.getResources().getIntArray(R.array.mute_options_durations);
        final CharSequence[] labels = new CharSequence[durations.length];
        for (int i = 0; i < durations.length; ++i) {
            if (durations[i] == -1) {
                labels[i] = activity.getString(R.string.until_further_notice);
            } else {
                labels[i] = TimeFrameUtils.resolve(activity, 1000L * durations[i]);
            }
        }
        builder.setItems(
                labels,
                (dialog, which) -> {
                    final long till;
                    if (durations[which] == -1) {
                        till = Long.MAX_VALUE;
                    } else {
                        till = System.currentTimeMillis() + (durations[which] * 1000L);
                    }
                    conversation.setMutedTill(till);
                    activity.xmppConnectionService.updateConversation(conversation);
                    activity.onConversationsListItemUpdated();
                    refresh();
                    activity.invalidateOptionsMenu();
                });
        builder.create().show();
    }

    private boolean hasPermissions(int requestCode, List<String> permissions) {
        final List<String> missingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    || Config.ONLY_INTERNAL_STORAGE)
                    && permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                continue;
            }
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (missingPermissions.size() == 0) {
            return true;
        } else {
            requestPermissions(missingPermissions.toArray(new String[0]), requestCode);
            return false;
        }
    }

    private boolean hasPermissions(int requestCode, String... permissions) {
        return hasPermissions(requestCode, ImmutableList.copyOf(permissions));
    }

    public void unMuteConversation(final Conversation conversation) {
        conversation.setMutedTill(0);
        this.activity.xmppConnectionService.updateConversation(conversation);
        this.activity.onConversationsListItemUpdated();
        refresh();
        this.activity.invalidateOptionsMenu();
    }

    protected void invokeAttachFileIntent(final int attachmentChoice) {
        Intent intent = new Intent();

        final var takePhotoIntent = new Intent();
        final Uri takePhotoUri = activity.xmppConnectionService.getFileBackend().getTakePhotoUri();
        pendingTakePhotoUri.push(takePhotoUri);
        takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, takePhotoUri);
        takePhotoIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        takePhotoIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        takePhotoIntent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);

        final var takeVideoIntent = new Intent();
        takeVideoIntent.setAction(MediaStore.ACTION_VIDEO_CAPTURE);

        switch (attachmentChoice) {
            case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"});
                intent = Intent.createChooser(intent, getString(R.string.perform_action_with));
                if (activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    intent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { takePhotoIntent, takeVideoIntent });
                }
                break;
            case ATTACHMENT_CHOICE_RECORD_VIDEO:
                intent = takeVideoIntent;
                break;
            case ATTACHMENT_CHOICE_TAKE_PHOTO:
                intent = takePhotoIntent;
                break;
            case ATTACHMENT_CHOICE_CHOOSE_FILE:
                intent.setAction(Intent.ACTION_GET_CONTENT);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent = Intent.createChooser(intent, getString(R.string.perform_action_with));
                break;
            case ATTACHMENT_CHOICE_RECORD_VOICE:
                backPressedLeaveVoiceRecorder.setEnabled(true);
                recordVoice();
                return;
            case ATTACHMENT_CHOICE_LOCATION:
                intent = GeoHelper.getFetchIntent(activity);
                break;
        }
        final Context context = activity;
        if (context == null) {
            return;
        }
        try {
            startActivityForResult(intent, attachmentChoice);
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_application_found, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.messagesView.post(this::fireReadEvent);
        updateChatBG();
        binding.textinput.requestFocus();
    }

    private void fireReadEvent() {
        if (activity != null && this.conversation != null) {
            String uuid = getLastVisibleMessageUuid();
            if (uuid != null) {
                activity.onConversationRead(this.conversation, uuid);
            }
        }
    }

    private void newSubThread() {
        Element oldThread = conversation.getThread();
        Element thread = new Element("thread", "jabber:client");
        thread.setContent(UUID.randomUUID().toString());
        if (oldThread != null) thread.setAttribute("parent", oldThread.getContent());
        setThread(thread);
    }

    private void newThread() {
        Element thread = new Element("thread", "jabber:client");
        thread.setContent(UUID.randomUUID().toString());
        if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
            setThread(thread);
        }
    }

    private void updateThreadFromLastMessage() {
        if (this.conversation != null && !this.conversation.getUserSelectedThread() && TextUtils.isEmpty(binding.textinput.getText())) {
            Message message = getLastVisibleMessage();
            if (message == null) {
                if (activity != null && activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
                    newThread();
                }
            } else {
                if (conversation.getMode() == Conversation.MODE_MULTI) {
                    if (activity == null || activity.xmppConnectionService == null) return;
                    if (message.getStatus() < Message.STATUS_SEND) {
                        if (activity != null && activity.xmppConnectionService != null && !activity.xmppConnectionService.getBooleanPreference("follow_thread_in_channel", R.bool.follow_thread_in_channel))
                            return;
                    }
                }
                if (activity != null && activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
                    setThread(message.getThread());
                }
            }
        }
    }

    private String getLastVisibleMessageUuid() {
        Message message =  getLastVisibleMessage();
        return message == null ? null : message.getUuid();
    }

    private Message getLastVisibleMessage() {
        if (binding == null) {
            return null;
        }
        synchronized (this.messageList) {
            int pos = binding.messagesView.getLastVisiblePosition();
            if (pos >= 0) {
                Message message = null;
                for (int i = pos; i >= 0; --i) {
                    try {
                        message = (Message) binding.messagesView.getItemAtPosition(i);
                    } catch (IndexOutOfBoundsException e) {
                        // should not happen if we synchronize properly. however if that fails we
                        // just gonna try item -1
                        continue;
                    }
                    if (message.getType() != Message.TYPE_STATUS) {
                        break;
                    }
                }
                if (message != null) {
                    return message;
                }
            }
        }
        return null;
    }

    private void openWith(final Message message) {
        if (message.isGeoUri()) {
            GeoHelper.view(activity, message);
        } else {
            final DownloadableFile file =
                    activity.xmppConnectionService.getFileBackend().getFile(message);
            final var fp = message.getFileParams();
            final var name = fp == null ? null : fp.getName();
            final var displayName = name == null ? file.getName() : name;
            ViewUtil.view(activity, file, displayName);
        }
    }

    private void reportMessage(final Message message) {
        BlockContactDialog.show(activity, conversation.getContact(), message.getServerMsgId());
    }

    private void showErrorMessage(final Message message) {
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setTitle(R.string.error_message);
        final String errorMessage = message.getErrorMessage();
        final String[] errorMessageParts =
                errorMessage == null ? new String[0] : errorMessage.split("\\u001f");
        final String displayError;
        if (errorMessageParts.length == 2) {
            displayError = errorMessageParts[1];
        } else {
            displayError = errorMessage;
        }
        builder.setMessage(displayError);
        builder.setNegativeButton(
                R.string.copy_to_clipboard,
                (dialog, which) -> {
                    activity.copyTextToClipboard(displayError, R.string.error_message);
                    Toast.makeText(
                                    activity,
                                    R.string.error_message_copied_to_clipboard,
                                    Toast.LENGTH_SHORT)
                            .show();
                });
        builder.setPositiveButton(R.string.confirm, null);
        builder.create().show();
    }

    public boolean onInlineImageLongClicked(Cid cid) {
        DownloadableFile f = activity.xmppConnectionService.getFileForCid(cid);
        if (f == null) return false;

        saveAsSticker(f, null);
        return true;
    }

    private void saveAsSticker(final Message m) {
        String existingName = m.getFileParams() != null && m.getFileParams().getName() != null ? m.getFileParams().getName() : "";
        existingName = existingName.lastIndexOf(".") == -1 ? existingName : existingName.substring(0, existingName.lastIndexOf("."));
        saveAsSticker(activity.xmppConnectionService.getFileBackend().getFile(m), existingName);
    }

    private void saveAsSticker(final File file, final String name) {
        final DocumentFile df = DocumentFile.fromFile(new File(dirStickers, file.getName()));
        try {
            activity.xmppConnectionService.getFileBackend().copyFileToDocumentFile(activity, file, df);
            Toast.makeText(activity, "Sticker saved", Toast.LENGTH_SHORT).show();
            LoadStickers();
        } catch (final FileBackend.FileCopyException e) {
            Toast.makeText(activity, e.getResId(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile(final Message message) {
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setNegativeButton(R.string.cancel, null);
        builder.setTitle(R.string.delete_file_dialog);
        builder.setMessage(R.string.delete_file_dialog_msg);
        builder.setPositiveButton(
                R.string.confirm,
                (dialog, which) -> {
                    List<Element> thumbs = selectedMessage.getFileParams() != null ? selectedMessage.getFileParams().getThumbnails() : null;
                    if (thumbs != null && !thumbs.isEmpty()) {
                        for (Element thumb : thumbs) {
                            Uri uri = Uri.parse(thumb.getAttribute("uri"));
                            if (uri.getScheme().equals("cid")) {
                                Cid cid = BobTransfer.cid(uri);
                                if (cid == null) continue;
                                DownloadableFile f = activity.xmppConnectionService.getFileForCid(cid);
                                activity.xmppConnectionService.evictPreview(f);
                                f.delete();
                            }
                        }
                    }
                    if (activity.xmppConnectionService.getFileBackend().deleteFile(message)) {
                        activity.xmppConnectionService.evictPreview(activity.xmppConnectionService.getFileBackend().getFile(message));
                        activity.xmppConnectionService.updateMessage(message, false);
                        activity.onConversationsListItemUpdated();
                        refresh();
                    }
                });
        builder.create().show();
    }

    private void saveToDownloads(final Message message) {
        activity.xmppConnectionService.copyAttachmentToDownloadsFolder(message, new UiCallback<>() {
            @Override
            public void success(Integer object) {
                runOnUiThread(() -> Toast.makeText(activity, R.string.save_to_downloads_success, Toast.LENGTH_LONG).show());
            }

            @Override
            public void error(int errorCode, Integer object) {
                runOnUiThread(() -> Toast.makeText(activity, object, Toast.LENGTH_LONG).show());
            }

            @Override
            public void userInputRequired(PendingIntent pi, Integer object) {
            }
        });
    }

    private void resendMessage(final Message message, final boolean forceP2P) {
        if (message.isFileOrImage()) {
            if (!(message.getConversation() instanceof Conversation conversation)) {
                return;
            }
            final DownloadableFile file =
                    activity.xmppConnectionService.getFileBackend().getFile(message);
            if ((file.exists() && file.canRead()) || message.hasFileOnRemoteHost()) {
                final XmppConnection xmppConnection = conversation.getAccount().getXmppConnection();
                if (!message.hasFileOnRemoteHost()
                        && xmppConnection != null
                        && conversation.getMode() == Conversational.MODE_SINGLE
                        && (!xmppConnection
                        .getFeatures()
                        .httpUpload(message.getFileParams().getSize())
                        || forceP2P)) {
                    activity.selectPresence(
                            conversation,
                            () -> {
                                message.setCounterpart(conversation.getNextCounterpart());
                                activity.xmppConnectionService.resendFailedMessages(
                                        message, forceP2P);
                                new Handler()
                                        .post(
                                                () -> {
                                                    int size = messageList.size();
                                                    this.binding.messagesView.setSelection(
                                                            size - 1);
                                                });
                            });
                    return;
                }
            } else if (!Compatibility.hasStoragePermission(activity)) {
                Toast.makeText(activity, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
                return;
            } else {
                Toast.makeText(activity, R.string.file_deleted, Toast.LENGTH_SHORT).show();
                message.setDeleted(true);
                activity.xmppConnectionService.updateMessage(message, false);
                activity.onConversationsListItemUpdated();
                refresh();
                return;
            }
        }
        activity.xmppConnectionService.resendFailedMessages(message, false);
        new Handler()
                .post(
                        () -> {
                            int size = messageList.size();
                            this.binding.messagesView.setSelection(size - 1);
                        });
    }

    private void cancelTransmission(Message message) {
        Transferable transferable = message.getTransferable();
        if (transferable != null) {
            transferable.cancel();
        } else if (message.getStatus() != Message.STATUS_RECEIVED) {
            activity.xmppConnectionService.markMessage(
                    message, Message.STATUS_SEND_FAILED, Message.ERROR_MESSAGE_CANCELLED);
        }
    }

    private void retryDecryption(Message message) {
        message.setEncryption(Message.ENCRYPTION_PGP);
        activity.onConversationsListItemUpdated();
        refresh();
        conversation.getAccount().getPgpDecryptionService().decrypt(message, false);
    }

    public void privateMessageWith(final Jid counterpart) {
        if (conversation.setOutgoingChatState(Config.DEFAULT_CHAT_STATE)) {
            activity.xmppConnectionService.sendChatState(conversation);
        }
        this.binding.textinput.setText("");
        this.conversation.setNextCounterpart(counterpart);
        updateChatMsgHint();
        updateSendButton();
        updateEditablity();
    }

    private void correctMessage(final Message message) {
        if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
            setThread(message.getThread());
        }
        conversation.setUserSelectedThread(true);
        this.conversation.setCorrectingMessage(message);
        final Editable editable = binding.textinput.getText();
        this.conversation.setDraftMessage(editable.toString());
        this.binding.textinput.setText("");
        this.binding.textinput.append(message.getBody(true));
        if (message.getSubject() != null && message.getSubject().length() > 0) {
            this.binding.textinputSubject.setText(message.getSubject());
            this.binding.textinputSubject.setVisibility(View.VISIBLE);
        }
        setupReply(message.getInReplyTo());
    }

    private void highlightInConference(String nick) {
        final Editable editable = this.binding.textinput.getText();
        String oldString = editable.toString().trim();
        final int pos = this.binding.textinput.getSelectionStart();
        if (oldString.isEmpty() || pos == 0) {
            editable.insert(0, nick + ": ");
        } else {
            final char before = editable.charAt(pos - 1);
            final char after = editable.length() > pos ? editable.charAt(pos) : '\0';
            if (before == '\n') {
                editable.insert(pos, nick + ": ");
            } else {
                if (pos > 2 && editable.subSequence(pos - 2, pos).toString().equals(": ")) {
                    if (NickValidityChecker.check(
                            conversation,
                            Arrays.asList(
                                    editable.subSequence(0, pos - 2).toString().split(", ")))) {
                        editable.insert(pos - 2, ", " + nick);
                        return;
                    }
                }
                editable.insert(
                        pos,
                        (Character.isWhitespace(before) ? "" : " ")
                                + nick
                                + (Character.isWhitespace(after) ? "" : " "));
                if (Character.isWhitespace(after)) {
                    this.binding.textinput.setSelection(
                            this.binding.textinput.getSelectionStart() + 1);
                }
            }
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        final Activity activity = getActivity();
        if (activity instanceof ConversationsActivity) {
            ((ConversationsActivity) activity).clearPendingViewIntent();
        }
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Store the pinned message in the bundle for configuration changes
        if (currentDisplayedPinnedMessageUuid != null) {
            outState.putString("current_displayed_pinned_uuid", currentDisplayedPinnedMessageUuid);
        }
        if (conversation != null) {
            outState.putString(STATE_CONVERSATION_UUID, conversation.getUuid());
            outState.putString(STATE_LAST_MESSAGE_UUID, lastMessageUuid);
            final Uri uri = pendingTakePhotoUri.peek();
            if (uri != null) {
                outState.putString(STATE_PHOTO_URI, uri.toString());
            }
            final ScrollState scrollState = getScrollPosition();
            if (scrollState != null) {
                outState.putParcelable(STATE_SCROLL_POSITION, scrollState);
            }
            final ArrayList<Attachment> attachments =
                    mediaPreviewAdapter == null
                            ? new ArrayList<>()
                            : mediaPreviewAdapter.getAttachments();
            if (attachments.size() > 0) {
                outState.putParcelableArrayList(STATE_MEDIA_PREVIEWS, attachments);
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }
        String uuid = savedInstanceState.getString(STATE_CONVERSATION_UUID);
        ArrayList<Attachment> attachments =
                savedInstanceState.getParcelableArrayList(STATE_MEDIA_PREVIEWS);
        pendingLastMessageUuid.push(savedInstanceState.getString(STATE_LAST_MESSAGE_UUID, null));
        if (uuid != null) {
            QuickLoader.set(uuid);
            this.pendingConversationsUuid.push(uuid);
            if (attachments != null && attachments.size() > 0) {
                this.pendingMediaPreviews.push(attachments);
            }
            String takePhotoUri = savedInstanceState.getString(STATE_PHOTO_URI);
            if (takePhotoUri != null) {
                pendingTakePhotoUri.push(Uri.parse(takePhotoUri));
            }
            pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (this.reInitRequiredOnStart && this.conversation != null) {
            final Bundle extras = pendingExtras.pop();
            reInit(this.conversation, extras != null, extras != null && extras.getString(ConversationsActivity.EXTRA_MESSAGE_UUID) != null);
            if (extras != null) {
                processExtras(extras);
            }
        } else if (conversation == null
                && activity != null
                && activity.xmppConnectionService != null) {
            final String uuid = pendingConversationsUuid.pop();
            Log.d(
                    Config.LOGTAG,
                    "ConversationFragment.onStart() - activity was bound but no conversation"
                            + " loaded. uuid="
                            + uuid);
            if (uuid != null) {
                findAndReInitByUuidOrArchive(uuid);
            }
        }
        updateChatBG();
    }

    @Override
    public void onStop() {
        super.onStop();
        final Activity activity = getActivity();
        messageListAdapter.unregisterListenerInAudioPlayer();
        if (activity == null || !activity.isChangingConfigurations()) {
            hideSoftKeyboard(activity);
            messageListAdapter.stopAudioPlayer();
        }
        if (this.conversation != null) {
            final String msg = this.binding.textinput.getText().toString();
            storeNextMessage(msg);
            updateChatState(this.conversation, msg);
            this.activity.xmppConnectionService.getNotificationService().setOpenConversation(null);
        }
        this.reInitRequiredOnStart = true;
    }

    private void updateChatState(final Conversation conversation, final String msg) {
        ChatState state = msg.length() == 0 ? Config.DEFAULT_CHAT_STATE : ChatState.PAUSED;
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(state)) {
            activity.xmppConnectionService.sendChatState(conversation);
        }
    }

    private void saveMessageDraftStopAudioPlayer() {
        final Conversation previousConversation = this.conversation;
        if (this.activity == null || this.binding == null || previousConversation == null) {
            return;
        }
        Log.d(Config.LOGTAG, "ConversationFragment.saveMessageDraftStopAudioPlayer()");
        final String msg = this.binding.textinput.getText().toString();
        storeNextMessage(msg);
        updateChatState(this.conversation, msg);
        messageListAdapter.stopAudioPlayer();
        mediaPreviewAdapter.clearPreviews();
        toggleInputMethod();
    }

    public void reInit(final Conversation conversation, final Bundle extras) {
        QuickLoader.set(conversation.getUuid());
        final boolean changedConversation = this.conversation != conversation;
        if (changedConversation) {
            this.saveMessageDraftStopAudioPlayer();
        }
        this.clearPending();
        if (this.reInit(conversation, extras != null, extras != null && extras.getString(ConversationsActivity.EXTRA_MESSAGE_UUID) != null)) {
            if (extras != null) {
                processExtras(extras);
            }
            this.reInitRequiredOnStart = false;
        } else {
            this.reInitRequiredOnStart = true;
            pendingExtras.push(extras);
        }
        resetUnreadMessagesCount();
    }

    private void reInit(Conversation conversation) {
        reInit(conversation, false, false);
    }

    private boolean reInit(final Conversation conversation, final boolean hasExtras, final boolean hasMessageUUID) {
        if (conversation == null) {
            return false;
        }
        final Conversation originalConversation = this.conversation;
        this.conversation = conversation;
        // once we set the conversation all is good and it will automatically do the right thing in
        // onStart()
        if (this.activity == null || this.binding == null) {
            return false;
        }

        if (!activity.xmppConnectionService.isConversationStillOpen(this.conversation)) {
            activity.onConversationArchived(this.conversation);
            return false;
        }
        updateinputfield(canSendMeCommand());
        setThread(conversation.getThread());
        setupReply(conversation.getReplyTo());

        stopScrolling();
        Log.d(Config.LOGTAG, "reInit(hasExtras=" + hasExtras + ")");

        if (this.conversation.isRead(activity == null ? null : activity.xmppConnectionService) && hasExtras) {
            Log.d(Config.LOGTAG, "trimming conversation");
            this.conversation.trim();
        }

        setupIme();

        final boolean scrolledToBottomAndNoPending =
                this.scrolledToBottom() && pendingScrollState.peek() == null;

        this.binding.textSendButton.setContentDescription(
                activity.getString(R.string.send_message_to_x, conversation.getName()));
        this.binding.textinput.setKeyboardListener(null);
        this.binding.textinputSubject.setKeyboardListener(null);
        final boolean participating =
                conversation.getMode() == Conversational.MODE_SINGLE
                        || conversation.getMucOptions().participating();
        if (participating) {
            this.binding.textinput.setText(this.conversation.getNextMessage());
            this.binding.textinput.setSelection(this.binding.textinput.length());
        } else {
            this.binding.textinput.setText(MessageUtils.EMPTY_STRING);
        }
        this.binding.textinput.setKeyboardListener(this);
        this.binding.textinputSubject.setKeyboardListener(this);
        messageListAdapter.updatePreferences();
        refresh(false);
        activity.invalidateOptionsMenu();
        this.conversation.messagesLoaded.set(true);
        Log.d(Config.LOGTAG, "scrolledToBottomAndNoPending=" + scrolledToBottomAndNoPending);

        if (!hasMessageUUID && (hasExtras || scrolledToBottomAndNoPending)) {
            resetUnreadMessagesCount();
            synchronized (this.messageList) {
                Log.d(Config.LOGTAG, "jump to first unread message");
                final Message first = conversation.getFirstUnreadMessage();
                final int bottom = Math.max(0, this.messageList.size() - 1);
                final int pos;
                final boolean jumpToBottom;
                if (first == null) {
                    pos = bottom;
                    jumpToBottom = true;
                } else {
                    int i = getIndexOf(first.getUuid(), this.messageList);
                    pos = i < 0 ? bottom : i;
                    jumpToBottom = false;
                }
                setSelection(pos, jumpToBottom);
            }
        }

        this.binding.messagesView.post(this::fireReadEvent);
        // TODO if we only do this when this fragment is running on main it won't *bing* in tablet
        // layout which might be unnecessary since we can *see* it
        activity.xmppConnectionService
                .getNotificationService()
                .setOpenConversation(this.conversation);

        if (commandAdapter != null && conversation != originalConversation) {
            commandAdapter.clear();
            conversation.setupViewPager(binding.conversationViewPager, binding.tabLayout, activity.xmppConnectionService.isOnboarding(), originalConversation);
            refreshCommands(false);
        }
        if (commandAdapter == null && conversation != null) {
            conversation.setupViewPager(binding.conversationViewPager, binding.tabLayout, activity.xmppConnectionService.isOnboarding(), null);
            commandAdapter = new CommandAdapter((XmppActivity) activity);
            binding.commandsView.setAdapter(commandAdapter);
            binding.commandsView.setOnItemClickListener((parent, view, position, id) -> {
                if (activity == null) return;

                commandAdapter.getItem(position).start(activity, ConversationFragment.this.conversation);
            });
            refreshCommands(false);
        }
        binding.commandsNote.setVisibility(activity.xmppConnectionService.isOnboarding() ? View.VISIBLE : View.GONE);
        previousClickedReply = null;
        return true;
    }

    @Override
    public void refreshForNewCaps(final Set<Jid> newCapsJids) {
        if (newCapsJids.isEmpty() || (conversation != null && newCapsJids.contains(conversation.getJid().asBareJid()))) {
            refreshCommands(true);
        }
    }

    protected void refreshCommands(boolean delayShow) {
        if (commandAdapter == null) return;

        final CommandAdapter.MucConfig mucConfig =
                conversation.getMucOptions().getSelf().getAffiliation().ranks(MucOptions.Affiliation.OWNER) ?
                        new CommandAdapter.MucConfig() :
                        null;

        Jid commandJid = conversation.getContact().resourceWhichSupport(Namespace.COMMANDS);
        if (commandJid == null && conversation.getMode() == Conversation.MODE_MULTI && conversation.getMucOptions().hasFeature(Namespace.COMMANDS)) {
            commandJid = conversation.getJid().asBareJid();
        }
        if (commandJid == null && conversation.getJid().isDomainJid()) {
            commandJid = conversation.getJid();
        }
        if (commandJid == null) {
            binding.commandsViewProgressbar.setVisibility(View.GONE);
            if (mucConfig == null) {
                conversation.hideViewPager();
            } else {
                commandAdapter.clear();
                commandAdapter.add(mucConfig);
                conversation.showViewPager();
            }
        } else {
            if (!delayShow) conversation.showViewPager();
            binding.commandsViewProgressbar.setVisibility(View.VISIBLE);
            activity.xmppConnectionService.fetchCommands(conversation.getAccount(), commandJid, (iq) -> {
                if (activity == null) return;

                activity.runOnUiThread(() -> {
                    binding.commandsViewProgressbar.setVisibility(View.GONE);
                    commandAdapter.clear();
                    if (iq.getType() == Iq.Type.RESULT) {
                        for (Element child : iq.query().getChildren()) {
                            if (!"item".equals(child.getName()) || !Namespace.DISCO_ITEMS.equals(child.getNamespace())) continue;
                            commandAdapter.add(new CommandAdapter.Command0050(child));
                        }
                    }

                    if (mucConfig != null) commandAdapter.add(mucConfig);

                    if (commandAdapter.getCount() < 1) {
                        conversation.hideViewPager();
                    } else if (delayShow) {
                        conversation.showViewPager();
                    }
                });
            });
        }
    }

    private void resetUnreadMessagesCount() {
        lastMessageUuid = null;
        hideUnreadMessagesCount();
    }

    private void hideUnreadMessagesCount() {
        if (this.binding == null) {
            return;
        }
        this.binding.scrollToBottomButton.setEnabled(false);
        this.binding.scrollToBottomButton.hide();
        previousClickedReply = null;
        this.binding.unreadCountCustomView.setVisibility(View.GONE);
    }

    private void setSelection(int pos, boolean jumpToBottom) {
        ListViewUtils.setSelection(this.binding.messagesView, pos, jumpToBottom);
        this.binding.messagesView.post(
                () -> ListViewUtils.setSelection(this.binding.messagesView, pos, jumpToBottom));
        this.binding.messagesView.post(this::fireReadEvent);
    }

    private boolean scrolledToBottom() {
        return !conversation.isInHistoryPart() && this.binding != null && scrolledToBottom(this.binding.messagesView);
    }

    private void processExtras(final Bundle extras) {
        final String downloadUuid = extras.getString(ConversationsActivity.EXTRA_DOWNLOAD_UUID);
        final String text = extras.getString(Intent.EXTRA_TEXT);
        final String nick = extras.getString(ConversationsActivity.EXTRA_NICK);
        final String node = extras.getString(ConversationsActivity.EXTRA_NODE);
        final String postInitAction =
                extras.getString(ConversationsActivity.EXTRA_POST_INIT_ACTION);
        final boolean asQuote = extras.getBoolean(ConversationsActivity.EXTRA_AS_QUOTE);
        final boolean pm = extras.getBoolean(ConversationsActivity.EXTRA_IS_PRIVATE_MESSAGE, false);
        final boolean doNotAppend =
                extras.getBoolean(ConversationsActivity.EXTRA_DO_NOT_APPEND, false);
        final String type = extras.getString(ConversationsActivity.EXTRA_TYPE);

        final String thread = extras.getString(ConversationsActivity.EXTRA_THREAD);
        if (thread != null) {
            conversation.setLockThread(true);
            backPressedLeaveSingleThread.setEnabled(true);
            setThread(new Element("thread").setContent(thread));
            refresh();
        }

        final List<Uri> uris = extractUris(extras);
        if (uris != null && uris.size() > 0) {
            if (uris.size() == 1 && "geo".equals(uris.get(0).getScheme())) {
                mediaPreviewAdapter.addMediaPreviews(
                        Attachment.of(activity, uris.get(0), Attachment.Type.LOCATION));
            } else {
                final List<Uri> cleanedUris = cleanUris(new ArrayList<>(uris));
                mediaPreviewAdapter.addMediaPreviews(
                        Attachment.of(activity, cleanedUris, type));
            }
            toggleInputMethod();
            return;
        }
        if (nick != null) {
            if (pm) {
                Jid jid = conversation.getJid();
                try {
                    Jid next = Jid.of(jid.getLocal(), jid.getDomain(), nick);
                    privateMessageWith(next);
                } catch (final IllegalArgumentException ignored) {
                    // do nothing
                }
            } else {
                final MucOptions mucOptions = conversation.getMucOptions();
                if (mucOptions.participating() || conversation.getNextCounterpart() != null) {
                    highlightInConference(nick);
                }
            }
        } else {
            if (text != null && GeoHelper.GEO_URI.matcher(text).matches()) {
                mediaPreviewAdapter.addMediaPreviews(
                        Attachment.of(activity, Uri.parse(text), Attachment.Type.LOCATION));
                toggleInputMethod();
                return;
            } else if (text != null && asQuote) {
                quoteText(text);
            } else {
                appendText(text, doNotAppend);
            }
        }
        if (ConversationsActivity.POST_ACTION_RECORD_VOICE.equals(postInitAction)) {
            attachFile(ATTACHMENT_CHOICE_RECORD_VOICE, false);
            return;
        }
        if ("call".equals(postInitAction)) {
            checkPermissionAndTriggerAudioCall();
        }
        if ("message".equals(postInitAction)) {
            binding.conversationViewPager.post(() -> {
                binding.conversationViewPager.setCurrentItem(0);
            });
        }
        if ("command".equals(postInitAction)) {
            binding.conversationViewPager.post(() -> {
                PagerAdapter adapter = binding.conversationViewPager.getAdapter();
                if (adapter != null && adapter.getCount() > 1) {
                    binding.conversationViewPager.setCurrentItem(1);
                }
                final String jid = extras.getString(ConversationsActivity.EXTRA_JID);
                Jid commandJid = null;
                if (jid != null) {
                    try {
                        commandJid = Jid.of(jid);
                    } catch (final IllegalArgumentException e) { }
                }
                if (commandJid == null || !commandJid.isFullJid()) {
                    final Jid discoJid = conversation.getContact().resourceWhichSupport(Namespace.COMMANDS);
                    if (discoJid != null) commandJid = discoJid;
                }
                if (node != null && commandJid != null && activity != null) {
                    conversation.startCommand(commandFor(commandJid, node), activity.xmppConnectionService);
                }
            });
            return;
        }
        Message message =
                downloadUuid == null ? null : conversation.findMessageWithFileAndUuid(downloadUuid);
        if ("webxdc".equals(postInitAction)) {
            if (message == null) {
                message = activity.xmppConnectionService.getMessage(conversation, downloadUuid);
            }
            if (message == null) return;

            Cid webxdcCid = message.getFileParams().getCids().get(0);
            WebxdcPage webxdc = new WebxdcPage(activity, webxdcCid, message);
            Conversation conversation = (Conversation) message.getConversation();
            if (!conversation.switchToSession("webxdc\0" + message.getUuid())) {
                conversation.startWebxdc(webxdc);
            }
        }
        if (message != null) {
            startDownloadable(message);
        }
        if (activity.xmppConnectionService.isOnboarding() && conversation.getJid().equals(Jid.of("cheogram.com"))) {
            if (!conversation.switchToSession("jabber:iq:register")) {
                conversation.startCommand(commandFor(Jid.of("cheogram.com/CHEOGRAM%jabber:iq:register"), "jabber:iq:register"), activity.xmppConnectionService);
            }
        }
        String messageUuid = extras.getString(ConversationsActivity.EXTRA_MESSAGE_UUID);
        if (messageUuid != null) {
            Runnable postSelectionRunnable = () -> highlightMessage(messageUuid);
            updateSelection(messageUuid, binding.messagesView.getHeight() / 2, postSelectionRunnable, false, false);
        }
    }

    private Element commandFor(final Jid jid, final String node) {
        if (commandAdapter != null) {
            for (int i = 0; i < commandAdapter.getCount(); i++) {
                final CommandAdapter.Command c = commandAdapter.getItem(i);
                if (!(c instanceof CommandAdapter.Command0050)) continue;

                final Element command = ((CommandAdapter.Command0050) c).el;
                final String commandNode = command.getAttribute("node");
                if (commandNode == null || !commandNode.equals(node)) continue;

                final Jid commandJid = command.getAttributeAsJid("jid");
                if (commandJid != null && !commandJid.asBareJid().equals(jid.asBareJid())) continue;

                return command;
            }
        }

        return new Element("command", Namespace.COMMANDS).setAttribute("name", node).setAttribute("node", node).setAttribute("jid", jid);
    }

    private List<Uri> extractUris(final Bundle extras) {
        final List<Uri> uris = extras.getParcelableArrayList(Intent.EXTRA_STREAM);
        if (uris != null) {
            return uris;
        }
        final Uri uri = extras.getParcelable(Intent.EXTRA_STREAM);
        if (uri != null) {
            return Collections.singletonList(uri);
        } else {
            return null;
        }
    }

    private List<Uri> cleanUris(final List<Uri> uris) {
        final Iterator<Uri> iterator = uris.iterator();
        while (iterator.hasNext()) {
            final Uri uri = iterator.next();
            if (FileBackend.dangerousFile(uri)) {
                iterator.remove();
                Toast.makeText(
                                requireActivity(),
                                R.string.security_violation_not_attaching_file,
                                Toast.LENGTH_SHORT)
                        .show();
            }
        }
        return uris;
    }

    private boolean showBlockSubmenu(View view) {
        final Jid jid = conversation.getJid();
        final int mode = conversation.getMode();
        final var contact = mode == Conversation.MODE_SINGLE ? conversation.getContact() : null;
        final boolean showReject = contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
        PopupMenu popupMenu = new PopupMenu(activity, view);
        popupMenu.inflate(R.menu.block);
        popupMenu.getMenu().findItem(R.id.block_contact).setVisible(jid.getLocal() != null);
        popupMenu.getMenu().findItem(R.id.reject).setVisible(showReject);
        popupMenu.getMenu().findItem(R.id.add_contact).setVisible(!contact.showInRoster());
        popupMenu.setOnMenuItemClickListener(
                menuItem -> {
                    Blockable blockable;
                    switch (menuItem.getItemId()) {
                        case R.id.reject:
                            activity.xmppConnectionService.stopPresenceUpdatesTo(
                                    conversation.getContact());
                            updateSnackBar(conversation);
                            return true;
                        case R.id.add_contact:
                            mAddBackClickListener.onClick(view);
                            return true;
                        case R.id.block_domain:
                            blockable =
                                    conversation
                                            .getAccount()
                                            .getRoster()
                                            .getContact(jid.getDomain());
                            break;
                        default:
                            blockable = conversation;
                    }
                    BlockContactDialog.show(activity, blockable);
                    return true;
                });
        popupMenu.show();
        return true;
    }

    private boolean showBlockMucSubmenu(View view) {
        final var jid = conversation.getJid();
        final var popupMenu = new PopupMenu(activity, view);
        popupMenu.inflate(R.menu.block_muc);
        popupMenu.getMenu().findItem(R.id.block_contact).setVisible(jid.getLocal() != null);
        popupMenu.setOnMenuItemClickListener(
                menuItem -> {
                    Blockable blockable;
                    switch (menuItem.getItemId()) {
                        case R.id.reject:
                            activity.xmppConnectionService.clearConversationHistory(conversation);
                            activity.xmppConnectionService.archiveConversation(conversation);
                            return true;
                        case R.id.add_bookmark:
                            activity.xmppConnectionService.saveConversationAsBookmark(conversation, "");
                            updateSnackBar(conversation);
                            return true;
                        case R.id.block_contact:
                            blockable =
                                    conversation
                                            .getAccount()
                                            .getRoster()
                                            .getContact(Jid.of(conversation.getAttribute("inviter")));
                            break;
                        default:
                            blockable = conversation;
                    }
                    BlockContactDialog.show(activity, blockable);
                    activity.xmppConnectionService.archiveConversation(conversation);
                    return true;
                });
        popupMenu.show();
        return true;
    }

    private void updateSnackBar(final Conversation conversation) {
        final Account account = conversation.getAccount();
        final XmppConnection connection = account.getXmppConnection();
        final int mode = conversation.getMode();
        final Contact contact = mode == Conversation.MODE_SINGLE ? conversation.getContact() : null;
        if (conversation.getStatus() == Conversation.STATUS_ARCHIVED) {
            return;
        }
        if (account.getStatus() == Account.State.DISABLED) {
            showSnackbar(
                    R.string.this_account_is_disabled,
                    R.string.enable,
                    this.mEnableAccountListener);
        } else if (account.getStatus() == Account.State.LOGGED_OUT) {
            showSnackbar(
                    R.string.this_account_is_logged_out,
                    R.string.log_in,
                    this.mEnableAccountListener);
        } else if (conversation.isBlocked()) {
            showSnackbar(R.string.contact_blocked, R.string.unblock, this.mUnblockClickListener);
        } else if (account.getStatus() == Account.State.CONNECTING) {
            showSnackbar(R.string.this_account_is_connecting, 0, null);
        } else if (account.getStatus() != Account.State.ONLINE) {
            showSnackbar(R.string.this_account_is_offline, 0, null);
        } else if (contact != null
                && !contact.showInRoster()
                && contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            showSnackbar(
                    R.string.contact_added_you,
                    R.string.options,
                    this.mBlockClickListener,
                    this.mLongPressBlockListener);
        } else if (contact != null
                && contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
            showSnackbar(
                    R.string.contact_asks_for_presence_subscription,
                    R.string.allow,
                    this.mAllowPresenceSubscription,
                    this.mLongPressBlockListener);
        } else if (mode == Conversation.MODE_MULTI
                && !conversation.getMucOptions().online()
                && account.getStatus() == Account.State.ONLINE) {
            switch (conversation.getMucOptions().getError()) {
                case NICK_IN_USE:
                    showSnackbar(R.string.nick_in_use, R.string.edit, clickToMuc);
                    break;
                case NO_RESPONSE:
                    showSnackbar(R.string.joining_conference, 0, null);
                    break;
                case SERVER_NOT_FOUND:
                    if (conversation.receivedMessagesCount() > 0) {
                        showSnackbar(R.string.remote_server_not_found, R.string.try_again, joinMuc);
                    } else {
                        showSnackbar(R.string.remote_server_not_found, R.string.leave, leaveMuc);
                    }
                    break;
                case REMOTE_SERVER_TIMEOUT:
                    if (conversation.receivedMessagesCount() > 0) {
                        showSnackbar(R.string.remote_server_timeout, R.string.try_again, joinMuc);
                    } else {
                        showSnackbar(R.string.remote_server_timeout, R.string.leave, leaveMuc);
                    }
                    break;
                case PASSWORD_REQUIRED:
                    showSnackbar(
                            R.string.conference_requires_password,
                            R.string.enter_password,
                            enterPassword);
                    break;
                case BANNED:
                    showSnackbar(R.string.conference_banned, R.string.leave, leaveMuc);
                    break;
                case MEMBERS_ONLY:
                    showSnackbar(R.string.conference_members_only, R.string.leave, leaveMuc);
                    break;
                case RESOURCE_CONSTRAINT:
                    showSnackbar(
                            R.string.conference_resource_constraint, R.string.try_again, joinMuc);
                    break;
                case KICKED:
                    showSnackbar(R.string.conference_kicked, R.string.join, joinMuc);
                    break;
                case TECHNICAL_PROBLEMS:
                    showSnackbar(
                            R.string.conference_technical_problems, R.string.try_again, joinMuc);
                    break;
                case UNKNOWN:
                    showSnackbar(R.string.conference_unknown_error, R.string.try_again, joinMuc);
                    break;
                case INVALID_NICK:
                    showSnackbar(R.string.invalid_muc_nick, R.string.edit, clickToMuc);
                case SHUTDOWN:
                    showSnackbar(R.string.conference_shutdown, R.string.try_again, joinMuc);
                    break;
                case DESTROYED:
                    showSnackbar(R.string.conference_destroyed, R.string.leave, leaveMuc);
                    break;
                case NON_ANONYMOUS:
                    showSnackbar(
                            R.string.group_chat_will_make_your_jabber_id_public,
                            R.string.join,
                            acceptJoin);
                    break;
                default:
                    hideSnackbar();
                    break;
            }
        } else if (account.hasPendingPgpIntent(conversation)) {
            showSnackbar(R.string.openpgp_messages_found, R.string.decrypt, clickToDecryptListener);
        } else if (mode == Conversation.MODE_SINGLE
                && conversation.smpRequested()) {
            showSnackbar(R.string.smp_requested, R.string.verify, this.mAnswerSmpClickListener);
        } else if (mode == Conversation.MODE_SINGLE
                && conversation.hasValidOtrSession()
                && (conversation.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED)
                && (!conversation.isOtrFingerprintVerified())) {
            showSnackbar(R.string.unknown_otr_fingerprint, R.string.verify, clickToVerify);
        } else if (connection != null
                && connection.getFeatures().blocking()
                && conversation.strangerInvited()) {
            showSnackbar(
                    R.string.received_invite_from_stranger,
                    R.string.options,
                    (v) -> showBlockMucSubmenu(v),
                    (v) -> showBlockMucSubmenu(v));
        } else if (connection != null
                && connection.getFeatures().blocking()
                && conversation.countMessages() != 0
                && !conversation.isBlocked()
                && conversation.isWithStranger()) {
            showSnackbar(
                    R.string.received_message_from_stranger,
                    R.string.options,
                    this.mBlockClickListener,
                    this.mLongPressBlockListener);
        } else {
            hideSnackbar();
        }
    }

    @Override
    public void refresh() {
        if (this.binding == null) {
            Log.d(
                    Config.LOGTAG,
                    "ConversationFragment.refresh() skipped updated because view binding was null");
            return;
        }
        updateChatBG();
        if (this.conversation != null
                && this.activity != null
                && this.activity.xmppConnectionService != null) {
            if (!activity.xmppConnectionService.isConversationStillOpen(this.conversation)) {
                activity.onConversationArchived(this.conversation);
                return;
            }
        }
        this.refresh(true);
    }

    private void refresh(boolean notifyConversationRead) {
        synchronized (this.messageList) {
            if (this.conversation != null) {
                if (messageListAdapter.hasSelection()) {
                    if (notifyConversationRead)
                        binding.messagesView.postDelayed(this::refresh, 1000L);
                } else {
                    conversation.populateWithMessages(this.messageList, activity == null ? null : activity.xmppConnectionService);
                    try {
                        updateStatusMessages();
                    } catch (IllegalStateException e) {
                        Log.e(Config.LOGTAG, "Problem updating status messages on refresh: " + e);
                    }
                    this.messageListAdapter.notifyDataSetChanged();
                }
                if (conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid) != 0) {
                    binding.unreadCountCustomView.setVisibility(View.VISIBLE);
                    binding.unreadCountCustomView.setUnreadCount(
                            conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid));
                }
                updateSnackBar(conversation);
                if (activity != null) updateChatMsgHint();
                if (notifyConversationRead && activity != null) {
                    binding.messagesView.post(this::fireReadEvent);
                }
                updateSendButton();
                updateEditablity();
                conversation.refreshSessions();

                if (activity != null && (binding.tabLayout.getVisibility() == View.GONE || binding.conversationViewPager.getCurrentItem() == 0)) {
                    activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
                }

                if (activity!= null) activity.runOnUiThread(() -> {

                    // Check pinned message presence
                    if (getConversationReliable(activity) != null) {
                        loadAndDisplayLatestPinnedMessage();
                    } else {
                        hidePinnedMessageView();
                    }

                    // Show muc subject in conferences and show status message in one-on-one chats
                    if (conversation != null && conversation.getMode() == Conversational.MODE_MULTI) {
                        String subject = conversation.getMucOptions().getSubject();
                        boolean hidden = conversation.getMucOptions().subjectHidden();

                        if (Bookmark.printableValue(subject) && !hidden) {
                            binding.mucSubjectText.setText(subject);
                            binding.mucSubjectIcon.setOnClickListener(v -> ConferenceDetailsActivity.open(activity, conversation));
                            binding.mucSubject.setOnClickListener(v -> ConferenceDetailsActivity.open(activity, conversation));
                            binding.mucSubjectHide.setOnClickListener(v -> {
                                conversation.getMucOptions().hideSubject();
                                binding.mucSubject.setVisibility(View.GONE);
                            });
                            if (activity != null && binding.mucSubjectIcon != null) {
                                binding.statusMessageIcon.setVisibility(GONE);
                                binding.mucSubjectIcon.setVisibility(VISIBLE);
                            }
                            binding.mucSubject.setVisibility(View.VISIBLE);
                        } else {
                            binding.mucSubject.setVisibility(View.GONE);
                        }
                    } else if (conversation != null && conversation.getMode() == Conversational.MODE_SINGLE && activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("pinned_status_message", R.bool.pinned_status_message)) {
                        boolean statusChange = conversation.onContactUpdatedAndCheckStatusChange(conversation.getContact());

                        if (conversation.getLastProcessedStatusText() != null && (statusChange || !conversation.statusMessageHidden())) {
                            binding.mucSubject.setVisibility(View.VISIBLE);
                            if (activity != null && binding.statusMessageIcon != null) {
                                binding.mucSubjectIcon.setVisibility(GONE);
                                binding.statusMessageIcon.setVisibility(VISIBLE);
                            }
                            binding.mucSubjectText.setText(conversation.getLastProcessedStatusText());
                            binding.statusMessageIcon.setOnClickListener(v -> activity.switchToContactDetails(conversation.getContact()));
                            binding.mucSubject.setOnClickListener(v -> activity.switchToContactDetails(conversation.getContact()));
                            binding.mucSubjectHide.setOnClickListener(v -> {
                                conversation.hideStatusMessage();
                                binding.mucSubject.setVisibility(View.GONE);
                            });
                        } else {
                            binding.mucSubject.setVisibility(View.GONE);
                        }
                    } else {
                        binding.mucSubject.setVisibility(View.GONE);
                    }

                    UserTune tune = conversation.getContact().getUserTune();
                    if (tune != null) {
                        binding.tuneSubjectText.setText(
                                getString(R.string.user_tune_listening_to, tune.title, tune.artist));
                        binding.tuneSubject.setOnClickListener(v -> activity.switchToContactDetails(conversation.getContact()));
                        binding.tuneSubjectHide.setOnClickListener(v -> {
                            binding.tuneSubject.setVisibility(View.GONE);
                            conversation.getContact().setUserTune(null);
                        });
                        binding.tuneSubject.setVisibility(View.VISIBLE);
                    } else {
                        binding.tuneSubject.setVisibility(View.GONE);
                    }
                });
            }
        }
    }

    protected void messageSent() {
        binding.textinputSubject.setText("");
        binding.textinputSubject.setVisibility(View.GONE);
        setThread(null);
        setupReply(null);
        conversation.setUserSelectedThread(false);
        mSendingPgpMessage.set(false);
        this.binding.textinput.setText("");
        if (conversation.setCorrectingMessage(null)) {
            this.binding.textinput.append(conversation.getDraftMessage());
            conversation.setDraftMessage(null);
        }
        storeNextMessage();
        updateChatMsgHint();
        if (activity == null) return;
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean prefScrollToBottom =
                p.getBoolean(
                        "scroll_to_bottom",
                        activity.getResources().getBoolean(R.bool.scroll_to_bottom));
        if (prefScrollToBottom || scrolledToBottom()) {
            new Handler()
                    .post(
                            () -> {
                                int size = messageList.size();
                                this.binding.messagesView.setSelection(size - 1);
                            });
        }
    }

    private boolean storeNextMessage() {
        return storeNextMessage(this.binding.textinput.getText().toString());
    }

    private boolean storeNextMessage(String msg) {
        final boolean participating =
                conversation.getMode() == Conversational.MODE_SINGLE
                        || conversation.getMucOptions().participating();
        if (this.conversation.getStatus() != Conversation.STATUS_ARCHIVED
                && participating
                && this.conversation.setNextMessage(msg) && activity != null) {
            activity.xmppConnectionService.updateConversation(this.conversation);
            return true;
        }
        return false;
    }

    public void doneSendingPgpMessage() {
        mSendingPgpMessage.set(false);
    }

    public long getMaxHttpUploadSize(Conversation conversation) {
        final XmppConnection connection = conversation.getAccount().getXmppConnection();
        return connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
    }

    private boolean canWrite() {
        return
                this.conversation.getMode() == Conversation.MODE_SINGLE
                        || this.conversation.getMucOptions().participating()
                        || this.conversation.getNextCounterpart() != null;
    }

    private void updateEditablity() {
        boolean canWrite = canWrite();
        this.binding.textinput.setFocusable(canWrite);
        this.binding.textinput.setFocusableInTouchMode(canWrite);
        this.binding.textSendButton.setEnabled(canWrite);
        this.binding.textSendButton.setVisibility(canWrite ? View.VISIBLE : View.GONE);
        this.binding.requestVoice.setVisibility(canWrite ? View.GONE : View.VISIBLE);
        if (!canWrite) {
            this.binding.emojiButton.setVisibility(GONE);
        }
        this.binding.textinput.setCursorVisible(canWrite);
        this.binding.textinput.setEnabled(canWrite);
    }

    public void updateSendButton() {
        boolean hasAttachments =
                mediaPreviewAdapter != null && mediaPreviewAdapter.hasAttachments();
        final Conversation c = this.conversation;
        final Presence.Status status;
        final String text =
                this.binding.textinput == null ? "" : Objects.requireNonNull(this.binding.textinput.getText()).toString();
        final SendButtonAction action;
        if (hasAttachments) {
            action = SendButtonAction.TEXT;
        } else {
            action = SendButtonTool.getAction(activity, c, text, binding.textinputSubject.getText().toString());
        }
        if (c.getAccount().getStatus() == Account.State.ONLINE) {
            if (activity != null
                    && activity.xmppConnectionService != null
                    && activity.xmppConnectionService.getMessageArchiveService().isCatchingUp(c)) {
                status = Presence.Status.OFFLINE;
            } else if (c.getMode() == Conversation.MODE_SINGLE) {
                status = c.getContact().getShownStatus();
            } else {
                status =
                        c.getMucOptions().online()
                                ? Presence.Status.ONLINE
                                : Presence.Status.OFFLINE;
            }
        } else {
            status = Presence.Status.OFFLINE;
        }
        this.binding.textSendButton.setTag(action);
        this.binding.textSendButton.setIconTint(ColorStateList.valueOf(SendButtonTool.getSendButtonColor(this.binding.textSendButton, status)));
        this.binding.mucSubjectIcon.setIconTint(ColorStateList.valueOf(SendButtonTool.getSendButtonColor(this.binding.mucSubjectIcon, status)));
        this.binding.statusMessageIcon.setIconTint(ColorStateList.valueOf(SendButtonTool.getSendButtonColor(this.binding.statusMessageIcon, status)));
        this.binding.tuneSubjectIcon.setImageTintList(ColorStateList.valueOf(SendButtonTool.getSendButtonColor(this.binding.statusMessageIcon, status)));
        // TODO send button color
        final Activity activity = getActivity();
        if (activity != null) {
            this.binding.textSendButton.setIconResource(
                    SendButtonTool.getSendButtonImageResource(action, text.length() > 0 || hasAttachments || (c.getThread() != null && binding.textinputSubject.getText().length() > 0)));
        }
        if (activity == null) return;
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
        if (canWrite() && pref != null && pref.getBoolean("show_thread_feature", getResources().getBoolean(R.bool.show_thread_feature))) {
            binding.threadIdenticonLayout.setVisibility(VISIBLE);
        } else {
            binding.threadIdenticonLayout.setVisibility(GONE);
        }
        boolean canWrite = canWrite();
        if (!binding.textinput.getText().toString().isEmpty() || !canWrite) {
            binding.recordVoiceButton.setVisibility(GONE);
            binding.takePictureButton.setVisibility(GONE);
        } else {
            binding.recordVoiceButton.setVisibility(VISIBLE);
            binding.takePictureButton.setVisibility(VISIBLE);
        }
    }

    protected void updateStatusMessages() {
        DateSeparator.addAll(this.messageList);
        if (showLoadMoreMessages(conversation)) {
            this.messageList.add(0, Message.createLoadMoreMessage(conversation));
        }
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            ChatState state = conversation.getIncomingChatState();
            if (state == ChatState.COMPOSING) {
                this.messageList.add(
                        Message.createStatusMessage(
                                conversation,
                                getString(R.string.contact_is_typing, conversation.getName())));
            } else if (state == ChatState.PAUSED) {
                this.messageList.add(
                        Message.createStatusMessage(
                                conversation,
                                getString(
                                        R.string.contact_has_stopped_typing,
                                        conversation.getName())));
            } else {
                for (int i = this.messageList.size() - 1; i >= 0; --i) {
                    final Message message = this.messageList.get(i);
                    if (message.getType() != Message.TYPE_STATUS) {
                        if (message.getStatus() == Message.STATUS_RECEIVED) {
                            return;
                        } else {
                            if (message.getStatus() == Message.STATUS_SEND_DISPLAYED) {
                                this.messageList.add(
                                        i + 1,
                                        Message.createStatusMessage(
                                                conversation,
                                                getString(
                                                        R.string.contact_has_read_up_to_this_point,
                                                        conversation.getName())));
                                return;
                            }
                        }
                    }
                }
            }
        } else {
            final MucOptions mucOptions = conversation.getMucOptions();
            final List<MucOptions.User> allUsers = mucOptions.getUsers();
            final Set<ReadByMarker> addedMarkers = new HashSet<>();
            ChatState state = ChatState.COMPOSING;
            List<MucOptions.User> users =
                    conversation.getMucOptions().getUsersWithChatState(state, 5);
            if (users.size() == 0) {
                state = ChatState.PAUSED;
                users = conversation.getMucOptions().getUsersWithChatState(state, 5);
            }
            if (mucOptions.isPrivateAndNonAnonymous()) {
                for (int i = this.messageList.size() - 1; i >= 0; --i) {
                    final Set<ReadByMarker> markersForMessage =
                            messageList.get(i).getReadByMarkers();
                    final List<MucOptions.User> shownMarkers = new ArrayList<>();
                    for (ReadByMarker marker : markersForMessage) {
                        if (!ReadByMarker.contains(marker, addedMarkers)) {
                            addedMarkers.add(
                                    marker); // may be put outside this condition. set should do
                            // dedup anyway
                            MucOptions.User user = mucOptions.findUser(marker);
                            if (user != null && !users.contains(user)) {
                                shownMarkers.add(user);
                            }
                        }
                    }
                    final ReadByMarker markerForSender = ReadByMarker.from(messageList.get(i));
                    final Message statusMessage;
                    final int size = shownMarkers.size();
                    if (size > 1) {
                        final String body;
                        if (size <= 4) {
                            body =
                                    getString(
                                            R.string.contacts_have_read_up_to_this_point,
                                            UIHelper.concatNames(shownMarkers));
                        } else if (ReadByMarker.allUsersRepresented(
                                allUsers, markersForMessage, markerForSender)) {
                            body = getString(R.string.everyone_has_read_up_to_this_point);
                        } else {
                            body =
                                    getString(
                                            R.string.contacts_and_n_more_have_read_up_to_this_point,
                                            UIHelper.concatNames(shownMarkers, 3),
                                            size - 3);
                        }
                        statusMessage = Message.createStatusMessage(conversation, body);
                        statusMessage.setCounterparts(shownMarkers);
                    } else if (size == 1) {
                        statusMessage =
                                Message.createStatusMessage(
                                        conversation,
                                        getString(
                                                R.string.contact_has_read_up_to_this_point,
                                                UIHelper.getDisplayName(shownMarkers.get(0))));
                        statusMessage.setCounterpart(shownMarkers.get(0).getFullJid());
                        statusMessage.setTrueCounterpart(shownMarkers.get(0).getRealJid());
                    } else {
                        statusMessage = null;
                    }
                    if (statusMessage != null) {
                        this.messageList.add(i + 1, statusMessage);
                    }
                    addedMarkers.add(markerForSender);
                    if (ReadByMarker.allUsersRepresented(allUsers, addedMarkers)) {
                        break;
                    }
                }
            }
            if (users.size() > 0) {
                Message statusMessage;
                if (users.size() == 1) {
                    MucOptions.User user = users.get(0);
                    int id =
                            state == ChatState.COMPOSING
                                    ? R.string.contact_is_typing
                                    : R.string.contact_has_stopped_typing;
                    statusMessage =
                            Message.createStatusMessage(
                                    conversation, getString(id, UIHelper.getDisplayName(user)));
                    statusMessage.setTrueCounterpart(user.getRealJid());
                    statusMessage.setCounterpart(user.getFullJid());
                } else {
                    int id =
                            state == ChatState.COMPOSING
                                    ? R.string.contacts_are_typing
                                    : R.string.contacts_have_stopped_typing;
                    statusMessage =
                            Message.createStatusMessage(
                                    conversation, getString(id, UIHelper.concatNames(users)));
                    statusMessage.setCounterparts(users);
                }
                this.messageList.add(statusMessage);
            }
        }
    }

    private void stopScrolling() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        binding.messagesView.dispatchTouchEvent(cancel);
    }

    private boolean showLoadMoreMessages(final Conversation c) {
        if (activity == null || activity.xmppConnectionService == null) {
            return false;
        }
        final boolean mam = hasMamSupport(c) && !c.getContact().isBlocked();
        final MessageArchiveService service =
                activity.xmppConnectionService.getMessageArchiveService();
        return mam
                && (c.getLastClearHistory().getTimestamp() != 0
                || (c.countMessages() == 0
                && c.messagesLoaded.get()
                && c.hasMessagesLeftOnServer()
                && !service.queryInProgress(c)));
    }

    private boolean hasMamSupport(final Conversation c) {
        if (c.getMode() == Conversation.MODE_SINGLE) {
            final XmppConnection connection = c.getAccount().getXmppConnection();
            return connection != null && connection.getFeatures().mam();
        } else {
            return c.getMucOptions().mamSupport();
        }
    }

    protected void showSnackbar(
            final int message, final int action, final OnClickListener clickListener) {
        showSnackbar(message, action, clickListener, null);
    }

    protected void showSnackbar(
            final int message,
            final int action,
            final OnClickListener clickListener,
            final View.OnLongClickListener longClickListener) {
        this.binding.snackbar.setVisibility(View.VISIBLE);
        this.binding.snackbar.setOnClickListener(null);
        this.binding.snackbarMessage.setText(message);
        this.binding.snackbarMessage.setOnClickListener(null);
        this.binding.snackbarAction.setVisibility(clickListener == null ? View.GONE : View.VISIBLE);
        if (action != 0) {
            this.binding.snackbarAction.setText(action);
        }
        this.binding.snackbarAction.setOnClickListener(clickListener);
        this.binding.snackbarAction.setOnLongClickListener(longClickListener);
    }

    protected void hideSnackbar() {
        this.binding.snackbar.setVisibility(View.GONE);
    }

    protected void sendMessage(Message message) {
        new Thread(() -> activity.xmppConnectionService.sendMessage(message)).start();
        messageSent();
    }

    protected void sendOtrMessage(final Message message) {
        final ConversationsActivity activity = (ConversationsActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        activity.selectPresence(conversation,
                () -> {
                    message.setCounterpart(conversation.getNextCounterpart());
                    xmppService.sendMessage(message);
                    messageSent();
                });
    }

    protected void sendPgpMessage(final Message message) {
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (!activity.hasPgp()) {
            activity.showInstallPgpDialog();
            return;
        }
        if (conversation.getAccount().getPgpSignature() == null) {
            activity.announcePgp(
                    conversation.getAccount(), conversation, null, activity.onOpenPGPKeyPublished);
            return;
        }
        if (!mSendingPgpMessage.compareAndSet(false, true)) {
            Log.d(Config.LOGTAG, "sending pgp message already in progress");
        }
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (contact.getPgpKeyId() != 0) {
                xmppService
                        .getPgpEngine()
                        .hasKey(
                                contact,
                                new UiCallback<Contact>() {

                                    @Override
                                    public void userInputRequired(
                                            PendingIntent pi, Contact contact) {
                                        startPendingIntent(pi, REQUEST_ENCRYPT_MESSAGE);
                                    }

                                    @Override
                                    public void success(Contact contact) {
                                        encryptTextMessage(message);
                                    }

                                    @Override
                                    public void error(int error, Contact contact) {
                                        activity.runOnUiThread(
                                                () ->
                                                        Toast.makeText(
                                                                        activity,
                                                                        R.string
                                                                                .unable_to_connect_to_keychain,
                                                                        Toast.LENGTH_SHORT)
                                                                .show());
                                        mSendingPgpMessage.set(false);
                                    }
                                });

            } else {
                showNoPGPKeyDialog(
                        false,
                        (dialog, which) -> {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            xmppService.updateConversation(conversation);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.sendMessage(message);
                            messageSent();
                        });
            }
        } else {
            if (conversation.getMucOptions().pgpKeysInUse()) {
                if (!conversation.getMucOptions().everybodyHasKeys()) {
                    Toast warning =
                            Toast.makeText(
                                    activity, R.string.missing_public_keys, Toast.LENGTH_LONG);
                    warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                    warning.show();
                }
                encryptTextMessage(message);
            } else {
                showNoPGPKeyDialog(
                        true,
                        (dialog, which) -> {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.updateConversation(conversation);
                            xmppService.sendMessage(message);
                            messageSent();
                        });
            }
        }
    }

    public void encryptTextMessage(Message message) {
        activity.xmppConnectionService
                .getPgpEngine()
                .encrypt(
                        message,
                        new UiCallback<Message>() {

                            @Override
                            public void userInputRequired(PendingIntent pi, Message message) {
                                startPendingIntent(pi, REQUEST_SEND_MESSAGE);
                            }

                            @Override
                            public void success(Message message) {
                                // TODO the following two call can be made before the callback
                                activity.runOnUiThread(() -> messageSent());
                            }

                            @Override
                            public void error(final int error, Message message) {
                                activity
                                        .runOnUiThread(
                                                () -> {
                                                    doneSendingPgpMessage();
                                                    Toast.makeText(
                                                                    activity,
                                                                    error == 0
                                                                            ? R.string
                                                                            .unable_to_connect_to_keychain
                                                                            : error,
                                                                    Toast.LENGTH_SHORT)
                                                            .show();
                                                });
                            }
                        });
    }

    public void showNoPGPKeyDialog(
            final boolean plural, final DialogInterface.OnClickListener listener) {
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        if (plural) {
            builder.setTitle(getString(R.string.no_pgp_keys));
            builder.setMessage(getText(R.string.contacts_have_no_pgp_keys));
        } else {
            builder.setTitle(getString(R.string.no_pgp_key));
            builder.setMessage(getText(R.string.contact_has_no_pgp_key));
        }
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.send_unencrypted), listener);
        builder.create().show();
    }

    public void appendText(String text, final boolean doNotAppend) {
        if (text == null) {
            return;
        }
        final Editable editable = this.binding.textinput.getText();
        String previous = editable == null ? "" : editable.toString();
        if (doNotAppend && !TextUtils.isEmpty(previous)) {
            Toast.makeText(activity, R.string.already_drafting_message, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (UIHelper.isLastLineQuote(previous)) {
            text = '\n' + text;
        } else if (previous.length() != 0
                && !Character.isWhitespace(previous.charAt(previous.length() - 1))) {
            text = " " + text;
        }
        this.binding.textinput.append(text);
    }

    @Override
    public boolean onEnterPressed(final boolean isCtrlPressed) {
        if (isCtrlPressed || enterIsSend()) {
            sendMessage();
            return true;
        }
        return false;
    }

    private boolean enterIsSend() {
        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        return p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
    }

    public boolean onArrowUpCtrlPressed() {
        final Message lastEditableMessage =
                conversation == null ? null : conversation.getLastEditableMessage();
        if (lastEditableMessage != null) {
            correctMessage(lastEditableMessage);
            return true;
        } else {
            Toast.makeText(activity, R.string.could_not_correct_message, Toast.LENGTH_LONG)
                    .show();
            return false;
        }
    }

    @Override
    public void onTypingStarted() {
        final XmppConnectionService service =
                activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        final Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE
                && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
            service.sendChatState(conversation);
        }
        runOnUiThread(this::updateSendButton);
    }

    @Override
    public void onTypingStopped() {
        final XmppConnectionService service =
                activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        final Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) {
            service.sendChatState(conversation);
        }
    }

    @Override
    public void onTextDeleted() {
        final XmppConnectionService service =
                activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        final Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE
                && conversation.setOutgoingChatState(Config.DEFAULT_CHAT_STATE)) {
            service.sendChatState(conversation);
        }
        if (storeNextMessage()) {
            runOnUiThread(
                    () -> {
                        if (activity == null) {
                            return;
                        }
                        activity.onConversationsListItemUpdated();
                    });
        }
        runOnUiThread(this::updateSendButton);
    }

    @Override
    public void onTextChanged() {
        if (conversation != null && conversation.getCorrectingMessage() != null) {
            runOnUiThread(this::updateSendButton);
        }
    }

    @Override
    public boolean onTabPressed(boolean repeated) {
        if (conversation == null || conversation.getMode() == Conversation.MODE_SINGLE) {
            return false;
        }
        if (repeated) {
            completionIndex++;
        } else {
            lastCompletionLength = 0;
            completionIndex = 0;
            final String content = this.binding.textinput.getText().toString();
            lastCompletionCursor = this.binding.textinput.getSelectionEnd();
            int start =
                    lastCompletionCursor > 0
                            ? content.lastIndexOf(" ", lastCompletionCursor - 1) + 1
                            : 0;
            firstWord = start == 0;
            incomplete = content.substring(start, lastCompletionCursor);
        }
        List<String> completions = new ArrayList<>();
        for (MucOptions.User user : conversation.getMucOptions().getUsers()) {
            String name = user.getNick();
            if (name != null && name.startsWith(incomplete)) {
                completions.add(name + (firstWord ? ": " : " "));
            }
        }
        Collections.sort(completions);
        if (completions.size() > completionIndex) {
            String completion = completions.get(completionIndex).substring(incomplete.length());
            this.binding
                    .textinput
                    .getEditableText()
                    .delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            this.binding.textinput.getEditableText().insert(lastCompletionCursor, completion);
            lastCompletionLength = completion.length();
        } else {
            completionIndex = -1;
            this.binding
                    .textinput
                    .getEditableText()
                    .delete(lastCompletionCursor, lastCompletionCursor + lastCompletionLength);
            lastCompletionLength = 0;
        }
        return true;
    }

    private void startPendingIntent(PendingIntent pendingIntent, int requestCode) {
        try {
            activity
                    .startIntentSenderForResult(
                            pendingIntent.getIntentSender(),
                            requestCode,
                            null,
                            0,
                            0,
                            0,
                            Compatibility.pgpStartIntentSenderOptions());
        } catch (final SendIntentException ignored) {
        }
    }

    @Override
    public void onBackendConnected() {
        Log.d(Config.LOGTAG, "ConversationFragment.onBackendConnected()");
        setupEmojiSearch();
        String uuid = pendingConversationsUuid.pop();
        if (uuid != null) {
            if (!findAndReInitByUuidOrArchive(uuid)) {
                return;
            }
        } else {
            if (!activity.xmppConnectionService.isConversationStillOpen(conversation)) {
                clearPending();
                activity.onConversationArchived(conversation);
                return;
            }
        }
        ActivityResult activityResult = postponedActivityResult.pop();
        if (activityResult != null) {
            handleActivityResult(activityResult);
        }
        clearPending();
    }

    private boolean findAndReInitByUuidOrArchive(@NonNull final String uuid) {
        Conversation conversation = activity.xmppConnectionService.findConversationByUuid(uuid);
        if (conversation == null) {
            clearPending();
            activity.onConversationArchived(null);
            return false;
        }
        reInit(conversation);
        ScrollState scrollState = pendingScrollState.pop();
        String lastMessageUuid = pendingLastMessageUuid.pop();
        List<Attachment> attachments = pendingMediaPreviews.pop();
        if (scrollState != null) {
            setScrollPosition(scrollState, lastMessageUuid);
        }
        if (attachments != null && attachments.size() > 0) {
            Log.d(Config.LOGTAG, "had attachments on restore");
            mediaPreviewAdapter.addMediaPreviews(attachments);
            toggleInputMethod();
        }
        return true;
    }

    private void clearPending() {
        if (postponedActivityResult.clear()) {
            Log.e(Config.LOGTAG, "cleared pending intent with unhandled result left");
            if (pendingTakePhotoUri.clear()) {
                Log.e(Config.LOGTAG, "cleared pending photo uri");
            }
        }
        if (pendingScrollState.clear()) {
            Log.e(Config.LOGTAG, "cleared scroll state");
        }
        if (pendingConversationsUuid.clear()) {
            Log.e(Config.LOGTAG, "cleared pending conversations uuid");
        }
        if (pendingMediaPreviews.clear()) {
            Log.e(Config.LOGTAG, "cleared pending media previews");
        }
    }

    public Conversation getConversation() {
        return conversation;
    }

    @Override
    public void onContactPictureLongClicked(View v, final Message message) {
        final String fingerprint;
        if (message.getEncryption() == Message.ENCRYPTION_PGP
                || message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
            fingerprint = "pgp";
        } else {
            fingerprint = message.getFingerprint();
        }
        final PopupMenu popupMenu = new PopupMenu(activity, v);
        final Contact contact = message.getContact();
        if (message.getStatus() <= Message.STATUS_RECEIVED
                && (contact == null || !contact.isSelf())) {
            if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
                final Jid cp = message.getCounterpart();
                if (cp == null || cp.isBareJid()) {
                    return;
                }
                final Jid tcp = message.getTrueCounterpart();
                final String occupantId = message.getOccupantId();
                final User userByRealJid =
                        tcp != null
                                ? conversation.getMucOptions().findOrCreateUserByRealJid(tcp, cp, occupantId)
                                : null;
                final User userByOccupantId =
                        occupantId != null
                                ? conversation.getMucOptions().findUserByOccupantId(occupantId, cp)
                                : null;
                final User user =
                        userByRealJid != null
                                ? userByRealJid
                                : (userByOccupantId != null ? userByOccupantId : conversation.getMucOptions().findUserByFullJid(cp));
                if (user == null) return;
                popupMenu.inflate(R.menu.muc_details_context);
                final Menu menu = popupMenu.getMenu();
                MucDetailsContextMenuHelper.configureMucDetailsContextMenu(
                        activity, menu, conversation, user);
                popupMenu.setOnMenuItemClickListener(
                        menuItem ->
                                MucDetailsContextMenuHelper.onContextItemSelected(
                                        menuItem, user, activity, fingerprint));
            } else {
                popupMenu.inflate(R.menu.one_on_one_context);
                popupMenu.setOnMenuItemClickListener(
                        item -> {
                            switch (item.getItemId()) {
                                case R.id.action_contact_details:
                                    activity.switchToContactDetails(
                                            message.getContact(), fingerprint);
                                    break;
                                case R.id.action_show_qr_code:
                                    activity.showQrCode(
                                            "xmpp:"
                                                    + message.getContact()
                                                    .getJid()
                                                    .asBareJid()
                                                    .toString());
                                    break;
                            }
                            return true;
                        });
            }
        } else {
            popupMenu.inflate(R.menu.account_context);
            final Menu menu = popupMenu.getMenu();
            menu.findItem(R.id.action_manage_accounts)
                    .setVisible(true);
            popupMenu.setOnMenuItemClickListener(
                    item -> {
                        final XmppActivity activity = this.activity;
                        if (activity == null) {
                            Log.e(Config.LOGTAG, "Unable to perform action. no context provided");
                            return true;
                        }
                        switch (item.getItemId()) {
                            case R.id.action_show_qr_code:
                                activity.showQrCode(conversation.getAccount().getShareableUri());
                                break;
                            case R.id.action_account_details:
                                activity.switchToAccount(
                                        message.getConversation().getAccount(), fingerprint);
                                break;
                            case R.id.action_manage_accounts:
                                AccountUtils.launchManageAccounts(activity);
                                break;
                        }
                        return true;
                    });
        }
        popupMenu.show();
    }

    @Override
    public void onContactPictureClicked(Message message) {
        if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_thread_feature", R.bool.show_thread_feature)) {
            setThread(message.getThread());
        }
        if (message.isPrivateMessage()) {
            privateMessageWith(message.getCounterpart());
            return;
        }
        forkNullThread(message);
        conversation.setUserSelectedThread(true);

        final boolean received = message.getStatus() <= Message.STATUS_RECEIVED;
        if (received) {
            if (message.getConversation() instanceof Conversation
                    && message.getConversation().getMode() == Conversation.MODE_MULTI) {
                Jid tcp = message.getTrueCounterpart();
                Jid user = message.getCounterpart();
                if (user != null && !user.isBareJid()) {
                    final MucOptions mucOptions =
                            ((Conversation) message.getConversation()).getMucOptions();
                    if (mucOptions.participating()
                            || ((Conversation) message.getConversation()).getNextCounterpart()
                            != null) {
                        MucOptions.User mucUser = mucOptions.findUserByFullJid(user);
                        MucOptions.User tcpMucUser = mucOptions.findUserByRealJid(tcp == null ? null : tcp.asBareJid());
                        if (mucUser == null && tcpMucUser == null) {
                            Toast.makeText(
                                            activity,
                                            activity.getString(
                                                    R.string.user_has_left_conference,
                                                    user.getResource()),
                                            Toast.LENGTH_SHORT)
                                    .show();
                        }
                        highlightInConference(mucUser == null || mucUser.getNick() == null ? (tcpMucUser == null || tcpMucUser.getNick() == null ? user.getResource() : tcpMucUser.getNick()) : mucUser.getNick());
                    } else {
                        Toast.makeText(
                                        activity,
                                        R.string.you_are_not_participating,
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                }
            }
        }
    }

    private Activity requireActivity() {
        Activity activity = getActivity();
        if (activity == null) activity = this.activity;
        if (activity == null) {
            throw new IllegalStateException("Activity not attached");
        }
        return activity;
    }

// Voice recorder
    public void recordVoice() {
        this.binding.recordingVoiceActivity.setVisibility(View.VISIBLE);
        this.binding.recordVoiceButton.setEnabled(false);
        if (!startRecording()) {
            this.binding.shareButton.setEnabled(false);
            this.binding.timer.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            this.binding.timer.setText(R.string.unable_to_start_recording);
        }
    }

    private static final Set<String> AAC_SENSITIVE_DEVICES =
            new ImmutableSet.Builder<String>()
                    .add("FP4")             // Fairphone 4 https://codeberg.org/monocles/monocles_chat/issues/133
                    .add("ONEPLUS A6000")   // OnePlus 6 https://github.com/iNPUTmice/Conversations/issues/4329
                    .add("ONEPLUS A6003")   // OnePlus 6 https://github.com/iNPUTmice/Conversations/issues/4329
                    .add("ONEPLUS A6010")   // OnePlus 6T https://codeberg.org/monocles/monocles_chat/issues/133
                    .add("ONEPLUS A6013")   // OnePlus 6T https://codeberg.org/monocles/monocles_chat/issues/133
                    .add("Pixel 2") // Pixel 2 // https://codeberg.org/iNPUTmice/Conversations/issues/526
                    .add("Pixel 4a")        // Pixel 4a https://github.com/iNPUTmice/Conversations/issues/4223
                    .add("SC-03K") // Samsung Galaxy S9+
                    .add("SCV39") // Samsung Galaxy S9+
                    .add("SM-G965F") // Samsung Galaxy S9+
                    .add("SM-G965N") // Samsung Galaxy S9+
                    .add("SM-G9650") // Samsung Galaxy S9+
                    .add("SM-G965W") // Samsung Galaxy S9+
                    .add("SM-G965U") // Samsung Galaxy S9+
                    .add("SM-G965U1") // Samsung Galaxy S9+  // https://codeberg.org/iNPUTmice/Conversations/issues/526
                    .add("WP12 Pro")        // Oukitel WP 12 Pro https://github.com/iNPUTmice/Conversations/issues/4223
                    .add("Volla Phone X")   // Volla Phone X https://github.com/iNPUTmice/Conversations/issues/4223
                    .add("Redmi Note 12S")  // Xiaomi Redmi Note 12S Model name
                    .add("23030RAC7Y")      // Xiaomi Redmi Note 12S Code
                    .build();

    private boolean startRecording() {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        final String userChosenCodec = activity.xmppConnectionService.getPreferences().getString("voice_message_codec", "");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mRecorder.setPrivacySensitive(true);
        }
        final int outputFormat;
        if (("opus".equals(userChosenCodec) || ("".equals(userChosenCodec) && Config.USE_OPUS_VOICE_MESSAGES)) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            outputFormat = MediaRecorder.OutputFormat.WEBM;
            mRecorder.setOutputFormat(outputFormat);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS);
            mRecorder.setAudioEncodingBitRate(64000);
            mRecorder.setAudioSamplingRate(48000);
        } else if ("mpeg4".equals(userChosenCodec) || !Config.USE_OPUS_VOICE_MESSAGES) {
            outputFormat = MediaRecorder.OutputFormat.MPEG_4;
            mRecorder.setOutputFormat(outputFormat);
            if (AAC_SENSITIVE_DEVICES.contains(Build.MODEL) && Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
                // Changing these three settings for AAC sensitive devices for Android<=13 might lead to sporadically truncated (cut-off) voice messages.
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
                mRecorder.setAudioSamplingRate(24_000);
                mRecorder.setAudioEncodingBitRate(28_000);
            } else {
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mRecorder.setAudioSamplingRate(44_100);
                mRecorder.setAudioEncodingBitRate(64_000);
            }
        } else {
            outputFormat = MediaRecorder.OutputFormat.THREE_GPP;
            mRecorder.setOutputFormat(outputFormat);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            mRecorder.setAudioEncodingBitRate(23850);
            mRecorder.setAudioSamplingRate(16000);
        }
        setupOutputFile(outputFormat);
        mRecorder.setOutputFile(mOutputFile.getAbsolutePath());
        binding.timer.clearAnimation();

        try {
            mRecorder.prepare();
            mRecorder.start();
            recording = true;
            mHandler.postDelayed(mTickExecutor, 0);
            Log.d(Config.LOGTAG, "started recording to " + mOutputFile.getAbsolutePath());
            binding.shareButton.setEnabled(true);
            return true;
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "prepare() failed ", e);
            return false;
        }
    }

    protected void stopRecording() {
        try {
            mRecorder.stop();
            mRecorder.release();
            recording = false;
            binding.shareButton.setEnabled(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        binding.recordVoiceButton.setEnabled(true);
    }

    private void StartTimerAnimation() {
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(500); //You can manage the blinking time with this parameter
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        binding.timer.startAnimation(anim);
    }

    protected void pauseRecording() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mRecorder.pause();
                mHandler.removeCallbacks(mTickExecutor);
                recording = false;
                StartTimerAnimation();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void resumeRecording() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mRecorder.resume();
                mHandler.postDelayed(mTickExecutor, 0);
                binding.timer.clearAnimation();
            }
            recording = true;
            Log.e("Voice Recorder", "resume recording");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void stopRecording(final boolean saveFile) {
        resumeRecording();
        try {
            if (recording) {
                stopRecording();
            }
        } catch (Exception e) {
            if (saveFile) {
                ToastCompat.makeText(activity, R.string.unable_to_save_recording, ToastCompat.LENGTH_SHORT).show();
                return;
            }
        } finally {
            mRecorder = null;
            mStartTime = 0;
            mHandler.removeCallbacks(mTickExecutor);
        }
        if (!saveFile && mOutputFile != null) {
            if (mOutputFile.delete()) {
                Log.d(Config.LOGTAG, "deleted canceled recording");
            }
        }
        if (saveFile) {
            new Thread(new Finisher(outputFileWrittenLatch, mOutputFile, activity)).start();
        }
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        activity.setRequestedOrientation(oldOrientation);
        binding.recordVoiceButton.setEnabled(true);
        binding.shareButton.setEnabled(false);
    }

    private class Finisher implements Runnable {

        private final CountDownLatch latch;
        private final File outputFile;
        private final WeakReference<Activity> activityReference;

        private Finisher(CountDownLatch latch, File outputFile, Activity activity) {
            this.latch = latch;
            this.outputFile = outputFile;
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            final String userChosenCodec = activity.xmppConnectionService.getPreferences().getString("voice_message_codec", "");
            if (("opus".equals(userChosenCodec) || ("".equals(userChosenCodec) && Config.USE_OPUS_VOICE_MESSAGES)) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    if (!latch.await(8, TimeUnit.SECONDS)) {
                        Log.d(Config.LOGTAG, "time out waiting for output file to be written");
                    }
                } catch (InterruptedException e) {
                    Log.d(Config.LOGTAG, "interrupted while waiting for output file to be written", e);
                }
                final Activity activity = activityReference.get();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(
                        () -> {
                            activity.setResult(
                                    Activity.RESULT_OK, new Intent().setData(Uri.fromFile(outputFile)));
                            mediaPreviewAdapter.addMediaPreviews(Attachment.of(activity, Uri.fromFile(outputFile), Attachment.Type.RECORDING));
                            toggleInputMethod();
                            //attachFileToConversation(conversation, Uri.fromFile(outputFile), "audio/oga;codecs=opus");
                            binding.recordingVoiceActivity.setVisibility(View.GONE);
                        });
            } else if ("mpeg4".equals(userChosenCodec) || !Config.USE_OPUS_VOICE_MESSAGES) {
                try {
                    if (!latch.await(8, TimeUnit.SECONDS)) {
                        Log.d(Config.LOGTAG, "time out waiting for output file to be written");
                    }
                } catch (InterruptedException e) {
                    Log.d(Config.LOGTAG, "interrupted while waiting for output file to be written", e);
                }
                final Activity activity = activityReference.get();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(
                        () -> {
                            activity.setResult(
                                    Activity.RESULT_OK, new Intent().setData(Uri.fromFile(outputFile)));
                            mediaPreviewAdapter.addMediaPreviews(Attachment.of(activity, Uri.fromFile(outputFile), Attachment.Type.RECORDING));
                            toggleInputMethod();
                            //attachFileToConversation(conversation, Uri.fromFile(outputFile), "audio/mp4");
                            binding.recordingVoiceActivity.setVisibility(View.GONE);
                        });
            }
        }
    }

    private File generateOutputFilename(final int outputFormat) {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US);
        final String extension;
        if (outputFormat == MediaRecorder.OutputFormat.MPEG_4) {
            extension = "m4a";
        } else if (outputFormat == MediaRecorder.OutputFormat.WEBM) {
            extension = "opus";
        } else if (outputFormat == MediaRecorder.OutputFormat.THREE_GPP) {
            extension = "awb";
        } else {
            throw new IllegalStateException("Unrecognized output format");
        }
        final String filename =
                String.format("RECORDING_%s.%s", dateFormat.format(new Date()), extension);
        final File parentDirectory;
        if (conversation.storeInCache(activity.xmppConnectionService)) {
            parentDirectory = new File(activity.xmppConnectionService.getCacheDir(), "/media");
        } else {
            parentDirectory =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + "/monocles chat" + "/recordings");
        }
        return new File(parentDirectory, filename);
    }

    private void setupOutputFile(final int outputFormat) {
        mOutputFile = generateOutputFilename(outputFormat);
        final File parentDirectory = mOutputFile.getParentFile();
        if (Objects.requireNonNull(parentDirectory).mkdirs()) {
            Log.d(Config.LOGTAG, "created " + parentDirectory.getAbsolutePath());
        }
        setupFileObserver(parentDirectory);
    }

    private void setupFileObserver(final File directory) {
    	outputFileWrittenLatch = new CountDownLatch(1);
        mFileObserver =
                new FileObserver(directory.getAbsolutePath()) {
                    @Override
                    public void onEvent(int event, String s) {
                        if (s != null
                                && s.equals(mOutputFile.getName())
                                && event == FileObserver.CLOSE_WRITE) {
                            outputFileWrittenLatch.countDown();
                        }
                    }
                };
        mFileObserver.startWatching();
    }

    private void tick() {
        //this.binding.timer.setText(TimeFrameUtils.formatTimePassed(mStartTime, true));
        int minutes = (mStartTime % 3600) / 60;
        int seconds = mStartTime % 60;

        // Format the seconds into hours, minutes,
        // and seconds.
        String time
                = String
                .format(Locale.getDefault(),
                        "%02d:%02d", minutes,
                        seconds);

        // Set the text view text.
        this.binding.timer.setText(time);

        // If running is true, increment the
        // seconds variable.
        if (recording) {
            mStartTime++;
        }
    }

    public void updateinputfield(final boolean me) {
        LinearLayout emojipickerview = binding.emojisStickerLayout;
        ViewGroup.LayoutParams params = emojipickerview.getLayoutParams();
        Fragment secondaryFragment = activity.getFragmentManager().findFragmentById(R.id.secondary_fragment);
        if (Build.VERSION.SDK_INT > 29) {
            ViewCompat.setOnApplyWindowInsetsListener(activity.getWindow().getDecorView(), (v, insets) -> {
                boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                int keyboardHeight = 0;
                if (activity != null && ViewConfiguration.get(activity).hasPermanentMenuKey()) {
                    keyboardHeight  = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom - insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom - 10;
                } else if (activity != null) {
                    keyboardHeight  = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom - insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom - 10;
                }
                if (keyboardHeight > 100 && !(secondaryFragment instanceof ConversationFragment)) {
                    binding.keyboardButton.setVisibility(View.GONE);
                    binding.emojiButton.setVisibility(View.VISIBLE);
                    params.height = keyboardHeight;
                    emojipickerview.setLayoutParams(params);
                } else if (keyboardHeight > 100) {
                    binding.keyboardButton.setVisibility(View.GONE);
                    binding.emojiButton.setVisibility(View.VISIBLE);
                    params.height = keyboardHeight - 127;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.emojiButton.getVisibility() == View.VISIBLE) {
                    binding.keyboardButton.setVisibility(View.GONE);
                    params.height = 0;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.keyboardButton.getVisibility() == View.VISIBLE && keyboardHeight == 0) {
                    binding.emojiButton.setVisibility(View.GONE);
                    params.height = 800;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.keyboardButton.getVisibility() == View.VISIBLE && keyboardHeight > 100) {
                    binding.emojiButton.setVisibility(View.GONE);
                    params.height = keyboardHeight;
                    emojipickerview.setLayoutParams(params);
                }
                if (activity != null && activity.xmppConnectionService != null && isKeyboardVisible && activity.xmppConnectionService.showTextFormatting()) {
                    showTextFormat(me);
                } else {
                    hideTextFormat();
                }
                return ViewCompat.onApplyWindowInsets(v, insets);
            });
        } else {
            if (keyboardHeightProvider != null) {
                return;
            }
            RelativeLayout llRoot = binding.conversationsFragment; //The root layout (Linear, Relative, Contraint, etc...)
            keyboardHeightListener = (int keyboardHeight, boolean keyboardOpen, boolean isLandscape) -> {
                Log.i("keyboard listener", "keyboardHeight: " + keyboardHeight + " keyboardOpen: " + keyboardOpen + " isLandscape: " + isLandscape);
                if (keyboardOpen && !(secondaryFragment instanceof ConversationFragment)) {
                    binding.keyboardButton.setVisibility(View.GONE);
                    binding.emojiButton.setVisibility(View.VISIBLE);
                    params.height = keyboardHeight - 10;
                    emojipickerview.setLayoutParams(params);
                } else if (keyboardOpen) {
                    binding.keyboardButton.setVisibility(View.GONE);
                    binding.emojiButton.setVisibility(View.VISIBLE);
                    params.height = keyboardHeight - 135;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.emojiButton.getVisibility() == View.VISIBLE) {
                    binding.keyboardButton.setVisibility(View.GONE);
                    params.height = 0;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.keyboardButton.getVisibility() == View.VISIBLE && keyboardHeight == 0) {
                    binding.emojiButton.setVisibility(View.GONE);
                    params.height = 600;
                    emojipickerview.setLayoutParams(params);
                } else if (binding.keyboardButton.getVisibility() == View.VISIBLE && keyboardHeight > 100) {
                    binding.emojiButton.setVisibility(View.GONE);
                    params.height = keyboardHeight;
                    emojipickerview.setLayoutParams(params);
                }
                if (activity != null && activity.xmppConnectionService != null && keyboardOpen && activity.xmppConnectionService.showTextFormatting()) {
                    showTextFormat(me);
                } else {
                    hideTextFormat();
                }
            };
            keyboardHeightProvider = new KeyboardHeightProvider(activity, activity.getWindowManager(), llRoot, keyboardHeightListener);
        }
    }

    private final OnClickListener memojiButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (binding.emojiButton.getVisibility() == VISIBLE && binding.emojisStickerLayout.getHeight() > 100) {
                binding.emojiButton.setVisibility(GONE);
                binding.keyboardButton.setVisibility(VISIBLE);
                hideSoftKeyboard(activity);
                EmojiPickerView emojiPickerView = binding.emojiPicker;
                backPressedLeaveEmojiPicker.setEnabled(true);
                binding.textinput.requestFocus();
                emojiPickerView.setOnEmojiPickedListener(emojiViewItem -> {
                    int start = binding.textinput.getSelectionStart(); //this is to get the the cursor position
                    binding.textinput.getText().insert(start, emojiViewItem.getEmoji()); //this will get the text and insert the emoji into   the current position
                });

                if (binding.emojiPicker.getVisibility() == VISIBLE) {
                    binding.emojisButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.emojisButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.emojisButton.setBackgroundColor(0);
                    binding.emojisButton.setTypeface(null, Typeface.NORMAL);
                }
                if (binding.stickersview.getVisibility() == VISIBLE) {
                    binding.stickersButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.stickersButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.stickersButton.setBackgroundColor(0);
                    binding.stickersButton.setTypeface(null, Typeface.NORMAL);
                }
                if (binding.gifsview.getVisibility() == VISIBLE) {
                    binding.gifsButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.gifsButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.gifsButton.setBackgroundColor(0);
                    binding.gifsButton.setTypeface(null, Typeface.NORMAL);
                }
            } else if (binding.emojiButton.getVisibility() == VISIBLE && binding.emojisStickerLayout.getHeight() < 100) {
                LinearLayout emojipickerview = binding.emojisStickerLayout;
                ViewGroup.LayoutParams params = emojipickerview.getLayoutParams();
                params.height = 800;
                emojipickerview.setLayoutParams(params);
                binding.emojiButton.setVisibility(GONE);
                binding.keyboardButton.setVisibility(VISIBLE);
                hideSoftKeyboard(activity);
                EmojiPickerView emojiPickerView = binding.emojiPicker;
                backPressedLeaveEmojiPicker.setEnabled(true);
                binding.textinput.requestFocus();
                emojiPickerView.setOnEmojiPickedListener(emojiViewItem -> {
                    int start = binding.textinput.getSelectionStart(); //this is to get the the cursor position
                    binding.textinput.getText().insert(start, emojiViewItem.getEmoji()); //this will get the text and insert the emoji into   the current position
                });

                if (binding.emojiPicker.getVisibility() == VISIBLE) {
                    binding.emojisButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.emojisButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.emojisButton.setBackgroundColor(0);
                    binding.emojisButton.setTypeface(null, Typeface.NORMAL);
                }
                if (binding.stickersview.getVisibility() == VISIBLE) {
                    binding.stickersButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.stickersButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.stickersButton.setBackgroundColor(0);
                    binding.stickersButton.setTypeface(null, Typeface.NORMAL);
                }
                if (binding.gifsview.getVisibility() == VISIBLE) {
                    binding.gifsButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                    binding.gifsButton.setTypeface(null, Typeface.BOLD);
                } else {
                    binding.gifsButton.setBackgroundColor(0);
                    binding.gifsButton.setTypeface(null, Typeface.NORMAL);
                }
            }
        }
    };

    private final OnClickListener memojisButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            binding.emojiPicker.setVisibility(VISIBLE);
            binding.stickersview.setVisibility(GONE);
            binding.gifsview.setVisibility(GONE);
            EmojiPickerView emojiPickerView = binding.emojiPicker;
            backPressedLeaveEmojiPicker.setEnabled(true);
            binding.textinput.requestFocus();
            emojiPickerView.setOnEmojiPickedListener(emojiViewItem -> {
                int start = binding.textinput.getSelectionStart(); //this is to get the the cursor position
                binding.textinput.getText().insert(start, emojiViewItem.getEmoji()); //this will get the text and insert the emoji into   the current position
            });

            if (binding.emojiPicker.getVisibility() == VISIBLE) {
                binding.emojisButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.emojisButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.emojisButton.setBackgroundColor(0);
                binding.emojisButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.stickersview.getVisibility() == VISIBLE) {
                binding.stickersButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.stickersButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.stickersButton.setBackgroundColor(0);
                binding.stickersButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.gifsview.getVisibility() == VISIBLE) {
                binding.gifsButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.gifsButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.gifsButton.setBackgroundColor(0);
                binding.gifsButton.setTypeface(null, Typeface.NORMAL);
            }
        }
    };

    private final OnClickListener mstickersButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            binding.emojiPicker.setVisibility(GONE);
            binding.stickersview.setVisibility(VISIBLE);
            binding.gifsview.setVisibility(GONE);
            backPressedLeaveEmojiPicker.setEnabled(true);
            binding.textinput.requestFocus();
            /*  //TODO: For some reason this leads to crash, fix it later
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isEmpty(dirStickers.toPath())) {
                    Toast.makeText(activity, R.string.update_default_stickers, Toast.LENGTH_LONG).show();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

             */
            if (binding.emojiPicker.getVisibility() == VISIBLE) {
                binding.emojisButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.emojisButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.emojisButton.setBackgroundColor(0);
                binding.emojisButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.stickersview.getVisibility() == VISIBLE) {
                binding.stickersButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.stickersButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.stickersButton.setBackgroundColor(0);
                binding.stickersButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.gifsview.getVisibility() == VISIBLE) {
                binding.gifsButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.gifsButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.gifsButton.setBackgroundColor(0);
                binding.gifsButton.setTypeface(null, Typeface.NORMAL);
            }
        }
    };

    private final OnClickListener mgifsButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            binding.emojiPicker.setVisibility(GONE);
            binding.stickersview.setVisibility(GONE);
            binding.gifsview.setVisibility(VISIBLE);
            backPressedLeaveEmojiPicker.setEnabled(true);
            binding.textinput.requestFocus();
            /*  //TODO: For some reason this leads to crash, fix it later
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isEmpty(dirGifs.toPath())) {
                    Toast.makeText(activity, R.string.copy_GIFs_to_GIFs_folder, Toast.LENGTH_LONG).show();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

             */
            if (binding.emojiPicker.getVisibility() == VISIBLE) {
                binding.emojisButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.emojisButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.emojisButton.setBackgroundColor(0);
                binding.emojisButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.stickersview.getVisibility() == VISIBLE) {
                binding.stickersButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.stickersButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.stickersButton.setBackgroundColor(0);
                binding.stickersButton.setTypeface(null, Typeface.NORMAL);
            }
            if (binding.gifsview.getVisibility() == VISIBLE) {
                binding.gifsButton.setBackground(ContextCompat.getDrawable(activity, R.drawable.selector_bubble));
                binding.gifsButton.setTypeface(null, Typeface.BOLD);
            } else {
                binding.gifsButton.setBackgroundColor(0);
                binding.gifsButton.setTypeface(null, Typeface.NORMAL);
            }
        }
    };

    private final OnClickListener mkeyboardButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (binding.keyboardButton.getVisibility() == VISIBLE) {
                binding.keyboardButton.setVisibility(GONE);
                binding.emojiButton.setVisibility(VISIBLE);
                InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (inputMethodManager != null) {
                    binding.textinput.requestFocus();
                    inputMethodManager.showSoftInput(binding.textinput, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }
    };

    private final OnBackPressedCallback backPressedLeaveEmojiPicker = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (binding.emojisStickerLayout.getHeight() > 100) {
                LinearLayout emojipickerview = binding.emojisStickerLayout;
                ViewGroup.LayoutParams params = emojipickerview.getLayoutParams();
                params.height = 0;
                emojipickerview.setLayoutParams(params);
                binding.keyboardButton.setVisibility(GONE);
                binding.emojiButton.setVisibility(VISIBLE);
            }
            this.setEnabled(false);
            refresh();
        }
    };

    public void LoadStickers() {
        if (!hasStoragePermission(activity)) return;

        // Use ArrayLists to dynamically collect file information
        List<File> allFilesList = new ArrayList<>();
        List<String> allFilePathsList = new ArrayList<>();
        List<String> allFileNamesList = new ArrayList<>();

        // Create the main stickers directory if it doesn't exist
        if (!dirStickers.exists()) {
            if (!dirStickers.mkdirs()) {
                Log.e("LoadStickers", "Failed to create stickers directory: " + dirStickers.getAbsolutePath());
                // Optionally show a toast to the user on the UI thread
                if (activity != null) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "Failed to create stickers folder", Toast.LENGTH_SHORT).show());
                }
                return;
            }
        }

        // Start the recursive search for files
        collectStickersRecursively(dirStickers, allFilesList, allFilePathsList, allFileNamesList);

        // Update your class member arrays
        File[] stickerfiles = allFilesList.toArray(new File[0]); // Keep for potential direct use if needed
        final String[] finalStickerFilePaths = allFilePathsList.toArray(new String[0]);
        final String[] finalStickerFileNames = allFileNamesList.toArray(new String[0]);

        // IMPORTANT: UI updates must happen on the main thread
        if (activity != null) {
            activity.runOnUiThread(() -> {
                StickerfilesPaths = finalStickerFilePaths;
                StickerfilesNames = finalStickerFileNames;

                de.monocles.chat.GridView StickersGrid = binding.stickersview;
                if (StickersGrid == null) return; // Guard against null binding if fragment is destroyed

                StickersGrid.setAdapter(new StickersAdapter(activity, StickerfilesNames, StickerfilesPaths));
                StickersGrid.setOnItemClickListener((parent, view, position, id) -> {
                    if (activity == null || StickerfilesPaths == null || position >= StickerfilesPaths.length) return;
                    String filePath = StickerfilesPaths[position];
                    mediaPreviewAdapter.addMediaPreviews(Attachment.of(activity, Uri.fromFile(new File(filePath)), Attachment.Type.IMAGE));
                    toggleInputMethod();
                });

                StickersGrid.setOnItemLongClickListener((parent, view, position, id) -> {
                    if (activity != null && StickerfilesPaths != null && position < StickerfilesPaths.length && StickerfilesPaths[position] != null) {
                        File file = new File(StickerfilesPaths[position]);
                        if (file.exists()) {
                            showStickerPreviewDialog(file);
                        } else {
                            Toast.makeText(activity, R.string.cant_open_file, Toast.LENGTH_LONG).show(); // Example for non-existent file
                        }
                    }
                    return true;
                });
            });
        }
    }

    public void LoadGifs() {
        if (!hasStoragePermission(activity)) return;

        List<File> allFilesList = new ArrayList<>();
        List<String> allFilePathsList = new ArrayList<>();
        List<String> allFileNamesList = new ArrayList<>();

        // Assuming dirGifs is the correct directory for GIFs, not dirStickers
        // If dirStickers is indeed used for GIFs too, then the check below is fine.
        // Otherwise, replace dirStickers with dirGifs.
        File gifsDirectory = dirStickers; // Or dirStickers if that's intended
        if (!gifsDirectory.exists()) {
            if (!gifsDirectory.mkdirs()) {
                Log.e("LoadGifs", "Failed to create GIFs directory: " + gifsDirectory.getAbsolutePath());
                if (activity != null) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "Failed to create GIFs folder", Toast.LENGTH_SHORT).show());
                }
                return;
            }
        }

        collectGIFsRecursively(gifsDirectory, allFilesList, allFilePathsList, allFileNamesList); // Pass the correct directory

        final String[] finalGifsFilePaths = allFilePathsList.toArray(new String[0]);
        final String[] finalGifsFileNames = allFileNamesList.toArray(new String[0]);

        if (activity != null) {
            activity.runOnUiThread(() -> {
                GifsfilesPaths = finalGifsFilePaths;
                GifsfilesNames = finalGifsFileNames;

                de.monocles.chat.GridView GifsGrid = binding.gifsview;
                if (GifsGrid == null) return;

                GifsGrid.setAdapter(new GifsAdapter(activity, GifsfilesNames, GifsfilesPaths));
                GifsGrid.setOnItemClickListener((parent, view, position, id) -> {
                    if (activity == null || GifsfilesPaths == null || position >= GifsfilesPaths.length) return;
                    String filePath = GifsfilesPaths[position];
                    mediaPreviewAdapter.addMediaPreviews(Attachment.of(activity, Uri.fromFile(new File(filePath)), Attachment.Type.IMAGE));
                    toggleInputMethod();
                });

                GifsGrid.setOnItemLongClickListener((parent, view, position, id) -> {
                    if (activity != null && GifsfilesPaths != null && position < GifsfilesPaths.length && GifsfilesPaths[position] != null) {
                        File file = new File(GifsfilesPaths[position]);
                        if (file.exists()) {
                            showStickerPreviewDialog(file);
                        } else {
                            Toast.makeText(activity, R.string.cant_open_file, Toast.LENGTH_LONG).show();
                        }
                    }
                    return true;
                });
            });
        }
    }

    /**
     * Recursively collects files from a directory and its subdirectories.
     * It only adds files that are considered images by isImageFile().
     */
    private void collectStickersRecursively(File directory, List<File> allFiles, List<String> allPaths, List<String> allNames) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }


        File[] listedFiles = directory.listFiles();


        if (listedFiles != null) {
            for (File file : listedFiles) {
                if (file.isDirectory()) {
                    collectStickersRecursively(file, allFiles, allPaths, allNames); // Recursive call for subdirectories
                } else {
                    // Add file if it's an image (you might want a more robust image check)
                    if (isImageFile(file.getName())) { // Assuming you have or will add an isImageFile method
                        allFiles.add(file);
                        allPaths.add(file.getAbsolutePath());
                        allNames.add(file.getName());
                    }
                }
            }
        }
    }

    /**
     * Helper method to check if a file is an image based on its extension.
     */
    private boolean isImageFile(String fileName) {
        if (fileName == null) return false;
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".png") ||
                lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".webp") || // Common Android image format
                lowerName.endsWith(".bmp") ||
                lowerName.endsWith(".svg");
    }

    /**
     * Recursively collects files from a directory and its subdirectories.
     * It only adds files that are considered images by isImageFile().
     */
    private void collectGIFsRecursively(File directory, List<File> allFiles, List<String> allPaths, List<String> allNames) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] listedFiles = directory.listFiles();
        if (listedFiles != null) {
            for (File file : listedFiles) {
                if (file.isDirectory()) {
                    collectGIFsRecursively(file, allFiles, allPaths, allNames); // Recursive call for subdirectories
                } else {
                    // Add file if it's an image (you might want a more robust image check)
                    if (isGIFFile(file.getName())) { // Assuming you have or will add an isGIFFile method
                        allFiles.add(file);
                        allPaths.add(file.getAbsolutePath());
                        allNames.add(file.getName());
                    }
                }
            }
        }
    }

    /**
     * Helper method to check if a file is an image based on its extension.
     */
    private boolean isGIFFile(String fileName) {
        if (fileName == null) return false;
        String lowerName = fileName.toLowerCase();
        return  lowerName.endsWith(".gif") ||
                lowerName.endsWith(".webp"); // Common Android image format
    }

    // New method to show the sticker preview dialog
    private void showStickerPreviewDialog(File stickerFile) {
        if (activity == null || stickerFile == null || !stickerFile.exists()) {
            return;
        }

        final Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.dialog_sticker_preview); // Your custom layout

        ImageView stickerPreviewImageView = dialog.findViewById(R.id.sticker_preview_image_view);

        // Use Glide (or your preferred image loading library) to load the sticker
        Glide.with(activity) // 'this' refers to the ConversationFragment instance
                .load(stickerFile)
                .into(stickerPreviewImageView);

        // Optional: Make the dialog dismiss when the image is clicked
        stickerPreviewImageView.setOnClickListener(v -> dialog.dismiss());

        // Optional: Make dialog background transparent if your layout has rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.show();
    }

    private boolean canSendMeCommand() {
        if (conversation != null) {
            final String body = binding.textinput.getText().toString();
            return body.isEmpty();
        }
        return false;
    }

    private void showTextFormat(final boolean me) {
        this.binding.textformat.setVisibility(View.VISIBLE);
        this.binding.me.setEnabled(me);
        this.binding.me.setOnClickListener(meCommand);
        this.binding.quote.setOnClickListener(quote);
        this.binding.bold.setOnClickListener(boldText);
        this.binding.italic.setOnClickListener(italicText);
        this.binding.monospace.setOnClickListener(monospaceText);
        this.binding.strikethrough.setOnClickListener(strikethroughText);
        this.binding.close.setOnClickListener(close);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.binding.me.setTooltipText(activity.getString(R.string.me));
            this.binding.quote.setTooltipText(activity.getString(R.string.quote));
            this.binding.bold.setTooltipText(activity.getString(R.string.bold));
            this.binding.italic.setTooltipText(activity.getString(R.string.italic));
            this.binding.monospace.setTooltipText(activity.getString(R.string.monospace));
            this.binding.monospace.setTooltipText(activity.getString(R.string.monospace));
            this.binding.strikethrough.setTooltipText(activity.getString(R.string.strikethrough));
            this.binding.close.setTooltipText(activity.getString(R.string.action_close));
        }
    }

    private void hideTextFormat() {
        this.binding.textformat.setVisibility(View.GONE);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            loadMediaFromBackground();
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "loadMediaFromBackground failed", e);
        }
    }

    private void loadMediaFromBackground() {
        backgroundExecutor.execute(() -> {
            LoadStickers();
            LoadGifs();
        });
    }

    private void loadAndDisplayLatestPinnedMessage() {
        final Conversation conversation = getConversationReliable(activity);
        if (conversation == null || pinnedMessageRepository == null || activity == null) {
            hidePinnedMessageView();
            return;
        }

        // Using a background thread for repository access
        new Thread(() -> {
            final PinnedMessageRepository.DecryptedPinnedMessageData pinnedData =
                    pinnedMessageRepository.getLatestDecryptedPinnedMessageForConversation(conversation.getUuid());

            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (pinnedData != null && (pinnedData.plaintextBody != null || pinnedData.cid != null)) {
                        currentDisplayedPinnedMessageUuid = pinnedData.messageUuid;
                        currentDisplayedPinnedMessageCid = pinnedData.cid; // Store CID

                        // Reset visibility of content types
                        binding.pinnedMessageText.setVisibility(View.GONE);
                        binding.pinnedMessageImageThumbnail.setVisibility(View.GONE);
                        binding.pinnedMessageFileIcon.setVisibility(View.GONE);

                        if (pinnedData.cid != null && isDisplayableMediaCid(pinnedData.cid)) { // You'll need an isDisplayableMediaCid helper
                            binding.pinnedMessageImageThumbnail.setVisibility(View.VISIBLE);
                            File mediaFile = activity.xmppConnectionService.getFileForCid(pinnedData.cid);
                            if (mediaFile != null) {
                                // Show media thumbnail
                                Glide.with(ConversationFragment.this)
                                        .load(mediaFile)
                                        .placeholder(R.drawable.ic_image_24dp) // Optional placeholder
                                        .error(R.drawable.rounded_broken_image_24) // Optional error
                                        .into(binding.pinnedMessageImageThumbnail);
                            } else {
                                // Fallback if URI can't be resolved, maybe show text or generic file icon
                                if (pinnedData.plaintextBody != null && !pinnedData.plaintextBody.isEmpty()) {
                                    binding.pinnedMessageText.setText(pinnedData.plaintextBody);
                                    binding.pinnedMessageText.setVisibility(View.VISIBLE);
                                } else {
                                    // You might want to set a more specific icon based on file type from CID
                                    binding.pinnedMessageFileIcon.setImageResource(R.drawable.ic_description_24dp);
                                    binding.pinnedMessageFileIcon.setVisibility(View.VISIBLE);
                                }
                            }
                        } else if (pinnedData.cid != null && isAudioCid(pinnedData.cid)) { // Audio File
                            // TODO: Add audio player directly in the pinned message container
                            binding.pinnedMessageFileIcon.setVisibility(View.VISIBLE);
                            binding.pinnedMessageFileIcon.setImageResource(R.drawable.audio_file_24dp);
                            if (pinnedData.plaintextBody != null && !pinnedData.plaintextBody.isEmpty()) {
                                binding.pinnedMessageText.setText(pinnedData.plaintextBody);
                                binding.pinnedMessageText.setVisibility(View.VISIBLE);
                                // Optional: Adjust layout if text and icon are shown together
                                // e.g., move text to the side of the icon, or ensure enough padding.
                            }
                        } else if (pinnedData.cid != null) { // Generic file
                            // TODO: Set appropriate file icon based on MIME type derived from CID or filename
                            binding.pinnedMessageFileIcon.setImageResource(R.drawable.ic_description_24dp); // Helper needed
                            binding.pinnedMessageFileIcon.setVisibility(View.VISIBLE);
                            // Set content description for accessibility
                        } else if (pinnedData.plaintextBody.startsWith("geo:")) {
                            if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_maps_inside", R.bool.show_maps_inside)) {
                                final String url = GeoHelper.MapPreviewUriFromString(pinnedData.plaintextBody, activity);
                                binding.pinnedMessageImageThumbnail.setVisibility(View.VISIBLE);
                                Glide.with(activity)
                                        .load(Uri.parse(url))
                                        .placeholder(R.drawable.marker)
                                        .error(R.drawable.marker)
                                        .into(binding.pinnedMessageImageThumbnail);
                            } else {
                                binding.pinnedMessageText.setText(pinnedData.plaintextBody);
                                binding.pinnedMessageText.setVisibility(View.VISIBLE);
                            }
                        } else if (pinnedData.plaintextBody != null && !pinnedData.plaintextBody.isEmpty()) { // Text only
                            binding.pinnedMessageText.setText(pinnedData.plaintextBody);
                            binding.pinnedMessageText.setVisibility(View.VISIBLE);
                        } else {
                            // Should not happen if validation in repository is correct, but good to handle
                            hidePinnedMessageView();
                            return;
                        }

                        binding.pinnedMessageContainer.setTag(pinnedData.messageUuid); // Store UUID for jumping
                        binding.pinnedMessageContainer.setVisibility(View.VISIBLE);

                        // Click to jump to message
                        binding.pinnedMessageContainer.setOnClickListener(v -> {
                            if (currentDisplayedPinnedMessageUuid != null) {
                                Log.d(Config.LOGTAG, "Jumping to pinned message with UUID: " + currentDisplayedPinnedMessageUuid);
                                Runnable postSelectionRunnable = () -> highlightMessage(currentDisplayedPinnedMessageUuid);
                                updateSelection(currentDisplayedPinnedMessageUuid, binding.messagesView.getHeight() / 2, postSelectionRunnable, false, false);
                            }
                        });

                        // Setup unpin button
                        binding.pinnedMessageHide.setOnClickListener(v -> unpinCurrentDisplayedMessage());

                    } else {
                        hidePinnedMessageView();
                    }
                });
            }
        }).start();
    }

    // Helper method to determine if a CID likely represents an image or video
    private boolean isDisplayableMediaCid(Cid cid) {
        if (cid == null) return false;
        File file = activity.xmppConnectionService.getFileForCid(cid);
        if (file == null) return false;
        String lowerFilePath = file.getAbsolutePath();
        String mimeType = MimeUtils.guessFromPath(lowerFilePath);
        return mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"));
    }

    private boolean isVideoCid(Cid cid) {
        if (cid == null) return false;
        File file = activity.xmppConnectionService.getFileForCid(cid);
        if (file == null) return false;
        String lowerFilePath = file.getAbsolutePath();
        String mimeType = MimeUtils.guessFromPath(lowerFilePath);
        // Video types
        return mimeType != null && mimeType.startsWith("video/");
    }

    private boolean isAudioCid(Cid cid) {
        if (cid == null) return false;
        File file = activity.xmppConnectionService.getFileForCid(cid);
        if (file == null) return false;
        String lowerFilePath = file.getAbsolutePath();
        String mimeType = MimeUtils.guessFromPath(lowerFilePath);
        // Audio types
        return mimeType != null && mimeType.startsWith("audio/");
    }

    // Called when user explicitly pins a message from the conversation
    public void pinMessage(final Message messageToPin) {
        final Conversation conversation = getConversationReliable(activity);
        if (messageToPin == null || conversation == null || pinnedMessageRepository == null || activity == null) {
            Toast.makeText(activity, R.string.error_pinning_message, Toast.LENGTH_SHORT).show();
            return;
        }
        String plaintextBody;
        if (messageToPin.isGeoUri()) {
            plaintextBody = messageToPin.getRawBody();
        } else {
            plaintextBody = messageToPin.getBody(); // Use appropriate method to get plaintext
        }
        // --- START NEW ---
        // Attempt to get a CID from the message.
        // This depends on how CIDs are stored or associated with your Message object.
        Cid cid = null;
        if (messageToPin.isFileOrImage()) { // Or some other method to check if it's a file/image
            cid = messageToPin.getFileParams().getCids().get(0); // Assuming there's only one CID
        }

        // You might want to allow pinning even if plaintextBody is empty, if a CID exists.
        // The repository now handles the case where plaintextBody is null but CID is present.
        if ((plaintextBody == null || plaintextBody.isEmpty()) && (cid == null || cid.toString().isEmpty())) {
            Toast.makeText(activity, R.string.cannot_pin_empty_content, Toast.LENGTH_SHORT).show(); // Updated string
            return;
        }
        // --- END NEW ---

        // Call the updated repository method, now including the CID
        pinnedMessageRepository.pinMessage(
                messageToPin.getUuid(),
                conversation.getUuid(),
                plaintextBody, // This can be null if you're only pinning based on CID
                cid,           // Pass the CID here
                success -> {
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (success) {
                                Toast.makeText(activity, R.string.message_pinned, Toast.LENGTH_SHORT).show();
                                loadAndDisplayLatestPinnedMessage(); // Refresh the view
                            } else {
                                Toast.makeText(activity, R.string.error_pinning_message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    // Call this from an "Unpin" button on the pinned message view
    public void unpinCurrentDisplayedMessage() {
        if (currentDisplayedPinnedMessageUuid == null || pinnedMessageRepository == null || activity == null) {
            return;
        }
        String uuidToUnpin = currentDisplayedPinnedMessageUuid; // Copy in case it changes

        pinnedMessageRepository.unpinMessage(uuidToUnpin,
                success -> {
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            if (success) {
                                Toast.makeText(activity, R.string.message_unpinned, Toast.LENGTH_SHORT).show();
                                // If the unpinned message was the one displayed, clear or load next
                                if (Objects.equals(currentDisplayedPinnedMessageUuid, uuidToUnpin)) {
                                    loadAndDisplayLatestPinnedMessage(); // This will hide if no more pins
                                }
                            } else {
                                Toast.makeText(activity, R.string.error_unpinning_message, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    private void hidePinnedMessageView() {
        if (binding.pinnedMessageContainer != null) { // Check if views are initialized
            binding.pinnedMessageContainer.setVisibility(View.GONE);
            binding.pinnedMessageText.setText("");
            binding.pinnedMessageImageThumbnail.setImageDrawable(null); // Clear image
            binding.pinnedMessageFileIcon.setImageDrawable(null); // Clear Icon
            binding.pinnedMessageContainer.setTag(null);
        }
        currentDisplayedPinnedMessageUuid = null;
        currentDisplayedPinnedMessageCid = null;
    }
}
