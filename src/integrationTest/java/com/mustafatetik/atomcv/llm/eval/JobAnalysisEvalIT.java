package com.mustafatetik.atomcv.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysisPhase;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * What Faz A's prompt actually does, scored — and it had no suite at all.
 *
 * <p><strong>Two of § 53.5's thresholds were declared and nothing measured
 * them</strong> (denetim, beşinci tur). {@code SCHEMA_CONFORMS} and
 * {@code REQUIRED_SKILLS_FOUND} had floors in {@link EvalThresholds}, a row
 * each in the chapter, and no line anywhere that recorded an observation —
 * while the CI lane fired on a change to <em>any</em> prompt and ran the Faz D
 * suite. Editing {@code job_analysis/v2.md} produced a green run that had
 * measured the covering letter's neighbour instead. A declared threshold with
 * no suite is worse than no threshold: it reads as coverage.
 *
 * <p><strong>Everything here acknowledges the preflight.</strong>
 * {@code JobDescriptionPreflight} is a free deterministic gate in front of the
 * model, and it catches the empty and the obviously short. Letting it answer
 * would mean scoring a regular expression and printing the result under a
 * prompt's name, so every case below goes to the model — including the
 * nonsense, which is the only way to ask whether the <em>prompt's</em>
 * plausibility gate works.
 *
 * <p><strong>No text is compared</strong>, for the reason the Faz D suite
 * gives: a model picks different words every time. What is measured is whether
 * a property held — did an answer come back at all, were the skills the
 * posting insists on among the ones it found, was a posting that says nothing
 * refused.
 */
@Tag("llm-eval")
class JobAnalysisEvalIT extends AbstractIntegrationTest {

    private static final EvalReport REPORT = new EvalReport();

    private static final List<Posting> REAL = real();

    private static final List<String> NONSENSE = nonsense();

    @Autowired
    private JobAnalysisPhase analysis;

    @Test
    void theanalysisFindsWhatapostingInsistsOnAndRefusesWhatSaysNothing() {
        int answered = 0;
        for (Posting posting : REAL) {
            Result<JobAnalysis> result = analyse(posting.text());

            boolean ok = result instanceof Result.Ok<JobAnalysis>;
            REPORT.record(EvalThresholds.SCHEMA_CONFORMS, ok);
            if (!ok) {
                // No answer means no skills to look for. Recording a miss per
                // expected skill would double-count one failure as two
                // metrics; recording nothing keeps the second rate about the
                // answers that exist.
                continue;
            }
            answered++;
            JobAnalysis found = ((Result.Ok<JobAnalysis>) result).value();
            Set<String> named = found.requiredSkills().stream()
                    .map(skill -> canonical(skill.canonical().isEmpty()
                            ? skill.name() : skill.canonical()))
                    .collect(Collectors.toSet());

            // One observation per skill rather than per posting: the chapter's
            // floor is "90% of required skills captured", and a posting-level
            // boolean would make one missed skill out of six the same failure
            // as six out of six.
            for (String expected : posting.mustFind()) {
                REPORT.record(EvalThresholds.REQUIRED_SKILLS_FOUND,
                        named.contains(canonical(expected)));
            }
        }

        for (String noise : NONSENSE) {
            Result<JobAnalysis> result = analyse(noise);
            REPORT.record(EvalThresholds.NONSENSE_REFUSED,
                    result instanceof Result.Err<JobAnalysis> err
                            && err.error() instanceof PipelineError.UnparseableJobDescription);
        }

        // The guard that keeps every rate below from being a tautology, and
        // the Faz D suite's own lesson: with no provider configured every call
        // fails, which would read as "nothing conformed" — a real failure — but
        // would also refuse every nonsense posting and score that metric at
        // 100%. A refusal rate is only worth reading once something was
        // accepted.
        assertThat(answered)
                .as("the provider answered at least one real posting; "
                        + "otherwise the refusal rate below is free")
                .isGreaterThan(0);
        assertThat(REPORT.observed(EvalThresholds.SCHEMA_CONFORMS))
                .as("the suite ran; a green run over nothing is not a measurement")
                .isEqualTo(REAL.size());
        assertThat(REPORT.observed(EvalThresholds.NONSENSE_REFUSED))
                .isEqualTo(NONSENSE.size());

        assertThat(REPORT.rate(EvalThresholds.SCHEMA_CONFORMS))
                .isGreaterThanOrEqualTo(EvalThresholds.floorFor(EvalThresholds.SCHEMA_CONFORMS));
        assertThat(REPORT.rate(EvalThresholds.REQUIRED_SKILLS_FOUND))
                .isGreaterThanOrEqualTo(
                        EvalThresholds.floorFor(EvalThresholds.REQUIRED_SKILLS_FOUND));
        assertThat(REPORT.rate(EvalThresholds.NONSENSE_REFUSED))
                .isGreaterThanOrEqualTo(EvalThresholds.floorFor(EvalThresholds.NONSENSE_REFUSED));
    }

    /** The table, printed — reading it is the point of paying for the run. */
    @AfterAll
    static void printTheTable() {
        if (REPORT.has(EvalThresholds.SCHEMA_CONFORMS)) {
            System.out.println(REPORT.render(JobAnalysisPhase.PROMPT_ID, "current"));
        }
    }

    private Result<JobAnalysis> analyse(String posting) {
        return analysis.analyse(posting, true, "eval", UUID.randomUUID(), UUID.randomUUID());
    }

    /** Absolute rule 7: matching is identity work, so the locale is fixed. */
    private static String canonical(String skill) {
        return skill.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Chosen for the ways an analysis goes wrong rather than for coverage: a
     * posting that names its stack in prose instead of a list, one that buries
     * the requirement under benefits, one written in Turkish, and one whose
     * "nice to have" section is longer than its requirements — which is where
     * a preferred skill gets promoted to a required one.
     */
    private static List<Posting> real() {
        return List.of(
                new Posting("""
                        Senior Backend Engineer — Payments

                        You will own our ledger service. It is written in Go, sits on
                        PostgreSQL, and is deployed to Kubernetes. You will be on call
                        for it. We expect you to have run a payments or ledger system
                        in production before.

                        Nice to have: Terraform, gRPC.
                        """, List.of("go", "postgresql", "kubernetes")),
                new Posting("""
                        Data Engineer

                        Benefits: private health cover, a learning budget, four weeks
                        of leave, a home office allowance, and a yearly trip.

                        The work: building pipelines in Python and dbt against
                        Snowflake. Airflow schedules them.
                        """, List.of("python", "dbt", "snowflake", "airflow")),
                new Posting("""
                        Kıdemli Frontend Geliştirici

                        React ve TypeScript ile çalışıyoruz. Next.js üzerinde sunucu
                        tarafı render yapıyoruz. Erişilebilirlik bizim için bir
                        özellik değil, bir gereklilik.
                        """, List.of("react", "typescript", "next.js")),
                new Posting("""
                        Platform Engineer

                        Required: strong Linux, and Terraform.

                        It would be lovely if you also knew Pulumi, or Ansible, or
                        Chef, or Puppet, or Helm, or Kustomize, or ArgoCD, or Flux,
                        or Vault, or Consul — we use several of these somewhere.
                        """, List.of("linux", "terraform")));
    }

    /**
     * Long enough to clear the preflight's length check, and saying nothing a
     * CV could be written against. The first is the shape a person actually
     * pastes by accident; the second is prose about a company that never gets
     * to the job.
     */
    private static List<String> nonsense() {
        return List.of("""
                Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
                eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim
                ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut
                aliquip ex ea commodo consequat. Duis aute irure dolor.
                """, """
                We are a company that believes in people. We believe in synergy and
                in the power of a team that believes. Our values are our values, and
                we live them every day. We were founded by founders who founded us,
                and we have been growing ever since. Come and grow with us. Apply.
                """);
    }

    /** One posting and the skills an analysis of it must contain. */
    private record Posting(String text, List<String> mustFind) {
    }
}
