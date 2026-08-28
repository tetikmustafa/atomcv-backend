package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bolum 21.7's check — the summary says nothing the page does not.
 *
 * <p>This is the phase's most dangerous output and the cheapest to get wrong.
 * A bullet is one job at one employer and a reader takes it as such; the
 * opening paragraph is read as the person's own account of themselves, and an
 * employer quotes it back in the first five minutes of an interview.
 */
class AboutValidatorTest {

    /** The whole point. Kubernetes is on no bullet, so it is on no summary. */
    @Test
    void asummaryMayNotNameATechnologyThePageDoesNotCarry() {
        var issues = AboutValidator.validate(
                candidate("Ran things.", List.of("postgres"), List.of()),
                "Backend engineer working across Postgres and Kubernetes.", List.of());

        assertThat(issues).containsExactly(RewriteIssue.UNSUPPORTED_CLAIM);
    }

    @Test
    void asummaryDrawnFromThePagePassesEveryCheck() {
        var issues = AboutValidator.validate(
                candidate("Ran things.", List.of("postgres", "etl"), List.of("40")),
                "Backend engineer who runs Postgres and ETL, and cut a nightly run to 40 "
                        + "minutes.", List.of());

        assertThat(issues).isEmpty();
    }

    /**
     * <strong>The reason Bolum 21.6.1 gives, applied here.</strong> A skill
     * the person wrote about themselves and the extraction never listed is
     * theirs to claim; refusing it would be an outage dressed as a guard.
     */
    @Test
    void atechnologyThePersonWroteAboutThemselvesIsTheirsToClaim() {
        var candidate = new AboutCandidate(UUID.randomUUID(),
                RichContent.plain("Ran things."), List.of("postgres"), List.of(),
                "Spent the last four years mostly in Kubernetes.", List.of(), 400);

        var issues = AboutValidator.validate(
                candidate, "Backend engineer working across Postgres and Kubernetes.", List.of());

        assertThat(issues).isEmpty();
    }

    /**
     * <strong>Where a summary lies most naturally.</strong> Three years here
     * and four there do not make seven, and no bullet on the page said eight.
     */
    @Test
    void asummaryMayNotCarryANumberThePageDoesNot() {
        var issues = AboutValidator.validate(
                candidate("Ran things.", List.of("postgres"), List.of("40")),
                "Backend engineer with 8 years across Postgres.", List.of());

        assertThat(issues).containsExactly(RewriteIssue.NUMBER_INVENTED);
    }

    /** A number the person themselves wrote is not invented. */
    @Test
    void anumberFromThePersonsOwnWordsIsAllowed() {
        var candidate = new AboutCandidate(UUID.randomUUID(),
                RichContent.plain("Ran things."), List.of("postgres"), List.of(),
                "8 years, most of them on call.", List.of(), 400);

        var issues = AboutValidator.validate(
                candidate, "Backend engineer, 8 years on Postgres.", List.of());

        assertThat(issues).isEmpty();
    }

    /**
     * <strong>And the check is deliberately literal.</strong> The person wrote
     * "Eight" and the answer wrote "8", which is the same claim and is refused
     * anyway. Reading numbers out of words is a language-by-language problem
     * with a wrong answer in every one of them, and the cost of the strict
     * rule is one paragraph falling back to what the person already had. The
     * prompt is told not to do this; a model that does it twice loses the
     * synthesis, not the CV.
     */
    @Test
    void awrittenOutNumberTurnedIntoAFigureIsStillRefused() {
        var candidate = new AboutCandidate(UUID.randomUUID(),
                RichContent.plain("Ran things."), List.of("postgres"), List.of(),
                "Eight years, most of them on call.", List.of(), 400);

        var issues = AboutValidator.validate(
                candidate, "Backend engineer, 8 years on Postgres.", List.of());

        assertThat(issues).containsExactly(RewriteIssue.NUMBER_INVENTED);
    }

    /** Bolum 21.3's ceiling: the page was costed on the paragraph that was there. */
    @Test
    void asummaryLongerThanTheParagraphItReplacesIsRefused() {
        var candidate = new AboutCandidate(UUID.randomUUID(),
                RichContent.plain("Short."), List.of("postgres"), List.of(), "", List.of(), 20);

        var issues = AboutValidator.validate(candidate,
                "A paragraph considerably longer than the one it is replacing.", List.of());

        assertThat(issues).contains(RewriteIssue.TOO_LONG);
    }

    /**
     * An empty answer is not a short answer. Selection paid for a heading and
     * a paragraph, and a heading with nothing under it spends the first
     * without printing the second.
     */
    @Test
    void anemptySummaryIsRefused() {
        var issues = AboutValidator.validate(
                candidate("Ran things.", List.of("postgres"), List.of()), "   ", List.of());

        assertThat(issues).containsExactly(RewriteIssue.TOO_LONG);
    }

    private static AboutCandidate candidate(
            String original, List<String> skills, List<String> metrics) {

        return new AboutCandidate(UUID.randomUUID(), RichContent.plain(original),
                skills, metrics, "", List.of(), 400);
    }
}
