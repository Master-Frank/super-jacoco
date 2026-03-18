package com.frank.superjacoco.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class CoverageLineHashUtil {

    private CoverageLineHashUtil() {
    }

    public static String hash(String lineContent) {
        if (lineContent == null) {
            return "";
        }
        String normalized = lineContent.trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8 && i < raw.length; i++) {
                builder.append(String.format(Locale.ROOT, "%02x", raw[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate line hash.", e);
        }
    }
}
