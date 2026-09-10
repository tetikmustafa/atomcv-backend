package com.mustafatetik.atomcv.generation.rewrite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.Collections;
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
 * <p>Since V11 it is also a column, and the copy below is ordered for what
 * happens <em>before</em> the column: {@code Map.copyOf} iterates in an order
 * salted per JVM run, so a trace, a log line or an assertion walking this map
 * read differently on two runs of one input (CLAUDE.md). The column itself is
 * safe either way and cannot be made to keep an order — {@code jsonb} stores
 * an object's keys sorted by length and bytes, so what goes in as insertion
 * order comes back sorted by atom id. Nothing here depends on that: this is a
 * lookup, and Faz E asks it for one atom at a time.
 *
 * @param byAtom the accepted rewrite for each atom it covers
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RewrittenContent(Map<UUID, RichContent> byAtom) {

    private static final RewrittenContent NONE = new RewrittenContent(Map.of());

    public RewrittenContent {
        byAtom = Collections.unmodifiableMap(new LinkedHashMap<>(byAtom));
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
