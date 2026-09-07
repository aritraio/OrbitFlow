package com.orbitflow.common.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/** Sanitizes rendered Markdown HTML to prevent stored XSS. */
public final class MarkdownSanitizer {
    private MarkdownSanitizer() {}

    public static String sanitizeHtml(String html) {
        if (html == null) return "";
        return Jsoup.clean(html, Safelist.relaxed()
                .removeTags("script", "style", "iframe", "object", "embed", "form", "input", "button")
                .removeAttributes("a", "on*", "style")
                .removeAttributes("*", "on*"));
    }

    /** Minimal markdown -> HTML renderer (headings, bold, italic, code, links, lists) then sanitized. */
    public static String renderMarkdown(String markdown) {
        if (markdown == null) return "";
        String escaped = markdown
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String html = escaped
                .replaceAll("(?m)^###\\s+(.+)$", "<h3>$1</h3>")
                .replaceAll("(?m)^##\\s+(.+)$", "<h2>$1</h2>")
                .replaceAll("(?m)^#\\s+(.+)$", "<h1>$1</h1>")
                .replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>")
                .replaceAll("\\*(.+?)\\*", "<em>$1</em>")
                .replaceAll("`(.+?)`", "<code>$1</code>")
                .replaceAll("(?m)^-\\s+(.+)$", "<li>$1</li>")
                .replace("\n", "<br/>");
        // linkify [text](http/https only)
        html = html.replaceAll("\\[([^\\]]+)\\]\\((https?://[^)]+)\\)", "<a href=\"$2\" rel=\"nofollow\">$1</a>");
        return sanitizeHtml(html);
    }
}
