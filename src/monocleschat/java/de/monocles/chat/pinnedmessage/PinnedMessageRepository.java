package de.monocles.chat.pinnedmessage;

import android.content.Context;
import android.util.Base64; // For Base64 encoding/decoding
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


import de.monocles.chat.pinnedmessage.PinnedMessage;
import de.monocles.chat.pinnedmessage.CryptoUtils;
import io.ipfs.cid.Cid;

public class PinnedMessageRepository {
    private static final String TAG = "PinnedMsgRepo";
    private static final String PINNED_MESSAGES_FILE_V2 = "pinned_messages_v2.enc.json"; // New filename

    private final Context context;
    private final Gson gson;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // For file I/O

    // In-memory cache. For a production app with many pins, consider a database or more optimized file access.
    private final List<PinnedMessage> pinnedMessagesCache = Collections.synchronizedList(new ArrayList<>());


    // Gson TypeAdapter for byte[] to Base64 String and vice-versa
    private static class ByteArrayToBase64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return Base64.decode(json.getAsString(), Base64.NO_WRAP);
        }

        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(Base64.encodeToString(src, Base64.NO_WRAP));
        }
    }


    public PinnedMessageRepository(Context context) {
        this.context = context.getApplicationContext();
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeHierarchyAdapter(byte[].class, new ByteArrayToBase64TypeAdapter());
        this.gson = gsonBuilder.create();
        loadPinnedMessagesAsync(); // Load existing messages on initialization
    }

    private File getStorageFile() {
        return new File(context.getFilesDir(), PINNED_MESSAGES_FILE_V2);
    }

    private void loadPinnedMessagesAsync() {
        executorService.submit(() -> {
            File file = getStorageFile();
            if (!file.exists()) {
                Log.i(TAG, "Pinned messages file does not exist. Starting fresh.");
                synchronized (pinnedMessagesCache) {
                    pinnedMessagesCache.clear();
                }
                return;
            }
            try (FileInputStream fis = new FileInputStream(file);
                 InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                Type listType = new TypeToken<ArrayList<PinnedMessage>>() {}.getType();
                List<PinnedMessage> loadedMessages = gson.fromJson(reader, listType);
                synchronized (pinnedMessagesCache) {
                    pinnedMessagesCache.clear();
                    if (loadedMessages != null) {
                        pinnedMessagesCache.addAll(loadedMessages);
                        Log.i(TAG, "Loaded " + loadedMessages.size() + " pinned messages from file.");
                    } else {
                        Log.w(TAG, "Pinned messages file was empty or corrupt.");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading pinned messages from file. File might be corrupt.", e);
                // Consider renaming the corrupt file and starting fresh to prevent crash loops
                // file.renameTo(new File(file.getAbsolutePath() + ".corrupt"));
                synchronized (pinnedMessagesCache) {
                    pinnedMessagesCache.clear();
                }
            }
        });
    }

    private void savePinnedMessagesAsync() {
        // Create a defensive copy for saving
        final List<PinnedMessage> messagesToSave;
        synchronized (pinnedMessagesCache) {
            messagesToSave = new ArrayList<>(pinnedMessagesCache);
        }

        executorService.submit(() -> {
            File file = getStorageFile();
            // Atomic save: write to temp file then rename
            File tempFile = new File(file.getAbsolutePath() + ".tmp");
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                gson.toJson(messagesToSave, writer);
                writer.flush(); // Ensure all data is written
                fos.getFD().sync(); // Ensure data is synced to disk
                if (tempFile.renameTo(file)) {
                    Log.i(TAG, "Saved " + messagesToSave.size() + " pinned messages to file.");
                } else {
                    Log.e(TAG, "Failed to rename temp file to actual pinned messages file.");
                    tempFile.delete(); // Clean up temp file on failure
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving pinned messages to file", e);
                if (tempFile.exists()) {
                    tempFile.delete(); // Clean up temp file on exception
                }
            }
        });
    }

    /**
     * Pins a message.
     *
     * @param messageUuid      The unique ID of the message.
     * @param conversationUuid The unique ID of the conversation.
     * @param plaintextBody    The plaintext body of the message (can be null if pinning a file/image by CID).
     * @param cid              The Content Identifier for a file/image (can be null if pinning plaintext).
     * @param listener         Callback for completion.
     */
    public void pinMessage(String messageUuid, String conversationUuid, String plaintextBody, Cid cid, final OnPinCompleteListener listener) {
        if (messageUuid == null || conversationUuid == null || plaintextBody == null) {
            if (listener != null) listener.onPinComplete(false);
            return;
        }

        executorService.submit(() -> {
            byte[] encryptedText = null;
            byte[] iv = null;

            if (plaintextBody != null) {
                CryptoUtils.EncryptionResult encryptionResult = CryptoUtils.encrypt(plaintextBody.getBytes(StandardCharsets.UTF_8));
                if (encryptionResult == null) {
                    Log.e(TAG, "Failed to encrypt message body for pinning: " + messageUuid);
                    if (listener != null) listener.onPinComplete(false);
                    return;
                }
                encryptedText = encryptionResult.ciphertext;
                iv = encryptionResult.iv;
            }

            PinnedMessage newPinnedMessage = new PinnedMessage(
                    messageUuid,
                    conversationUuid,
                    encryptedText, // Will be null if only CID is provided
                    iv,            // Will be null if only CID is provided
                    System.currentTimeMillis(),
                    cid            // Store the CID
            );

            synchronized (pinnedMessagesCache) {
                // Remove if already exists to update it (or handle as an error if only one pin per message allowed)
                pinnedMessagesCache.removeIf(pm -> pm.getMessageUuid().equals(messageUuid));
                pinnedMessagesCache.add(newPinnedMessage);
                // Optional: Sort or limit total number of pinned messages globally or per conversation
            }
            savePinnedMessagesAsync();
            if (listener != null) listener.onPinComplete(true);
        });
    }

    public void unpinMessage(String messageUuid, final OnUnpinCompleteListener listener) {
        if (messageUuid == null) {
            if (listener != null) listener.onUnpinComplete(false);
            return;
        }
        boolean removed;
        synchronized (pinnedMessagesCache) {
            removed = pinnedMessagesCache.removeIf(pm -> pm.getMessageUuid().equals(messageUuid));
        }
        if (removed) {
            savePinnedMessagesAsync();
        }
        if (listener != null) listener.onUnpinComplete(removed);
    }


    // This method returns the decrypted content for UI display
    public DecryptedPinnedMessageData getDecryptedPinnedMessage(String messageUuid) {
        PinnedMessage foundMessage;
        synchronized (pinnedMessagesCache) {
            foundMessage = pinnedMessagesCache.stream()
                    .filter(pm -> pm.getMessageUuid().equals(messageUuid))
                    .findFirst()
                    .orElse(null);
        }

        if (foundMessage != null) {
            String decryptedText = null;
            if (foundMessage.getEncryptedContent() != null && foundMessage.getIv() != null) {
                byte[] decryptedBytes = CryptoUtils.decrypt(foundMessage.getIv(), foundMessage.getEncryptedContent());
                if (decryptedBytes != null) {
                    decryptedText = new String(decryptedBytes, StandardCharsets.UTF_8);
                } else {
                    Log.w(TAG, "Failed to decrypt pinned message content: " + messageUuid);
                    // Optionally remove the corrupt entry here if decryption fails consistently
                    // unpinMessage(messageUuid, null);
                }
            }
            // Return data even if only CID is present and text decryption failed or was not applicable
            return new DecryptedPinnedMessageData(
                    foundMessage.getMessageUuid(),
                    foundMessage.getConversationUuid(),
                    decryptedText,
                    foundMessage.getTimestamp(),
                    foundMessage.getCid() // Include CID
            );
        }
        return null;
    }

    // Get latest decrypted pinned message for a specific conversation
    public DecryptedPinnedMessageData getLatestDecryptedPinnedMessageForConversation(String conversationUuid) {
        List<PinnedMessage> conversationPins;
        synchronized (pinnedMessagesCache) {
            conversationPins = pinnedMessagesCache.stream()
                    .filter(pm -> pm.getConversationUuid().equals(conversationUuid))
                    .sorted(Comparator.comparingLong(PinnedMessage::getTimestamp).reversed()) // Newest first
                    .collect(Collectors.toList());
        }

        if (!conversationPins.isEmpty()) {
            PinnedMessage latest = conversationPins.get(0);
            String decryptedText = null;

            if (latest.getEncryptedContent() != null && latest.getIv() != null) {
                byte[] decryptedBytes = CryptoUtils.decrypt(latest.getIv(), latest.getEncryptedContent());
                if (decryptedBytes != null) {
                    decryptedText = new String(decryptedBytes, StandardCharsets.UTF_8);
                } else {
                    Log.w(TAG, "Failed to decrypt latest pinned message content for conversation " + conversationUuid + ", UUID: " + latest.getMessageUuid());
                }
            }

            return new DecryptedPinnedMessageData(
                    latest.getMessageUuid(),
                    latest.getConversationUuid(),
                    decryptedText,
                    latest.getTimestamp(),
                    latest.getCid() // Include CID
            );
        }
        return null;
    }

    // Callbacks for async operations
    public interface OnPinCompleteListener { void onPinComplete(boolean success); }
    public interface OnUnpinCompleteListener { void onUnpinComplete(boolean success); }

    // Data class for returning decrypted data to the UI layer
    public static class DecryptedPinnedMessageData {
        public final String messageUuid;
        public final String conversationUuid;
        public final String plaintextBody; // Can be null if only CID is present
        public final long timestamp;
        public final Cid cid; // New field

        public DecryptedPinnedMessageData(String messageUuid, String conversationUuid, String plaintextBody, long timestamp, Cid cid) {
            this.messageUuid = messageUuid;
            this.conversationUuid = conversationUuid;
            this.plaintextBody = plaintextBody;
            this.timestamp = timestamp;
            this.cid = cid; // Initialize new field
        }
    }
}