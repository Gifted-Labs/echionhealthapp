package com.giftedlabs.echoinhealthbackend.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility for AES-256-GCM encryption at rest (UR-052).
 * Used for encrypting sensitive fields before database storage.
 */
@Component
@Slf4j
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;

    public EncryptionUtil(@Value("${encryption.key:}") String encryptionKey) {
        if (encryptionKey == null || encryptionKey.isEmpty()) {
            // Default key for development (should be set in production)
            encryptionKey = "EchionHealthAES256SecretKey12345";
            log.warn("Using default encryption key - set ENCRYPTION_KEY in production!");
        }

        // Ensure key is exactly 32 bytes for AES-256
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        byte[] key32 = new byte[32];
        System.arraycopy(keyBytes, 0, key32, 0, Math.min(keyBytes.length, 32));
        this.secretKey = new SecretKeySpec(key32, "AES");
    }

    /**
     * Encrypt a string using AES-256-GCM
     * 
     * @param plaintext The text to encrypt
     * @return Base64-encoded ciphertext (IV + encrypted data)
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypt a string using AES-256-GCM
     * 
     * @param ciphertext Base64-encoded ciphertext (IV + encrypted data)
     * @return Decrypted plaintext
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            // Extract ciphertext
            byte[] encrypted = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Check if a string appears to be encrypted (Base64 format)
     */
    public boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(text);
            return decoded.length > GCM_IV_LENGTH;
        } catch (Exception e) {
            return false;
        }
    }
}
