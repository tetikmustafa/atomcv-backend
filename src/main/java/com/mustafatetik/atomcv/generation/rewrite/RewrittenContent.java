package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What Faz D changed, and nothing else (Bolum 21.5).
 *
 * <p>Only the atoms whose rewrite was accepted appear here. Everything else is
 * printed the way the person wrote it, and there is no entry for it — an atom
 * that was never a candidate, one the model refused twice, and one the
 * provider could not be reached for are all the same thing to Faz E: absent,
 * and therefore original. That is what makes Bolum 21.6's "then use the
 * original" a rule the renderer cannot get wrong.
 *
 * <p>It is also what the compile loop carries between attempts. A document
 * that came out too long sends a smaller budget back to selection, and the
 * atoms that survive have already been rewritten — paying for them again
 * would buy the same sentences twice.
 *
 * @param byAtom the accepted rewrite for each atom it covers
 */
public record RewrittenContent(Map<UUID, RichContent> byAtom) {

    private static final RewrittenContent NONE = new RewrittenContent(Map.of());

    public RewrittenContent {
        byAtom = Map.copyOf(byAtom);
    }

    /** Faz D did not run, or changed nothing. */
    public static RewrittenContent none() {
        return NONE;
    }

    public boolean isEmpty() {
        return byAtom.isEmpty();
    }

    public boolean covers(UUID atomId) {
        return byAtom.containsKey(atomId);
    }

    /**
     * The line to print for this atom: the rewrite if there is one, and what
     * the person wrote if there is not.
     */
    public RichContent orOriginal(UUID atomId, RichContent original) {
        return byAtom.getOrDefault(atomId, original);
    }

    /** This, plus what a later pass produced. The later pass wins a tie. */
    public RewrittenContent and(Map<UUID, RichContent> more) {
        if (more.isEmpty()) {
            return this;
        }
        var merged = new LinkedHashMap<>(byAtom);
        merged.putAll(more);
        return new RewrittenContent(merged);
    }

    /** Counts, never a line of the CV (absolute rule 4). */
    @Override
    public String toString() {
        return "RewrittenContent[atoms=" + byAtom.size() + "]";
    }
}
