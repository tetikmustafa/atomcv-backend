package com.mustafatetik.atomcv.ingestion.normalization;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The dates a CV actually contains, read into a year and a month
 * (Bolum 31.5).
 *
 * <p><strong>An unreadable date is left out, never guessed at.</strong> Bolum
 * 31.5 says so and the reason is that a plausible wrong date is the one kind
 * of error nobody proofreads out — a person scanning their own CV sees a date
 * where a date belongs and reads past it. The caller raises an
 * {@code ambiguous_date} warning instead, and the review screen of Bolum 31.6
 * opens on it.
 *
 * <p><strong>A year with no month is unreadable here.</strong> "2019" could
 * mean any of twelve months, and widening it to January is inventing eleven
 * months of employment. The person is asked.
 *
 * <p>Turkish and English month names, because those are the two languages the
 * product reads (Bolum 32). {@code Locale.ROOT} on the folding, absolute rule
 * 7: a Turkish default locale lowercases "IX" to "ıx" and the Roman numeral
 * some CVs use would stop matching.
 */
public final class PartialDates {

    /** {@code 2025-09} and {@code 2025/09}, which is what a model returns. */
    private static final Pattern ISO = Pattern.compile("^(\\d{4})[-/](\\d{1,2})$");

    /** {@code 09/2025} and {@code 09.2025}: month first, as most of Europe writes it. */
    private static final Pattern MONTH_FIRST = Pattern.compile("^(\\d{1,2})[-/.](\\d{4})$");

    /** {@code September 2025}, {@code Eyl 2025}, {@code Eylül, 2025}. */
    private static final Pattern NAMED = Pattern.compile("^([\\p{L}]+)[\\s,.]+(\\d{4})$");

    /** {@code 2025 September} and {@code 2025 Eylül}, which Turkish CVs also write. */
    private static final Pattern YEAR_FIRST = Pattern.compile("^(\\d{4})[\\s,.]+([\\p{L}]+)$");

    /**
     * A year on its own, matched only to be refused with a reason.
     *
     * <p>Kept as a pattern rather than falling through so the distinction is
     * visible: "2019" is a date we read and rejected, "spring-ish" is a date we
     * did not read at all. Both become null, and the caller does not yet
     * distinguish them — but the next person to ask "why is this null" reads
     * this and knows.
     */
    private static final Pattern YEAR_ONLY = Pattern.compile("^(\\d{4})$");

    private static final Map<String, Integer> MONTHS = months();

    private PartialDates() {
    }

    /**
     * @param written the date as it appeared, in any of the shapes above
     * @return the year and month, or empty when it could not be read without
     *         guessing
     */
    public static Optional<YearMonth> parse(String written) {
        if (written == null || written.isBlank()) {
            return Optional.empty();
        }
        String value = written.strip().toLowerCase(Locale.ROOT);

        Matcher iso = ISO.matcher(value);
        if (iso.matches()) {
            return of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)));
        }
        Matcher monthFirst = MONTH_FIRST.matcher(value);
        if (monthFirst.matches()) {
            return of(Integer.parseInt(monthFirst.group(2)),
                    Integer.parseInt(monthFirst.group(1)));
        }
        Matcher named = NAMED.matcher(value);
        if (named.matches()) {
            return byName(named.group(1), named.group(2));
        }
        Matcher yearFirst = YEAR_FIRST.matcher(value);
        if (yearFirst.matches()) {
            return byName(yearFirst.group(2), yearFirst.group(1));
        }
        // A bare year, and everything else. Both are the same answer.
        return Optional.empty();
    }

    /** Whether a value was a year with no month, for a warning that says so. */
    public static boolean isYearOnly(String written) {
        return written != null && YEAR_ONLY.matcher(written.strip()).matches();
    }

    /** What is stored and what the schema publishes: {@code YYYY-MM}. */
    public static String format(YearMonth month) {
        return month == null ? null : String.format(Locale.ROOT, "%04d-%02d",
                month.getYear(), month.getMonthValue());
    }

    private static Optional<YearMonth> byName(String name, String year) {
        Integer month = MONTHS.get(name);
        return month == null ? Optional.empty() : of(Integer.parseInt(year), month);
    }

    private static Optional<YearMonth> of(int year, int month) {
        if (month < 1 || month > 12 || year < 1900 || year > 2100) {
            // A date outside any working life is a misreading, not a date.
            return Optional.empty();
        }
        return Optional.of(YearMonth.of(year, month));
    }

    /**
     * Every spelling of a month this product may meet, mapped to its number.
     *
     * <p>Written out rather than taken from {@code java.time.Month} and a
     * locale: the abbreviations CVs use are not the ones the JDK's Turkish
     * locale produces ("Eyl" against "Eylül"), and the folded forms without
     * diacritics matter more than either — people type "eylul".
     */
    private static Map<String, Integer> months() {
        Map<String, Integer> months = new LinkedHashMap<>();
        add(months, 1, "january", "jan", "ocak", "oca");
        add(months, 2, "february", "feb", "şubat", "subat", "şub", "sub");
        add(months, 3, "march", "mar", "mart");
        add(months, 4, "april", "apr", "nisan", "nis");
        add(months, 5, "may", "mayıs", "mayis", "may");
        add(months, 6, "june", "jun", "haziran", "haz");
        add(months, 7, "july", "jul", "temmuz", "tem");
        add(months, 8, "august", "aug", "ağustos", "agustos", "ağu", "agu");
        add(months, 9, "september", "sep", "sept", "eylül", "eylul", "eyl");
        add(months, 10, "october", "oct", "ekim", "eki");
        add(months, 11, "november", "nov", "kasım", "kasim", "kas");
        add(months, 12, "december", "dec", "aralık", "aralik", "ara");
        return Map.copyOf(months);
    }

    private static void add(Map<String, Integer> months, int number, String... names) {
        for (String name : names) {
            months.put(name, number);
        }
    }
}
