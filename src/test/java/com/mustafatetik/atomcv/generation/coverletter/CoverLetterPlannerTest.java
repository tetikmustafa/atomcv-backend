package com.mustafatetik.atomcv.generation.coverletter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.EntryNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bolum 34.2 — the letter is the narrative version of the page.
 *
 * <p>Which is a constraint before it is a feature: everything the letter is
 * allowed to say is collected from the atoms selection <em>kept</em>, so a
 * letter cannot tell a story about a CV the employer is not holding.
 */
class CoverLetterPlannerTest {

    private static final UUID PROFILE = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 28);

    @Test
    void theevidenceIsTheHighestScoringAtomsOnThePage() {
        var fixture = new Fixture();
        var best = fixture.bullet("Ran the Postgres fleet", List.of("postgres"), List.of());
        var middling = fixture.bullet("Wrote the runbooks", List.of(), List.of());
        var worst = fixture.bullet("Kept the wiki tidy", List.of(), List.of());

        var input = CoverLetterPlanner.plan(fixture.profile(), fixture.tree(),
                selection(chosen(worst, 0.2), chosen(best, 0.9), chosen(middling, 0.5)),
                posting(), "", "en", "formal", TODAY);

        assertThat(input.evidence()).extracting(CoverLetterInput.Evidence::text)
                .containsExactly("Ran the Postgres fleet", "Wrote the runbooks",
                        "Kept the wiki tidy");
    }

    /** Bolum 34.3 asks for two or three, and three is the cap. */
    @Test
    void atmostThreePiecesOfEvidenceAreCarried() {
        var fixture = new Fixture();
        var atoms = new ArrayList<SelectedAtom>();
        for (int i = 0; i < 6; i++) {
            atoms.add(chosen(fixture.bullet("Bullet " + i, List.of("java"), List.of()),
                    0.9 - i * 0.1));
        }

        var input = CoverLetterPlanner.plan(fixture.profile(), fixture.tree(),
                selection(atoms.toArray(new SelectedAtom[0])), posting(), "", "en", "formal",
                TODAY);

        assertThat(input.evidence()).hasSize(CoverLetterPlanner.EVIDENCE_COUNT);
    }

    /**
     * <strong>The constraint, not the content.</strong> An atom dropped for
     * budget is not on the page, so the letter may not name what it claimed.
     */
    @Test
    void theallowedSkillsAreTheOnesSelectionKept() {
        var fixture = new Fixture();
        var onThePage = fixture.bullet("Ran the Postgres fleet", List.of("postgres"), List.of());
        fixture.bullet("Wrote the Kafka consumers", List.of("kafka"), List.of());

        var input = CoverLetterPlanner.plan(fixture.profile(), fixture.tree(),
                selection(chosen(onThePage, 0.9)), posting(), "", "en", "formal", TODAY);

        assertThat(input.allowedSkills()).containsExactly("postgres");
    }

    /**
     * <strong>The span, not the sum.</strong> Two jobs held at once are two
     * entries and one stretch of a life; adding them would hand the letter a
     * larger number than the truth, which is exactly Bolum 34.4's most common
     * fabrication.
     */
    @Test
    void theyearsWorkedAreTheSpanAndNotTheSumOfTheEntries() {
        var entries = List.of(
                entry("Initech", LocalDate.of(2018, 1, 1), LocalDate.of(2024, 1, 1)),
                entry("Moonlight", LocalDate.of(2020, 1, 1), LocalDate.of(2023, 1, 1)));

        assertThat(CoverLetterPlanner.yearsWorking(entries, TODAY)).isEqualTo(6);
    }

    /** An entry still running counts up to today. */
    @Test
    void anongoingEntryCountsUpToToday() {
        var entries = List.of(entry("Initech", LocalDate.of(2020, 8, 28), null));

        assertThat(CoverLetterPlanner.yearsWorking(entries, TODAY)).isEqualTo(6);
    }

    /** Undated entries contribute nothing rather than a guess. */
    @Test
    void anundatedProfileClaimsNoYearsAtAll() {
        assertThat(CoverLetterPlanner.yearsWorking(
                List.of(entry("Initech", null, null)), TODAY)).isZero();
    }

    /** The greeting check needs the person's own employers, from the tree. */
    @Test
    void theownEmployersAreCarriedForTheGreetingCheck() {
        var fixture = new Fixture();
        var bullet = fixture.bullet("Ran the Postgres fleet", List.of("postgres"), List.of());

        var input = CoverLetterPlanner.plan(fixture.profile(), fixture.tree(),
                selection(chosen(bullet, 0.9)), posting(), "", "en", "formal", TODAY);

        assertThat(input.ownEmployers()).containsExactly("Initech");
        assertThat(input.companyName()).isEqualTo("Acme");
        assertThat(input.applicantName()).isEqualTo("Ada Lovelace");
    }

    /** Bolum 34.5: what the person knows about the employer travels as given. */
    @Test
    void whatThePersonKnowsAboutTheCompanyIsCarriedThrough() {
        var fixture = new Fixture();
        var bullet = fixture.bullet("Ran the Postgres fleet", List.of("postgres"), List.of());

        var input = CoverLetterPlanner.plan(fixture.profile(), fixture.tree(),
                selection(chosen(bullet, 0.9)), posting(), "They open-sourced their scheduler.",
                "en", "formal", TODAY);

        assertThat(input.companyNote()).isEqualTo("They open-sourced their scheduler.");
    }

    // -- fixtures ----------------------------------------------------------

    private static SelectionState selection(SelectedAtom... atoms) {
        return new SelectionState(List.of(atoms), List.of(),
                new SelectionState.BudgetBreakdown(600, 100, 500, 300));
    }

    private static SelectedAtom chosen(AtomNode node, double score) {
        return new SelectedAtom(node.atom().getId(), node.variants().get(0).getId(),
                score, 12.0, false);
    }

    private static Entry entry(String organisation, LocalDate start, LocalDate end) {
        Entry entry = new Entry(PROFILE, UUID.randomUUID(), "Engineer", (short) 0);
        entry.setOrganization(organisation);
        entry.setStartDate(start);
        entry.setEndDate(end);
        return entry;
    }

    private static JobAnalysis posting() {
        return new JobAnalysis(
                new JobAnalysis.Role("Backend Engineer", JobAnalysis.Seniority.SENIOR,
                        "fintech", JobAnalysis.EmploymentType.FULL_TIME,
                        JobAnalysis.WorkMode.REMOTE),
                new JobAnalysis.Company("Acme", JobAnalysis.SizeHint.SCALEUP),
                List.of(), List.of(), List.of("run the platform"), List.of(),
                new JobAnalysis.ExperienceYears(5, null),
                List.of("en"), "technical", "en", 0.94, List.of());
    }

    /** One entry at one employer, with bullets under it. */
    private static final class Fixture {

        private final Section section =
                new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
        private final Entry entry = entry("Initech",
                LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1));
        private final List<AtomNode> bullets = new ArrayList<>();

        AtomNode bullet(String text, List<String> skills, List<String> metrics) {
            Atom atom = new Atom(PROFILE, section.getId(), entry.getId(),
                    AtomKind.BULLET, (short) bullets.size());
            atom.setSkills(skills);
            atom.setMetrics(metrics);
            var wording = new AtomVariant(PROFILE, atom.getId(), "en", RichContent.plain(text));
            wording.setPrimary(true);
            AtomNode node = new AtomNode(atom, List.of(wording));
            bullets.add(node);
            return node;
        }

        Profile profile() {
            Profile profile = new Profile(UUID.randomUUID());
            profile.setContact(new Contact("Ada Lovelace", "ada@example.com", null,
                    null, null, null, "Istanbul"));
            return profile;
        }

        ProfileTree tree() {
            return new ProfileTree(PROFILE, List.of(new SectionNode(
                    section, List.of(new EntryNode(entry, List.copyOf(bullets))), List.of())));
        }
    }
}
