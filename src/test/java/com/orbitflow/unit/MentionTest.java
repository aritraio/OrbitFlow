package com.orbitflow.unit;

import com.orbitflow.comment.MentionParser;
import com.orbitflow.common.util.MarkdownSanitizer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MentionTest {
    @Test
    void extractsMentions() {
        var mentions = MentionParser.extract("Hello @alice and @Bob_123, please review");
        assertThat(mentions).containsExactlyInAnyOrder("alice", "bob_123");
    }

    @Test
    void deduplicatesMentions() {
        var mentions = MentionParser.extract("@alice hi @alice hi @ALICE");
        assertThat(mentions).containsExactly("alice");
    }

    @Test
    void ignoresEmailLikePatterns() {
        // Our regex requires @ not preceded by word char, so email user@domain should not match domain part weirdly
        var mentions = MentionParser.extract("contact test@example.com for help");
        // 'example' preceded by '@'? In "test@example.com", @ is preceded by 't' (word char) so no match
        assertThat(mentions).isEmpty();
    }

    @Test
    void sanitizerStripsScripts() {
        String html = MarkdownSanitizer.renderMarkdown("hello <script>alert(1)</script> **bold**");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("<strong>bold</strong>");
    }

    @Test
    void sanitizerBlocksJavascriptLinks() {
        String html = MarkdownSanitizer.renderMarkdown("[click](https://example.com) <a href=\"javascript:alert(1)\">x</a>");
        // Raw HTML is escaped (&lt;), so no executable anchor remains; only safe https link is a real tag
        assertThat(html).doesNotContain("<a href=\"javascript");
        assertThat(html).contains("<a href=\"https://example.com\"");
    }
}
