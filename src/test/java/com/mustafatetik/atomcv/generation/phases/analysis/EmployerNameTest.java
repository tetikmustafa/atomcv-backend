package com.mustafatetik.atomcv.generation.phases.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * F-025: the employer is a name the posting carries, or it is nothing.
 *
 * <p>The three cases at the top are not invented. They are what this
 * repository's three recorded {@code job_analysis} answers actually wrote for
 * a posting that named no employer, plus the fourth the frontend measured
 * against the real end — four spellings of "absent" from one model, which is
 * why the check is a property and not a list of them.
 */
class EmployerNameTest {

    private static final String POSTING = """
            Calico Teknoloji is hiring a fullstack developer for its geospatial
            team. You will work on ingestion pipelines in Python and Go, and on
            the PostGIS side of the platform.
            """;

    @Test
    void aNameThePostingCarriesIsTheEmployer() {
        var checked = EmployerName.verifiedAgainst(analysisNaming("Calico Teknoloji"), POSTING);

        assertThat(checked.company().name()).isEqualTo("Calico Teknoloji");
    }

    /** The one the frontend measured on the wire. */
    @Test
    void aSentenceSayingThereIsNoEmployerIsNotAnEmployer() {
        var checked = EmployerName.verifiedAgainst(analysisNaming("not specified"), POSTING);

        assertThat(checked.company().name()).isEmpty();
    }

    /** And the one sitting in a fixture in this repository. */
    @Test
    void norIsTheWordUnknown() {
        var checked = EmployerName.verifiedAgainst(analysisNaming("Unknown"), POSTING);

        assertThat(checked.company().name()).isEmpty();
    }

    /** In the posting's own language, without anything having to know which. */
    @Test
    void norIsItsTurkishEquivalent() {
        var checked = EmployerName.verifiedAgainst(analysisNaming("Belirtilmemiş"), POSTING);

        assertThat(checked.company().name()).isEmpty();
    }

    /** Already absent, and it stays that way rather than becoming a rebuild. */
    @Test
    void anEmptyNameIsLeftExactlyAsItWas() {
        var analysis = analysisNaming("");

        assertThat(EmployerName.verifiedAgainst(analysis, POSTING)).isSameAs(analysis);
    }

    @Test
    void quotingItInAnotherCaseIsStillQuotingIt() {
        var checked = EmployerName.verifiedAgainst(analysisNaming("CALICO TEKNOLOJI"),
                POSTING.toUpperCase(java.util.Locale.ROOT));

        assertThat(checked.company().name()).isEqualTo("CALICO TEKNOLOJI");
    }

    /**
     * A posting is pasted text and it wraps where the browser put it. The name
     * is still in it.
     */
    @Test
    void aNameThePostingBrokeAcrossTwoLinesIsStillInIt() {
        var wrapped = "We are Calico\nTeknoloji and we are hiring a developer.";

        var checked = EmployerName.verifiedAgainst(analysisNaming("Calico Teknoloji"), wrapped);

        assertThat(checked.company().name()).isEqualTo("Calico Teknoloji");
    }

    /**
     * <strong>The cost of the rule, asserted rather than left implied.</strong>
     * A model that expands or translates the name instead of quoting it loses
     * the label. That is the trade being made: a row with no company still
     * says what the job was, and a row naming the wrong one cannot be spotted
     * by the person reading it.
     */
    @Test
    void aNameTheModelRewroteRatherThanQuotedIsLost() {
        var checked = EmployerName.verifiedAgainst(
                analysisNaming("Calico Teknoloji A.Ş."), POSTING);

        assertThat(checked.company().name()).isEmpty();
    }

    /** Only the name goes. Everything the analysis is for stays. */
    @Test
    void nothingElseAboutTheAnalysisChanges() {
        var checked = EmployerName.verifiedAgainst(analysisNaming("not specified"), POSTING);

        assertThat(checked.role().title()).isEqualTo("Fullstack Developer");
        assertThat(checked.company().sizeHint()).isEqualTo(JobAnalysis.SizeHint.STARTUP);
        assertThat(checked.requiredSkills()).hasSize(1);
        assertThat(checked.confidence()).isEqualTo(0.9);
        assertThat(checked.jdLanguage()).isEqualTo("en");
    }

    private static JobAnalysis analysisNaming(String company) {
        return new JobAnalysis(
                new JobAnalysis.Role("Fullstack Developer", null, "geospatial", null, null),
                new JobAnalysis.Company(company, JobAnalysis.SizeHint.STARTUP),
                List.of(new JobAnalysis.Skill("Python", "python", null)),
                List.of(), List.of("build ingestion pipelines"), List.of("postgis"),
                null, List.of(), "technical", "en", 0.9, List.of());
    }
}
