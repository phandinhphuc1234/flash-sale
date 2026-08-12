package com.philia.flashsale.authentication.security.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

@Component
/** One-way digest adapter ensuring only refresh hashes are persisted. */
public class Sha256RefreshCredentialDigestAdapter {
    public String digest(String rawCredential) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawCredential.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the runtime", exception);
        }
    }
}
