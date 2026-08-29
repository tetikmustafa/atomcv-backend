package com.mustafatetik.atomcv.ingestion.normalization;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where in a normalised profile a warning happened (F-018).
 *
 * <p><strong>Both coordinates, and both after the sort.</strong> The form this
 * replaced was {@code sections.entries[i]} with {@code i} taken from the
 * model's own answer order and no section at all, so it had two ways of being
 * wrong at once: every section restarted at zero, and the index it did carry
 * was overwritten a line later when {@code newestFirst} reordered the entries
 * and {@code display_order} was rewritten from the new positions. A warning
 * that cannot say which row it is about is a count with a decoration on it.
 *
 * <p>Written and read in one place so the two cannot drift. It is an internal
 * coordinate and never reaches a client: what a client is given is the
 * {@code sectionId} and {@code entryId} this resolves to once the rows exist.
 *
 * <p>Nothing here is user content — two integers (absolute rule 4).
 */
public record EntryPath(int section, int entry) {

    private static final Pattern FORM =
            Pattern.compile("^sections\\[(\\d+)]\\.entries\\[(\\d+)]$");

    public static String of(int section, int entry) {
        return "sections[" + section + "].entries[" + entry + "]";
    }

    /**
     * @return empty for a warning that names no entry — the model raises some
     *         of those, and a document-level warning is not a broken one
     */
    public static Optional<EntryPath> parse(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = FORM.matcher(path.strip());
        return matcher.matches()
                ? Optional.of(new EntryPath(
                        Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))))
                : Optional.empty();
    }
}
