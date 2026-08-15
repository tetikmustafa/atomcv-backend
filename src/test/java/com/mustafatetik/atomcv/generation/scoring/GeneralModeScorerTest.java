package com.mustafatetik.atomcv.generation.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.Entry;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Scoring without a job description (Bolum 19.4). */
class GeneralModeScorerTest {

    private static final UUID PROFILE = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);

    @Test
    void anOngoingJobIsAsRecentAsItGets() {
        assertThat(GeneralModeScorer.recency(entryEnding(null), TODAY)).isEqualTo(1.0);
    }

    @Test
    void anAtomWithNoEntryIsNotPunishedForHavingNoDates() {
        // A skill is not less relevant for being undated.
        assertThat(GeneralModeScorer.recency(null, TODAY)).isEqualTo(1.0);
    }

    @Test
    void recencyHalvesOverTheHalfLife() {
        var fiveYearsAgo = entryEnding(TODAY.minusYears(5));
        var tenYearsAgo = entryEnding(TODAY.minusYears(10));

        assertThat(GeneralModeScorer.recency(fiveYearsAgo, TODAY)).isCloseTo(0.5, within(0.01));
        assertThat(GeneralModeScorer.recency(tenYearsAgo, TODAY)).isCloseTo(0.25, within(0.01));
    }

    @Test
    void olderIsAlwaysWorseButNeverZero() {
        double previous = 1.0;
        for (int years = 1; years <= 30; years++) {
            double score = GeneralModeScorer.recency(entryEnding(TODAY.minusYears(years)), TODAY);
            assertThat(score).as("%d years", years).isLessThan(previous).isGreaterThan(0.0);
            previous = score;
        }
    }

    @Test
    void aBulletWithANumberInItCountsForMore() {
        var withMetric = atom();
        withMetric.setMetrics(List.of("300K+ rows"));

        assertThat(GeneralModeScorer.impact(withMetric)).isEqualTo(1.0);
        assertThat(GeneralModeScorer.impact(atom())).isEqualTo(0.3);
    }

    @Test
    void theWeightsAddUpToOneAndTheScoreStaysInRange() {
        var best = atom();
        best.setImportance(1.0f);
        best.setMetrics(List.of("50%"));
        best.setVerified(true);

        var worst = atom();
        worst.setImportance(0.0f);

        assertThat(GeneralModeScorer.score(best, entryEnding(null), TODAY))
                .isCloseTo(1.0, within(1e-9))
                .isLessThanOrEqualTo(1.0);
        assertThat(GeneralModeScorer.score(worst, entryEnding(TODAY.minusYears(40)), TODAY))
                .isGreaterThan(0.0)
                .isLessThan(0.1);
    }

    @Test
    void verificationIsWorthExactlyItsWeight() {
        var unverified = atom();
        unverified.setImportance(0.5f);
        var verified = atom();
        verified.setImportance(0.5f);
        verified.setVerified(true);

        assertThat(GeneralModeScorer.score(verified, null, TODAY)
                - GeneralModeScorer.score(unverified, null, TODAY))
                .isCloseTo(0.15, within(1e-9));
    }

    /** Bolum 19.6: the same profile on the same day scores the same. */
    @Test
    void theSameAtomScoresTheSameEveryTime() {
        var atom = atom();
        atom.setImportance(0.7f);
        var entry = entryEnding(TODAY.minusYears(3));
        double first = GeneralModeScorer.score(atom, entry, TODAY);

        for (int run = 0; run < 50; run++) {
            assertThat(GeneralModeScorer.score(atom, entry, TODAY)).isEqualTo(first);
        }
    }

    @Test
    void anEndDateInTheFutureIsAPlanNotAMistake() {
        assertThat(GeneralModeScorer.recency(entryEnding(TODAY.plusYears(1)), TODAY))
                .isEqualTo(1.0);
    }

    private static Atom atom() {
        return new Atom(PROFILE, UUID.randomUUID(), null, AtomKind.BULLET, (short) 0);
    }

    private static Entry entryEnding(LocalDate end) {
        var entry = new Entry(PROFILE, UUID.randomUUID(), "Engineer", (short) 0);
        entry.setStartDate(LocalDate.of(2015, 1, 1));
        entry.setEndDate(end);
        return entry;
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
