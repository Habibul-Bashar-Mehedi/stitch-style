package com.stitchstyle.app.service;

import java.util.Locale;

public final class LocaleHelper {
    private LocaleHelper() {}

    public static String resolve(String lang, String acceptLanguage) {
        if (lang != null && !lang.isBlank()) return normalize(lang);
        if (acceptLanguage != null && !acceptLanguage.isBlank()) return normalize(acceptLanguage);
        return "en";
    }

    private static String normalize(String value) {
        String first = value.split(",")[0].trim().toLowerCase(Locale.ROOT);
        if (first.startsWith("bn")) return "bn";
        return "en";
    }
}
