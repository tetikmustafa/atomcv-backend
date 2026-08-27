package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.List;
import java.util.UUID;

/**
 * One bullet Faz D may work on, and everything it is allowed to know
 * (Bolum 21.2-21.4).
 *
 * <p><strong>{@code skills} is the honesty constraint, not a hint.</strong>
 * Bolum 21.4 hands the model the atom's own skills and tells it that anything
 * the posting wants which is not on that list must not be mentioned. The
 * validator of Bolum 21.6 then checks the answer against the same list with
 * zero tolerance. A candidate that arrived without its skills would be a
 * request to write whatever sounds good.
 *
 * @param atomId      what is being rewritten
 * @param variantId   the wording chosen for it (Bolum 21.1), which is what the
 *                    rewrite replaces if it succeeds and what stands if it
 *                    does not
 * @param original    that wording, marks and all — the length ceiling and the
 *                    drift check are both measured against it
 * @param skills      canonical, lowercase, English. The whole of what this
 *                    sentence may claim
 * @param metrics     the numbers it claims, as written; every one must survive
 * @param properNouns names that must not be translated or reworded
 * @param score       Faz B's relevance, which decided the intent
 * @param maxChars    Bolum 21.3's ceiling: the original plus five per cent
 * @param intent      how far Faz D may go
 * @param originalVector the atom's own embedding, which Bolum 21.6's drift
 *                    check measures the answer against. {@code null} for an
 *                    atom that has not been embedded yet — a profile imported
 *                    minutes ago, or an anonymous one, which has none at all
 */
public record RewriteCandidate(
        UUID atomId,
        UUID variantId,
        RichContent original,
        List<String> skills,
        List<String> metrics,
        List<String> properNouns,
        double score,
        int maxChars,
        RewriteIntent intent,
        float[] originalVector) {

    public RewriteCandidate {
        skills = List.copyOf(skills);
        metrics = List.copyOf(metrics);
        properNouns = List.copyOf(properNouns);
    }

    /** What the model is given and what the answer is measured against. */
    public String originalText() {
        return original.plainText();
    }
}
