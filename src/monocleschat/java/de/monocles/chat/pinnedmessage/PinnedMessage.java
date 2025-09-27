package de.monocles.chat.pinnedmessage;

// PinnedMessage.java
import java.util.Arrays; // For Arrays.equals and Arrays.hashCode if needed for byte[]

import io.ipfs.cid.Cid;

public class PinnedMessage {
    private final String messageUuid; // UUID of the original message
    private final String conversationUuid;
    private final byte[] encryptedContent; // The message body, encrypted for local storage
    private final byte[] iv; // Initialization Vector used for the encryption
    private final long timestamp; // When this message was pinned
    private final Cid cid; // New field for Content Identifier

    public PinnedMessage(String messageUuid, String conversationUuid, byte[] encryptedContent, byte[] iv, long timestamp, Cid cid) {
        this.messageUuid = messageUuid;
        this.conversationUuid = conversationUuid;
        this.encryptedContent = encryptedContent;
        this.iv = iv;
        this.timestamp = timestamp;
        this.cid = cid; // For files
    }

    // Getters
    public String getMessageUuid() { return messageUuid; }
    public String getConversationUuid() { return conversationUuid; }
    public byte[] getEncryptedContent() { return encryptedContent; }
    public byte[] getIv() { return iv; }
    public long getTimestamp() { return timestamp; }
    public Cid getCid() { // Getter for CID
        return cid;
    }

    // It's good practice to override equals and hashCode if these objects are used in collections
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PinnedMessage that = (PinnedMessage) o;
        return messageUuid.equals(that.messageUuid); // Assuming messageUuid is unique
    }

    @Override
    public int hashCode() {
        return messageUuid.hashCode();
    }
}