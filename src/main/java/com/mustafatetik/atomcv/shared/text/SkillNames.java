package com.mustafatetik.atomcv.shared.text;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One skill name reduced to the key everything matches on (Bolum 31.5).
 *
 * <p><strong>In {@code shared} because both sides of a comparison have to
 * apply the same rule.</strong> Faz B scores an atom's skills against a
 * posting's, and the two arrive from different places — one from a CV read at
 * ingestion, one from a posting read by Faz A. A dictionary applied to only
 * one of them is worse than no dictionary at all: a posting saying
 * {@code React.js} would stop matching an atom that had been normalised to
 * {@code react}, and the pairs that broke would be exactly the ones the
 * dictionary was added to fix. {@code RelevanceScorer} delegates here for that
 * reason, and its own comment already required it — all its callers have to
 * agree.
 *
 * <p>Three steps, cheapest first. Case and spacing are mechanical and cover
 * most of it; the dictionary handles what spelling alone cannot, which is that
 * {@code React.js} and {@code react} are one technology under two names.
 *
 * <p>{@code Locale.ROOT} everywhere, absolute rule 7: a Turkish default locale
 * lowercases {@code SQL} to {@code sqı}, and no atom would ever match it again.
 */
public final class SkillNames {

    /**
     * The dictionary Bolum 31.5 asks for, as a resource rather than a constant.
     *
     * <p>Adding an alias is then a data change a reviewer can read as a list,
     * and the file can grow to hundreds of lines without the class it lives in
     * becoming unreadable.
     */
    private static final String ALIASES = "skills/aliases.txt";

    /** Runs of whitespace, underscores and dots between words. */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s_]+");

    private static final Map<String, String> CANONICAL_BY_ALIAS = load();

    private SkillNames() {
    }

    /**
     * @param name a skill as written anywhere — a posting, a CV, a form
     * @return the key to compare on; empty for a name that was only whitespace
     */
    public static String canonical(String name) {
        if (name == null) {
            return "";
        }
        String spelled = SEPARATORS.matcher(name.strip().toLowerCase(Locale.ROOT))
                .replaceAll("-");
        // Punctuation a list leaves behind: a leading bullet, a trailing
        // comma. A LEADING DOT SURVIVES, because ".NET" is the name and not a
        // stray mark -- and a dot inside one survives for the same reason
        // ("Node.js").
        spelled = spelled.replaceAll("^[-,;]+|[-.,;]+$", "");
        return CANONICAL_BY_ALIAS.getOrDefault(spelled, spelled);
    }

    /** What the dictionary knows, for a test to assert against. */
    public static Map<String, String> aliases() {
        return CANONICAL_BY_ALIAS;
    }

    /**
     * Reads {@code alias = canonical} pairs, one per line.
     *
     * <p>An unreadable or missing file is a startup failure and not a silently
     * empty dictionary: an empty one still matches most pairs, so the loss
     * would show up as a slightly worse score on some CVs and nowhere else.
     */
    private static Map<String, String> load() {
        Map<String, String> aliases = new LinkedHashMap<>();
        try (InputStream in = SkillNames.class.getClassLoader().getResourceAsStream(ALIASES)) {
            if (in == null) {
                throw new IllegalStateException("Missing skill alias dictionary: " + ALIASES);
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String entry = line.strip();
                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }
                int equals = entry.indexOf('=');
                if (equals < 1) {
                    throw new IllegalStateException(
                            "Malformed alias line in " + ALIASES + ": " + entry);
                }
                aliases.put(entry.substring(0, equals).strip().toLowerCase(Locale.ROOT),
                        entry.substring(equals + 1).strip().toLowerCase(Locale.ROOT));
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Could not read " + ALIASES, unreadable);
        }
        // LinkedHashMap under an unmodifiable view rather than Map.copyOf: the
        // JDK's immutable maps iterate in an order salted per JVM run, and a
        // test that prints this would differ between runs for no reason.
        return Collections.unmodifiableMap(aliases);
    }
}
