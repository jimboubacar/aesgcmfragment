package com.example.aesgcmfragment.keystoredemo; // Package where this utility class lives

import android.util.Base64;                       // Android Base64 helper for encoding/decoding
import java.nio.charset.StandardCharsets;         // Provides UTF-8 charset constant
import java.security.SecureRandom;                // Cryptographically secure PRNG

public final class CryptoUtils {                  // Final utility class (not intended to be subclassed)
    private static final SecureRandom RNG = new SecureRandom(); // Single SecureRandom instance for strong random bytes

    private CryptoUtils() {}                      // Private constructor to prevent instantiation

    public static byte[] randomBytes(int n) {     // Returns 'n' cryptographically secure random bytes
        byte[] b = new byte[n];                   // Allocate a byte array of requested size
        RNG.nextBytes(b);                         // Fill it with secure random data
        return b;                                 // Hand the caller the random bytes
    }

    public static byte[] utf8(String s) {         // Converts a Java String to UTF-8 bytes
        return s.getBytes(StandardCharsets.UTF_8);// Use explicit UTF-8 to avoid platform default encodings
    }

    public static String b64(byte[] data) {       // Encodes bytes to a Base64 string (no line breaks)
        return Base64.encodeToString(data, Base64.NO_WRAP); // NO_WRAP avoids inserting newline characters
    }
}
