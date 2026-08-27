package com.mustafatetik.atomcv.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Bolum 21.8's fourth step, on both sides of the line.
 *
 * <p>The false positives carry the weight here. A guard that rejects correct
 * translations is a guard somebody switches off, and this one runs on a
 * background job where a rejection is invisible except as a wording that never
 * updates.
 */
class TranslationAuditTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    // -- what it lets through ----------------------------------------------

    @Test
    void agoodTranslationKeepsEverythingAndPasses() {
        var lost = TranslationAudit.missingFrom(
                List.of("300,000 rows"), List.of("Microsoft Fabric"),
                "300.000 satırı Microsoft Fabric ile taşıdım");

        assertThat(lost).isEmpty();
    }

    /**
     * The separator is typography and the digits are the claim. A check that
     * rejected {@code 300.000} for a source that wrote {@code 300,000} would
     * refuse every correct Turkish translation there is.
     */
    @Test
    void aNumberWrittenWithAnotherLocalesSeparatorIsStillTheSameNumber() {
        assertThat(TranslationAudit.missingFrom(
                List.of("1,250"), List.of(), "1.250 kayıt")).isEmpty();
        assertThat(TranslationAudit.missingFrom(
                List.of("40%"), List.of(), "yüzde 40 azalttım")).isEmpty();
    }

    @Test
    void aProperNounWrittenWithDifferentCapitalisationIsStillTheSameName() {
        assertThat(TranslationAudit.missingFrom(
                List.of(), List.of("PostgreSQL"), "postgresql ile çalıştım")).isEmpty();
    }

    /**
     * A metric with no digits in it is carried by its wording, and there is
     * nothing to compare. This audit refuses what it can prove was lost, not
     * what it cannot check.
     */
    @Test
    void ametricWithNoDigitsInItIsNotSomethingThisCanJudge() {
        assertThat(TranslationAudit.missingFrom(
                List.of("a quarter of the team"), List.of(), "ekibin bir kısmı")).isEmpty();
    }

    @Test
    void nothingToKeepMeansNothingToLose() {
        assertThat(TranslationAudit.missingFrom(List.of(), List.of(), "herhangi bir cümle"))
                .isEmpty();
    }

    // -- what it stops -----------------------------------------------------

    /**
     * The failure this exists for: a sentence that still reads perfectly and
     * no longer says what the person claimed.
     */
    @Test
    void anumberTurnedIntoAWordIsCaught() {
        var lost = TranslationAudit.missingFrom(
                List.of("300,000 rows"), List.of(), "yüz binlerce satır taşıdım");

        assertThat(lost).containsExactly("300,000 rows");
    }

    @Test
    void arenamedEmployerIsCaught() {
        var lost = TranslationAudit.missingFrom(
                List.of(), List.of("Brisa Bridgestone"), "bir lastik firmasında çalıştım");

        assertThat(lost).containsExactly("Brisa Bridgestone");
    }

    @Test
    void atranslatedProductNameIsCaught() {
        var lost = TranslationAudit.missingFrom(
                List.of(), List.of("Microsoft Fabric"), "Microsoft Kumaş ile taşıdım");

        assertThat(lost).containsExactly("Microsoft Fabric");
    }

    @Test
    void everythingLostIsReportedRatherThanTheFirstOne() {
        var lost = TranslationAudit.missingFrom(
                List.of("300,000"), List.of("Brisa", "Microsoft Fabric"),
                "Microsoft Fabric ile çok satır taşıdım");

        assertThat(lost).containsExactlyInAnyOrder("300,000", "Brisa");
    }

    @Test
    void anEmptyTranslationLosesEverything() {
        var lost = TranslationAudit.missingFrom(List.of("42"), List.of("Brisa"), "");

        assertThat(lost).hasSize(2);
    }

    // -- absolute rule 7 ---------------------------------------------------

    /**
     * The folding is {@code Locale.ROOT}. Under a Turkish default locale "SQL"
     * folds to "sqı" and every proper-noun check would fail — on the machine
     * of the users this feature exists for.
     */
    @Test
    void aTurkishDefaultLocaleDoesNotChangeWhatPasses() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));

        assertThat(TranslationAudit.missingFrom(
                List.of(), List.of("SQL"), "SQL sorgularını hızlandırdım")).isEmpty();
        assertThat(TranslationAudit.missingFrom(
                List.of(), List.of("ISTANBUL"), "Istanbul ofisinde")).isEmpty();
    }
}
