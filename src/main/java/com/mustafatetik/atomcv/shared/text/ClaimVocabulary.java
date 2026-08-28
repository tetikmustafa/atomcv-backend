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
