package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bolum 21.7 — what the summary is written from, decided before a call.
 *
 * <p>Two promises run through these cases. The paragraph is written from
 * <em>the page</em>, not from the profile: a skill that was dropped for budget
 * is not something the summary may lead with, because the employer is not
 * holding a CV that mentions it. And the paragraph is never invented — a
 * profile with no About keeps not having one, whatever the posting would have
 * liked to read.
 */
class AboutSynthesisTest {

    private static final UUID PROFILE = UUID.randomUUID();

    @Test
    void theaboutIsWrittenFromTheSkillsThatAreOnThePage() {
        var fixture = new Fixture();
        var about = fixture.about("Backend engineer.");
        var onThePage = fixture.bullet("Ran the Postgres fleet", List.of("postgres"), List.of());
        var droppedForBudget =
                fixture.bullet("Wrote the Kafka consumers", List.of("kafka"), List.of());

        var candidate = AboutSynthesis.plan(fixture.tree(),
                selection(chosen(about), chosen(onThePage)), context()).orElseThrow();

        assertThat(candidate.atomId()).isEqualTo(about.atom().getId());
        assertThat(candidate.skills()).containsExactly("postgres");
        assertThat(droppedForBudget).isNotNull();
    }

    /** And the numbers, for the same reason and with more at stake. */
    @Test
    void themetricsAreTheOnesThePageCarries() {
        var fixture = new Fixture();
        var about = fixture.about("Backend engineer.");
        var bullet = fixture.bullet("Cut the nightly run to 40 minutes",
                List.of("etl"), List.of("40"));

        var candidate = AboutSynthesis.plan(fixture.tree(),
                selection(chosen(about), chosen(bullet)), context()).orElseThrow();

        assertThat(candidate.metrics()).containsExactly("40");
    }

    /**
     * <strong>The promise this phase makes about the page limit.</strong>
     * Selection costed the atoms it chose; a summary where no section held one
     * is a block the budget never accounted for. Bolum 21.7 says how to write
     * the paragraph and nothing about where it goes, so it goes where the
     * person put one.
     */
    @Test
    void aprofileWithNoAboutSectionDoesNotGetOne() {
        var fixture = new Fixture();
        var bullet = fixture.bullet("Ran the Postgres fleet", List.of("postgres"), List.of());

        assertThat(AboutSynthesis.plan(fixture.tree(), selection(chosen(bullet)), context()))
                .isEmpty();
    }

    /** Switched off, or dropped for budget: either way it is not on the page. */
    @Test
    void anaboutThatSelectionDidNotChooseIsNotWritten() {
        var fixture = new Fixture();
        fixture.about("Backend engineer.");
        var bullet = fixture.bullet("Ran the Postgres fleet", List.of("postgres"), List.of());

        assertThat(AboutSynthesis.plan(fixture.tree(), selection(chosen(bullet)), context()))
                .isEmpty();
    }

    /** The person said this paragraph is to be printed as written. */
    @Test
    void averbatimAboutIsNeverRewritten() {
        var fixture = new Fixture();
        var about = fixture.about("Twenty years of keeping other people's systems running.");
        about.atom().setVerbatim(true);
        var bullet = fixture.bullet("Ran the Postgres fleet", List.of("postgres"), List.of());

        assertThat(AboutSynthesis.plan(fixture.tree(),
                selection(chosen(about), chosen(bullet)), context())).isEmpty();
    }

    /**
     * Nothing on the page and nothing the person said about themselves. There
     * is no synthesis to make here, only an invention.
     */
    @Test
    void anaboutWithNothingToSynthesiseFromIsLeftAlone() {
        var fixture = new Fixture();
        var about = fixture.about("Backend engineer.");
        var bullet = fixture.bullet("Ran the on-call rota", List.of(), List.of());

        assertThat(AboutSynthesis.plan(fixture.tree(),
                selection(chosen(about), chosen(bullet)),
                new RewriteContext(List.of("java"), List.of(), "", "en",
                        Tone.FORMAL.wireValue(), "bucket"))).isEmpty();
    }

    /** But the person's own words are enough on their own. */
    @Test
    void whatThePersonWroteAboutThemselvesIsEnoughToWriteFrom() {
        var fixture = new Fixture();
        var about = fixture.about("Backend engineer.");
        var bullet = fixture.bullet("Ran the on-call rota", List.of(), List.of());

        var candidate = AboutSynthesis.plan(fixture.tree(),
                selection(chosen(about), chosen(bullet)),
                new RewriteContext(List.of("java"), List.of(), "Likes small teams.", "en",
                        Tone.FORMAL.wireValue(), "bucket"));

        assertThat(candidate).isPresent();
        assertThat(candidate.orElseThrow().ownWords()).isEqualTo("Likes small teams.");
    }

    /**
     * <strong>Bolum 21.3 still binds.</strong> The page was costed on the
     * paragraph that is there now, so Bolum 21.7's sixty-five words is a
     * ceiling and not an allowance — whichever of the two is smaller wins.
     */
    @Test
    void theceilingIsTheSmallerOfTheMeasuredOneAndSixtyFiveWords() {
        var fixture = new Fixture();
        var about = fixture.about("Short.");
        var bullet = fixture.bullet("Ran the Postgres fleet", List.of("postgres"), List.of());

        var candidate = AboutSynthesis.plan(fixture.tree(),
                selection(chosen(about), chosen(bullet)), context()).orElseThrow();

        assertThat(candidate.maxChars()).isEqualTo(RewritePlanner.maxCharsFor("Short."));
        assertThat(candidate.maxChars()).isLessThan(AboutSynthesis.ABOUT_BUDGET_CHARS);
    }

    @Test
    void alongAboutIsCappedAtSixtyFiveWords() {
        var fixture = new Fixture();
        var about = fixture.about("A paragraph of ".repeat(80));
        var bullet = fixture.bullet("Ran the Postgres fleet", List.of("postgres"), List.of());

        var candidate = AboutSynthesis.plan(fixture.tree(),
                selection(chosen(about), chosen(bullet)), context()).orElseThrow();

        assertThat(candidate.maxChars()).isEqualTo(AboutSynthesis.ABOUT_BUDGET_CHARS);
    }

    // -- fixtures ----------------------------------------------------------

    private static RewriteContext context() {
        return new RewriteContext(List.of("java"), List.of("run the platform"),
                "Likes small teams.", "en", Tone.FORMAL.wireValue(), "bucket");
    }

    private static SelectionState selection(SelectedAtom... atoms) {
        return new SelectionState(List.of(atoms), List.of(),
                new SelectionState.BudgetBreakdown(600, 100, 500, 300));
    }

    private static SelectedAtom chosen(AtomNode node) {
        return new SelectedAtom(node.atom().getId(), node.variants().get(0).getId(),
                0.5, 12.0, false);
    }

    /** One profile: an About section and an Experience section of loose atoms. */
    private static final class Fixture {

        private final Section aboutSection =
                new Section(PROFILE, SectionKind.ABOUT, "About", (short) 0);
        private final Section experience =
                new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 1);
        private final List<AtomNode> aboutAtoms = new ArrayList<>();
        private final List<AtomNode> bullets = new ArrayList<>();

        AtomNode about(String text) {
            AtomNode node = node(aboutSection, AtomKind.ABOUT_PARAGRAPH, text,
                    List.of(), List.of());
            aboutAtoms.add(node);
            return node;
        }

        AtomNode bullet(String text, List<String> skills, List<String> metrics) {
            AtomNode node = node(experience, AtomKind.BULLET, text, skills, metrics);
            bullets.add(node);
            return node;
        }

        ProfileTree tree() {
            return new ProfileTree(PROFILE, List.of(
                    new SectionNode(aboutSection, List.of(), List.copyOf(aboutAtoms)),
                    new SectionNode(experience, List.of(), List.copyOf(bullets))));
        }

        private static AtomNode node(Section section, AtomKind kind, String text,
                List<String> skills, List<String> metrics) {

            Atom atom = new Atom(PROFILE, section.getId(), null, kind, (short) 0);
            atom.setSkills(skills);
            atom.setMetrics(metrics);
            var wording = new AtomVariant(PROFILE, atom.getId(), "en", RichContent.plain(text));
            wording.setPrimary(true);
            return new AtomNode(atom, List.of(wording));
        }
    }
}
