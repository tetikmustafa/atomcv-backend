package com.mustafatetik.atomcv.generation.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionState.BudgetBreakdown;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Faz E — the bridge from a decision to a document (Bolum 22).
 *
 * <p>The interesting property is what does <em>not</em> cross it: an atom that
 * selection rejected, a heading with nothing under it, and any order other
 * than the profile's own.
 */
class RenderPhaseTest {

    private static final UUID PROFILE = UUID.randomUUID();

    @Test
    void onlySelectedAtomsAreRendered() {
        var fixture = twoBulletsInOneJob();

        var request = build(fixture, selection(fixture.first));

        var entry = request.sections().get(0).entries().get(0);
        assertThat(plain(entry.atoms())).containsExactly("Built ETL pipelines");
    }

    @Test
    void bulletsKeepProfileOrderNotSelectionOrder() {
        var fixture = twoBulletsInOneJob();

        // Selection ranks by score, so the second bullet comes back first.
        var request = build(fixture, selection(fixture.second, fixture.first));

        assertThat(plain(request.sections().get(0).entries().get(0).atoms()))
                .containsExactly("Built ETL pipelines", "Cut build times in half");
    }

    /**
     * <strong>Faz D reaches the page here and nowhere else.</strong> The
     * profile is untouched — what the person wrote is theirs, and a rewrite is
     * true of one generation and one posting.
     */
    @Test
    void arewrittenBulletIsPrintedInPlaceOfTheOriginal() {
        var fixture = twoBulletsInOneJob();

        var request = build(fixture, selection(fixture.first, fixture.second),
                new RewrittenContent(Map.of(fixture.first.getId(),
                        RichContent.plain("Rebuilt the ingest path on Postgres"))));

        assertThat(plain(request.sections().get(0).entries().get(0).atoms()))
                .containsExactly("Rebuilt the ingest path on Postgres", "Cut build times in half");
    }

    /**
     * And it cannot put a line on a page selection did not pay for: the
     * selection gate comes first, so a rewrite of an atom that was dropped for
     * budget is dropped with it.
     */
    @Test
    void arewriteOfAnAtomThatWasNotSelectedIsNotPrinted() {
        var fixture = twoBulletsInOneJob();

        var request = build(fixture, selection(fixture.first),
                new RewrittenContent(Map.of(fixture.second.getId(),
                        RichContent.plain("A sentence the budget had no room for"))));

        assertThat(plain(request.sections().get(0).entries().get(0).atoms()))
                .containsExactly("Built ETL pipelines");
    }

    @Test
    void anEntryWithNothingSelectedIsNotPrinted() {
        var fixture = twoBulletsInOneJob();

        var request = build(fixture, new SelectionState(
                List.of(), List.of(), new BudgetBreakdown(700, 50, 650, 0)));

        assertThat(request.sections()).isEmpty();
    }

    @Test
    void aSectionKeepsItsLooseAtoms() {
        var experience = new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
        var skills = new Section(PROFILE, SectionKind.SKILLS, "Skills", (short) 1);
        var skill = new Atom(PROFILE, skills.getId(), null, AtomKind.SKILL, (short) 0);
        var wording = variant(skill, "Go");
        var tree = ProfileAssembler.assemble(PROFILE,
                List.of(experience, skills), List.of(), List.of(skill), List.of(wording));

        var request = RenderPhase.build(profile(), tree,
                state(new SelectedAtom(skill.getId(), wording.getId(), 0.9, 20, false)),
                RewrittenContent.none(), TemplateCustomization.CLASSIC, Locale.ENGLISH);

        // The empty Experience heading is gone; Skills carries its atom.
        assertThat(request.sections()).hasSize(1);
        assertThat(request.sections().get(0).title()).isEqualTo("Skills");
        assertThat(plain(request.sections().get(0).atoms())).containsExactly("Go");
    }

    @Test
    void theWordingSelectionChoseIsTheOneRendered() {
        var fixture = twoBulletsInOneJob();
        var turkish = new AtomVariant(PROFILE, fixture.first.getId(), "tr",
                RichContent.plain("ETL hatlari kurdum"));
        fixture.variants.add(turkish);

        var request = build(fixture,
                state(new SelectedAtom(fixture.first.getId(), turkish.getId(), 0.9, 20, false)));

        assertThat(plain(request.sections().get(0).entries().get(0).atoms()))
                .containsExactly("ETL hatlari kurdum");
    }

    @Test
    void theHeaderCarriesTheContactLinesThatExist() {
        var fixture = twoBulletsInOneJob();
        var profile = profile();
        profile.setContact(new Contact("Ada Lovelace", "ada@example.com", null,
                null, "github.com/ada", null, "Istanbul"));
        profile.setHeadline("Backend engineer");

        var request = RenderPhase.build(profile, tree(fixture), selection(fixture.first),
                RewrittenContent.none(), TemplateCustomization.CLASSIC, Locale.ENGLISH);

        assertThat(request.header().name()).isEqualTo("Ada Lovelace");
        assertThat(request.header().headline()).isEqualTo("Backend engineer");
        assertThat(request.header().contactLines())
                .containsExactly("ada@example.com", "Istanbul", "github.com/ada");
    }

    @Test
    void anOngoingEntrySaysSoInTheContentLanguage() {
        var entry = new Entry(PROFILE, UUID.randomUUID(), "Engineer", (short) 0);
        entry.setStartDate(LocalDate.of(2021, 3, 1));

        assertThat(RenderPhase.dateRange(entry, Locale.ENGLISH)).endsWith("– Present");
        assertThat(RenderPhase.dateRange(entry, Locale.forLanguageTag("tr"))).endsWith("– Halen");
    }

    @Test
    void anEntryWithoutDatesShowsNoRange() {
        var entry = new Entry(PROFILE, UUID.randomUUID(), "Engineer", (short) 0);

        assertThat(RenderPhase.dateRange(entry, Locale.ENGLISH)).isEmpty();
    }

    @Test
    void theRenderRequestCarriesNoIdentifiers() {
        // Bolum 22.2: the renderer cannot re-decide what selection decided,
        // because it is never told which atom a line came from.
        var fixture = twoBulletsInOneJob();

        var request = build(fixture, selection(fixture.first));

        assertThat(request.toString()).doesNotContain(fixture.first.getId().toString());
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private record Fixture(
            Section section, Entry entry, Atom first, Atom second,
            List<AtomVariant> variants) {
    }

    private static Fixture twoBulletsInOneJob() {
        var section = new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
        var entry = new Entry(PROFILE, section.getId(), "Senior Engineer", (short) 0);
        entry.setOrganization("Acme");
        entry.setLocation("Istanbul");
        entry.setStartDate(LocalDate.of(2021, 3, 1));
        entry.setEndDate(LocalDate.of(2024, 6, 1));

        var first = new Atom(PROFILE, section.getId(), entry.getId(), AtomKind.BULLET, (short) 0);
        var second = new Atom(PROFILE, section.getId(), entry.getId(), AtomKind.BULLET, (short) 1);
        var variants = new ArrayList<AtomVariant>();
        variants.add(variant(first, "Built ETL pipelines"));
        variants.add(variant(second, "Cut build times in half"));
        return new Fixture(section, entry, first, second, variants);
    }

    private static ProfileTree tree(Fixture fixture) {
        return ProfileAssembler.assemble(PROFILE,
                List.of(fixture.section), List.of(fixture.entry),
                List.of(fixture.first, fixture.second), fixture.variants);
    }

    private static RenderRequest build(Fixture fixture, SelectionState selection) {
        return build(fixture, selection, RewrittenContent.none());
    }

    private static RenderRequest build(
            Fixture fixture, SelectionState selection, RewrittenContent rewritten) {

        return RenderPhase.build(profile(), tree(fixture), selection, rewritten,
                TemplateCustomization.CLASSIC, Locale.ENGLISH);
    }

    /** A selection of the given atoms, each with its primary wording. */
    private static SelectionState selection(Atom... atoms) {
        var selected = new ArrayList<SelectedAtom>();
        double score = 0.9;
        for (Atom atom : atoms) {
            selected.add(new SelectedAtom(atom.getId(), null, score, 20, false));
            score -= 0.1;
        }
        return new SelectionState(selected, List.of(),
                new BudgetBreakdown(700, 50, 650, selected.size() * 20.0));
    }

    private static SelectionState state(SelectedAtom... atoms) {
        return new SelectionState(List.of(atoms), List.of(),
                new BudgetBreakdown(700, 50, 650, atoms.length * 20.0));
    }

    private static AtomVariant variant(Atom atom, String text) {
        var variant = new AtomVariant(PROFILE, atom.getId(), "en", RichContent.plain(text));
        variant.setPrimary(true);
        return variant;
    }

    private static Profile profile() {
        return new Profile(UUID.randomUUID());
    }

    private static List<String> plain(List<RichContent> content) {
        return content.stream().map(RichContent::plainText).toList();
    }
}
