package com.giftedlabs.echoinhealthbackend.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for generating secure random tokens
 */
public class TokenGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    /**
     * Generate a secure random token of specified byte length
     * 
     * @param byteLength Number of random bytes (default: 32 for 256-bit security)
     * @return Base64-encoded random token
     */
    public static String generateToken(int byteLength) {
        byte[] randomBytes = new byte[byteLength];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }

    /**
     * Generate a secure random token with default length (32 bytes / 256 bits)
     */
    public static String generateToken() {
        return generateToken(32);
    }
}
