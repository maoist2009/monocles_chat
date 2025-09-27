package eu.siacs.conversations.parser;

import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.util.Log;
import android.util.Pair;

import de.monocles.chat.BobTransfer;
import de.monocles.chat.WebxdcUpdate;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;

import java.io.File;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import eu.siacs.conversations.crypto.OtrService;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.xmpp.pep.UserTune;
import io.ipfs.cid.Cid;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.BrokenSessionException;
import eu.siacs.conversations.crypto.axolotl.NotEncryptedForThisDeviceException;
import eu.siacs.conversations.crypto.axolotl.OutdatedSenderException;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Reaction;
import eu.siacs.conversations.entities.ReadByMarker;
import eu.siacs.conversations.entities.ReceiptRequest;
import eu.siacs.conversations.entities.RtpSessionStatus;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.LocalizedContent;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.pep.Avatar;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.axolotl.Encrypted;
import im.conversations.android.xmpp.model.carbons.Received;
import im.conversations.android.xmpp.model.carbons.Sent;
import im.conversations.android.xmpp.model.correction.Replace;
import im.conversations.android.xmpp.model.forward.Forwarded;
import im.conversations.android.xmpp.model.markers.Displayed;
import im.conversations.android.xmpp.model.occupant.OccupantId;
import im.conversations.android.xmpp.model.reactions.Reactions;

public class MessageParser extends AbstractParser
        implements Consumer<im.conversations.android.xmpp.model.stanza.Message> {

    private static final List<String> CLIENTS_SENDING_HTML_IN_OTR =
            Arrays.asList("Pidgin", "Adium", "Trillian");

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);

    private static final List<String> JINGLE_MESSAGE_ELEMENT_NAMES =
            Arrays.asList("accept", "propose", "proceed", "reject", "retract", "ringing", "finish");

    public MessageParser(final XmppConnectionService service, final Account account) {
        super(service, account);
    }

    private static String extractStanzaId(
            Element packet, boolean isTypeGroupChat, Conversation conversation) {
        final Jid by;
        final boolean safeToExtract;
        if (isTypeGroupChat) {
            by = conversation.getJid().asBareJid();
            safeToExtract = conversation.getMucOptions().hasFeature(Namespace.STANZA_IDS);
        } else {
            Account account = conversation.getAccount();
            by = account.getJid().asBareJid();
            safeToExtract = account.getXmppConnection().getFeatures().stanzaIds();
        }
        return safeToExtract ? extractStanzaId(packet, by) : null;
    }

    private static String extractStanzaId(Account account, Element packet) {
        final boolean safeToExtract = account.getXmppConnection().getFeatures().stanzaIds();
        return safeToExtract ? extractStanzaId(packet, account.getJid().asBareJid()) : null;
    }

    private static String extractStanzaId(Element packet, Jid by) {
        for (Element child : packet.getChildren()) {
            if (child.getName().equals("stanza-id")
                    && Namespace.STANZA_IDS.equals(child.getNamespace())
                    && by.equals(Jid.Invalid.getNullForInvalid(child.getAttributeAsJid("by")))) {
                return child.getAttribute("id");
            }
        }
        return null;
    }

    private static Jid getTrueCounterpart(Element mucUserElement, Jid fallback) {
        final Element item = mucUserElement == null ? null : mucUserElement.findChild("item");
        Jid result =
                item == null ? null : Jid.Invalid.getNullForInvalid(item.getAttributeAsJid("jid"));
        return result != null ? result : fallback;
    }

    private static boolean clientMightSendHtml(Account account, Jid from) {
        String resource = from.getResource();
        if (resource == null) {
            return false;
        }
        Presence presence = account.getRoster().getContact(from).getPresences().getPresencesMap().get(resource);
        ServiceDiscoveryResult disco = presence == null ? null : presence.getServiceDiscoveryResult();
        if (disco == null) {
            return false;
        }
        return hasIdentityKnowForSendingHtml(disco.getIdentities());
    }

    private static boolean hasIdentityKnowForSendingHtml(List<ServiceDiscoveryResult.Identity> identities) {
        for (ServiceDiscoveryResult.Identity identity : identities) {
            if (identity.getName() != null) {
                if (CLIENTS_SENDING_HTML_IN_OTR.contains(identity.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean extractChatState(
            Conversation c,
            final boolean isTypeGroupChat,
            final im.conversations.android.xmpp.model.stanza.Message packet) {
        ChatState state = ChatState.parse(packet);
        if (state != null && c != null) {
            final Account account = c.getAccount();
            final Jid from = packet.getFrom();
            if (from.asBareJid().equals(account.getJid().asBareJid())) {
                c.setOutgoingChatState(state);
                if (state == ChatState.ACTIVE || state == ChatState.COMPOSING) {
                    if (c.getContact().isSelf()) {
                        return false;
                    }
                    mXmppConnectionService.markRead(c);
                    activateGracePeriod(account);
                }
                return false;
            } else {
                if (isTypeGroupChat) {
                    MucOptions.User user = c.getMucOptions().findUserByFullJid(from);
                    if (user != null) {
                        return user.setChatState(state);
                    } else {
                        return false;
                    }
                } else {
                    return c.setIncomingChatState(state);
                }
            }
        }
        return false;
    }


    private Message parseOtrChat(String body, Jid from, String id, Conversation conversation) {
        String presence;
        if (from.isBareJid()) {
            presence = "";
        } else {
            presence = from.getResource();
        }
        if (body.matches("^\\?OTRv\\d{1,2}\\?.*")) {
            conversation.endOtrIfNeeded();
        }
        if (!conversation.hasValidOtrSession()) {
            conversation.startOtrSession(presence, false);
        } else {
            String foreignPresence = conversation.getOtrSession().getSessionID().getUserID();
            if (!foreignPresence.equals(presence)) {
                conversation.endOtrIfNeeded();
                conversation.startOtrSession(presence, false);
            }
        }
        try {
            conversation.setLastReceivedOtrMessageId(id);
            Session otrSession = conversation.getOtrSession();
            body = otrSession.transformReceiving(body);
            SessionStatus status = otrSession.getSessionStatus();
            if (body == null && status == SessionStatus.ENCRYPTED) {
                mXmppConnectionService.onOtrSessionEstablished(conversation);
                return null;
            } else if (body == null && status == SessionStatus.FINISHED) {
                conversation.resetOtrSession();
                mXmppConnectionService.updateConversationUi();
                return null;
            } else if (body == null || (body.isEmpty())) {
                return null;
            }
            if (body.startsWith(CryptoHelper.FILETRANSFER)) {
                String key = body.substring(CryptoHelper.FILETRANSFER.length());
                conversation.setSymmetricKey(CryptoHelper.hexToBytes(key));
                return null;
            }
            if (clientMightSendHtml(conversation.getAccount(), from)) {
                Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": received OTR message from bad behaving client. escaping HTML…");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    body = Html.fromHtml(body, Html.FROM_HTML_MODE_LEGACY).toString();
                } else {
                    body = Html.fromHtml(body).toString();
                }
            }

            final OtrService otrService = conversation.getAccount().getOtrService();
            Message finishedMessage = new Message(conversation, body, Message.ENCRYPTION_OTR, Message.STATUS_RECEIVED);
            finishedMessage.setFingerprint(otrService.getFingerprint(otrSession.getRemotePublicKey()));
            conversation.setLastReceivedOtrMessageId(null);

            return finishedMessage;
        } catch (Exception e) {
            conversation.resetOtrSession();
            return null;
        }
    }

    private Message parseAxolotlChat(
            final Encrypted axolotlMessage,
            final Jid from,
            final Conversation conversation,
            final int status,
            final boolean checkedForDuplicates,
            final boolean postpone) {
        final AxolotlService service = conversation.getAccount().getAxolotlService();
        final XmppAxolotlMessage xmppAxolotlMessage;
        try {
            xmppAxolotlMessage = XmppAxolotlMessage.fromElement(axolotlMessage, from.asBareJid());
        } catch (final Exception e) {
            Log.d(
                    Config.LOGTAG,
                    conversation.getAccount().getJid().asBareJid()
                            + ": invalid omemo message received "
                            + e.getMessage());
            return null;
        }
        if (xmppAxolotlMessage.hasPayload()) {
            final XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage;
            try {
                plaintextMessage =
                        service.processReceivingPayloadMessage(xmppAxolotlMessage, postpone);
            } catch (BrokenSessionException e) {
                if (checkedForDuplicates) {
                    if (service.trustedOrPreviouslyResponded(from.asBareJid())) {
                        service.reportBrokenSessionException(e, postpone);
                        return new Message(
                                conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                "ignoring broken session exception because contact was not"
                                        + " trusted");
                        return new Message(
                                conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
                    }
                } else {
                    Log.d(
                            Config.LOGTAG,
                            "ignoring broken session exception because checkForDuplicates failed");
                    return null;
                }
            } catch (NotEncryptedForThisDeviceException e) {
                return new Message(
                        conversation, "", Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE, status);
            } catch (OutdatedSenderException e) {
                return new Message(conversation, "", Message.ENCRYPTION_AXOLOTL_FAILED, status);
            }
            if (plaintextMessage != null) {
                Message finishedMessage =
                        new Message(
                                conversation,
                                plaintextMessage.getPlaintext(),
                                Message.ENCRYPTION_AXOLOTL,
                                status);
                finishedMessage.setFingerprint(plaintextMessage.getFingerprint());
                Log.d(
                        Config.LOGTAG,
                        AxolotlService.getLogprefix(finishedMessage.getConversation().getAccount())
                                + " Received Message with session fingerprint: "
                                + plaintextMessage.getFingerprint());
                return finishedMessage;
            }
        } else {
            Log.d(
                    Config.LOGTAG,
                    conversation.getAccount().getJid().asBareJid()
                            + ": received OMEMO key transport message");
            service.processReceivingKeyTransportMessage(xmppAxolotlMessage, postpone);
        }
        return null;
    }

    private Invite extractInvite(final Element message) {
        final Element mucUser = message.findChild("x", Namespace.MUC_USER);
        if (mucUser != null) {
            final Element invite = mucUser.findChild("invite");
            if (invite != null) {
                final String password = mucUser.findChildContent("password");
                final Jid from = Jid.Invalid.getNullForInvalid(invite.getAttributeAsJid("from"));
                final Jid to = Jid.Invalid.getNullForInvalid(invite.getAttributeAsJid("to"));
                if (to != null && from == null) {
                    Log.d(Config.LOGTAG, "do not parse outgoing mediated invite " + message);
                    return null;
                }
                final Jid room = Jid.Invalid.getNullForInvalid(message.getAttributeAsJid("from"));
                if (room == null) {
                    return null;
                }
                return new Invite(room, password, false, from);
            }
        }
        final Element conference = message.findChild("x", "jabber:x:conference");
        if (conference != null) {
            Jid from = Jid.Invalid.getNullForInvalid(message.getAttributeAsJid("from"));
            Jid room = Jid.Invalid.getNullForInvalid(conference.getAttributeAsJid("jid"));
            if (room == null) {
                return null;
            }
            return new Invite(room, conference.getAttribute("password"), true, from);
        }
        return null;
    }

    private void parseEvent(final Element event, final Jid from, final Account account) {
        final Element items = event.findChild("items");
        final String node = items == null ? null : items.getAttribute("node");
        if (Namespace.AVATAR_METADATA.equals(node)) {
            Avatar avatar = Avatar.parseMetadata(items);
            if (avatar != null) {
                avatar.owner = from.asBareJid();
                if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                    if (account.getJid().asBareJid().equals(from)) {
                        if (account.setAvatar(avatar.getFilename())) {
                            mXmppConnectionService.databaseBackend.updateAccount(account);
                            mXmppConnectionService.notifyAccountAvatarHasChanged(account);
                        }
                        mXmppConnectionService.getAvatarService().clear(account);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateAccountUi();
                    } else {
                        final Contact contact = account.getRoster().getContact(from);
                        if (contact.setAvatar(avatar)) {
                            mXmppConnectionService.syncRoster(account);
                            mXmppConnectionService.getAvatarService().clear(contact);
                            mXmppConnectionService.updateConversationUi();
                            mXmppConnectionService.updateRosterUi(XmppConnectionService.UpdateRosterReason.AVATAR);
                        }
                    }
                } else if (mXmppConnectionService.isDataSaverDisabled()) {
                    mXmppConnectionService.fetchAvatar(account, avatar);
                }
            }
        } else if (Namespace.NICK.equals(node)) {
            final Element i = items.findChild("item");
            final String nick = i == null ? null : i.findChildContent("nick", Namespace.NICK);
            if (nick != null) {
                setNick(account, from, nick);
            }
        } else if (AxolotlService.PEP_DEVICE_LIST.equals(node)) {
            Element item = items.findChild("item");
            final Set<Integer> deviceIds = IqParser.deviceIds(item);
            Log.d(
                    Config.LOGTAG,
                    AxolotlService.getLogprefix(account)
                            + "Received PEP device list "
                            + deviceIds
                            + " update from "
                            + from
                            + ", processing... ");
            final AxolotlService axolotlService = account.getAxolotlService();
            axolotlService.registerDevices(from, deviceIds);
        } else if (Namespace.BOOKMARKS.equals(node) && account.getJid().asBareJid().equals(from)) {
            final var connection = account.getXmppConnection();
            if (connection.getFeatures().bookmarksConversion()) {
                if (connection.getFeatures().bookmarks2()) {
                    Log.w(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": received storage:bookmark notification even though we"
                                    + " opted into bookmarks:1");
                }
                final Element i = items.findChild("item");
                final Element storage =
                        i == null ? null : i.findChild("storage", Namespace.BOOKMARKS);
                final Map<Jid, Bookmark> bookmarks = Bookmark.parseFromStorage(storage, account);
                mXmppConnectionService.processBookmarksInitial(account, bookmarks, true);
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid() + ": processing bookmark PEP event");
            } else {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignoring bookmark PEP event because bookmark conversion was"
                                + " not detected");
            }
        } else if (Namespace.BOOKMARKS2.equals(node) && account.getJid().asBareJid().equals(from)) {
            final Element item = items.findChild("item");
            final Element retract = items.findChild("retract");
            if (item != null) {
                final Bookmark bookmark = Bookmark.parseFromItem(item, account);
                if (bookmark != null) {
                    account.putBookmark(bookmark);
                    mXmppConnectionService.processModifiedBookmark(bookmark);
                    mXmppConnectionService.updateConversationUi();
                }
            }
            if (retract != null) {
                final Jid id = Jid.Invalid.getNullForInvalid(retract.getAttributeAsJid("id"));
                if (id != null) {
                    account.removeBookmark(id);
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid() + ": deleted bookmark for " + id);
                    mXmppConnectionService.processDeletedBookmark(account, id);
                    mXmppConnectionService.updateConversationUi();
                }
            }
        } else if (Config.MESSAGE_DISPLAYED_SYNCHRONIZATION
                && Namespace.MDS_DISPLAYED.equals(node)
                && account.getJid().asBareJid().equals(from)) {
            final Element item = items.findChild("item");
            mXmppConnectionService.processMdsItem(account, item);
        } else if (Namespace.USER_TUNE.equals(node)) {
            final Conversation conversation =
                    mXmppConnectionService.find(account, from.asBareJid());
            if (conversation != null) { // Check if conversation exists
                final Contact contact = conversation.getContact();
                    if (contact != null) { // Check if contact exists
                        final UserTune lastTune = contact.getUserTune();
                        final UserTune thisTune = UserTune.parse(items);

                        if (!Objects.equals(lastTune, thisTune)) {
                            contact.setUserTune(UserTune.parse(items));
                            mXmppConnectionService.updateConversationUi();
                        }
                } else {
                    Log.w(Config.LOGTAG, "Contact not found for conversation: " + conversation.getJid());
                }
            } else {
                Log.w(Config.LOGTAG, "Conversation not found for JID: " + from.asBareJid());
            }
        } else {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + " received pubsub notification for node="
                            + node);
        }
    }

    private void parseDeleteEvent(final Element event, final Jid from, final Account account) {
        final Element delete = event.findChild("delete");
        final String node = delete == null ? null : delete.getAttribute("node");
        if (Namespace.NICK.equals(node)) {
            Log.d(Config.LOGTAG, "parsing nick delete event from " + from);
            setNick(account, from, null);
        } else if (Namespace.BOOKMARKS2.equals(node) && account.getJid().asBareJid().equals(from)) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": deleted bookmarks node");
            deleteAllBookmarks(account);
        } else if (Namespace.AVATAR_METADATA.equals(node)
                && account.getJid().asBareJid().equals(from)) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": deleted avatar metadata node");
        }
    }

    private void parsePurgeEvent(final Element event, final Jid from, final Account account) {
        final Element purge = event.findChild("purge");
        final String node = purge == null ? null : purge.getAttribute("node");
        if (Namespace.BOOKMARKS2.equals(node) && account.getJid().asBareJid().equals(from)) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": purged bookmarks");
            deleteAllBookmarks(account);
        }
    }

    private void deleteAllBookmarks(final Account account) {
        final var previous = account.getBookmarkedJids();
        account.setBookmarks(Collections.emptyMap());
        mXmppConnectionService.processDeletedBookmarks(account, previous);
    }

    private void setNick(final Account account, final Jid user, final String nick) {
        if (user.asBareJid().equals(account.getJid().asBareJid())) {
            account.setDisplayName(nick);
            if (QuickConversationsService.isQuicksy()) {
                mXmppConnectionService.getAvatarService().clear(account);
            }
            mXmppConnectionService.checkMucRequiresRename();
        } else {
            Contact contact = account.getRoster().getContact(user);
            if (contact.setPresenceName(nick)) {
                mXmppConnectionService.syncRoster(account);
                mXmppConnectionService.getAvatarService().clear(contact);
            }
        }
        mXmppConnectionService.updateConversationUi();
        mXmppConnectionService.updateAccountUi();
    }

    private boolean handleErrorMessage(
            final Account account,
            final im.conversations.android.xmpp.model.stanza.Message packet) {
        if (packet.getType() == im.conversations.android.xmpp.model.stanza.Message.Type.ERROR) {
            if (packet.fromServer(account)) {
                final var forwarded =
                        getForwardedMessagePacket(packet, "received", Namespace.CARBONS);
                if (forwarded != null) {
                    return handleErrorMessage(account, forwarded.first);
                }
            }
            final Jid from = packet.getFrom();
            final String id = packet.getId();
            if (from != null && id != null) {
                final Message message = mXmppConnectionService.markMessage(account,
                        from.asBareJid(),
                        packet.getId(),
                        Message.STATUS_SEND_FAILED,
                        extractErrorMessage(packet));
                if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
                    final String sessionId =
                            id.substring(
                                    JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length());
                    mXmppConnectionService
                            .getJingleConnectionManager()
                            .updateProposedSessionDiscovered(
                                    account,
                                    from,
                                    sessionId,
                                    JingleConnectionManager.DeviceDiscoveryState.FAILED);
                    return true;
                }
                if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROCEED_ID_PREFIX)) {
                    final String sessionId =
                            id.substring(
                                    JingleRtpConnection.JINGLE_MESSAGE_PROCEED_ID_PREFIX.length());
                    final String errorMessage = extractErrorMessage(packet);
                    mXmppConnectionService
                            .getJingleConnectionManager()
                            .failProceed(account, from, sessionId, errorMessage);
                    return true;
                }
                mXmppConnectionService.markMessage(
                        account,
                        from.asBareJid(),
                        id,
                        Message.STATUS_SEND_FAILED,
                        extractErrorMessage(packet));
                final Element error = packet.findChild("error");
                final boolean pingWorthyError =
                        error != null
                                && (error.hasChild("not-acceptable")
                                || error.hasChild("remote-server-timeout")
                                || error.hasChild("remote-server-not-found"));
                if (pingWorthyError) {
                    Conversation conversation = mXmppConnectionService.find(account, from);
                    if (conversation != null
                            && conversation.getMode() == Conversational.MODE_MULTI) {
                        if (conversation.getMucOptions().online()) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": received ping worthy error for seemingly online"
                                            + " muc at "
                                            + from);
                            mXmppConnectionService.mucSelfPingAndRejoin(conversation);
                        }
                    }
                }
                if (message != null) {
                    if (message.getEncryption() == Message.ENCRYPTION_OTR) {
                        Conversation conversation = (Conversation) message.getConversation();
                        conversation.endOtrIfNeeded();
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void accept(final im.conversations.android.xmpp.model.stanza.Message original) {
        if (handleErrorMessage(account, original)) {
            return;
        }
        final im.conversations.android.xmpp.model.stanza.Message packet;
        Long timestamp = null;
        final boolean isForwarded;
        boolean isCarbon = false;
        String serverMsgId = null;
        final Element fin =
                original.findChild("fin", MessageArchiveService.Version.MAM_0.namespace);
        if (fin != null) {
            mXmppConnectionService
                    .getMessageArchiveService()
                    .processFinLegacy(fin, original.getFrom());
            return;
        }
        final Element result = MessageArchiveService.Version.findResult(original);
        final String queryId = result == null ? null : result.getAttribute("queryid");
        final MessageArchiveService.Query query =
                queryId == null
                        ? null
                        : mXmppConnectionService.getMessageArchiveService().findQuery(queryId);
        final boolean offlineMessagesRetrieved =
                account.getXmppConnection().isOfflineMessagesRetrieved();
        if (query != null && query.validFrom(original.getFrom())) {
            final var f = getForwardedMessagePacket(original, "result", query.version.namespace);
            if (f == null) {
                return;
            }
            timestamp = f.second;
            packet = f.first;
            isForwarded = true;
            serverMsgId = result.getAttribute("id");
            query.incrementMessageCount();
            if (handleErrorMessage(account, packet)) {
                return;
            }
            final var contact = packet.getFrom() == null || packet.getFrom() instanceof Jid.Invalid ? null : account.getRoster().getContact(packet.getFrom());
            if (contact != null && contact.isBlocked()) {
                Log.d(Config.LOGTAG, "Got MAM result from blocked contact, ignoring...");
                return;
            }
        } else if (query != null) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": received mam result with invalid from ("
                            + original.getFrom()
                            + ") or queryId ("
                            + queryId
                            + ")");
            return;
        } else if (original.fromServer(account)
                && original.getType()
                != im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT) {
            Pair<im.conversations.android.xmpp.model.stanza.Message, Long> f;
            f = getForwardedMessagePacket(original, Received.class);
            f = f == null ? getForwardedMessagePacket(original, Sent.class) : f;
            packet = f != null ? f.first : original;
            if (handleErrorMessage(account, packet)) {
                return;
            }
            timestamp = f != null ? f.second : null;
            isCarbon = f != null;
            isForwarded = isCarbon;
        } else {
            packet = original;
            isForwarded = false;
        }

        if (timestamp == null) {
            timestamp =
                    AbstractParser.parseTimestamp(original, AbstractParser.parseTimestamp(packet));
        }

        final Element mucUserElement = packet.findChild("x", Namespace.MUC_USER);
        final boolean isTypeGroupChat =
                packet.getType()
                        == im.conversations.android.xmpp.model.stanza.Message.Type.GROUPCHAT;
        final String pgpEncrypted = packet.findChildContent("x", "jabber:x:encrypted");

        Element replaceElement = packet.findChild("replace", "urn:xmpp:message-correct:0");
        Set<Message.FileParams> attachments = new LinkedHashSet<>();
        for (Element child : packet.getChildren()) {
            // SIMS first so they get preference in the set
            if (child.getName().equals("reference") && child.getNamespace().equals("urn:xmpp:reference:0")) {
                if (child.findChild("media-sharing", "urn:xmpp:sims:1") != null) {
                    attachments.add(new Message.FileParams(child));
                }
            }
        }
        for (Element child : packet.getChildren()) {
            if (child.getName().equals("x") && child.getNamespace().equals(Namespace.OOB)) {
                attachments.add(new Message.FileParams(child));
            }
        }
        String replacementId = replaceElement == null ? null : replaceElement.getAttribute("id");
        if (replacementId == null) {
            final Element fasten = packet.findChild("apply-to", "urn:xmpp:fasten:0");
            if (fasten != null) {
                replaceElement = fasten.findChild("retract", "urn:xmpp:message-retract:0");
                if (replaceElement == null) replaceElement = fasten.findChild("moderated", "urn:xmpp:message-moderate:0");
            }
            if (replaceElement == null) replaceElement = packet.findChild("retract", "urn:xmpp:message-retract:1");
            if (replaceElement == null) replaceElement = packet.findChild("moderate", "urn:xmpp:message-moderate:1");
            if (replaceElement != null) {
                var reason = replaceElement.findChildContent("reason", "urn:xmpp:message-moderate:0");
                if (reason == null) reason = replaceElement.findChildContent("reason", "urn:xmpp:message-moderate:1");
                replacementId = (fasten == null ? replaceElement : fasten).getAttribute("id");
                packet.setBody(reason == null ? "" : reason);   //TODO: fix this
            }
        }
        LocalizedContent body = packet.getBody();

        final var reactions = packet.getExtension(Reactions.class);

        final var axolotlEncrypted = packet.getOnlyExtension(Encrypted.class);
        int status;
        final Jid counterpart;
        final Jid to = packet.getTo();
        final Jid from = packet.getFrom();
        final Element originId = packet.findChild("origin-id", Namespace.STANZA_IDS);
        final String remoteMsgId;
        if (originId != null && originId.getAttribute("id") != null) {
            remoteMsgId = originId.getAttribute("id");
        } else {
            remoteMsgId = packet.getId();
        }
        boolean notify = false;

        Element html = packet.findChild("html", "http://jabber.org/protocol/xhtml-im");
        if (html != null && html.findChild("body", "http://www.w3.org/1999/xhtml") == null) {
            html = null;
        }

        if (from == null || !Jid.Invalid.isValid(from) || !Jid.Invalid.isValid(to)) {
            Log.e(Config.LOGTAG, "encountered invalid message from='" + from + "' to='" + to + "'");
            return;
        }
        if (query != null && !query.muc() && isTypeGroupChat) {
            Log.e(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": received groupchat ("
                            + from
                            + ") message on regular MAM request. skipping");
            return;
        }
        final Jid mucTrueCounterPart;
        final OccupantId occupant;
        if (isTypeGroupChat) {
            final Conversation conversation =
                    mXmppConnectionService.find(account, from.asBareJid());
            final Jid mucTrueCounterPartByPresence;
            if (conversation != null) {
                final var mucOptions = conversation.getMucOptions();
                occupant = mucOptions.occupantId() ? packet.getExtension(OccupantId.class) : null;
                final var user =
                        occupant == null ? null : mucOptions.findUserByOccupantId(occupant.getId(), from);
                mucTrueCounterPartByPresence = user == null ? null : user.getRealJid();
            } else {
                occupant = null;
                mucTrueCounterPartByPresence = null;
            }
            mucTrueCounterPart =
                    getTrueCounterpart(
                            (query != null && query.safeToExtractTrueCounterpart())
                                    ? mucUserElement
                                    : null,
                            mucTrueCounterPartByPresence);
        } else if (mucUserElement != null) {
            final Conversation conversation =
                    mXmppConnectionService.find(account, from.asBareJid());
            if (conversation != null) {
                final var mucOptions = conversation.getMucOptions();
                occupant = mucOptions.occupantId() ? packet.getExtension(OccupantId.class) : null;
            } else {
                occupant = null;
            }
            mucTrueCounterPart = null;
        } else {
            mucTrueCounterPart = null;
            occupant = null;
        }
        boolean isProperlyAddressed = (to != null) && (!to.isBareJid() || account.countPresences() == 0);
        boolean isMucStatusMessage =
                Jid.Invalid.hasValidFrom(packet)
                        && from.isBareJid()
                        && mucUserElement != null
                        && mucUserElement.hasChild("status");
        boolean selfAddressed;
        if (packet.fromAccount(account)) {
            status = Message.STATUS_SEND;
            selfAddressed = to == null || account.getJid().asBareJid().equals(to.asBareJid());
            if (selfAddressed) {
                counterpart = from;
            } else {
                counterpart = to != null ? to : account.getJid();
            }
        } else {
            status = Message.STATUS_RECEIVED;
            counterpart = from;
            selfAddressed = false;
        }

        final Invite invite = extractInvite(packet);
        if (invite != null) {
            if (invite.jid.asBareJid().equals(account.getJid().asBareJid())) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignore invite to "
                                + invite.jid
                                + " because it matches account");
            } else if (isTypeGroupChat) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignoring invite to "
                                + invite.jid
                                + " because it was received as group chat");
            } else if (invite.direct
                    && (mucUserElement != null
                    || invite.inviter == null
                    || mXmppConnectionService.isMuc(account, invite.inviter))) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": ignoring direct invite to "
                                + invite.jid
                                + " because it was received in MUC");
            } else {
                invite.execute(account);
                return;
            }
        }

        final boolean conversationIsProbablyMuc = isTypeGroupChat || mucUserElement != null || account.getXmppConnection().getMucServersWithholdAccount().contains(counterpart.getDomain().toString());
        final Element webxdc = packet.findChild("x", "urn:xmpp:webxdc:0");
        final Element thread = packet.findChild("thread");
        if (webxdc != null && thread != null) {
            final Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.asBareJid(), conversationIsProbablyMuc, false, query, false);
            Jid webxdcSender = counterpart.asBareJid();
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                if(conversation.getMucOptions().nonanonymous()) {
                    webxdcSender = conversation.getMucOptions().getTrueCounterpart(counterpart);
                } else {
                    webxdcSender = counterpart;
                }
            }
            final var document = webxdc.findChildContent("document", "urn:xmpp:webxdc:0");
            final var summary = webxdc.findChildContent("summary", "urn:xmpp:webxdc:0");
            final var payload = webxdc.findChildContent("json", "urn:xmpp:json:0");
            if (document != null || summary != null || payload != null) {
                mXmppConnectionService.insertWebxdcUpdate(new WebxdcUpdate(
                        conversation,
                        remoteMsgId,
                        counterpart,
                        thread,
                        body == null ? null : body.content,
                        document,
                        summary,
                        payload
                ));
            }

            final var realtime = webxdc.findChildContent("data", "urn:xmpp:webxdc:0");
            if (realtime != null) conversation.webxdcRealtimeData(thread, realtime);

            mXmppConnectionService.updateConversationUi();
        }

        // Basic visibility for voice requests
        if (body == null && html == null && pgpEncrypted == null && axolotlEncrypted == null && !isMucStatusMessage) {
            final Element formEl = packet.findChild("x", "jabber:x:data");
            if (formEl != null) {
                final Data form = Data.parse(formEl);
                final String role = form.getValue("muc#role");
                final String nick = form.getValue("muc#roomnick");
                if ("http://jabber.org/protocol/muc#request".equals(form.getFormType()) && "participant".equals(role)) {
                    body = new LocalizedContent("" + nick + " " + mXmppConnectionService.getString(R.string.is_requesting_to_speak), "en", 1);
                }
            }
        }

        if (reactions == null && (body != null
                || pgpEncrypted != null
                || (axolotlEncrypted != null && axolotlEncrypted.hasChild("payload"))
                || !attachments.isEmpty() || html != null || (packet.hasChild("subject") && packet.hasChild("thread")))
                && !isMucStatusMessage) {
            final Conversation conversation =
                    mXmppConnectionService.findOrCreateConversation(
                            account,
                            counterpart.asBareJid(),
                            conversationIsProbablyMuc,
                            false,
                            query,
                            false);
            final boolean conversationMultiMode = conversation.getMode() == Conversation.MODE_MULTI;

            if (serverMsgId == null) {
                serverMsgId = extractStanzaId(packet, isTypeGroupChat, conversation);
            }

            if (selfAddressed) {
                // don’t store serverMsgId on reflections for edits
                final var reflectedServerMsgId =
                        Strings.isNullOrEmpty(replacementId) ? serverMsgId : null;
                if (mXmppConnectionService.markMessage(
                        conversation,
                        remoteMsgId,
                        Message.STATUS_SEND_RECEIVED,
                        reflectedServerMsgId)) {
                    return;
                }
                status = Message.STATUS_RECEIVED;
                if (remoteMsgId != null
                        && conversation.findMessageWithRemoteId(remoteMsgId, counterpart) != null) {
                    return;
                }
            }

            if (isTypeGroupChat) {
                if (conversation.getMucOptions().isSelf(counterpart)) {
                    status = Message.STATUS_SEND_RECEIVED;
                    isCarbon = true; // not really carbon but received from another resource
                    // don’t store serverMsgId on reflections for edits
                    final var reflectedServerMsgId =
                            Strings.isNullOrEmpty(replacementId) ? serverMsgId : null;
                    if (mXmppConnectionService.markMessage(conversation, remoteMsgId, status, reflectedServerMsgId, body, html, packet.findChildContent("subject"), packet.findChild("thread"), attachments)) {
                        return;
                    } else if (remoteMsgId == null || Config.IGNORE_ID_REWRITE_IN_MUC) {
                        if (body != null) {
                            Message message = conversation.findSentMessageWithBody(body.content);
                            if (message != null) {
                                mXmppConnectionService.markMessage(message, status);
                                return;
                            }
                        }
                    }
                } else {
                    status = Message.STATUS_RECEIVED;
                }
            }
            final Message message;
            if (body != null && body.content.startsWith("?OTR") && Config.supportOtr()) {
                if (!isForwarded && !isTypeGroupChat && isProperlyAddressed && !conversationMultiMode) {
                    message = parseOtrChat(body.content, from, remoteMsgId, conversation);
                    if (message == null) {
                        return;
                    }
                } else {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ignoring OTR message from " + from + " isForwarded=" + Boolean.toString(isForwarded) + ", isProperlyAddressed=" + Boolean.valueOf(isProperlyAddressed));
                    message = new Message(conversation, body.content, Message.ENCRYPTION_NONE, status);
                    if (body.count > 1) {
                        message.setBodyLanguage(body.language);
                    }
                }
            } else if (pgpEncrypted != null && Config.supportOpenPgp()) {
                message = new Message(conversation, pgpEncrypted, Message.ENCRYPTION_PGP, status);
            } else if (axolotlEncrypted != null && Config.supportOmemo()) {
                Jid origin;
                Set<Jid> fallbacksBySourceId = Collections.emptySet();
                if (conversationMultiMode) {
                    final Jid fallback =
                            conversation.getMucOptions().getTrueCounterpart(counterpart);
                    origin = getTrueCounterpart(query != null ? mucUserElement : null, fallback);
                    if (origin == null) {
                        try {
                            fallbacksBySourceId =
                                    account.getAxolotlService()
                                            .findCounterpartsBySourceId(
                                                    XmppAxolotlMessage.parseSourceId(
                                                            axolotlEncrypted));
                        } catch (IllegalArgumentException e) {
                            // ignoring
                        }
                    }
                    if (origin == null && fallbacksBySourceId.isEmpty()) {
                        Log.d(
                                Config.LOGTAG,
                                "axolotl message in anonymous conference received and no possible"
                                        + " fallbacks");
                        return;
                    }
                } else {
                    fallbacksBySourceId = Collections.emptySet();
                    origin = from;
                }

                final boolean liveMessage =
                        query == null && !isTypeGroupChat && mucUserElement == null;
                final boolean checkedForDuplicates =
                        liveMessage
                                || (serverMsgId != null
                                && remoteMsgId != null
                                && !conversation.possibleDuplicate(
                                serverMsgId, remoteMsgId));

                if (origin != null) {
                    message =
                            parseAxolotlChat(
                                    axolotlEncrypted,
                                    origin,
                                    conversation,
                                    status,
                                    checkedForDuplicates,
                                    query != null);
                } else {
                    Message trial = null;
                    for (Jid fallback : fallbacksBySourceId) {
                        trial =
                                parseAxolotlChat(
                                        axolotlEncrypted,
                                        fallback,
                                        conversation,
                                        status,
                                        checkedForDuplicates && fallbacksBySourceId.size() == 1,
                                        query != null);
                        if (trial != null) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": decoded muc message using fallback");
                            origin = fallback;
                            break;
                        }
                    }
                    message = trial;
                }
                if (message == null) {
                    if (query == null
                            && extractChatState(
                            mXmppConnectionService.find(account, counterpart.asBareJid()),
                            isTypeGroupChat,
                            packet)) {
                        mXmppConnectionService.updateConversationUi();
                    }
                    if (query != null && status == Message.STATUS_SEND && remoteMsgId != null) {
                        Message previouslySent = conversation.findSentMessageWithUuid(remoteMsgId);
                        if (previouslySent != null
                                && previouslySent.getServerMsgId() == null
                                && serverMsgId != null) {
                            previouslySent.setServerMsgId(serverMsgId);
                            mXmppConnectionService.databaseBackend.updateMessage(
                                    previouslySent, false);
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": encountered previously sent OMEMO message without"
                                            + " serverId. updating...");
                        }
                    }
                    return;
                }
                if (conversationMultiMode) {
                    message.setTrueCounterpart(origin);
                }
            } else if (body == null && !attachments.isEmpty()) {
                message = new Message(conversation, "", Message.ENCRYPTION_NONE, status);
            } else {
                message = new Message(conversation, body == null ? null : body.content, Message.ENCRYPTION_NONE, status);
                if (body != null && body.count > 1) {
                    message.setBodyLanguage(body.language);
                }
            }

            Element addresses = packet.findChild("addresses", "http://jabber.org/protocol/address");
            if (status == Message.STATUS_RECEIVED && addresses != null) {
                for (Element address : addresses.getChildren()) {
                    if (!address.getName().equals("address") || !address.getNamespace().equals("http://jabber.org/protocol/address")) continue;

                    if (address.getAttribute("type").equals("ofrom") && address.getAttribute("jid") != null) {
                        Jid ofrom = address.getAttributeAsJid("jid");
                        if (Jid.Invalid.isValid(ofrom) && ofrom.getDomain().equals(counterpart.getDomain()) &&
                                conversation.getAccount().getRoster().getContact(counterpart.getDomain()).getPresences().anySupport("http://jabber.org/protocol/address")) {

                            message.setTrueCounterpart(ofrom);
                        }
                    }
                }
            }

            if (html != null) message.addPayload(html);
            message.setSubject(packet.findChildContent("subject"));
            message.setCounterpart(counterpart);
            message.setRemoteMsgId(remoteMsgId);
            message.setServerMsgId(serverMsgId);
            message.setCarbon(isCarbon);
            message.setTime(timestamp);
            if (!attachments.isEmpty()) {
                message.setFileParams(attachments.iterator().next());
                if (CryptoHelper.isPgpEncryptedUrl(message.getFileParams().url)) {
                    message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                }
            }
            message.markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");
            for (Element el : packet.getChildren()) {
                if ((el.getName().equals("query") && el.getNamespace().equals("http://jabber.org/protocol/disco#items") && el.getAttribute("node").equals("http://jabber.org/protocol/commands")) ||
                        (el.getName().equals("fallback") && el.getNamespace().equals("urn:xmpp:fallback:0"))) {
                    message.addPayload(el);
                }
                if (el.getName().equals("thread") && (el.getNamespace() == null || el.getNamespace().equals("jabber:client"))) {
                    el.setAttribute("xmlns", "jabber:client");
                    message.addPayload(el);
                }
                if (el.getName().equals("reply") && el.getNamespace() != null && el.getNamespace().equals("urn:xmpp:reply:0")) {
                    message.addPayload(el);
                    if (el.getAttribute("id") != null) {
                        for (final var parent : mXmppConnectionService.getMessageFuzzyIds(conversation, List.of(el.getAttribute("id"))).entrySet()) {
                            message.setInReplyTo(parent.getValue());
                        }
                    }
                }
                if (el.getName().equals("attention") && el.getNamespace() != null && el.getNamespace().equals("urn:xmpp:attention:0")) {
                    message.addPayload(el);
                }
                if (el.getName().equals("Description") && el.getNamespace() != null && el.getNamespace().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#")) {
                    message.addPayload(el);
                }
            }
            if (conversationMultiMode) {
                final var mucOptions = conversation.getMucOptions();
                if (occupant != null) {
                    message.setOccupantId(occupant.getId());
                }
                message.setMucUser(mucOptions.findUserByFullJid(counterpart));
                final Jid fallback = mucOptions.getTrueCounterpart(counterpart);
                Jid trueCounterpart;
                if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                    trueCounterpart = message.getTrueCounterpart();
                } else if (query != null && query.safeToExtractTrueCounterpart()) {
                    trueCounterpart = getTrueCounterpart(mucUserElement, fallback);
                } else {
                    trueCounterpart = fallback;
                }
                if (trueCounterpart != null && isTypeGroupChat) {
                    if (trueCounterpart.asBareJid().equals(account.getJid().asBareJid())) {
                        status =
                                isTypeGroupChat
                                        ? Message.STATUS_SEND_RECEIVED
                                        : Message.STATUS_SEND;
                    } else {
                        status = Message.STATUS_RECEIVED;
                        message.setCarbon(false);
                    }
                }
                message.setStatus(status);
                message.setTrueCounterpart(trueCounterpart);
                if (!isTypeGroupChat) {
                    message.setType(Message.TYPE_PRIVATE);
                }
            } else {
                updateLastseen(account, from);
            }

            // Old but working message retraction and moderation
            if (replacementId != null && mXmppConnectionService.allowMessageCorrection()) {
                final Message replacedMessage =
                        conversation.findMessageWithRemoteIdAndCounterpart(
                                replacementId,
                                counterpart);
                if (replacedMessage != null) {
                    final boolean fingerprintsMatch =
                            replacedMessage.getFingerprint() == null
                                    || replacedMessage
                                    .getFingerprint()
                                    .equals(message.getFingerprint());
                    final boolean trueCountersMatch =
                            replacedMessage.getTrueCounterpart() != null
                                    && message.getTrueCounterpart() != null
                                    && replacedMessage
                                    .getTrueCounterpart()
                                    .asBareJid()
                                    .equals(message.getTrueCounterpart().asBareJid());
                    final boolean occupantIdMatch =
                            replacedMessage.getOccupantId() != null
                                    && replacedMessage
                                    .getOccupantId()
                                    .equals(message.getOccupantId());
                    final boolean mucUserMatches =
                            query == null
                                    && replacedMessage.sameMucUser(
                                    message); // can not be checked when using mam
                    final boolean duplicate = conversation.hasDuplicateMessage(message);
                    if (fingerprintsMatch && (trueCountersMatch || occupantIdMatch || !conversationMultiMode || mucUserMatches || counterpart.isBareJid()) && !duplicate) {
                        synchronized (replacedMessage) {
                            final String uuid = replacedMessage.getUuid();
                            replacedMessage.setUuid(UUID.randomUUID().toString());
                            replacedMessage.setBody(message.getBody());
                            replacedMessage.setSubject(message.getSubject());
                            replacedMessage.setThread(message.getThread());
                            replacedMessage.putEdited(replacedMessage.getRemoteMsgId(), replacedMessage.getServerMsgId());
                            if (replaceElement != null && !replaceElement.getName().equals("replace")) {
                                mXmppConnectionService.getFileBackend().deleteFile(replacedMessage);
                                mXmppConnectionService.evictPreview(message.getUuid());
                                List<Element> thumbs = replacedMessage.getFileParams() != null ? replacedMessage.getFileParams().getThumbnails() : null;
                                if (thumbs != null && !thumbs.isEmpty()) {
                                    for (Element thumb : thumbs) {
                                        Uri uri = Uri.parse(thumb.getAttribute("uri"));
                                        if (uri.getScheme().equals("cid")) {
                                            Cid cid = BobTransfer.cid(uri);
                                            if (cid == null) continue;
                                            DownloadableFile f = mXmppConnectionService.getFileForCid(cid);
                                            if (f != null) {
                                                mXmppConnectionService.evictPreview(f);
                                                f.delete();
                                            }
                                        }
                                    }
                                }
                                replacedMessage.clearPayloads();
                                replacedMessage.setFileParams(null);
                                replacedMessage.addPayload(replaceElement);

                                replacedMessage.setDeleted(true);
                                replacedMessage.setRetractId(replacementId);
                                mXmppConnectionService.updateMessage(replacedMessage, replacedMessage.getUuid());
                            } else {
                                replacedMessage.clearPayloads();
                                for (final var p : message.getPayloads()) {
                                    replacedMessage.addPayload(p);
                                }
                            }
                            replacedMessage.setInReplyTo(message.getInReplyTo());

                            // we store the IDs of the replacing message. This is essentially unused
                            // today (only the fact that there are _some_ edits causes the edit icon
                            // to appear)
                            replacedMessage.putEdited(
                                    message.getRemoteMsgId(), message.getServerMsgId());

                            // we used to call
                            // `replacedMessage.setServerMsgId(message.getServerMsgId());` so during
                            // catchup we could start from the edit; not the original message
                            // however this caused problems for things like reactions that refer to
                            // the serverMsgId

                            replacedMessage.setEncryption(message.getEncryption());
                            if (replacedMessage.getStatus() == Message.STATUS_RECEIVED) {
                                replacedMessage.markUnread();
                            }
                            extractChatState(
                                    mXmppConnectionService.find(account, counterpart.asBareJid()),
                                    isTypeGroupChat,
                                    packet);
                            mXmppConnectionService.updateMessage(replacedMessage, uuid);
                            if (mXmppConnectionService.confirmMessages()
                                    && replacedMessage.getStatus() == Message.STATUS_RECEIVED
                                    && (replacedMessage.trusted()
                                    || replacedMessage
                                    .isPrivateMessage()) // TODO do we really want
                                    // to send receipts for all
                                    // PMs?
                                    && remoteMsgId != null
                                    && !selfAddressed
                                    && !isTypeGroupChat) {
                                processMessageReceipts(account, packet, remoteMsgId, query);
                            }
                            if (replacedMessage.getEncryption() == Message.ENCRYPTION_PGP) {
                                conversation
                                        .getAccount()
                                        .getPgpDecryptionService()
                                        .discard(replacedMessage);
                                conversation
                                        .getAccount()
                                        .getPgpDecryptionService()
                                        .decrypt(replacedMessage, false);
                            }
                        }
                        mXmppConnectionService.getNotificationService().updateNotification();
                        return;
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": received message correction but verification didn't"
                                        + " check out");
                    }
                } else if (message.getBody() == null || message.getBody().equals("") || message.getBody().equals(" ")) {
                    return;
                }
                if (replaceElement != null && !replaceElement.getName().equals("replace")) return;
            }

            boolean checkForDuplicates =
                    (isTypeGroupChat && packet.hasChild("delay", "urn:xmpp:delay"))
                            || message.isPrivateMessage()
                            || message.getServerMsgId() != null
                            || (query == null
                            && mXmppConnectionService
                            .getMessageArchiveService()
                            .isCatchupInProgress(conversation));
            if (checkForDuplicates) {
                final Message duplicate = conversation.findDuplicateMessage(message);
                if (duplicate != null) {
                    final boolean serverMsgIdUpdated;
                    if (duplicate.getStatus() != Message.STATUS_RECEIVED
                            && duplicate.getUuid().equals(message.getRemoteMsgId())
                            && duplicate.getServerMsgId() == null
                            && message.getServerMsgId() != null) {
                        duplicate.setServerMsgId(message.getServerMsgId());
                        if (mXmppConnectionService.databaseBackend.updateMessage(
                                duplicate, false)) {
                            serverMsgIdUpdated = true;
                        } else {
                            serverMsgIdUpdated = false;
                            Log.e(Config.LOGTAG, "failed to update message");
                        }
                    } else {
                        serverMsgIdUpdated = false;
                    }
                    Log.d(
                            Config.LOGTAG,
                            "skipping duplicate message with "
                                    + message.getCounterpart()
                                    + ". serverMsgIdUpdated="
                                    + serverMsgIdUpdated);
                    return;
                }
            }

            if (query != null
                    && query.getPagingOrder() == MessageArchiveService.PagingOrder.REVERSE) {
                conversation.prepend(query.getActualInThisQuery(), message);
            } else {
                conversation.add(message);
            }
            if (query != null) {
                query.incrementActualMessageCount();
            }

            if (query == null || query.isCatchup()) { // either no mam or catchup
                if (status == Message.STATUS_SEND || status == Message.STATUS_SEND_RECEIVED) {
                    mXmppConnectionService.markRead(conversation);
                    if (query == null) {
                        activateGracePeriod(account);
                    }
                } else {
                    message.markUnread();
                    notify = true;
                }
            }

            if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                notify =
                        conversation
                                .getAccount()
                                .getPgpDecryptionService()
                                .decrypt(message, notify);
            } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE
                    || message.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
                notify = false;
            }

            if (query == null) {
                extractChatState(
                        mXmppConnectionService.find(account, counterpart.asBareJid()),
                        isTypeGroupChat,
                        packet);
                mXmppConnectionService.updateConversationUi();
            }

            if (mXmppConnectionService.confirmMessages()
                    && message.getStatus() == Message.STATUS_RECEIVED
                    && (message.trusted() || message.isPrivateMessage())
                    && remoteMsgId != null
                    && !selfAddressed
                    && !isTypeGroupChat) {
                processMessageReceipts(account, packet, remoteMsgId, query);
            }

            if (message.getFileParams() != null) {
                for (Cid cid : message.getFileParams().getCids()) {
                    File f = mXmppConnectionService.getFileForCid(cid);
                    if (f != null && f.canRead()) {
                        message.setRelativeFilePath(f.getAbsolutePath());
                        mXmppConnectionService.getFileBackend().updateFileParams(message, null, false);
                        break;
                    }
                }
            }

            if (message.getStatus() == Message.STATUS_RECEIVED
                    && conversation.getOtrSession() != null
                    && !conversation.getOtrSession().getSessionID().getUserID()
                    .equals(message.getCounterpart().getResource())) {
                conversation.endOtrIfNeeded();
            }

            mXmppConnectionService.databaseBackend.createMessage(message);
            final HttpConnectionManager manager =
                    this.mXmppConnectionService.getHttpConnectionManager();
            if (message.trusted() && message.treatAsDownloadable() && manager.getAutoAcceptFileSize() > 0) {
                if (message.getOob() != null && "cid".equalsIgnoreCase(message.getOob().getScheme())) {
                    try {
                        BobTransfer transfer = new BobTransfer.ForMessage(message, mXmppConnectionService);
                        message.setTransferable(transfer);
                        transfer.start();
                    } catch (URISyntaxException e) {
                        Log.d(Config.LOGTAG, "BobTransfer failed to parse URI");
                    }
                } else {
                    manager.createNewDownloadConnection(message);
                }
            } else if (notify) {
                if (query != null && query.isCatchup()) {
                    mXmppConnectionService.getNotificationService().pushFromBacklog(message);
                } else {
                    mXmppConnectionService.getNotificationService().push(message);
                }
            }
        } else if (!packet.hasChild("body")) { // no body
            final Conversation conversation =
                    mXmppConnectionService.find(account, from.asBareJid());
            if (axolotlEncrypted != null) {
                Jid origin;
                if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
                    final Jid fallback =
                            conversation.getMucOptions().getTrueCounterpart(counterpart);
                    origin = getTrueCounterpart(query != null ? mucUserElement : null, fallback);
                    if (origin == null) {
                        Log.d(
                                Config.LOGTAG,
                                "omemo key transport message in anonymous conference received");
                        return;
                    }
                } else if (isTypeGroupChat) {
                    return;
                } else {
                    origin = from;
                }
                try {
                    final XmppAxolotlMessage xmppAxolotlMessage =
                            XmppAxolotlMessage.fromElement(axolotlEncrypted, origin.asBareJid());
                    account.getAxolotlService()
                            .processReceivingKeyTransportMessage(xmppAxolotlMessage, query != null);
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": omemo key transport message received from "
                                    + origin);
                } catch (Exception e) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": invalid omemo key transport message received "
                                    + e.getMessage());
                    return;
                }
            }

            if (query == null
                    && extractChatState(
                    mXmppConnectionService.find(account, counterpart.asBareJid()),
                    isTypeGroupChat,
                    packet)) {
                mXmppConnectionService.updateConversationUi();
            }

            if (isTypeGroupChat) {
                if (packet.hasChild("subject")
                        && !packet.hasChild("thread")) { // We already know it has no body per above
                    if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
                        conversation.setHasMessagesLeftOnServer(conversation.countMessages() > 0);
                        final LocalizedContent subject =
                                packet.findInternationalizedChildContentInDefaultNamespace(
                                        "subject");
                        if (subject != null
                                && conversation.getMucOptions().setSubject(subject.content)) {
                            mXmppConnectionService.updateConversation(conversation);
                        }
                        mXmppConnectionService.updateConversationUi();
                        return;
                    }
                }
            }
            if (conversation != null
                    && mucUserElement != null
                    && Jid.Invalid.hasValidFrom(packet)
                    && from.isBareJid()) {
                for (Element child : mucUserElement.getChildren()) {
                    if ("status".equals(child.getName())) {
                        try {
                            int code = Integer.parseInt(child.getAttribute("code"));
                            if ((code >= 170 && code <= 174) || (code >= 102 && code <= 104)) {
                                mXmppConnectionService.fetchConferenceConfiguration(conversation);
                                break;
                            }
                        } catch (Exception e) {
                            // ignored
                        }
                    } else if ("item".equals(child.getName())) {
                        final var user = AbstractParser.parseItem(conversation, child);
                        Log.d(
                                Config.LOGTAG,
                                account.getJid()
                                        + ": changing affiliation for "
                                        + user.getRealJid()
                                        + " to "
                                        + user.getAffiliation()
                                        + " in "
                                        + conversation.getJid().asBareJid());
                        if (!user.realJidMatchesAccount()) {
                            final var mucOptions = conversation.getMucOptions();
                            final boolean isNew = mucOptions.updateUser(user);
                            final var avatarService = mXmppConnectionService.getAvatarService();
                            if (Strings.isNullOrEmpty(mucOptions.getAvatar())) {
                                avatarService.clear(mucOptions);
                            }
                            avatarService.clear(user);
                            mXmppConnectionService.updateMucRosterUi();
                            mXmppConnectionService.updateConversationUi();
                            Contact contact = user.getContact();
                            if (!user.getAffiliation().ranks(MucOptions.Affiliation.MEMBER)) {
                                Jid jid = user.getRealJid();
                                List<Jid> cryptoTargets = conversation.getAcceptedCryptoTargets();
                                if (cryptoTargets.remove(user.getRealJid())) {
                                    Log.d(
                                            Config.LOGTAG,
                                            account.getJid().asBareJid()
                                                    + ": removed "
                                                    + jid
                                                    + " from crypto targets of "
                                                    + conversation.getName());
                                    conversation.setAcceptedCryptoTargets(cryptoTargets);
                                    mXmppConnectionService.updateConversation(conversation);
                                }
                            } else if (isNew
                                    && user.getRealJid() != null
                                    && conversation.getMucOptions().isPrivateAndNonAnonymous()
                                    && (contact == null || !contact.mutualPresenceSubscription())
                                    && account.getAxolotlService()
                                    .hasEmptyDeviceList(user.getRealJid())) {
                                account.getAxolotlService().fetchDeviceIds(user.getRealJid());
                            }
                        }
                    }
                }
            }
            if (!isTypeGroupChat) {
                for (Element child : packet.getChildren()) {
                    if (Namespace.JINGLE_MESSAGE.equals(child.getNamespace())
                            && JINGLE_MESSAGE_ELEMENT_NAMES.contains(child.getName())) {
                        final String action = child.getName();
                        final String sessionId = child.getAttribute("id");
                        if (sessionId == null) {
                            break;
                        }
                        if (query == null && offlineMessagesRetrieved) {
                            if (serverMsgId == null) {
                                serverMsgId = extractStanzaId(account, packet);
                            }
                            mXmppConnectionService
                                    .getJingleConnectionManager()
                                    .deliverMessage(
                                            account,
                                            packet.getTo(),
                                            packet.getFrom(),
                                            child,
                                            remoteMsgId,
                                            serverMsgId,
                                            timestamp);
                            final Contact contact = account.getRoster().getContact(from);
                            // this is the same condition that is found in JingleRtpConnection for
                            // the 'ringing' response. Responding with delivery receipts predates
                            // the 'ringing' spec'd
                            final boolean sendReceipts =
                                    contact.showInContactList()
                                            || Config.JINGLE_MESSAGE_INIT_STRICT_OFFLINE_CHECK;
                            if (remoteMsgId != null && !contact.isSelf() && sendReceipts) {
                                processMessageReceipts(account, packet, remoteMsgId, null);
                            }
                        } else if ((query != null && query.isCatchup())
                                || !offlineMessagesRetrieved) {
                            if ("propose".equals(action)) {
                                final Element description = child.findChild("description");
                                final String namespace =
                                        description == null ? null : description.getNamespace();
                                if (Namespace.JINGLE_APPS_RTP.equals(namespace)) {
                                    final Conversation c =
                                            mXmppConnectionService.findOrCreateConversation(
                                                    account, counterpart.asBareJid(), false, false);
                                    final Message preExistingMessage =
                                            c.findRtpSession(sessionId, status);
                                    if (preExistingMessage != null) {
                                        preExistingMessage.setServerMsgId(serverMsgId);
                                        mXmppConnectionService.updateMessage(preExistingMessage);
                                        break;
                                    }
                                    final Message message =
                                            new Message(
                                                    c, status, Message.TYPE_RTP_SESSION, sessionId);
                                    message.setServerMsgId(serverMsgId);
                                    message.setTime(timestamp);
                                    message.setBody(new RtpSessionStatus(false, 0).toString());
                                    message.markUnread();
                                    c.add(message);
                                    mXmppConnectionService.getNotificationService().possiblyMissedCall(c.getUuid() + sessionId, message);
                                    if (query != null) query.incrementActualMessageCount();
                                    mXmppConnectionService.databaseBackend.createMessage(message);
                                }
                            } else if ("proceed".equals(action)) {
                                // status needs to be flipped to find the original propose
                                final Conversation c =
                                        mXmppConnectionService.findOrCreateConversation(
                                                account, counterpart.asBareJid(), false, false);
                                final int s =
                                        packet.fromAccount(account)
                                                ? Message.STATUS_RECEIVED
                                                : Message.STATUS_SEND;
                                final Message message = c.findRtpSession(sessionId, s);
                                if (message != null) {
                                    message.setBody(new RtpSessionStatus(true, 0).toString());
                                    if (serverMsgId != null) {
                                        message.setServerMsgId(serverMsgId);
                                    }
                                    message.setTime(timestamp);
                                    message.markRead();
                                    mXmppConnectionService.getNotificationService().possiblyMissedCall(c.getUuid() + sessionId, message);
                                    if (query != null) query.incrementActualMessageCount();
                                    mXmppConnectionService.updateMessage(message, true);
                                } else {
                                    Log.d(
                                            Config.LOGTAG,
                                            "unable to find original rtp session message for"
                                                    + " received propose");
                                }

                            } else if ("finish".equals(action)) {
                                Log.d(
                                        Config.LOGTAG,
                                        "received JMI 'finish' during MAM catch-up. Can be used to"
                                                + " update success/failure and duration");
                            }
                        } else {
                            // MAM reloads (non catchups
                            if ("propose".equals(action)) {
                                final Element description = child.findChild("description");
                                final String namespace =
                                        description == null ? null : description.getNamespace();
                                if (Namespace.JINGLE_APPS_RTP.equals(namespace)) {
                                    final Conversation c =
                                            mXmppConnectionService.findOrCreateConversation(
                                                    account, counterpart.asBareJid(), false, false);
                                    final Message preExistingMessage =
                                            c.findRtpSession(sessionId, status);
                                    if (preExistingMessage != null) {
                                        preExistingMessage.setServerMsgId(serverMsgId);
                                        mXmppConnectionService.updateMessage(preExistingMessage);
                                        break;
                                    }
                                    final Message message =
                                            new Message(
                                                    c, status, Message.TYPE_RTP_SESSION, sessionId);
                                    message.setServerMsgId(serverMsgId);
                                    message.setTime(timestamp);
                                    message.setBody(new RtpSessionStatus(true, 0).toString());
                                    if (query.getPagingOrder()
                                            == MessageArchiveService.PagingOrder.REVERSE) {
                                        c.prepend(query.getActualInThisQuery(), message);
                                    } else {
                                        c.add(message);
                                    }
                                    if (query != null) query.incrementActualMessageCount();
                                    mXmppConnectionService.databaseBackend.createMessage(message);
                                }
                            }
                        }
                        break;
                    }
                }
            }

            final var received =
                    packet.getExtension(
                            im.conversations.android.xmpp.model.receipts.Received.class);
            if (received != null) {
                processReceived(received, packet, query, from);
            }
            final var displayed = packet.getExtension(Displayed.class);
            if (displayed != null) {
                processDisplayed(
                        displayed,
                        packet,
                        selfAddressed,
                        counterpart,
                        query,
                        isTypeGroupChat,
                        conversation,
                        mucUserElement,
                        from);
            }

            // end no body
        }

        if (reactions != null) {
            processReactions(
                    reactions,
                    mXmppConnectionService.find(account, counterpart.asBareJid()),
                    isTypeGroupChat,
                    occupant,
                    counterpart,
                    mucTrueCounterPart,
                    status,
                    packet);
        }

        final Element event =
                original.findChild("event", "http://jabber.org/protocol/pubsub#event");
        if (event != null && Jid.Invalid.hasValidFrom(original) && original.getFrom().isBareJid()) {
            if (event.hasChild("items")) {
                parseEvent(event, original.getFrom(), account);
            } else if (event.hasChild("delete")) {
                parseDeleteEvent(event, original.getFrom(), account);
            } else if (event.hasChild("purge")) {
                parsePurgeEvent(event, original.getFrom(), account);
            }
        }

        final String nick = packet.findChildContent("nick", Namespace.NICK);
        if (nick != null && Jid.Invalid.hasValidFrom(original)) {
            if (mXmppConnectionService.isMuc(account, from)) {
                return;
            }
            final Contact contact = account.getRoster().getContact(from);
            if (contact.setPresenceName(nick)) {
                mXmppConnectionService.syncRoster(account);
                mXmppConnectionService.getAvatarService().clear(contact);
            }
        }
    }

    private void processReceived(
            final im.conversations.android.xmpp.model.receipts.Received received,
            final im.conversations.android.xmpp.model.stanza.Message packet,
            final MessageArchiveService.Query query,
            final Jid from) {
        final var id = received.getId();
        if (packet.fromAccount(account)) {
            if (query != null && id != null && packet.getTo() != null) {
                query.removePendingReceiptRequest(new ReceiptRequest(packet.getTo(), id));
            }
        } else if (id != null) {
            if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
                final String sessionId =
                        id.substring(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length());
                mXmppConnectionService
                        .getJingleConnectionManager()
                        .updateProposedSessionDiscovered(
                                account,
                                from,
                                sessionId,
                                JingleConnectionManager.DeviceDiscoveryState.DISCOVERED);
            } else {
                mXmppConnectionService.markMessage(
                        account, from.asBareJid(), id, Message.STATUS_SEND_RECEIVED);
            }
        }
    }

    private void processDisplayed(
            final Displayed displayed,
            final im.conversations.android.xmpp.model.stanza.Message packet,
            final boolean selfAddressed,
            final Jid counterpart,
            final MessageArchiveService.Query query,
            final boolean isTypeGroupChat,
            Conversation conversation,
            Element mucUserElement,
            Jid from) {
        final var id = displayed.getId();
        // TODO we don’t even use 'sender' any more. Remove this!
        final Jid sender = Jid.Invalid.getNullForInvalid(displayed.getAttributeAsJid("sender"));
        if (packet.fromAccount(account) && !selfAddressed) {
            final Conversation c = mXmppConnectionService.find(account, counterpart.asBareJid());
            final Message message =
                    (c == null || id == null) ? null : c.findReceivedWithRemoteId(id);
            if (message != null && (query == null || query.isCatchup())) {
                mXmppConnectionService.markReadUpTo(c, message);
            }
            if (query == null) {
                activateGracePeriod(account);
            }
        } else if (isTypeGroupChat) {
            final Message message;
            if (conversation != null && id != null) {
                if (sender != null) {
                    message = conversation.findMessageWithRemoteId(id, sender);
                } else {
                    message = conversation.findMessageWithServerMsgId(id);
                }
            } else {
                message = null;
            }
            if (message != null) {
                // TODO use occupantId to extract true counterpart from presence
                final Jid fallback = conversation.getMucOptions().getTrueCounterpart(counterpart);
                // TODO try to externalize mucTrueCounterpart
                final Jid trueJid =
                        getTrueCounterpart(
                                (query != null && query.safeToExtractTrueCounterpart())
                                        ? mucUserElement
                                        : null,
                                fallback);
                final boolean trueJidMatchesAccount =
                        account.getJid()
                                .asBareJid()
                                .equals(trueJid == null ? null : trueJid.asBareJid());
                if (trueJidMatchesAccount || conversation.getMucOptions().isSelf(counterpart)) {
                    if (!message.isRead()
                            && (query == null || query.isCatchup())) { // checking if message is
                        // unread fixes race conditions
                        // with reflections
                        mXmppConnectionService.markReadUpTo(conversation, message);
                    }
                } else if (!counterpart.isBareJid() && trueJid != null) {
                    final ReadByMarker readByMarker = ReadByMarker.from(counterpart, trueJid);
                    if (message.addReadByMarker(readByMarker)) {
                        final var mucOptions = conversation.getMucOptions();
                        final var everyone = ImmutableSet.copyOf(mucOptions.getMembers(false));
                        final var readyBy = message.getReadyByTrue();
                        final var mStatus = message.getStatus();
                        if (mucOptions.isPrivateAndNonAnonymous()
                                && (mStatus == Message.STATUS_SEND_RECEIVED
                                || mStatus == Message.STATUS_SEND)
                                && readyBy.containsAll(everyone)) {
                            message.setStatus(Message.STATUS_SEND_DISPLAYED);
                        }
                        mXmppConnectionService.updateMessage(message, false);
                    }
                }
            }
        } else {
            final Message displayedMessage =
                    mXmppConnectionService.markMessage(
                            account, from.asBareJid(), id, Message.STATUS_SEND_DISPLAYED);
            Message message = displayedMessage == null ? null : displayedMessage.prev();
            while (message != null
                    && message.getStatus() == Message.STATUS_SEND_RECEIVED
                    && message.getTimeSent() < displayedMessage.getTimeSent()) {
                mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
                message = message.prev();
            }
            if (displayedMessage != null && selfAddressed) {
                dismissNotification(account, counterpart, query, id);
            }
        }
    }

    private void processReactions(
            final Reactions reactions,
            final Conversation conversation,
            final boolean isTypeGroupChat,
            final OccupantId occupant,
            final Jid counterpart,
            final Jid mucTrueCounterPart,
            final int status,
            final im.conversations.android.xmpp.model.stanza.Message packet) {
        final String reactingTo = reactions.getId();
        if (conversation != null && reactingTo != null) {
            if (isTypeGroupChat && conversation.getMode() == Conversational.MODE_MULTI) {
                final var mucOptions = conversation.getMucOptions();
                final var occupantId = occupant == null ? null : occupant.getId();
                if (occupantId != null) {
                    final boolean isReceived = !mucOptions.isSelf(occupantId);
                    final Message message;
                    final var inMemoryMessage = conversation.findMessageWithServerMsgId(reactingTo);
                    if (inMemoryMessage != null) {
                        message = inMemoryMessage;
                    } else {
                        message =
                                mXmppConnectionService.databaseBackend.getMessageWithServerMsgId(
                                        conversation, reactingTo);
                    }
                    if (message != null) {
                        final var newReactions = new HashSet<>(reactions.getReactions());
                        newReactions.removeAll(message.getReactions().stream().filter(r -> occupantId.equals(r.occupantId)).map(r -> r.reaction).collect(Collectors.toList()));
                        final var combinedReactions =
                                Reaction.withOccupantId(
                                        message.getReactions(),
                                        reactions.getReactions(),
                                        isReceived,
                                        counterpart,
                                        mucTrueCounterPart,
                                        occupantId,
                                        message.getRemoteMsgId());
                        message.setReactions(combinedReactions);
                        mXmppConnectionService.updateMessage(message, false);
                        if (isReceived) mXmppConnectionService.getNotificationService().push(message, counterpart, occupantId, newReactions);
                    } else {
                        Log.d(Config.LOGTAG, "message with id " + reactingTo + " not found");
                    }
                } else {
                    Log.d(Config.LOGTAG, "received reaction in channel w/o occupant ids. ignoring");
                }
            } else {
                final Message message;
                final var inMemoryMessage = conversation.findMessageWithUuidOrRemoteId(reactingTo);
                if (inMemoryMessage != null) {
                    message = inMemoryMessage;
                } else {
                    message =
                            mXmppConnectionService.databaseBackend.getMessageWithUuidOrRemoteId(
                                    conversation, reactingTo);
                }
                if (message == null) {
                    Log.d(Config.LOGTAG, "message with id " + reactingTo + " not found");
                    return;
                }
                final boolean isReceived;
                final Jid reactionFrom;
                if (conversation.getMode() == Conversational.MODE_MULTI) {
                    Log.d(Config.LOGTAG, "received reaction as MUC PM. triggering validation");
                    final var mucOptions = conversation.getMucOptions();
                    final var occupantId = occupant == null ? null : occupant.getId();
                    if (occupantId == null) {
                        Log.d(
                                Config.LOGTAG,
                                "received reaction via PM channel w/o occupant ids. ignoring");
                        return;
                    }
                    isReceived = !mucOptions.isSelf(occupantId);
                    if (isReceived) {
                        reactionFrom = counterpart;
                    } else {
                        if (!occupantId.equals(message.getOccupantId())) {
                            Log.d(
                                    Config.LOGTAG,
                                    "reaction received via MUC PM did not pass validation");
                            return;
                        }
                        reactionFrom = account.getJid().asBareJid();
                    }
                } else {
                    if (packet.fromAccount(account)) {
                        isReceived = false;
                        reactionFrom = account.getJid().asBareJid();
                    } else {
                        isReceived = true;
                        reactionFrom = counterpart;
                    }
                }
                final var newReactions = new HashSet<>(reactions.getReactions());
                newReactions.removeAll(message.getReactions().stream().filter(r -> reactionFrom.equals(r.from)).map(r -> r.reaction).collect(Collectors.toList()));
                final var combinedReactions =
                        Reaction.withFrom(
                                message.getReactions(),
                                reactions.getReactions(),
                                isReceived,
                                reactionFrom,
                                message.getRemoteMsgId());
                message.setReactions(combinedReactions);
                mXmppConnectionService.updateMessage(message, false);
                if (status < Message.STATUS_SEND) mXmppConnectionService.getNotificationService().push(message, counterpart, null, newReactions);
            }
        }
    }

    private static Pair<im.conversations.android.xmpp.model.stanza.Message, Long>
    getForwardedMessagePacket(
            final im.conversations.android.xmpp.model.stanza.Message original,
            Class<? extends Extension> clazz) {
        final var extension = original.getExtension(clazz);
        final var forwarded = extension == null ? null : extension.getExtension(Forwarded.class);
        if (forwarded == null) {
            return null;
        }
        final Long timestamp = AbstractParser.parseTimestamp(forwarded, null);
        final var forwardedMessage = forwarded.getMessage();
        if (forwardedMessage == null) {
            return null;
        }
        return new Pair<>(forwardedMessage, timestamp);
    }

    private static Pair<im.conversations.android.xmpp.model.stanza.Message, Long>
    getForwardedMessagePacket(
            final im.conversations.android.xmpp.model.stanza.Message original,
            final String name,
            final String namespace) {
        final Element wrapper = original.findChild(name, namespace);
        final var forwardedElement =
                wrapper == null ? null : wrapper.findChild("forwarded", Namespace.FORWARD);
        if (forwardedElement instanceof Forwarded forwarded) {
            final Long timestamp = AbstractParser.parseTimestamp(forwarded, null);
            final var forwardedMessage = forwarded.getMessage();
            if (forwardedMessage == null) {
                return null;
            }
            return new Pair<>(forwardedMessage, timestamp);
        }
        return null;
    }

    private void dismissNotification(
            Account account, Jid counterpart, MessageArchiveService.Query query, final String id) {
        final Conversation conversation =
                mXmppConnectionService.find(account, counterpart.asBareJid());
        if (conversation != null && (query == null || query.isCatchup())) {
            final String displayableId = conversation.findMostRecentRemoteDisplayableId();
            if (displayableId != null && displayableId.equals(id)) {
                mXmppConnectionService.markRead(conversation);
            } else {
                Log.w(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": received dismissing display marker that did not match our last"
                                + " id in that conversation");
            }
        }
    }

    private void processMessageReceipts(
            final Account account,
            final im.conversations.android.xmpp.model.stanza.Message packet,
            final String remoteMsgId,
            MessageArchiveService.Query query) {
        final boolean markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");
        final boolean request = packet.hasChild("request", "urn:xmpp:receipts");
        if (query == null) {
            final ArrayList<String> receiptsNamespaces = new ArrayList<>();
            if (markable) {
                receiptsNamespaces.add("urn:xmpp:chat-markers:0");
            }
            if (request) {
                receiptsNamespaces.add("urn:xmpp:receipts");
            }
            if (receiptsNamespaces.size() > 0) {
                final var receipt =
                        mXmppConnectionService
                                .getMessageGenerator()
                                .received(
                                        account,
                                        packet.getFrom(),
                                        remoteMsgId,
                                        receiptsNamespaces,
                                        packet.getType());
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        } else if (query.isCatchup()) {
            if (request) {
                query.addPendingReceiptRequest(new ReceiptRequest(packet.getFrom(), remoteMsgId));
            }
        }
    }

    private void activateGracePeriod(Account account) {
        long duration =
                mXmppConnectionService.getLongPreference(
                        "grace_period_length", R.integer.grace_period)
                        * 1000;
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid()
                        + ": activating grace period till "
                        + TIME_FORMAT.format(new Date(System.currentTimeMillis() + duration)));
        account.activateGracePeriod(duration);
    }

    private class Invite {
        final Jid jid;
        final String password;
        final boolean direct;
        final Jid inviter;

        Invite(Jid jid, String password, boolean direct, Jid inviter) {
            this.jid = jid;
            this.password = password;
            this.direct = direct;
            this.inviter = inviter;
        }

        public boolean execute(final Account account) {
            if (this.jid == null) {
                return false;
            }
            final Contact contact = this.inviter != null ? account.getRoster().getContact(this.inviter) : null;
            if (contact != null && contact.isBlocked()) {
                Log.d(Config.LOGTAG,account.getJid().asBareJid()+": ignore invite from "+contact.getJid()+" because contact is blocked");
                return false;
            }
            final Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, jid, true, false);
            conversation.setAttribute("inviter", inviter.toString());
            if (conversation.getMucOptions().online()) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received invite to " + jid + " but muc is considered to be online");
                mXmppConnectionService.mucSelfPingAndRejoin(conversation);
            } else {
                conversation.getMucOptions().setPassword(password);
                mXmppConnectionService.databaseBackend.updateConversation(conversation);
                mXmppConnectionService.joinMuc(conversation, contact != null && contact.showInContactList());
                mXmppConnectionService.updateConversationUi();
            }
            return true;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
