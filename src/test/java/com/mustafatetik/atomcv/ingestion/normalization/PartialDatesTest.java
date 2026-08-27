package com.mustafatetik.atomcv.ingestion.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Bolum 31.5's date parsing, and the cases where it must refuse.
 *
 * <p>The refusals carry the weight. A wrong date on a CV is the one error
 * nobody proofreads out — a person scanning their own document sees a date
 * where a date belongs and reads straight past it — so anything that would
 * take a guess has to come back empty and become a question instead.
 */
class PartialDatesTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    // -- what it reads -----------------------------------------------------

    @Test
    void theShapeTheModelIsAskedFor() {
        assertThat(PartialDates.parse("2025-09")).contains(YearMonth.of(2025, 9));
        assertThat(PartialDates.parse("2025-9")).contains(YearMonth.of(2025, 9));
        assertThat(PartialDates.parse("2025/09")).contains(YearMonth.of(2025, 9));
    }

    @Test
    void theShapeMostOfEuropeWrites() {
        assertThat(PartialDates.parse("09/2025")).contains(YearMonth.of(2025, 9));
        assertThat(PartialDates.parse("9.2025")).contains(YearMonth.of(2025, 9));
    }

    @Test
    void englishMonthNamesLongAndShort() {
        assertThat(PartialDates.parse("September 2025")).contains(YearMonth.of(2025, 9));
        assertThat(PartialDates.parse("Sep 2025")).contains(YearMonth.of(2025, 9));
        assertThat(PartialDates.parse("Sept 2025")).contains(YearMonth.of(2025, 9));
    }

    /** Bolum 32: the product reads two languages, so it parses two. */
    @Test
    void turkishMonthNamesWithAndWithoutTheirDiacritics() {
        assertThat(PartialDates.parse("Eylül 2025")).contains(YearMonth.of(2025, 9));
        assertThat(PartialDates.parse("Eylul 2025")).contains(YearMonth.of(2025, 9));
        assertThat(PartialDates.parse("Eyl 2025")).contains(YearMonth.of(2025, 9));
        assertThat(PartialDates.parse("Ağustos 2024")).contains(YearMonth.of(2024, 8));
        assertThat(PartialDates.parse("Aralık 2023")).contains(YearMonth.of(2023, 12));
    }

    @Test
    void theYearMayComeFirst() {
        assertThat(PartialDates.parse("2025 Eylül")).contains(YearMonth.of(2025, 9));
        assertThat(PartialDates.parse("2025 September")).contains(YearMonth.of(2025, 9));
    }

    @Test
    void punctuationBetweenTheTwoIsIgnored() {
        assertThat(PartialDates.parse("Eylül, 2025")).contains(YearMonth.of(2025, 9));
        assertThat(PartialDates.parse("  September  2025 ")).contains(YearMonth.of(2025, 9));
    }

    // -- what it refuses ---------------------------------------------------

    /**
     * A year could mean any of twelve months, and widening it to January
     * invents eleven months of employment.
     */
    @Test
    void aYearWithNoMonthIsRefusedAndSaysSo() {
        assertThat(PartialDates.parse("2019")).isEmpty();
        assertThat(PartialDates.isYearOnly("2019")).isTrue();
        assertThat(PartialDates.isYearOnly("Eylül 2019")).isFalse();
    }

    @Test
    void aMonthThatIsNotAMonthIsRefused() {
        assertThat(PartialDates.parse("13/2025")).isEmpty();
        assertThat(PartialDates.parse("2025-00")).isEmpty();
        assertThat(PartialDates.parse("Smarch 2025")).isEmpty();
    }

    @Test
    void aYearOutsideAnyWorkingLifeIsAMisreadingAndNotADate() {
        assertThat(PartialDates.parse("1066-09")).isEmpty();
        assertThat(PartialDates.parse("3025-09")).isEmpty();
    }

    @Test
    void somethingThatIsNotADateAtAllIsRefused() {
        assertThat(PartialDates.parse("present")).isEmpty();
        assertThat(PartialDates.parse("halen")).isEmpty();
        assertThat(PartialDates.parse("")).isEmpty();
        assertThat(PartialDates.parse(null)).isEmpty();
    }

    // -- absolute rule 7 ---------------------------------------------------

    /**
     * The folding is {@code Locale.ROOT}, so a Turkish default locale does not
     * change which month names match — and this is a parser that reads Turkish
     * month names on a machine whose locale is Turkish.
     */
    @Test
    void aTurkishDefaultLocaleDoesNotChangeWhatParses() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        assertThat(PartialDates.parse("IX 2025")).isEmpty();
        assertThat(PartialDates.parse("Jan 2025")).contains(YearMonth.of(2025, 1));
        assertThat(PartialDates.parse("January 2025")).contains(YearMonth.of(2025, 1));
    }

    // -- and back out again ------------------------------------------------

    @Test
    void whatIsStoredIsTheShapeTheSchemaPublishes() {
        assertThat(PartialDates.format(YearMonth.of(2025, 9))).isEqualTo("2025-09");
        assertThat(PartialDates.format(YearMonth.of(999, 12))).isEqualTo("0999-12");
        assertThat(PartialDates.format(null)).isNull();
    }
}
