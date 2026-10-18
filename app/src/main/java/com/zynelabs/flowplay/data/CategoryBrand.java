package com.zynelabs.flowplay.data;

import java.util.Locale;

/** Presentation-only brand identification; never alters a category or its persistent keys. */
public final class CategoryBrand {
    private CategoryBrand() { }

    /** Empty means no supported, recognizable service name: use a generic category icon. */
    public static String forName(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String words = " " + name.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ").trim() + " ";
        if (has(words, "NETFLIX")) return "netflix";
        if (has(words, "DISNEY") || has(words, "DISNEYPLUS")) return "disney_plus";
        if (has(words, "PRIME") || has(words, "PRIMEVIDEO") || has(words, "AMAZON")) return "prime_video";
        if (has(words, "APPLE") || has(words, "APPLETV") || has(words, "APPLETVPLUS")) return "apple_tv";
        if (has(words, "HBO") || has(words, "HBOMAX")) return "hbo";
        if (has(words, "MOVISTAR") || has(words, "MOVISTARPLUS")) return "movistar";
        return "";
    }

    private static boolean has(String words, String word) {
        return words.contains(" " + word + " ");
    }
}
