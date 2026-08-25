package com.mustafatetik.atomcv.generation.phases.analysis;

import com.mustafatetik.atomcv.shared.error.UnreadablePostingReason;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The checks that run before a call is paid for (Bolum 18.1).
 *
 * <p>Design principle 5: everything that can refuse a generation runs before
 * anything is spent. A posting that is 30 characters of nothing costs a token
 * count and an answer if it reaches the model, and refusing it here costs a
 * string scan.
 *
 * <p><strong>A refusal here is a question, not a wall.</strong> Bolum 18.1
 * offers three ways past it, and one of them is proceeding anyway — the
 * heuristics below are cheap on purpose and the user may know better than
 * they do.
 */
public final class JobDescriptionPreflight {

    /** Below this, there is not enough text to analyse (Bolum 18.1). */
    static final int MIN_CHARACTERS = 150;

    /** Above it, the paste is a page rather than a posting. */
    static final int MAX_CHARACTERS = 20_000;

    static final int MIN_WORDS = 40;

    /**
     * Distinct words over total words. Repetitive filler — a paste loop, a
     * wall of the same phrase — sits far below this; real prose sits well
     * above it even in an inflected language.
     */
    static final double MIN_UNIQUE_WORD_RATIO = 0.15;

    static final int MIN_SIGNAL_WORDS = 2;

    /**
     * Bolum 18.1's dictionary, in both languages the product writes.
     *
     * <p>Matched against lowercased words with {@code Locale.ROOT} (absolute
     * rule 7): under a Turkish locale "TERCIHEN" lowercases to "tercihen" but
     * an English "I" becomes a dotless "ı", and the dictionary would start
     * missing words for reasons no one would look for.
     */
    private static final Set<String> SIGNAL_WORDS = Set.of(
            // TR
            "sorumluluk", "aranan", "nitelik", "deneyim", "pozisyon", "ekip",
            "başvuru", "yetkinlik", "görev", "beklenen", "tercihen", "çalışma",
            // EN
            "responsibilities", "requirements", "qualifications", "experience",
            "role", "team", "apply", "skills", "duties", "preferred", "seeking",
            "position");

    /** Splits on anything that is not a letter or a digit, Unicode-aware. */
    private static final Pattern WORDS = Pattern.compile("[^\\p{L}\\p{N}]+");

    private JobDescriptionPreflight() {
    }

    /**
     * Why a posting was refused, or {@link Verdict#ACCEPTED}.
     *
     * <p>The catalogue still publishes one code for all of them (EK D.6), but
     * the distinction now reaches the user in {@code params.reason} rather than
     * stopping at a log line (F-016). It was always worth a metric — "postings
     * refused" says nothing, "refused as low-entropy" says the heuristic may
     * need looking at — and it turns out to be worth a sentence too: the four
     * refusals here send the user to four different places.
     */
    public enum Verdict {

        /** Good enough to analyse — or empty, which is general CV mode. */
        ACCEPTED(null),

        /** Fewer than {@link #MIN_CHARACTERS} characters or {@link #MIN_WORDS} words. */
        TOO_SHORT(UnreadablePostingReason.TOO_SHORT),

        /** More than {@link #MAX_CHARACTERS}. */
        TOO_LONG(UnreadablePostingReason.TOO_LONG),

        /** Too few distinct words to be prose. */
        LOW_ENTROPY(UnreadablePostingReason.LOW_ENTROPY),

        /** Prose, but nothing in it reads like a job posting. */
        NOT_JOB_LIKE(UnreadablePostingReason.NOT_JOB_LIKE);

        private final UnreadablePostingReason reason;

        Verdict(UnreadablePostingReason reason) {
            this.reason = reason;
        }

        public boolean isAccepted() {
            return this == ACCEPTED;
        }

        /** @throws IllegalStateException if called on {@link #ACCEPTED} */
        public UnreadablePostingReason reason() {
            if (reason == null) {
                throw new IllegalStateException("ACCEPTED is not a refusal");
            }
            return reason;
        }
    }

    /**
     * @param jobDescription the pasted text; null or blank is general CV mode
     *                       and passes, because there is nothing to analyse
     *                       rather than something wrong (Bolum 18.1)
     */
    public static Verdict check(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return Verdict.ACCEPTED;
        }
        if (jobDescription.length() > MAX_CHARACTERS) {
            return Verdict.TOO_LONG;
        }
        if (jobDescription.length() < MIN_CHARACTERS) {
            return Verdict.TOO_SHORT;
        }

        var words = words(jobDescription);
        if (words.length < MIN_WORDS) {
            return Verdict.TOO_SHORT;
        }
        if (uniqueRatio(words) < MIN_UNIQUE_WORD_RATIO) {
            return Verdict.LOW_ENTROPY;
        }
        if (signalScore(words) < MIN_SIGNAL_WORDS) {
            return Verdict.NOT_JOB_LIKE;
        }
        return Verdict.ACCEPTED;
    }

    private static String[] words(String text) {
        return Arrays.stream(WORDS.split(text.trim()))
                .filter(word -> !word.isEmpty())
                .toArray(String[]::new);
    }

    private static double uniqueRatio(String[] words) {
        long distinct = Arrays.stream(words)
                .map(word -> word.toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        return (double) distinct / words.length;
    }

    /**
     * Distinct signal words, not occurrences: a posting repeating
     * "deneyim" nine times has said one thing, not nine.
     */
    private static long signalScore(String[] words) {
        return Arrays.stream(words)
                .map(word -> word.toLowerCase(Locale.ROOT))
                .filter(SIGNAL_WORDS::contains)
                .distinct()
                .count();
    }
}
