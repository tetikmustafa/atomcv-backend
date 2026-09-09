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
     * <strong>Ekleme — numbers said in words, which nothing used to read
     * (Bolum 34.4.2).</strong>
     *
     * <p>Measured on the twelve recorded drafts: four of them spell a number
     * out, and one is a claim the page does not support — the page says a
     * response time went from 800 ms to 90 ms and the letter wrote "from over
     * eighty milliseconds to ninety milliseconds". Read as digits that is 80
     * against a page carrying 800, which is exactly what this check exists to
     * refuse, and it walked through because the digits were never written.
     *
     * <p><strong>Single words only, and a run of two is skipped entirely.</strong>
     * "twenty five people" must not be read as five: getting a quantity wrong
     * is worse than not reading it, because this check throws the letter away.
     * A compound therefore costs a miss, never a wrong number — the same rule
     * the scale words are held to.
     *
     * <p><strong>A spelled number in front of a scale word is a deliberate
     * gap.</strong> "two million events" appears in two of the twelve drafts
     * and is not read, though the machinery to read it is right here. Reading
     * it would mean a letter claiming 2000000 against a page that may well
     * write "2M" — and the page's side turns that into the digit run 2. That
     * asymmetry already exists for digits and is accepted there; extending it
     * to two drafts that pass today, without the pages to check against, is
     * how a guard starts refusing honest letters.
     */
    private static final java.util.Map<String, String> SPELLED = java.util.Map.ofEntries(
            java.util.Map.entry("zero", "0"), java.util.Map.entry("sıfır", "0"),
            java.util.Map.entry("sifir", "0"),
            java.util.Map.entry("one", "1"), java.util.Map.entry("bir", "1"),
            java.util.Map.entry("two", "2"), java.util.Map.entry("iki", "2"),
            java.util.Map.entry("three", "3"), java.util.Map.entry("üç", "3"),
            java.util.Map.entry("uc", "3"),
            java.util.Map.entry("four", "4"), java.util.Map.entry("dört", "4"),
            java.util.Map.entry("dort", "4"),
            java.util.Map.entry("five", "5"), java.util.Map.entry("beş", "5"),
            java.util.Map.entry("bes", "5"),
            java.util.Map.entry("six", "6"), java.util.Map.entry("altı", "6"),
            java.util.Map.entry("alti", "6"),
            java.util.Map.entry("seven", "7"), java.util.Map.entry("yedi", "7"),
            java.util.Map.entry("eight", "8"), java.util.Map.entry("sekiz", "8"),
            java.util.Map.entry("nine", "9"), java.util.Map.entry("dokuz", "9"),
            java.util.Map.entry("ten", "10"), java.util.Map.entry("on", "10"),
            java.util.Map.entry("eleven", "11"), java.util.Map.entry("twelve", "12"),
            java.util.Map.entry("thirteen", "13"), java.util.Map.entry("fourteen", "14"),
            java.util.Map.entry("fifteen", "15"), java.util.Map.entry("sixteen", "16"),
            java.util.Map.entry("seventeen", "17"), java.util.Map.entry("eighteen", "18"),
            java.util.Map.entry("nineteen", "19"),
            java.util.Map.entry("twenty", "20"), java.util.Map.entry("yirmi", "20"),
            java.util.Map.entry("thirty", "30"), java.util.Map.entry("otuz", "30"),
            java.util.Map.entry("forty", "40"), java.util.Map.entry("kırk", "40"),
            java.util.Map.entry("kirk", "40"),
            java.util.Map.entry("fifty", "50"), java.util.Map.entry("elli", "50"),
            java.util.Map.entry("sixty", "60"), java.util.Map.entry("altmış", "60"),
            java.util.Map.entry("altmis", "60"),
            java.util.Map.entry("seventy", "70"), java.util.Map.entry("yetmiş", "70"),
            java.util.Map.entry("yetmis", "70"),
            java.util.Map.entry("eighty", "80"), java.util.Map.entry("seksen", "80"),
            java.util.Map.entry("ninety", "90"), java.util.Map.entry("doksan", "90"));

    /**
     * What turns a number word into a measurement rather than a turn of phrase.
     *
     * <p>This is the whole reason the check is anchored. English writes
     * "particularly <em>one</em> focused on Java" and means no quantity at all;
     * a rule reading every number word would throw that letter away for the
     * word "one". A number followed by a unit is saying how many, and a letter
     * has no reason to say how many about anything the page does not carry.
     */
    private static final Set<String> UNITS = Set.of(
            "year", "years", "yıl", "yil", "sene", "yila", "yıla",
            "month", "months", "ay", "aylık", "aylik",
            "week", "weeks", "hafta", "haftalık", "haftalik",
            "day", "days", "gün", "gun", "günlük", "gunluk",
            "hour", "hours", "saat", "saatlik",
            "minute", "minutes", "dakika", "dakikalık", "dakikalik",
            "second", "seconds", "saniye", "saniyelik",
            "millisecond", "milliseconds", "ms", "milisaniye",
            "percent", "percentage", "yüzde", "yuzde",
            "people", "person", "kişi", "kisi", "kişilik", "kisilik",
            "engineer", "engineers", "developer", "developers",
            "member", "members", "mühendis", "muhendis", "geliştirici", "gelistirici");

    /** The units that make a number a claim about length of service. */
    private static final Set<String> YEARS = Set.of("year", "years", "yıl", "yil", "sene");

    /** "a team of six" — the anchor comes before the number instead of after. */
    private static final Set<String> GROUPS = Set.of(
            "team", "group", "squad", "crew", "ekip", "takım", "takim", "grup");

    /** Words, in order, lowercased — the unit of the spelled-number scan. */
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}%]+");

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

    /**
     * Every claim about length of service, in digits or in words.
     *
     * <p>The spelled half has to be here and not only in the quantity scan.
     * These two checks are ordered — a supported "eight years" is removed from
     * the invented-number comparison because the dates already answered it —
     * and a spelled year read by one check and not the other would refuse
     * "thirteen years" on a page whose dates say thirteen. It would be a
     * quantity nothing had accounted for, which is the worst kind of false
     * positive: correct letter, thrown away, no original behind it.
     */
    private static List<String> yearsClaimedIn(String letter) {
        List<String> claimed = new ArrayList<>(
                YEARS_CLAIM.matcher(letter).results().map(match -> match.group(1)).toList());

        List<String> words = WORDS.matcher(letter).results()
                .map(match -> match.group().toLowerCase(Locale.ROOT))
                .toList();
        for (int at = 0; at + 1 < words.size(); at++) {
            boolean alone = !SPELLED.containsKey(words.get(at + 1))
                    && (at == 0 || !SPELLED.containsKey(words.get(at - 1)));
            if (alone && SPELLED.containsKey(words.get(at)) && YEARS.contains(words.get(at + 1))) {
                claimed.add(SPELLED.get(words.get(at)));
            }
        }
        return claimed;
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
        List<String> stated = new ArrayList<>(QUANTITY.matcher(value).results()
                .map(CoverLetterValidator::quantityOf)
                .toList());
        stated.addAll(spelledQuantitiesIn(value));
        return stated;
    }

    /**
     * The numbers this text says in words, as the digits they stand for.
     *
     * <p>Three shapes count, and nothing else does:
     *
     * <ul>
     *   <li>a number word followed by a unit — "thirteen years", "eighty
     *       milliseconds", "altı kişilik"</li>
     *   <li>a group noun, "of", then a number word — "a team of six", where
     *       the anchor is in front</li>
     *   <li>"zero" and "sıfır" on their own, because there is no way to say
     *       zero of something and not be making a claim about it: "zero
     *       duplicate charges" is a measurement whether or not the noun after
     *       it is a unit this knows</li>
     * </ul>
     *
     * <p>Everything else is left alone on purpose. "one focused on Java",
     * "three reasons", "one of the things I did" are turns of phrase, and this
     * check throws the whole letter away — a rule that read them would refuse
     * an honest draft for its grammar.
     */
    // Package-private so the scan can be asserted on real letters, not only through validate().
    static List<String> spelledQuantitiesIn(String value) {
        List<String> words = WORDS.matcher(value).results()
                .map(match -> match.group().toLowerCase(Locale.ROOT))
                .toList();

        List<String> stated = new ArrayList<>();
        for (int at = 0; at < words.size(); at++) {
            if (!SPELLED.containsKey(words.get(at))) {
                continue;
            }
            int end = at;
            while (end + 1 < words.size() && SPELLED.containsKey(words.get(end + 1))) {
                end++;
            }
            if (end > at) {
                // A run of two or more: "twenty five" is twenty-five and this
                // does not do arithmetic on words. Reading it as five would be
                // a wrong number, which costs the letter.
                at = end;
                continue;
            }
            if (isAQuantity(words, at)) {
                stated.add(SPELLED.get(words.get(at)));
            }
        }
        return stated;
    }

    private static boolean isAQuantity(List<String> words, int at) {
        String word = words.get(at);
        if ("0".equals(SPELLED.get(word))) {
            return true;
        }
        if (at + 1 < words.size() && UNITS.contains(words.get(at + 1))) {
            return true;
        }
        return at >= 2 && "of".equals(words.get(at - 1)) && GROUPS.contains(words.get(at - 2));
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
