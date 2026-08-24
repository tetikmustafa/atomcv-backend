package com.mustafatetik.atomcv.profile.domain.content;

import java.util.Objects;
import java.util.Set;

/**
 * A semantic label applied to a {@link Run} (Bolum 12).
 *
 * <p>Marks are semantic, never presentational: a renderer decides that
 * {@code technology} becomes bold, and a different template may decide
 * otherwise. Scoring and validation read the same marks — {@code metric}
 * identifies the numbers a rewrite must preserve, {@code technology} feeds the
 * skill cross-check.
 *
 * <p>This is deliberately not an enum. Stored content may carry a mark written
 * by a newer build; parsing must not fail and a round-trip must not drop it.
 * Renderers fall through to plain text for anything they do not know
 * (Bolum 16.2).
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record Mark(String value) {

    public static final Mark TECHNOLOGY = new Mark("technology");
    public static final Mark METRIC = new Mark("metric");
    public static final Mark EMPHASIS = new Mark("emphasis");
    public static final Mark LINK = new Mark("link");
    public static final Mark ORGANIZATION = new Mark("organization");

    private static final Set<Mark> KNOWN =
            Set.of(TECHNOLOGY, METRIC, EMPHASIS, LINK, ORGANIZATION);

    public Mark {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("A mark value must not be blank");
        }
    }

    /**
     * Whether this build knows how to treat the mark. An unknown mark is valid
     * content; it simply renders as plain text.
     *
     * <p><strong>Never serialised.</strong> It is derived from a set this
     * build happens to hold, not state: writing it would put one release's
     * opinion into a column that outlives the release, and Jackson then
     * refuses to read its own output back — which is how this was found, as a
     * 500 on a download re-rendering a stored snapshot.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isKnown() {
        return KNOWN.contains(this);
    }

    @Override
    public String toString() {
        return value;
    }
}
