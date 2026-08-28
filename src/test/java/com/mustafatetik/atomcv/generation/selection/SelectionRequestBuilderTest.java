package com.mustafatetik.atomcv.generation.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.selection.SelectionRequest.AtomCandidate;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The piece that reads a profile the way its owner marked it (Bolum 20.2).
 *
 * <p>Most of these are about a control the user set: an inactive row, a lock,
 * a minimum. Getting one of them wrong does not break a build — it quietly
 * produces a CV the user did not ask for, which is why each has its own test.
 */
class SelectionRequestBuilderTest {

    private static final UUID PROFILE = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 15);
    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    // ── what the user switched off ────────────────────────────────────────

    @Test
    void anInactiveSectionIsNotPartOfTheCvAtAll() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.EXPERIENCE, 0);
        section.setActive(false);
        profile.looseAtom(section, "Go");

        assertThat(build(profile).request().sections()).isEmpty();
    }

    @Test
    void anInactiveEntryTakesItsBulletsWithIt() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.EXPERIENCE, 0);
        var entry = profile.entry(section, 0);
        entry.setActive(false);
        profile.bullet(section, entry, "Built ETL pipelines");

        assertThat(build(profile).request().sections()).isEmpty();
    }

    @Test
    void anInactiveAtomStaysACandidateSoItCanBeExplained() {
        // Bolum 19.5: it is not scored away, it is rejected with a reason.
        var profile = new Fixture();
        var section = profile.section(SectionKind.SKILLS, 0);
        var atom = profile.looseAtom(section, "Go");
        atom.setActive(false);

        var candidates = build(profile).request().sections().get(0).atoms();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).active()).isFalse();
    }

    // ── the user's locks ──────────────────────────────────────────────────

    @Test
    void anAtomLockTravelsThrough() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.SKILLS, 0);
        var atom = profile.looseAtom(section, "Go");
        atom.setAlwaysInclude(true);

        assertThat(build(profile).request().sections().get(0).atoms().get(0).alwaysInclude())
                .isTrue();
    }

    /** A locked entry means the heading plus the minimum it is worth printing at. */
    @Test
    void aLockedEntryPinsAsManyBulletsAsItsMinimum() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.EXPERIENCE, 0);
        var entry = profile.entry(section, 0);
        entry.setAlwaysInclude(true);
        entry.setMinAtoms((short) 2);
        for (int index = 0; index < 5; index++) {
            profile.bullet(section, entry, "Bullet " + index).setImportance(0.1f * index);
        }

        var plan = build(profile).request().sections().get(0).entries().get(0);

        assertThat(plan.atoms()).filteredOn(AtomCandidate::alwaysInclude).hasSize(2);
        // And it pins the best ones, not the first ones.
        double pinnedLowest = plan.atoms().stream()
                .filter(AtomCandidate::alwaysInclude)
                .mapToDouble(AtomCandidate::score).min().orElseThrow();
        double looseHighest = plan.atoms().stream()
                .filter(candidate -> !candidate.alwaysInclude())
                .mapToDouble(AtomCandidate::score).max().orElseThrow();
        assertThat(pinnedLowest).isGreaterThanOrEqualTo(looseHighest);
    }

    @Test
    void aLockedSectionKeepsAtLeastOneOfItsAtoms() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.SKILLS, 0);
        section.setAlwaysInclude(true);
        profile.looseAtom(section, "Go");
        profile.looseAtom(section, "Kubernetes");

        var plan = build(profile).request().sections().get(0);

        assertThat(plan.alwaysInclude()).isTrue();
        assertThat(plan.atoms()).filteredOn(AtomCandidate::alwaysInclude).hasSize(1);
    }

    @Test
    void aLockedSectionOfEntriesPinsInsideItsFirstEntry() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.EXPERIENCE, 0);
        section.setAlwaysInclude(true);
        var entry = profile.entry(section, 0);
        profile.bullet(section, entry, "Built ETL pipelines");

        var plan = build(profile).request().sections().get(0);

        assertThat(plan.entries().get(0).atoms())
                .filteredOn(AtomCandidate::alwaysInclude).hasSize(1);
    }

    // ── costs ─────────────────────────────────────────────────────────────

    @Test
    void aMeasuredWordingIsChargedWhatItMeasured() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.SKILLS, 0);
        profile.looseAtom(section, "Go");
        profile.variants.get(0).recordRenderCost(
                TemplateCustomization.CLASSIC.costKey(), 41.5, java.time.Instant.now());

        var built = build(profile);

        assertThat(built.request().sections().get(0).atoms().get(0).renderCostPt())
                .isEqualTo(41.5);
        assertThat(built.estimatedAtoms()).isZero();
    }

    @Test
    void anUnmeasuredWordingIsEstimatedAndCounted() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.SKILLS, 0);
        profile.looseAtom(section, "Go");

        var built = build(profile);

        assertThat(built.estimatedAtoms()).isEqualTo(1);
        assertThat(built.request().sections().get(0).atoms().get(0).renderCostPt())
                .isPositive();
    }

    @Test
    void anAtomWithNoWordingIsCountedRatherThanCharged() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.SKILLS, 0);
        profile.atoms.add(new Atom(PROFILE, section.getId(), null, AtomKind.SKILL, (short) 0));

        var built = build(profile);

        assertThat(built.withoutWording()).isEqualTo(1);
        assertThat(built.request().sections()).isEmpty();
    }

    @Test
    void theWordingInTheAskedForLanguageWins() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.SKILLS, 0);
        var atom = profile.looseAtom(section, "Built ETL pipelines");
        var turkish = new AtomVariant(PROFILE, atom.getId(), "tr",
                RichContent.plain("ETL hatlari kurdum"));
        profile.variants.add(turkish);

        var built = SelectionRequestBuilder.build(profile.tree(),
                TemplateCustomization.CLASSIC, CAPACITY, 1, "tr", Tone.FORMAL, TODAY);

        assertThat(built.request().sections().get(0).atoms().get(0).variantId())
                .isEqualTo(turkish.getId());
    }

    /**
     * <strong>Bolum 21.1's choice, and it is made here.</strong>
     *
     * <p>The spec puts the tone in Faz D, after selection has run. It cannot
     * live there: this is where a wording is charged to the budget, and a Faz D
     * that swapped in the other one afterwards would print a line whose height
     * nothing had measured — the page limit is only a guarantee because every
     * line on the page was costed.
     */
    @Test
    void theWordingInTheProfilesToneIsBothChosenAndCharged() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.SKILLS, 0);
        var atom = profile.looseAtom(section, "Built ETL pipelines");
        String costKey = TemplateCustomization.CLASSIC.costKey();
        profile.variants.get(0).recordRenderCost(costKey, 40.0, java.time.Instant.now());
        var technical = new AtomVariant(PROFILE, atom.getId(), "en",
                RichContent.plain("Stood the ETL DAGs up on their own scheduler"));
        technical.setTone(Tone.TECHNICAL);
        technical.recordRenderCost(costKey, 47.0, java.time.Instant.now());
        profile.variants.add(technical);

        var built = SelectionRequestBuilder.build(profile.tree(),
                TemplateCustomization.CLASSIC, CAPACITY, 1, "en", Tone.TECHNICAL, TODAY);

        var candidate = built.request().sections().get(0).atoms().get(0);
        assertThat(candidate.variantId()).isEqualTo(technical.getId());
        assertThat(candidate.renderCostPt()).isEqualTo(47.0);
    }

    /**
     * <strong>Bolum 32.3, and the half the test above does not cover.</strong>
     *
     * <p>Picking the right wording and then charging the other one's height is
     * the failure the section is about: the page is optimised against English
     * costs and overflows when Turkish is what gets rendered. The wording that
     * is chosen has to be the wording that is charged.
     */
    @Test
    void theWordingInTheAskedForLanguageIsChargedItsOwnHeight() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.SKILLS, 0);
        var atom = profile.looseAtom(section, "Built ETL pipelines");
        String costKey = TemplateCustomization.CLASSIC.costKey();
        profile.variants.get(0).recordRenderCost(costKey, 40.0, java.time.Instant.now());

        var turkish = new AtomVariant(PROFILE, atom.getId(), "tr",
                RichContent.plain("ETL hatlari kurdum"));
        // Bolum 32.3: the same claim, ten to twenty per cent taller.
        turkish.recordRenderCost(costKey, 47.0, java.time.Instant.now());
        profile.variants.add(turkish);

        var built = SelectionRequestBuilder.build(profile.tree(),
                TemplateCustomization.CLASSIC, CAPACITY, 1, "tr", Tone.FORMAL, TODAY);

        assertThat(built.request().sections().get(0).atoms().get(0).renderCostPt())
                .isEqualTo(47.0);
    }

    /**
     * <strong>The consequence Bolum 32.3 calls the right behaviour.</strong>
     *
     * <p>Turkish runs longer for the same claim, so the same budget holds
     * fewer of its bullets. That the two languages select different sets is
     * not a defect to be smoothed over — it is what a page limit measured in
     * points rather than in items means, and the alternative is a document
     * that overflows.
     */
    @Test
    void thesameBudgetHoldsFewerTurkishBulletsThanEnglishOnes() {
        assertThat(chargedTotalFor("tr")).isGreaterThan(chargedTotalFor("en"));
    }

    private double chargedTotalFor(String language) {
        var profile = new Fixture();
        var section = profile.section(SectionKind.SKILLS, 0);
        String costKey = TemplateCustomization.CLASSIC.costKey();

        for (int i = 0; i < 4; i++) {
            var atom = profile.looseAtom(section, "Built ETL pipelines number " + i);
            // The English wording the fixture just made for this atom.
            profile.variants.stream()
                    .filter(variant -> variant.getAtomId().equals(atom.getId()))
                    .forEach(variant -> variant.recordRenderCost(
                            costKey, 40.0, java.time.Instant.now()));

            var turkish = new AtomVariant(PROFILE, atom.getId(), "tr",
                    RichContent.plain("ETL hatlari kurdum numara " + i));
            turkish.recordRenderCost(costKey, 47.0, java.time.Instant.now());
            profile.variants.add(turkish);
        }

        var built = SelectionRequestBuilder.build(profile.tree(),
                TemplateCustomization.CLASSIC, CAPACITY, 1, language, Tone.FORMAL, TODAY);

        return built.request().sections().stream()
                .flatMap(sec -> sec.atoms().stream())
                .mapToDouble(candidate -> candidate.renderCostPt())
                .sum();
    }

    // ── shape ─────────────────────────────────────────────────────────────

    @Test
    void anEmptySectionDoesNotReachSelection() {
        var profile = new Fixture();
        profile.section(SectionKind.EXPERIENCE, 0);

        assertThat(build(profile).request().sections()).isEmpty();
    }

    @Test
    void everyBulletCarriesItsEntry() {
        // The invariant SelectionRequest enforces: an atom under an entry has
        // to name it, or the entry heading is never charged (EK D.8.5).
        var profile = new Fixture();
        var section = profile.section(SectionKind.EXPERIENCE, 0);
        var entry = profile.entry(section, 0);
        profile.bullet(section, entry, "Built ETL pipelines");

        var plan = build(profile).request().sections().get(0).entries().get(0);

        assertThat(plan.atoms()).allSatisfy(candidate ->
                assertThat(candidate.entryId()).isEqualTo(entry.getId()));
    }

    // ── an entry with nothing under it ────────────────────────────────────

    /**
     * Bolum 20.2: a diploma line has no bullets, and asking the person to
     * invent one is asking them to pad.
     */
    @Test
    void anEntryWithNoAtomsIsOfferedAsItsOwnHeading() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.EDUCATION, 0);
        var degree = profile.entry(section, 0);

        var plan = build(profile).request().sections().get(0).entries().get(0);

        assertThat(plan.entryId()).isEqualTo(degree.getId());
        assertThat(plan.atoms()).singleElement().satisfies(candidate -> {
            assertThat(candidate.headerOnly()).isTrue();
            assertThat(candidate.entryId()).isEqualTo(degree.getId());
            // Nothing of its own: the heading is furniture, and SelectionPhase
            // is what charges for it.
            assertThat(candidate.renderCostPt()).isZero();
            assertThat(candidate.active()).isTrue();
        });
    }

    @Test
    void anEntryThatHasBulletsIsNotOfferedAHeadingAsWell() {
        // The two together would open the entry without paying for the list
        // the bullets are printed in, and the page would overflow by that much.
        var profile = new Fixture();
        var section = profile.section(SectionKind.EXPERIENCE, 0);
        var entry = profile.entry(section, 0);
        profile.bullet(section, entry, "Built ETL pipelines");

        var plan = build(profile).request().sections().get(0).entries().get(0);

        assertThat(plan.atoms()).noneMatch(AtomCandidate::headerOnly);
    }

    /**
     * The score of a heading comes from the entry, so a stale qualification
     * ranks below a current one — and the source, not the builder, decides.
     */
    @Test
    void aHeadingIsScoredFromItsEntryRatherThanFromAnyAtom() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.EDUCATION, 0);
        var recent = profile.entry(section, 0);
        recent.setEndDate(TODAY);
        var old = profile.entry(section, 1);
        old.setEndDate(TODAY.minusYears(20));

        var entries = build(profile).request().sections().get(0).entries();

        assertThat(headingOf(entries, recent).score())
                .isGreaterThan(headingOf(entries, old).score());
    }

    @Test
    void twoReadsOfOneProfileBreakAHeadingTieTheSameWay() {
        // Entry ids are minted per import, so a tie-break that used one would
        // reorder two identical degree lines between two reads of one CV
        // (Bolum 20.3).
        var first = withOneDegree();
        var second = withOneDegree();

        assertThat(headingOf(first, 0).atomId()).isNotEqualTo(headingOf(second, 0).atomId());
        assertThat(headingOf(first, 0).tieBreak()).isEqualTo(headingOf(second, 0).tieBreak());
    }

    @Test
    void twoDifferentDegreeLinesDoNotShareATieBreak() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.EDUCATION, 0);
        profile.entry(section, 0).setOrganization("University of Manchester");
        profile.entry(section, 1).setOrganization("Trafford College");

        var entries = build(profile).request().sections().get(0).entries();

        assertThat(headingOf(entries, 0).tieBreak())
                .isNotEqualTo(headingOf(entries, 1).tieBreak());
    }

    private static List<SelectionRequest.EntryPlan> withOneDegree() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.EDUCATION, 0);
        var degree = profile.entry(section, 0);
        degree.setOrganization("University of Manchester");
        degree.setLocation("Manchester");
        return build(profile).request().sections().get(0).entries();
    }

    private static AtomCandidate headingOf(List<SelectionRequest.EntryPlan> entries, int index) {
        return entries.get(index).atoms().get(0);
    }

    private static AtomCandidate headingOf(
            List<SelectionRequest.EntryPlan> entries, Entry entry) {

        return entries.stream()
                .filter(plan -> plan.entryId().equals(entry.getId()))
                .findFirst().orElseThrow()
                .atoms().get(0);
    }

    @Test
    void theSameProfileBuildsTheSameRequest() {
        var profile = new Fixture();
        var section = profile.section(SectionKind.EXPERIENCE, 0);
        var entry = profile.entry(section, 0);
        for (int index = 0; index < 10; index++) {
            profile.bullet(section, entry, "Bullet " + index);
        }

        var first = build(profile).request();
        for (int run = 0; run < 20; run++) {
            assertThat(build(profile).request()).isEqualTo(first);
        }
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    // -- Bolum 19.4: the score function is the only thing that differs -----

    /**
     * What separating Faz B from Faz C buys. The builder reads numbers from
     * whatever it was handed and does not know which mode produced them — the
     * general-mode scorer here would have ranked these two the other way
     * round, because the second bullet is the more recent.
     */
    @Test
    void thescoresComeFromWhicheverSourceTheCallerGave() {
        var fixture = new Fixture();
        var section = fixture.section(SectionKind.EXPERIENCE, 0);
        var entry = fixture.entry(section, 0);
        var first = fixture.bullet(section, entry, "Built ETL pipelines");
        var second = fixture.bullet(section, entry, "Ran the on-call rota");

        var built = SelectionRequestBuilder.build(fixture.tree(), TemplateCustomization.CLASSIC,
                CAPACITY, 1, "en", Tone.FORMAL,
                (atom, parent) -> atom.getId().equals(first.getId()) ? 0.9 : 0.1);

        var candidates = built.request().sections().get(0).entries().get(0).atoms();
        assertThat(candidateFor(candidates, first).score()).isEqualTo(0.9);
        assertThat(candidateFor(candidates, second).score()).isEqualTo(0.1);
    }

    /**
     * A lock is pinned by score, so the source decides which atom survives a
     * budget that only has room for one of them.
     */
    @Test
    void thepinnedAtomOfAnAlwaysIncludeEntryIsTheOneTheSourceRanksHighest() {
        var fixture = new Fixture();
        var section = fixture.section(SectionKind.EXPERIENCE, 0);
        var entry = fixture.entry(section, 0);
        entry.setAlwaysInclude(true);
        // One bullet is enough to be worth printing, so only one is pinned
        // and the source is what decides which (EK D.8.7).
        entry.setMinAtoms((short) 1);
        var ignored = fixture.bullet(section, entry, "Built ETL pipelines");
        var wanted = fixture.bullet(section, entry, "Ran the on-call rota");

        var built = SelectionRequestBuilder.build(fixture.tree(), TemplateCustomization.CLASSIC,
                CAPACITY, 1, "en", Tone.FORMAL,
                (atom, parent) -> atom.getId().equals(wanted.getId()) ? 0.9 : 0.1);

        var candidates = built.request().sections().get(0).entries().get(0).atoms();
        assertThat(candidateFor(candidates, wanted).alwaysInclude()).isTrue();
        assertThat(candidateFor(candidates, ignored).alwaysInclude()).isFalse();
    }

    private static AtomCandidate candidateFor(List<AtomCandidate> candidates, Atom atom) {
        return candidates.stream()
                .filter(candidate -> candidate.atomId().equals(atom.getId()))
                .findFirst().orElseThrow();
    }

    private static SelectionRequestBuilder.BuiltRequest build(Fixture fixture) {
        return SelectionRequestBuilder.build(fixture.tree(),
                TemplateCustomization.CLASSIC, CAPACITY, 1, "en", Tone.FORMAL, TODAY);
    }

    /** A profile under construction, flat, the way the repositories return it. */
    private static final class Fixture {

        private final List<Section> sections = new ArrayList<>();
        private final List<Entry> entries = new ArrayList<>();
        private final List<Atom> atoms = new ArrayList<>();
        private final List<AtomVariant> variants = new ArrayList<>();

        Section section(SectionKind kind, int order) {
            var section = new Section(PROFILE, kind, kind.name(), (short) order);
            sections.add(section);
            return section;
        }

        Entry entry(Section section, int order) {
            var entry = new Entry(PROFILE, section.getId(), "Engineer", (short) order);
            entry.setStartDate(LocalDate.of(2020, 1, 1));
            entries.add(entry);
            return entry;
        }

        Atom bullet(Section section, Entry entry, String text) {
            return atom(section, entry, AtomKind.BULLET, text);
        }

        Atom looseAtom(Section section, String text) {
            return atom(section, null, AtomKind.SKILL, text);
        }

        private Atom atom(Section section, Entry entry, AtomKind kind, String text) {
            var atom = new Atom(PROFILE, section.getId(),
                    entry == null ? null : entry.getId(), kind, (short) atoms.size());
            var variant = new AtomVariant(PROFILE, atom.getId(), "en", RichContent.plain(text));
            variant.setPrimary(true);
            atoms.add(atom);
            variants.add(variant);
            return atom;
        }

        ProfileTree tree() {
            return ProfileAssembler.assemble(PROFILE, sections, entries, atoms, variants);
        }
    }
}
