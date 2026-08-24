package com.mustafatetik.atomcv.generation.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Bolum 23.3's ladder, and the counts under it.
 *
 * <p>The report is the one place the product says how well a CV fits, so its
 * failure mode is not a crash: it is a number that flatters. Every test here
 * is about the report staying honest when it would be easy not to be.
 */
class FitReportTest {

    @Test
    void everyRequirementCoveredAndMostNiceToHavesIsStrong() {
        var report = FitReport.of(
                posting(List.of("go", "kubernetes"), List.of("grpc", "terraform", "ci")),
                Set.of("go", "kubernetes", "grpc", "ci"));

        assertThat(report.requiredCovered()).isEqualTo(2);
        assertThat(report.requiredTotal()).isEqualTo(2);
        assertThat(report.preferredCovered()).isEqualTo(2);
        assertThat(report.preferredTotal()).isEqualTo(3);
        assertThat(report.level()).isEqualTo(MatchLevel.STRONG);
    }

    @Test
    void oneMissingRequirementIsModerateHoweverManyNiceToHavesAreCovered() {
        // The order of the rungs is the design: nice-to-haves never stand in
        // for a requirement. A report that let three preferred skills lift a
        // missing required one would be telling the user their gap does not
        // matter.
        var report = FitReport.of(
                posting(List.of("go", "kubernetes"), List.of("grpc", "terraform", "ci")),
                Set.of("go", "grpc", "terraform", "ci"));

        assertThat(report.level()).isEqualTo(MatchLevel.MODERATE);
        assertThat(report.missingRequired()).containsExactly("kubernetes");
        assertThat(report.preferredCovered()).isEqualTo(3);
    }

    @Test
    void twoMissingRequirementsIsWeak() {
        var report = FitReport.of(
                posting(List.of("go", "kubernetes", "postgres"), List.of("grpc")),
                Set.of("postgres", "grpc"));

        assertThat(report.level()).isEqualTo(MatchLevel.WEAK);
        assertThat(report.missingRequired()).containsExactly("go", "kubernetes");
    }

    @Test
    void aCleanMatchWithFewNiceToHavesIsGoodAndNotStrong() {
        // Exactly 0.6 is not "more than 0.6". Written as >= it would promote
        // three-of-five, and the two levels would stop meaning different
        // things.
        var report = FitReport.of(
                posting(List.of("go"), List.of("a", "b", "c", "d", "e")),
                Set.of("go", "a", "b", "c"));

        assertThat(report.preferredCovered()).isEqualTo(3);
        assertThat(report.preferredTotal()).isEqualTo(5);
        assertThat(report.level()).isEqualTo(MatchLevel.GOOD);
    }

    @Test
    void apostingThatAskedForNothingCannotBeShortOfIt() {
        var report = FitReport.of(posting(List.of(), List.of()), Set.of("go"));

        assertThat(report.level()).isEqualTo(MatchLevel.GOOD);
        assertThat(report.requiredTotal()).isZero();
        assertThat(report.coveredSkills()).isEmpty();
    }

    @Test
    void theNamesThatComeBackArePostingsOwnWordsAndTheMatchIsOnTheKey() {
        // The posting was written in Turkish; the canonical key is English
        // because that is what an atom carries. The user looks for the word
        // they read in the advert, so that is the word the report returns.
        var skills = List.of(
                new JobAnalysis.Skill("mikroservis", "microservices",
                        JobAnalysis.Importance.CRITICAL),
                new JobAnalysis.Skill("veri tabanı", "postgresql",
                        JobAnalysis.Importance.CRITICAL));

        var report = FitReport.of(postingOf(skills, List.of()), Set.of("microservices"));

        assertThat(report.coveredSkills()).containsExactly("mikroservis");
        assertThat(report.missingRequired()).containsExactly("veri tabanı");
    }

    @Test
    void matchingSurvivesTheCaseThePostingHappenedToUse() {
        // Absolute rule 7 with the case that proves it: under a Turkish
        // locale "SQL" lowercases to "sqı" and would match no atom at all.
        var skills = List.of(
                new JobAnalysis.Skill("SQL", "  SQL  ", JobAnalysis.Importance.CRITICAL));

        var report = FitReport.of(postingOf(skills, List.of()), Set.of("sql"));

        assertThat(report.requiredCovered()).isEqualTo(1);
        assertThat(report.level()).isEqualTo(MatchLevel.GOOD);
    }

    @Test
    void askillTheModelCouldNotCanonicaliseCountsAsMissing() {
        // Skipping it would shrink the denominator, which turns an extraction
        // gap into a better-looking match — the one direction this report is
        // not allowed to be wrong in.
        var skills = List.of(
                new JobAnalysis.Skill("something odd", "", JobAnalysis.Importance.CRITICAL),
                new JobAnalysis.Skill("Go", "go", JobAnalysis.Importance.CRITICAL));

        var report = FitReport.of(postingOf(skills, List.of()), Set.of("go", ""));

        assertThat(report.requiredTotal()).isEqualTo(2);
        assertThat(report.requiredCovered()).isEqualTo(1);
        assertThat(report.missingRequired()).containsExactly("something odd");
        assertThat(report.level()).isEqualTo(MatchLevel.MODERATE);
    }

    @Test
    void coveredSkillsCarryBothListsAndTheMissingOnesStayApart() {
        var report = FitReport.of(
                posting(List.of("go"), List.of("grpc", "terraform")),
                Set.of("go", "grpc"));

        assertThat(report.coveredSkills()).containsExactly("go", "grpc");
        assertThat(report.missingRequired()).isEmpty();
        assertThat(report.missingPreferred()).containsExactly("terraform");
    }

    private static JobAnalysis posting(List<String> required, List<String> preferred) {
        return postingOf(
                required.stream().map(name -> new JobAnalysis.Skill(
                        name, name, JobAnalysis.Importance.CRITICAL)).toList(),
                preferred.stream().map(name -> new JobAnalysis.Skill(
                        name, name, null)).toList());
    }

    private static JobAnalysis postingOf(
            List<JobAnalysis.Skill> required, List<JobAnalysis.Skill> preferred) {

        return new JobAnalysis(
                new JobAnalysis.Role("Senior Backend Engineer", JobAnalysis.Seniority.SENIOR,
                        "fintech", JobAnalysis.EmploymentType.FULL_TIME,
                        JobAnalysis.WorkMode.REMOTE),
                new JobAnalysis.Company("Acme", JobAnalysis.SizeHint.SCALEUP),
                required, preferred,
                List.of("design and scale payment systems"),
                List.of("distributed systems"),
                new JobAnalysis.ExperienceYears(5, null),
                List.of("en"), "technical", "en", 0.94, List.of());
    }
}
