package com.mustafatetik.atomcv.shared.text;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The names a keyword-stuffed answer would reach for (Bolum 21.6, 21.7, 34.4).
 *
 * <p>A guard can only refuse a claim it can recognise, and this is the list of
 * things it recognises. One copy, because the bullet rewrite, the summary and
 * the covering letter are exposed to the same temptation, and a term missing
 * from one of three lists is a hole in exactly one of them.
 *
 * <p><strong>Both halves of the alias dictionary.</strong> The file maps
 * {@code k8s = kubernetes}, so reading only its keys leaves the canonical name
 * — the one the model actually writes — out of the vocabulary entirely. That
 * was the shape of a real gap: "Kubernetes" in an answer matched nothing,
 * because nobody had ever needed an alias line whose left side was already the
 * name people write.
 */
public final class ClaimVocabulary {

    /** A word, keeping the characters technology names are actually spelled with. */
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}.+#-]*");

    private ClaimVocabulary() {
    }

    /**
     * @param postingSkills what this posting asked for, which is where the
     *                      temptation comes from — the prompt has just been
     *                      shown them. May be empty
     */
    public static Set<String> of(Collection<String> postingSkills) {
        Set<String> terms = new LinkedHashSet<>();
        for (String skill : postingSkills) {
            if (skill != null && !skill.isBlank()) {
                terms.add(skill.toLowerCase(Locale.ROOT));
            }
        }
        SkillNames.aliases().forEach((alias, canonical) -> {
            terms.add(alias);
            terms.add(canonical);
        });
        return terms;
    }

    /**
     * Names the answer introduced that none of its sources account for.
     *
     * <p>{@link #of} can only refuse a claim it recognises, and what it
     * recognises is a 74-line alias file plus the posting. A technology outside
     * both is invisible to it, and that is not a hypothetical: a summary
     * reached a real CV saying "modern caching and message queues (Redis,
     * Kafka)" where Redis was on the page and Kafka was nowhere in the profile.
     * Redis was checked and allowed; Kafka was never checked at all. Every
     * {@code UNSUPPORTED_CLAIM} test in the suite used Kubernetes, which is in
     * the file — so the hole had never been seen (§ 51.7).
     *
     * <p>This asks the question from the other side, where it is closed: not
     * "does the answer mention something I know?" but "does it name something
     * none of its sources do?". The sources are everything the rewrite was
     * allowed to draw on, so a name the person actually wrote is always
     * permitted and only an invention is left.
     *
     * <p>A token counts as a name when its shape says so and a sentence's first
     * word never does: an internal capital ({@code PyTorch}, {@code MySQL}), a
     * dot, plus or hash between characters ({@code Node.js}, {@code C++}), a
     * digit against letters ({@code S3}), or a capital that is not where a
     * sentence begins. False positives cost a discarded rewrite and the
     * person's own wording in its place, which is the direction P3 asks to
     * fail in.
     */
    public static Set<String> introducedNames(String answer, Collection<String> sources) {
        if (answer == null || answer.isBlank()) {
            return Set.of();
        }
        String folded = sourcesFolded(sources);
        Set<String> introduced = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(answer);
        while (matcher.find()) {
            // The token regex keeps the punctuation a name is spelled with,
            // so it also swallows the full stop that ends a sentence: the
            // trailing run goes before the word is looked anywhere up, or
            // "Postgres." is hunted for in sources that say "Postgres".
            String token = matcher.group().replaceAll("[.+#-]+$", "");
            if (!looksLikeAName(token, answer, matcher.start())) {
                continue;
            }
            if (mentions(folded, token)) {
                continue;
            }
            introduced.add(token);
        }
        return introduced;
    }

    private static String sourcesFolded(Collection<String> sources) {
        StringBuilder joined = new StringBuilder();
        for (String source : sources) {
            if (source != null && !source.isBlank()) {
                joined.append(' ').append(source);
            }
        }
        return joined.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikeAName(String trimmed, String answer, int at) {
        if (trimmed.length() < 2 || trimmed.chars().noneMatch(Character::isLetter)) {
            // A number is not a name, and 300.000 is a thousands separator in
            // one locale and a decimal point in another. Numbers are checked
            // as numbers, by their own rule.
            return false;
        }
        for (int index = 1; index < trimmed.length(); index++) {
            char here = trimmed.charAt(index);
            if (Character.isUpperCase(here)) {
                return true;
            }
            if ((here == '.' || here == '+' || here == '#')
                    && Character.isLetterOrDigit(trimmed.charAt(index - 1))) {
                return true;
            }
            if (Character.isDigit(here) && Character.isLetter(trimmed.charAt(index - 1))) {
                return true;
            }
        }
        return Character.isUpperCase(trimmed.charAt(0)) && !startsASentence(answer, at);
    }

    /**
     * Whether this is the first word of a sentence, where a capital says
     * nothing about the word.
     */
    private static boolean startsASentence(String answer, int at) {
        for (int index = at - 1; index >= 0; index--) {
            char before = answer.charAt(index);
            if (Character.isWhitespace(before)) {
                continue;
            }
            return before == '.' || before == '!' || before == '?'
                    || before == ':' || before == ';';
        }
        return true;
    }

    /**
     * Whether {@code foldedText} names {@code term} as a word of its own.
     *
     * <p>Whole words only, and the boundary has to admit the names people
     * actually write: {@code .NET}, {@code Node.js} and {@code C++} all begin
     * or end in a character a naive word boundary reads in the wrong
     * place, so the guard is written against word characters and hyphens
     * directly. A canonical skill is hyphenated where the writing has a space,
     * so both spellings are tried.
     *
     * @param foldedText already lowercased by the caller, which usually has it
     *                   folded once for several hundred of these
     */
    public static boolean mentions(String foldedText, String term) {
        String spelled = term.strip().toLowerCase(Locale.ROOT);
        if (spelled.isEmpty()) {
            return false;
        }
        return contains(foldedText, spelled) || contains(foldedText, spelled.replace('-', ' '));
    }

    private static boolean contains(String foldedText, String term) {
        Matcher matcher = Pattern.compile(
                "(?<![\\w-])" + Pattern.quote(term) + "(?![\\w-])").matcher(foldedText);
        return matcher.find();
    }
}
