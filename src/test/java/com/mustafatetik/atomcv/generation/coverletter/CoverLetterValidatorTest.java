package com.mustafatetik.atomcv.generation.coverletter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bolum 34.4 — the six checks, and why they are stricter than Faz D's.
 *
 * <p>A refused rewrite prints the person's own sentence. A refused letter
 * prints nothing, so every check here has to be against a closed set: a false
 * positive costs the whole letter, and a false negative is a claim made in the
 * first person that an interview opens with.
 */
class CoverLetterValidatorTest {

    /** The whole point. Kubernetes is on no atom of this page. */
    @Test
    void aletterMayNotNameATechnologyThePageDoesNotCarry() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Acme,", padded("Backend engineer working across Postgres "
                        + "and Kubernetes.")));

        assertThat(issues).containsExactly(CoverLetterIssue.UNSUPPORTED_CLAIM);
    }

    @Test
    void aletterDrawnFromThePagePassesEveryCheck() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Acme,", padded("Ran Postgres in production and cut the "
                        + "nightly load to 40 minutes.")));

        assertThat(issues).isEmpty();
    }

    /**
     * <strong>Bolum 34.4's "most common fabrication".</strong> The dates on the
     * profile say six years; the letter says twelve.
     */
    @Test
    void aletterMayNotClaimMoreYearsThanTheProfileCarries() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Acme,", padded("Twelve is not the number; 12 years of "
                        + "Postgres is what this claims.")));

        assertThat(issues).contains(CoverLetterIssue.EXPERIENCE_OVERSTATED);
    }

    /** Claiming fewer years than the dates support is not a lie. */
    @Test
    void aletterMayClaimFewerYearsThanTheProfileCarries() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Acme,", padded("Across 5 years of Postgres work.")));

        assertThat(issues).isEmpty();
    }

    /**
     * And a supported claim about years is not also reported as an invented
     * number — the two checks read the same digits and would otherwise both
     * fire on one honest sentence.
     */
    @Test
    void asupportedYearsClaimIsNotAlsoReportedAsAnInventedNumber() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Acme,", padded("Across 6 years of Postgres work.")));

        assertThat(issues).isEmpty();
    }

    @Test
    void aletterMayNotCarryANumberThePageDoesNot() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Acme,", padded("Ran a fleet of 97 Postgres instances.")));

        assertThat(issues).containsExactly(CoverLetterIssue.NUMBER_INVENTED);
    }

    /**
     * <strong>F-026, and this one is measured.</strong> The page says
     * {@code "saniyede 40 bin istek"}; the draft the frontend recorded against
     * the real end on 2026-08-30 says {@code "40,000 requests per second"}.
     * The same number, written the way a letter writes it — and read as digit
     * runs it was {@code 40} against {@code 40} and {@code 000}, so the
     * separator was reported as an invented number and the letter was thrown
     * away for quoting the page correctly.
     */
    @Test
    void thesameNumberGroupedTheWayALetterWritesItIsNotAnInvention() {
        var issues = CoverLetterValidator.validate(pageCarrying("saniyede 40 bin istek"),
                draft("Dear Acme,", padded("The cache served 40,000 requests per second.")));

        assertThat(issues).isEmpty();
    }

    /** The other separator, which is the one the posting's own language uses. */
    @Test
    void andSoIsTheSeparatorTurkishWritesIt() {
        var issues = CoverLetterValidator.validate(pageCarrying("saniyede 40 bin istek"),
                draft("Dear Acme,", padded("The cache served 40.000 requests per second.")));

        assertThat(issues).isEmpty();
    }

    /**
     * And the scale word on the letter's side. The page is written without
     * separators here on purpose: with them it would carry a bare {@code 2} of
     * its own and the letter's {@code 2 million} would pass without anything
     * having to understand the word.
     */
    @Test
    void ascaleWordOnTheLettersSideIsTheQuantityItStandsFor() {
        var issues = CoverLetterValidator.validate(pageCarrying("2000000 olay"),
                draft("Dear Acme,", padded("The pipeline handled 2 million events a day.")));

        assertThat(issues).isEmpty();
    }

    /**
     * <strong>The other half, also measured.</strong> The same recording run
     * produced "a 12% increase in system efficiency", and there is no 12
     * anywhere on that page. Reading quantities rather than digit runs must
     * not turn the check off.
     */
    @Test
    void anumberTheLetterReallyInventedIsStillCaught() {
        var issues = CoverLetterValidator.validate(pageCarrying("saniyede 40 bin istek"),
                draft("Dear Acme,", padded("A 12% increase, on 40,000 requests per second.")));

        assertThat(issues).containsExactly(CoverLetterIssue.NUMBER_INVENTED);
    }

    /**
     * A scale word is a word. Without that, "bin" inside "binary" would make
     * three formats into three thousand of them — and the letter would then be
     * carrying a number nobody wrote.
     */
    @Test
    void ascaleWordInsideAnotherWordIsNotAScale() {
        var issues = CoverLetterValidator.validate(pageCarrying("3 Postgres instances"),
                draft("Dear Acme,", padded("Across 3 binary formats on Postgres.")));

        assertThat(issues).isEmpty();
    }

    /**
     * <strong>The mistake a reader sees before they have read a
     * sentence.</strong> The model has just been shown this person's CV, and
     * addresses the letter to the employer it read there.
     */
    @Test
    void aletterGreetingAPastEmployerIsRefused() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Initech,", padded("Ran Postgres in production.")));

        assertThat(issues).containsExactly(CoverLetterIssue.WRONG_COMPANY);
    }

    /** A generic greeting is ordinary — a posting need not name a company. */
    @Test
    void agenericGreetingIsFine() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Hiring Manager,", padded("Ran Postgres in production.")));

        assertThat(issues).isEmpty();
    }

    /**
     * And an employer that is also the company being written to is not the
     * wrong company — somebody applying back to a place they worked.
     */
    @Test
    void agreetingNamingTheEmployerBeingWrittenToIsFine() {
        var input = new CoverLetterInput("Ada Lovelace", "Backend Engineer", "Initech",
                List.of(new CoverLetterInput.Evidence(
                        "Ran the Postgres fleet", List.of("postgres"), List.of())),
                List.of("postgres"), List.of("40"), List.of("Initech"), 6, "", "en", "formal");

        var issues = CoverLetterValidator.validate(input,
                draft("Dear Initech,", padded("Ran Postgres in production.")));

        assertThat(issues).isEmpty();
    }

    @Test
    void aletterTooShortToBeOneIsRefused() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Acme,", "Ran Postgres in production."));

        assertThat(issues).containsExactly(CoverLetterIssue.LENGTH_OUT_OF_RANGE);
    }

    /**
     * <strong>F-026, and the reason the floor moved.</strong> The shorter of
     * the two drafts recorded against the real end on 2026-08-30 was 130
     * words, and every draft of that run was thrown away for it. The only
     * resolution the wire offers is {@code retry}, which asks the same model
     * for the same letter — so an honest letter of the length this pipeline
     * actually writes has to be a letter that passes.
     */
    @Test
    void aletterOfTheLengthThisPipelineWritesIsAccepted() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Acme,", "Ran Postgres in production. " + "filler ".repeat(125)));

        assertThat(issues).isEmpty();
    }

    /** The ceiling is Bolum 34.4's and did not move: over 400 nobody reads it. */
    @Test
    void aletterPastTheCeilingIsStillRefused() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Acme,", "Ran Postgres in production. " + "filler ".repeat(420)));

        assertThat(issues).containsExactly(CoverLetterIssue.LENGTH_OUT_OF_RANGE);
    }

    /**
     * Bolum 34.4's banned openings. Not for being clumsy — each is a sentence
     * that would be true of everybody applying, and the letter has 400 words.
     */
    @Test
    void abannedOpeningIsRefused() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Dear Acme,", padded("I am writing to express my interest in "
                        + "this role, and I ran Postgres.")));

        assertThat(issues).containsExactly(CoverLetterIssue.CLICHE);
    }

    @Test
    void theturkishBannedListIsCheckedToo() {
        var issues = CoverLetterValidator.validate(input(),
                draft("Merhaba,", padded("Bu pozisyona başvurmak istiyorum ve "
                        + "Postgres calistirdim.")));

        assertThat(issues).contains(CoverLetterIssue.CLICHE);
    }

    // -- fixtures ----------------------------------------------------------

    /** Six years of dates, one past employer, Postgres and a 40. */
    private static CoverLetterInput input() {
        return new CoverLetterInput("Ada Lovelace", "Backend Engineer", "Acme",
                List.of(new CoverLetterInput.Evidence(
                        "Ran the Postgres fleet", List.of("postgres"), List.of())),
                List.of("postgres"), List.of("40"), List.of("Initech"), 6, "", "en", "formal");
    }

    /** The same page, with one metric of this test's own. */
    private static CoverLetterInput pageCarrying(String metric) {
        return new CoverLetterInput("Ada Lovelace", "Backend Engineer", "Acme",
                List.of(new CoverLetterInput.Evidence(
                        "Ran the Postgres fleet", List.of("postgres"), List.of())),
                List.of("postgres"), List.of(metric), List.of("Initech"), 6, "", "en", "formal");
    }

    private static CoverLetterDraft draft(String greeting, String body) {
        return new CoverLetterDraft(greeting, "About this role.", body,
                "Happy to talk.", "Ada Lovelace");
    }

    /** The same body, padded into Bolum 34.4's band so length is not the issue. */
    private static String padded(String body) {
        return body + " " + "filler ".repeat(260);
    }
}
