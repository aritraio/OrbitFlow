package com.orbitflow.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public final class SlugUtils {
    private SlugUtils() {}

    public static String slugify(String input) {
        if (input == null) return "ws-" + UUID.randomUUID().toString().substring(0, 8);
        String s = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (s.isBlank()) s = "workspace";
        if (s.length() > 60) s = s.substring(0, 60).replaceAll("-$", "");
        return s;
    }

    public static String uniqueSlug(String base, java.util.function.Predicate<String> exists) {
        String slug = slugify(base);
        if (!exists.test(slug)) return slug;
        for (int i = 2; i < 10000; i++) {
            String candidate = slug + "-" + i;
            if (!exists.test(candidate)) return candidate;
        }
        return slug + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
