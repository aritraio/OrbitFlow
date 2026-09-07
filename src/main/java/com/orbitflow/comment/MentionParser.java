package com.orbitflow.comment;

import java.util.*;
import java.util.regex.*;

/** Extracts @username mentions; deduplicates; used by CommentService + unit tests. */
public final class MentionParser {
    private static final Pattern MENTION = Pattern.compile("(?<!\\w)@([a-zA-Z0-9_.-]{3,50})");
    private MentionParser() {}

    public static Set<String> extract(String markdown) {
        Set<String> out = new LinkedHashSet<>();
        if (markdown == null) return out;
        Matcher m = MENTION.matcher(markdown);
        while (m.find()) out.add(m.group(1).toLowerCase(Locale.ROOT));
        return out;
    }
}
