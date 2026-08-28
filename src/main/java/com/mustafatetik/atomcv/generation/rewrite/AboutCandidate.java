package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.List;
import java.util.UUID;

/**
 * The About paragraph, and everything it is allowed to say (Bolum 21.7).
 *
 * <p><strong>The lists are the whole of what the paragraph may claim.</strong>
 * Bolum 21.7's rule is that every technology in the About appears in the union
 * of the selected atoms' skills — which is a stronger statement than it looks:
 * a summary is not a place where new claims are made, it is where claims made
 * elsewhere on the page are gathered. The same goes for the numbers, and for
 * the same reason a rewritten bullet may not invent one.
 *
 * @param atomId     the About paragraph being replaced
 * @param original   what the person has now — the length ceiling is measured
 *                   against it, and it is what stands if the synthesis fails
 * @param skills     every skill on the rest of the page, canonical. Nothing
 *                   outside this list may be named
 * @param metrics    every number on the rest of the page, as written
 * @param ownWords   {@code profiles.self_description}: the only source for a
 *                   claim that is not a skill or a number
 * @param focus      what the posting is asking for, so the paragraph leads
 *                   with the part of this person the posting wants
 * @param maxChars   Bolum 21.3's ceiling, narrowed by Bolum 21.7's ~65 words
 */
public record AboutCandidate(
        UUID atomId,
        RichContent original,
        List<String> skills,
        List<String> metrics,
        String ownWords,
        List<String> focus,
        int maxChars) {

    public AboutCandidate {
        skills = List.copyOf(skills);
        metrics = List.copyOf(metrics);
        focus = List.copyOf(focus);
        ownWords = ownWords == null ? "" : ownWords;
    }

    public String originalText() {
        return original.plainText();
    }

    /** Counts, never the paragraph (absolute rule 4). */
    @Override
    public String toString() {
        return "AboutCandidate[skills=" + skills.size() + ", metrics=" + metrics.size()
                + ", ownWords=" + (ownWords.isBlank() ? "none" : "set")
                + ", maxChars=" + maxChars + "]";
    }
}
