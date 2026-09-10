package com.mustafatetik.atomcv.generation.selection;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What this one generation was told to do differently (Bolum 18.7, 24.4).
 *
 * <p>Its own object rather than a field of {@code JobAnalysis}, and for a
 * reason that costs money to get wrong: an analysis is cached by the hash of
 * the posting, so two people asking about one job share it. A directive is the
 * opposite — it belongs to a person and to a single run, and folding it into
 * the cached half would serve one caller's edits to the next (Bolum 18.7).
 *
 * <p><strong>Ids only.</strong> Bolum 18.7 gives the record two more fields,
 * {@code emphasize} and {@code freeformNote}, and neither is here yet: they are
 * text, they are read by Faz B and Faz D, and {@link SelectionRequest} is
 * deliberately textless. A directive that reached selection and did nothing
 * would be worse than one that was never accepted, so each field arrives with
 * the phase that honours it.
 *
 * <p>Both lists keep their order and drop their duplicates. The order is not
 * read by selection — membership is all it asks — but this record is written
 * to {@code generations.directives}, and a JSONB column that reorders itself
 * between two runs of one input would make Faz C look non-deterministic when
 * it is not (CLAUDE.md).
 */
public record GenerationDirectives(List<UUID> includeAtoms, List<UUID> excludeAtoms) {

    private static final GenerationDirectives NONE =
            new GenerationDirectives(List.of(), List.of());

    private static final String INCLUDE = "includeAtoms";
    private static final String EXCLUDE = "excludeAtoms";

    public GenerationDirectives {
        includeAtoms = distinct(includeAtoms);
        excludeAtoms = distinct(excludeAtoms);

        // An atom cannot be both asked for and refused. The parse in Faz G and
        // the toggle endpoint both have to answer this before it gets here --
        // rejecting the request is an answer, silently picking one of the two
        // is not, and whichever we picked would be wrong half the time.
        for (UUID atomId : includeAtoms) {
            if (excludeAtoms.contains(atomId)) {
                throw new IllegalArgumentException(
                        "An atom is either included or excluded, not both: " + atomId);
            }
        }
    }

    /** No directives, which is every generation until somebody edits one. */
    public static GenerationDirectives none() {
        return NONE;
    }

    public boolean isEmpty() {
        return includeAtoms.isEmpty() && excludeAtoms.isEmpty();
    }

    /**
     * Whether the user asked for this atom by hand.
     *
     * <p>It means the same thing to selection as {@code always_include} does,
     * and is kept apart from it because the two have different lifetimes: the
     * flag is a property of the profile and outlives every generation, this is
     * a statement about one document.
     */
    public boolean includes(UUID atomId) {
        return includeAtoms.contains(atomId);
    }

    /** Whether the user took this atom off this CV. */
    public boolean excludes(UUID atomId) {
        return excludeAtoms.contains(atomId);
    }

    /**
     * The shape {@code generations.directives} holds, decided here and nowhere
     * else.
     *
     * <p>The same reasoning {@code GenerationPayload} records: a reader
     * spelling a key differently from the writer fails silently, and the
     * failure looks like the user's edit never happening.
     *
     * <p>Ordered, because it is a JSONB column and the JDK's immutable maps
     * iterate in an order salted per JVM run (CLAUDE.md).
     */
    public Map<String, Object> asMap() {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put(INCLUDE, includeAtoms.stream().map(UUID::toString).toList());
        stored.put(EXCLUDE, excludeAtoms.stream().map(UUID::toString).toList());
        return stored;
    }

    /** What a row or a job payload carries, or none for a row that has neither. */
    public static GenerationDirectives fromMap(Map<String, Object> stored) {
        if (stored == null || stored.isEmpty()) {
            return NONE;
        }
        return new GenerationDirectives(idsIn(stored, INCLUDE), idsIn(stored, EXCLUDE));
    }

    /** This, plus one more edit. An edit is the sum of the edits before it. */
    public GenerationDirectives and(GenerationDirectives later) {
        if (later.isEmpty()) {
            return this;
        }
        // Ordered, and the later statement wins: asking for an atom that was
        // excluded two edits ago is how a person undoes that exclusion, and
        // the constructor would otherwise refuse the pair outright.
        var included = new LinkedHashSet<>(includeAtoms);
        var excluded = new LinkedHashSet<>(excludeAtoms);
        included.removeAll(later.excludeAtoms);
        excluded.removeAll(later.includeAtoms);
        included.addAll(later.includeAtoms);
        excluded.addAll(later.excludeAtoms);
        return new GenerationDirectives(List.copyOf(included), List.copyOf(excluded));
    }

    private static List<UUID> idsIn(Map<String, Object> stored, String key) {
        Object value = stored.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> items)) {
            throw new IllegalArgumentException(key + " is a list, got " + value.getClass());
        }
        return items.stream().map(String::valueOf).map(UUID::fromString).toList();
    }

    private static List<UUID> distinct(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // LinkedHashSet rather than Set.copyOf: the JDK's immutable sets
        // iterate in an order salted per JVM run, and this list is written to
        // a JSONB column (CLAUDE.md).
        return List.copyOf(new LinkedHashSet<>(ids));
    }
}
