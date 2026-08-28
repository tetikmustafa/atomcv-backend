package com.mustafatetik.atomcv.generation.coverletter;

import com.mustafatetik.atomcv.shared.text.ClaimVocabulary;
import com.mustafatetik.atomcv.shared.text.SkillNames;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bolum 34.4 — six checks, and the letter is thrown away if any of them fires.
 *
 * <p>Stricter than Faz D's, and it has to be. A rewritten bullet that fails is
 * replaced by the sentence the person wrote; a letter that fails has no
 * original behind it, so the only two outcomes are an honest letter and none
 * at all. That makes a false positive expensive, which is why every check here
 * is against a closed set rather than a judgement — nothing refuses a draft for
 * being badly written, only for claiming something.
 */
public final class CoverLetterValidator {

    /** Bolum 34.4, verbatim. */
    static final int MIN_WORDS = 250;
    static final int MAX_WORDS = 400;

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /**
     * A number said about time: "eight years", "8 yıl". Both languages the
     * product ships in, because the claim is the same claim in either.
     */
    private static final Pattern YEARS_CLAIM = Pattern.compile(
            "(\\d+)\\s*\\+?\\s*(?:years?|yıl|yil|sene)", Pattern.CASE_INSENSITIVE);

    /**
     * Bolum 34.4's banned list, and its Turkish equivalents.
     *
     * <p>These are not banned for being clumsy. Each one is a sentence that
     * would be true of every applicant, which makes it a line the reader has
     * already skipped — and the letter has 400 words.
     */
    private static final List<String> CLICHES = List.of(
            "i am writing to express my interest",
            "i am writing to apply",
            "i believe i would be a great fit",
            "i would be a great fit",
            "i am a passionate",
            "i am passionate about",
            "i am a dedicated",
            "results-driven",
            "results driven",
            "thank you for considering my application",
            "proven track record",
            "team player",
            "detail-oriented",
            "ilginizi çekeceğimi",
            "başvurmak istiyorum",
            "kendimi geliştirmeyi seven",
            "takım oyuncusu",
            "sonuç odaklı",
            "tutkulu bir");

    private CoverLetterValidator() {
    }

    public static List<CoverLetterIssue> validate(
            CoverLetterInput input, CoverLetterDraft draft) {

        List<CoverLetterIssue> issues = new ArrayList<>();
        String letter = draft.plainText();
        String folded = letter.toLowerCase(Locale.ROOT);

        // 1. Bolum 34.4: every technology is one the page carries.
        if (namesSomethingThePageDoesNot(input, folded)) {
            issues.add(CoverLetterIssue.UNSUPPORTED_CLAIM);
        }

        // 3 before 2: a claim about years is checked against the dates, and
        // the digits it used must not then be reported as invented as well.
        List<String> yearsClaimed = yearsClaimedIn(letter);
        if (yearsClaimed.stream().anyMatch(
                claimed -> Integer.parseInt(claimed) > input.profileYears())) {
            issues.add(CoverLetterIssue.EXPERIENCE_OVERSTATED);
        }

        // 2. Every other number is one the page carries.
        if (carriesANumberThePageDoesNot(input, letter, yearsClaimed)) {
            issues.add(CoverLetterIssue.NUMBER_INVENTED);
        }

        // 4. The greeting is addressed to the right employer.
        if (greetsTheWrongCompany(input, draft.greeting().toLowerCase(Locale.ROOT))) {
            issues.add(CoverLetterIssue.WRONG_COMPANY);
        }

        // 5. Bolum 34.4's band.
        int words = wordCount(letter);
        if (words < MIN_WORDS || words > MAX_WORDS) {
            issues.add(CoverLetterIssue.LENGTH_OUT_OF_RANGE);
        }

        // 6. And the openings that say nothing about anybody.
        if (CLICHES.stream().anyMatch(folded::contains)) {
            issues.add(CoverLetterIssue.CLICHE);
        }
        return List.copyOf(issues);
    }

    /**
     * The vocabulary is the one Faz D checks a rewrite against; the permitted
     * half is the page's skills, plus what the person told us about the
     * employer (Bolum 34.5) and the sentences the letter is drawing from. A
     * technology inside the evidence is already on the page — refusing the
     * letter for repeating it would be refusing it for doing its job.
     */
    private static boolean namesSomethingThePageDoesNot(
            CoverLetterInput input, String foldedLetter) {

        Set<String> allowed = new LinkedHashSet<>();
        for (String skill : input.allowedSkills()) {
            allowed.add(SkillNames.canonical(skill));
        }
        StringBuilder ownWords = new StringBuilder(input.companyNote());
        input.evidence().forEach(evidence -> ownWords.append(' ').append(evidence.text()));
        String foldedOwn = ownWords.toString().toLowerCase(Locale.ROOT);

        for (String term : ClaimVocabulary.of(List.of())) {
            if (!ClaimVocabulary.mentions(foldedLetter, term)) {
                continue;
            }
            if (allowed.contains(SkillNames.canonical(term)) || ClaimVocabulary.mentions(foldedOwn, term)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Every digit run in the letter is one the page already carries — in a
     * metric, in the sentences being drawn from, or in what the person wrote
     * about the employer. The years claims are checked separately and removed
     * here, so a supported "eight years" is not also reported as an invention.
     */
    private static boolean carriesANumberThePageDoesNot(
            CoverLetterInput input, String letter, List<String> yearsClaimed) {

        Set<String> known = new LinkedHashSet<>(yearsClaimed);
        input.allowedMetrics().forEach(metric -> known.addAll(digitsOf(metric)));
        input.evidence().forEach(evidence -> known.addAll(digitsOf(evidence.text())));
        known.addAll(digitsOf(input.companyNote()));
        return !known.containsAll(digitsOf(letter));
    }

    /**
     * <strong>Ekleme — a closed set, not a company detector.</strong> Bolum
     * 34.4 asks whether the company name is right, and there is no dictionary
     * of employers to answer that with in general. What can be answered is the
     * failure that actually happens: a model that has just read this person's
     * CV addresses the letter to the employer it read there. So the greeting
     * is checked against the organisations on their own profile, and naming
     * one of those — where it is not also the company being written to — is
     * the letter going to the wrong place.
     */
    private static boolean greetsTheWrongCompany(
            CoverLetterInput input, String foldedGreeting) {

        String posting = input.companyName().strip().toLowerCase(Locale.ROOT);
        for (String employer : input.ownEmployers()) {
            String folded = employer.strip().toLowerCase(Locale.ROOT);
            if (folded.isEmpty() || folded.equals(posting)) {
                continue;
            }
            if (foldedGreeting.contains(folded)) {
                return true;
            }
        }
        return false;
    }

    static int wordCount(String letter) {
        String stripped = letter.strip();
        return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
    }

    private static List<String> yearsClaimedIn(String letter) {
        return YEARS_CLAIM.matcher(letter).results().map(match -> match.group(1)).toList();
    }

    /**
     * The names a stuffed letter would reach for: the alias dictionary, both
     * halves of it. {@code k8s = kubernetes} means the canonical name is only
     * ever on the right, and it is the one a model writes.
     */
    private static Set<String> vocabulary() {
        Set<String> terms = new LinkedHashSet<>();
        SkillNames.aliases().forEach((alias, canonical) -> {
            terms.add(alias);
            terms.add(canonical);
        });
        return terms;
    }

    private static List<String> digitsOf(String value) {
        if (value == null) {
            return List.of();
        }
        return DIGITS.matcher(value).results().map(match -> match.group()).toList();
    }
}
