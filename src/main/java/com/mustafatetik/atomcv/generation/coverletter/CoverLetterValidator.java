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

    /**
     * Bolum 34.4's ceiling, verbatim — and a floor it no longer names.
     *
     * <p><strong>Düzeltme (F-026).</strong> The section said 250, and 250 was
     * never measured. Five drafts recorded against the real end on 2026-08-30
     * came to 106, 119, 127, 130 and 153 words; the two from a real posting
     * were the last two. Every one of them was thrown away, and the only way
     * out the wire offers for that is {@code retry} — which asks the same
     * model for the same letter and gets the same length. A dead end, and the
     * screen was telling people it usually clears.
     *
     * <p>The floor is 120 because a letter with a greeting, an opening, two or
     * three pieces of evidence, a closing and a signature does not get much
     * under it without one of them missing — and because 150, the obvious
     * round number, would still refuse the shorter of the two real drafts and
     * leave the dead end half open.
     *
     * <p><strong>Length is the only check here that is not about a
     * claim.</strong> The other five ask whether the letter says something the
     * page does not; this one asks how long it is. A false positive costs the
     * whole letter in both cases, but only here does it cost it for something
     * that was true. That is the argument for moving the floor rather than
     * arguing with the model about it.
     *
     * <p>The prompt still asks for 250-400 and deliberately so: asking for
     * more than the floor is not a contradiction, and changing what is asked
     * is a new prompt version (Bolum 53.2) — worth doing when the model is
     * chosen and the ask can be measured against it.
     */
    static final int MIN_WORDS = 120;
    static final int MAX_WORDS = 400;

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /**
     * A number, however it was written down: grouped with separators or not,
     * and followed by a scale word or not. The grouped form comes first in the
     * alternation so that {@code 40,000} is one match rather than two.
     *
     * <p>The scale word has to be a whole word. Without that, {@code bin}
     * would match inside {@code binary} and turn "three binary formats" into
     * three thousand of them.
     */
    private static final Pattern QUANTITY = Pattern.compile(
            "(\\d{1,3}(?:[.,\\u00A0\\u202F ]\\d{3})+|\\d+)"
                    + "(?:\\s*(bin|milyon|milyar|thousand|million|billion)\\b)?",
            Pattern.CASE_INSENSITIVE);

    /** What separates the groups of a written-out number, and nothing else. */
    private static final Pattern SEPARATORS = Pattern.compile("[.,\\u00A0\\u202F ]");

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
     * Every quantity in the letter is one the page already carries — in a
     * metric, in the sentences being drawn from, or in what the person wrote
     * about the employer. The years claims are checked separately and removed
     * here, so a supported "eight years" is not also reported as an invention.
     *
     * <p><strong>Düzeltme against Bolum 34.4.1 (F-026).</strong> That decision
     * says the check "reads digit runs", and reading them was measured wrong.
     * The page carries {@code "saniyede 40 bin istek"} and the model wrote
     * {@code "40,000 requests per second"} — the same number, said the way a
     * letter says it. Split on {@code \d+} that is {@code 40} against
     * {@code 40} and {@code 000}, so the separator itself was reported as an
     * invented number, and the letter was thrown away for quoting the page
     * correctly. Two of the four drafts the frontend measured failed on
     * exactly this and on nothing else about numbers.
     *
     * <p>So the comparison is between quantities rather than character runs:
     * a grouped number is read as one, and a scale word after it is read as
     * the zeroes it stands for. The page's side keeps its raw runs as well, so
     * a letter quoting only part of a written-out number is no worse off than
     * it was.
     */
    private static boolean carriesANumberThePageDoesNot(
            CoverLetterInput input, String letter, List<String> yearsClaimed) {

        Set<String> known = new LinkedHashSet<>(yearsClaimed);
        input.allowedMetrics().forEach(metric -> known.addAll(quantitiesOnThePage(metric)));
        input.evidence().forEach(
                evidence -> known.addAll(quantitiesOnThePage(evidence.text())));
        known.addAll(quantitiesOnThePage(input.companyNote()));
        return !known.containsAll(quantitiesIn(letter));
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

    /**
     * The quantities a piece of text states, each as its digits with nothing
     * between them.
     *
     * <p>A grouped number is one quantity: {@code 40,000}, {@code 40.000} and
     * {@code 40 000} all read as {@code 40000}, because the separator is
     * typography and not a second number. A scale word after a number is the
     * zeroes it stands for, so {@code 40 bin} reads as {@code 40000} as well
     * and the page and the letter can spell the same figure differently.
     *
     * <p><strong>The scale words are a closed numeric vocabulary, and that is
     * not the thing F-025 refused to write.</strong> There the list would have
     * been of sentences a model might use to say "I do not know" — open, and
     * one entry behind whatever it writes next. Six words for a thousand, a
     * million and a billion in the two languages the product ships in is a
     * fact about those languages instead. A word this does not know costs a
     * merge, never a wrong one.
     */
    private static List<String> quantitiesIn(String value) {
        if (value == null) {
            return List.of();
        }
        return QUANTITY.matcher(value).results()
                .map(CoverLetterValidator::quantityOf)
                .toList();
    }

    /**
     * The same, plus the raw digit runs.
     *
     * <p>Only for the page's side of the comparison. "40 bin" states 40000 and
     * it also, on the page, has a 40 in it — a letter that quotes the 40 and
     * not the scale is saying less than the page does, and the check is about
     * saying more.
     */
    private static List<String> quantitiesOnThePage(String value) {
        if (value == null) {
            return List.of();
        }
        List<String> stated = new ArrayList<>(quantitiesIn(value));
        DIGITS.matcher(value).results().forEach(match -> stated.add(match.group()));
        return stated;
    }

    private static String quantityOf(java.util.regex.MatchResult match) {
        String digits = SEPARATORS.matcher(match.group(1)).replaceAll("");
        String scale = match.group(2);
        return scale == null ? digits : digits + zeroesFor(scale);
    }

    private static String zeroesFor(String scaleWord) {
        return switch (scaleWord.toLowerCase(Locale.ROOT)) {
            case "bin", "thousand" -> "000";
            case "milyon", "million" -> "000000";
            default -> "000000000";
        };
    }
}
