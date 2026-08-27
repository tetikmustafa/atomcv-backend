package com.mustafatetik.atomcv.profile.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Bolum 21.8's fourth step: what a translation was not allowed to change.
 *
 * <p>A CV is a set of claims about a person, and a language change must not
 * change any of them. A translation that turns 300,000 into "hundreds of
 * thousands", or renames an employer, has altered what the person is saying
 * about themselves — and it is the kind of alteration nobody proofreads out,
 * because the sentence still reads perfectly.
 *
 * <p><strong>Numbers are compared as digits, not as strings.</strong> Locales
 * write the same quantity as {@code 300,000} and {@code 300.000}, and a check
 * that rejected the separator would reject correct translations — the failure
 * mode that makes a guard get switched off. The digits are the claim; the
 * punctuation is typography.
 *
 * <p><strong>Proper nouns are compared case-folded.</strong> A model that
 * writes a name at the start of a sentence may capitalise it differently, and
 * that is not a change to the claim. {@code Locale.ROOT}, absolute rule 7:
 * under a Turkish locale "SQL" folds to "sqı" and every check would fail for
 * Turkish users only.
 */
public final class TranslationAudit {

    /** A run of digits, whatever surrounds it. */
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private TranslationAudit() {
    }

    /**
     * @param metrics     what the source claimed, as written
     * @param properNouns names that may not be translated
     * @param translated  the candidate wording
     * @return what went missing, as the source wrote it; empty when nothing did
     */
    public static List<String> missingFrom(
            List<String> metrics, List<String> properNouns, String translated) {

        String folded = translated == null ? "" : translated.toLowerCase(Locale.ROOT);
        List<String> digitsPresent = digitsOf(translated);
        List<String> missing = new ArrayList<>();

        for (String metric : metrics) {
            if (!digitsOf(metric).stream().allMatch(digitsPresent::contains)) {
                missing.add(metric);
            }
        }
        for (String noun : properNouns) {
            if (noun != null && !noun.isBlank()
                    && !folded.contains(noun.toLowerCase(Locale.ROOT))) {
                missing.add(noun);
            }
        }
        return List.copyOf(missing);
    }

    /**
     * A metric with no digits in it at all — "a quarter of the team" — is
     * carried by its proper nouns and its wording, and there is nothing here
     * to compare. Returning an empty list makes it pass, which is right: this
     * audit refuses what it can prove was lost, not what it cannot check.
     */
    private static List<String> digitsOf(String value) {
        if (value == null) {
            return List.of();
        }
        return DIGITS.matcher(value).results().map(match -> match.group()).toList();
    }
}
