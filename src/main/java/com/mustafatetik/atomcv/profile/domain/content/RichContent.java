package com.mustafatetik.atomcv.profile.domain.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * The content of a single atom variant: an ordered list of {@link Run}s
 * (Bolum 12, 14.1).
 *
 * <p>No format-specific markup ever enters this model (design principle 1).
 * LaTeX, HTML and DOCX are produced from the same instance at render time.
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record RichContent(List<Run> runs) {

    public static final RichContent EMPTY = new RichContent(List.of());

    private static final HexFormat HEX = HexFormat.of();

    public RichContent {
        runs = runs == null ? List.of() : List.copyOf(runs);
    }

    public static RichContent of(Run... runs) {
        return new RichContent(List.of(runs));
    }

    /** Content with no marks at all. */
    public static RichContent plain(String text) {
        return text.isEmpty() ? EMPTY : new RichContent(List.of(Run.of(text)));
    }

    /**
     * The text a reader sees, with every mark removed. This is what the
     * embedding, the length checks and the rewrite validator work on.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String plainText() {
        var sb = new StringBuilder();
        for (Run run : runs) {
            sb.append(run.text());
        }
        return sb.toString();
    }

    /**
     * {@code sha256(plainText)}, lowercase hex.
     *
     * <p>Deliberately computed over the plain text and not over the run
     * structure: re-marking a sentence leaves its meaning and its rendered
     * width untouched, so it must not invalidate the stored embedding or the
     * measured render costs (Bolum 16.2).
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String contentHash() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
        return HEX.formatHex(digest.digest(plainText().getBytes(StandardCharsets.UTF_8)));
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEmpty() {
        return plainText().isEmpty();
    }

    /**
     * Shape only, never content (absolute rule 4). The ArchUnit logging rule
     * catches a {@code RichContent} passed to a logger, but not one that
     * reaches a log line through string concatenation — this override is what
     * makes that case harmless too.
     */
    @Override
    public String toString() {
        return "RichContent[runs=" + runs.size() + ", chars=" + plainText().length() + "]";
    }
}
