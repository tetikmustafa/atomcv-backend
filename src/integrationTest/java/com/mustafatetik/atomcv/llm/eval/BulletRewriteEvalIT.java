package com.mustafatetik.atomcv.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.generation.rewrite.BulletRewriteService;
import com.mustafatetik.atomcv.generation.rewrite.RewriteCandidate;
import com.mustafatetik.atomcv.generation.rewrite.RewriteContext;
import com.mustafatetik.atomcv.generation.rewrite.RewriteIntent;
import com.mustafatetik.atomcv.generation.rewrite.RewriteIssue;
import com.mustafatetik.atomcv.generation.rewrite.RewriteValidator;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * What Faz D's prompt actually does, scored (Bolum 53.4, 53.5).
 *
 * <p><strong>This costs money and nothing runs it for you.</strong> Its own
 * Gradle lane — {@code gradlew llmEval} — excluded from {@code check}, from
 * {@code integrationTest} and from CI, and deliberately not nightly: Bolum
 * 53.7 says production telemetry gives the same information for nothing. Run
 * it when a prompt changes, which is about thirty cents.
 *
 * <p><strong>No text is compared.</strong> A model picks different words every
 * time, so an answer measured against a stored one measures the weather. What
 * is measured is whether a property held — did the numbers survive, did a
 * technology appear that the atom never claimed — and a rate over many cases
 * is a fact about a prompt.
 *
 * <p><strong>The properties are the production validator's, not a second
 * implementation.</strong> Bolum 53.8: {@link RewriteValidator} is what Faz D
 * runs against every answer in production, and it is what runs here. Separate
 * implementations breed "passes in the eval, broken on the page".
 *
 * <p>One threshold is not a threshold. {@code no_new_technologies} is zero
 * tolerance — a CV that claims a skill because a posting asked for it is not a
 * better CV, it is a false one, and the person has to defend it in an
 * interview.
 */
@Tag("llm-eval")
class BulletRewriteEvalIT extends AbstractIntegrationTest {

    private static final EvalReport REPORT = new EvalReport();

    /** Bolum 53.4's "thirty to fifty well-chosen cases", as the shapes that break things. */
    private static final List<Case> CASES = cases();

    @Autowired
    private BulletRewriteService rewrites;

    @Test
    void therewriteKeepsWhatTheAtomActuallyClaims() {
        int answered = 0;
        int unreachable = 0;
        for (Case scenario : CASES) {
            RewriteCandidate candidate = scenario.candidate();
            var result = rewrites.rewrite(candidate, context());
            unreachable += result.tally().unreachable();
            // Different from the original is the only proof a model answered.
            // Not a metric and not compared to a stored answer -- Bolum 53.4
            // forbids that because a model picks different words every time,
            // which is exactly why "it changed at all" is the signal here.
            if (!result.content().plainText().equals(scenario.text())) {
                answered++;
            }
            String rewritten = result.content().plainText();

            // The production validator, on the production path. Every metric
            // below is one of its issues, counted rather than thrown.
            List<RewriteIssue> issues = RewriteValidator.validate(
                    candidate, rewritten, context().postingSkills(), null, null);

            REPORT.record(EvalThresholds.NUMBERS_PRESERVED,
                    !issues.contains(RewriteIssue.NUMBER_LOST)
                            && !issues.contains(RewriteIssue.NUMBER_INVENTED));
            REPORT.record(EvalThresholds.ENTITIES_PRESERVED,
                    !issues.contains(RewriteIssue.PROPER_NOUN_LOST));
            REPORT.record(EvalThresholds.NO_NEW_TECHNOLOGIES,
                    !issues.contains(RewriteIssue.UNSUPPORTED_CLAIM));
            REPORT.record(EvalThresholds.LENGTH_WITHIN_BOUNDS,
                    !issues.contains(RewriteIssue.TOO_LONG));
            // What Faz D would have done with it: an answer carrying any
            // issue is thrown away and the original printed (Bolum 21.6), so
            // this rate is how often the phase was worth its call.
            REPORT.record(EvalThresholds.VALIDATION_ACCEPTED, issues.isEmpty());
        }

        assertThat(REPORT.observed(EvalThresholds.NO_NEW_TECHNOLOGIES))
                .as("the suite ran; a green run over nothing is not a measurement")
                .isEqualTo(CASES.size());

        // The guard that makes every number below mean something, and the
        // second attempt at it. With no provider configured Faz D answers with
        // the original bullet, and the original preserves its own numbers, its
        // own names and claims no technology it does not have -- so every rate
        // comes out at 100% and the table reads as a perfect prompt. Somebody
        // running this without a key would take that at face value.
        //
        // The first draft counted calls, and calls is the wrong number: the
        // tally records a request going out whatever came back, so eight
        // failures counted as eight calls and this passed. Running the lane
        // without a key is what showed it -- five metrics at 100% over eight
        // cases nobody had answered.
        assertThat(unreachable)
                .as("the provider was reachable; every rate below is otherwise a tautology")
                .isZero();
        assertThat(answered)
                .as("a model actually rewrote something")
                .isGreaterThan(0);

        // The blocker first, and on its own line: it is the one refusal that
        // is about the product rather than about quality.
        assertThat(REPORT.rate(EvalThresholds.NO_NEW_TECHNOLOGIES))
                .as("zero invented technologies (Bolum 53.5)")
                .isEqualTo(1.0);

        assertThat(REPORT.rate(EvalThresholds.NUMBERS_PRESERVED))
                .isGreaterThanOrEqualTo(EvalThresholds.floorFor(EvalThresholds.NUMBERS_PRESERVED));
        assertThat(REPORT.rate(EvalThresholds.ENTITIES_PRESERVED))
                .isGreaterThanOrEqualTo(
                        EvalThresholds.floorFor(EvalThresholds.ENTITIES_PRESERVED));
        assertThat(REPORT.rate(EvalThresholds.LENGTH_WITHIN_BOUNDS))
                .isGreaterThanOrEqualTo(
                        EvalThresholds.floorFor(EvalThresholds.LENGTH_WITHIN_BOUNDS));
    }

    /**
     * The table, printed. Reading it is the point of paying for the run —
     * a pass or a fail says less than five rates next to their floors.
     */
    @AfterAll
    static void printTheTable() {
        if (REPORT.has(EvalThresholds.NO_NEW_TECHNOLOGIES)) {
            System.out.println(REPORT.render(BulletRewriteService.PROMPT_ID, "current"));
        }
    }

    private static RewriteContext context() {
        return new RewriteContext(
                List.of("go", "postgresql", "kubernetes", "terraform"),
                List.of("Go", "PostgreSQL", "Kubernetes", "Terraform"),
                List.of("distributed systems", "reliability"),
                "Backend engineer, payments and ledgers.",
                "en", "formal", "eval", UUID.randomUUID(), UUID.randomUUID());
    }

    /**
     * Chosen for the ways an answer goes wrong, not for coverage: a bullet
     * with a number the model likes to round, one with a name it likes to
     * translate, one whose skills the posting does not ask for -- which is
     * where a keyword-stuffed answer comes from -- and one already at the
     * length limit.
     */
    private static List<Case> cases() {
        return List.of(
                new Case("Cut the nightly ledger window from six hours to fifty minutes",
                        List.of("go"), List.of("six", "fifty")),
                new Case("Moved 300K rows a night through a Go batch pipeline",
                        List.of("go"), List.of("300K")),
                new Case("Kept p99 latency under 90 ms at 40k requests a second",
                        List.of("postgresql"), List.of("90", "40k")),
                new Case("Mentored four engineers and ran the hiring loop",
                        List.of(), List.of("four")),
                new Case("Rebuilt the courier assignment algorithm; delivery time fell 12%",
                        List.of(), List.of("12%")),
                // A bullet whose skill the posting never mentions. If the
                // rewrite reaches for Kubernetes here, that is the failure
                // this whole suite exists to catch.
                new Case("Ran the release process for a Django monolith",
                        List.of("python"), List.of()),
                new Case("Wrote the incident review process the team still uses",
                        List.of(), List.of()),
                new Case("Split the order service from one deployable into six",
                        List.of("go"), List.of("six")));
    }

    /** One bullet and what it is allowed to claim. */
    private record Case(String text, List<String> skills, List<String> metrics) {

        RewriteCandidate candidate() {
            return new RewriteCandidate(UUID.randomUUID(), UUID.randomUUID(),
                    RichContent.plain(text), skills, metrics, List.of(),
                    0.8, text.length() + 30, RewriteIntent.ADAPT, null);
        }
    }
}
