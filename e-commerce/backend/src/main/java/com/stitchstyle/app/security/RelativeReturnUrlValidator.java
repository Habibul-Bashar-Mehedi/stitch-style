package com.stitchstyle.app.security;

import org.springframework.util.StringUtils;

import java.util.Locale;

public final class RelativeReturnUrlValidator {

    private RelativeReturnUrlValidator() {}

    public static boolean isSafe(String url) {
        if (!StringUtils.hasText(url)) return false;

        // strip() removes whitespace but keeps NUL/control characters so the check
        // below can reject them. trim() would silently remove a trailing U+0000.
        String candidate = url.strip();
        String lower = candidate.toLowerCase(Locale.ROOT);

        if (candidate.startsWith("//") || candidate.startsWith("\\\\")) return false;
        if (candidate.contains("://")) return false;
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
            return false;
        }
        // Reject NUL and every other control character. These are never valid in a
        // redirect target and a NUL (%00) must never reach a database text column.
        if (candidate.chars().anyMatch(RelativeReturnUrlValidator::isControlCharacter)) return false;
        return candidate.startsWith("/") && !candidate.startsWith("/\\");
    }

    public static String safeOrDefault(String url, String fallback) {
        return isSafe(url) ? url.strip() : fallback;
    }

    private static boolean isControlCharacter(int codePoint) {
        return codePoint < 0x20 || codePoint == 0x7f;
    }
}
