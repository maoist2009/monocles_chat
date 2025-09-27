package de.monocles.chat.pinnedmessage;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
// No need for SecureRandom here if Keystore generates IV
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class CryptoUtils {

    private static final String TAG = "PinnedMsgCrypto";
    private static final String ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS_PINNED_MESSAGES = "pinned_messages_encryption_key_v1";
    private static final String AES_GCM_NO_PADDING_TRANSFORMATION = "AES/GCM/NoPadding";

    // GCM_IV_LENGTH_BYTES is still useful information if you want to verify the length of the generated IV,
    // but we won't be creating a byte array of this size for IV generation anymore.
    // private static final int GCM_IV_LENGTH_BYTES = 12; // Recommended for GCM
    private static final int GCM_TAG_LENGTH_BITS = 128; // Recommended for GCM

    private static KeyStore keyStoreInstance;

    static {
        try {
            keyStoreInstance = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER);
            keyStoreInstance.load(null);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            Log.e(TAG, "Failed to initialize Android KeyStore", e);
            // Critical error: The app might not be able to encrypt/decrypt.
        }
    }

    private static SecretKey getOrCreateSecretKey() throws NoSuchAlgorithmException,
            NoSuchProviderException, InvalidAlgorithmParameterException, KeyStoreException,
            UnrecoverableEntryException, CertificateException, IOException {

        if (keyStoreInstance == null) {
            keyStoreInstance = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER);
            keyStoreInstance.load(null);
            if (keyStoreInstance == null) {
                throw new KeyStoreException("Keystore could not be re-initialized.");
            }
        }

        if (keyStoreInstance.containsAlias(KEY_ALIAS_PINNED_MESSAGES)) {
            KeyStore.Entry entry = keyStoreInstance.getEntry(KEY_ALIAS_PINNED_MESSAGES, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            } else {
                Log.w(TAG, "Keystore alias found but not a SecretKeyEntry. Recreating.");
                keyStoreInstance.deleteEntry(KEY_ALIAS_PINNED_MESSAGES);
            }
        }

        Log.i(TAG, "Generating new secret key for pinned messages.");
        final KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER);

        final KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS_PINNED_MESSAGES,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256);

        // IMPORTANT: For Android Keystore to generate the IV,
        // it's often implied by not providing one during Cipher.init() for GCM.
        // For API levels where setRandomizedEncryptionRequired is available,
        // it can be used to enforce this behavior if desired, though default behavior often suffices.
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        //     builder.setRandomizedEncryptionRequired(true); // Default is true for GCM anyway
        // }

        keyGenerator.init(builder.build());
        return keyGenerator.generateKey();
    }

    public static class EncryptionResult {
        public final byte[] iv;
        public final byte[] ciphertext;

        public EncryptionResult(byte[] iv, byte[] ciphertext) {
            this.iv = iv;
            this.ciphertext = ciphertext;
        }
    }

    public static EncryptionResult encrypt(byte[] plaintextData) {
        if (plaintextData == null || keyStoreInstance == null) {
            Log.e(TAG, "Encryption pre-conditions not met (data or keystore is null).");
            return null;
        }
        try {
            SecretKey secretKey = getOrCreateSecretKey();
            final Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING_TRANSFORMATION);

            // Let the Keystore provider generate the IV.
            // DO NOT provide GCMParameterSpec here for encryption when Keystore handles IV.
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            // Retrieve the IV generated by the Cipher (Keystore provider)
            byte[] iv = cipher.getIV();
            if (iv == null) {
                // This should not happen if init was successful and Keystore is behaving as expected
                Log.e(TAG, "Cipher failed to generate an IV.");
                return null;
            }

            byte[] ciphertext = cipher.doFinal(plaintextData);
            return new EncryptionResult(iv, ciphertext);

        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException |
                 KeyStoreException | UnrecoverableEntryException | CertificateException | IOException |
                 NoSuchPaddingException | InvalidKeyException | BadPaddingException |
                 IllegalBlockSizeException e) {
            Log.e(TAG, "Encryption failed", e); // The original exception will be caught here
            return null;
        }
    }

    public static byte[] decrypt(byte[] iv, byte[] ciphertext) {
        if (iv == null || ciphertext == null || keyStoreInstance == null) {
            Log.e(TAG, "Decryption pre-conditions not met (iv, ciphertext, or keystore is null).");
            return null;
        }
        try {
            SecretKey secretKey = getOrCreateSecretKey();
            final Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING_TRANSFORMATION);

            // For decryption, you MUST provide the IV that was used during encryption.
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);

            return cipher.doFinal(ciphertext);
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException |
                 KeyStoreException | UnrecoverableEntryException | CertificateException | IOException |
                 NoSuchPaddingException | InvalidKeyException | BadPaddingException | // AEADBadTagException is a subclass of BadPaddingException
                 IllegalBlockSizeException e) {
            Log.e(TAG, "Decryption failed", e);
            return null;
        }
    }
}