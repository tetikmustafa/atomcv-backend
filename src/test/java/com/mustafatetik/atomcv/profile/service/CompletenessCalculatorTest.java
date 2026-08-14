package com.mustafatetik.atomcv.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bolum 31.9's weights, and the threshold the preflight gate leans on.
 *
 * <p>The figure is user-visible and it decides whether a generation may start
 * (Bolum 25.5), so the arithmetic is pinned rather than assumed.
 */
class CompletenessCalculatorTest {

    private static final UUID PROFILE = UUID.randomUUID();

    @Test
    void anEmptyProfileScoresNothing() {
        assertThat(CompletenessCalculator.of(profile(), tree())).isZero();
    }

    @Test
    void contactIsWorthFifteenAndNeedsBothNameAndEmail() {
        var withName = profile();
        withName.setContact(new Contact("Mustafa Tetik", null, null, null, null, null, null));
        assertThat(CompletenessCalculator.of(withName, tree())).isZero();

        var full = profile();
        full.setContact(new Contact("Mustafa Tetik", "mustafa@example.com",
                null, null, null, null, null));
        assertThat(CompletenessCalculator.of(full, tree())).isEqualTo((short) 15);
    }

    @Test
    void oneExperienceIsWorthThirty() {
        // 20 for having education or experience at all, plus 10 for the entry.
        assertThat(CompletenessCalculator.of(profile(), treeWith(SectionKind.EXPERIENCE, 1)))
                .isEqualTo((short) 30);
    }

    @Test
    void experienceStopsCountingAfterTwoEntries() {
        assertThat(CompletenessCalculator.of(profile(), treeWith(SectionKind.EXPERIENCE, 2)))
                .isEqualTo((short) 40);
        assertThat(CompletenessCalculator.of(profile(), treeWith(SectionKind.EXPERIENCE, 5)))
                .isEqualTo((short) 40);
    }

    @Test
    void educationCountsForTheThresholdButNotForTheExperienceWeight() {
        assertThat(CompletenessCalculator.of(profile(), treeWith(SectionKind.EDUCATION, 3)))
                .isEqualTo((short) 20);
    }

    @Test
    void projectsAreWorthFiveEachUpToFifteen() {
        assertThat(CompletenessCalculator.of(profile(), treeWith(SectionKind.PROJECTS, 2)))
                .isEqualTo((short) 10);
        assertThat(CompletenessCalculator.of(profile(), treeWith(SectionKind.PROJECTS, 9)))
                .isEqualTo((short) 15);
    }

    @Test
    void skillAtomsAreWorthOneEachUpToTen() {
        assertThat(CompletenessCalculator.of(profile(), treeWithSkills(4)))
                .isEqualTo((short) 4);
        assertThat(CompletenessCalculator.of(profile(), treeWithSkills(30)))
                .isEqualTo((short) 10);
    }

    @Test
    void aSelfDescriptionIsWorthTenAndBlankTextIsNotOne() {
        var blank = profile();
        blank.setSelfDescription("   ");
        assertThat(CompletenessCalculator.of(blank, tree())).isZero();

        var written = profile();
        written.setSelfDescription("Builds things that stay built");
        assertThat(CompletenessCalculator.of(written, tree())).isEqualTo((short) 10);
    }

    @Test
    void threeAtomsWithNumbersAreTheQualitySignal() {
        assertThat(CompletenessCalculator.of(profile(), treeWithMetrics(2))).isZero();
        assertThat(CompletenessCalculator.of(profile(), treeWithMetrics(3))).isEqualTo((short) 10);
    }

    /**
     * Bolum 31.9 puts "contact + one education or experience + three skills"
     * at roughly 45. Which of the two it is decides the exact number: with one
     * degree it lands at 38, with one position at 48, and the document's
     * estimate sits between them.
     */
    @Test
    void theDocumentedGenerationThresholdComesOutWhereItSays() {
        var profile = profile();
        profile.setContact(new Contact("Mustafa Tetik", "mustafa@example.com",
                null, null, null, null, null));

        var withDegree = List.of(
                sectionNode(SectionKind.EDUCATION, 1, List.of()),
                sectionNode(SectionKind.SKILLS, 0, skillAtoms(3)));
        var withPosition = List.of(
                sectionNode(SectionKind.EXPERIENCE, 1, List.of()),
                sectionNode(SectionKind.SKILLS, 0, skillAtoms(3)));

        assertThat(CompletenessCalculator.of(profile, new ProfileTree(PROFILE, withDegree)))
                .isEqualTo((short) 38);
        assertThat(CompletenessCalculator.of(profile, new ProfileTree(PROFILE, withPosition)))
                .isEqualTo((short) 48);
    }

    @Test
    void aFullProfileStopsAtOneHundred() {
        var profile = profile();
        profile.setContact(new Contact("Mustafa Tetik", "mustafa@example.com",
                null, null, null, null, null));
        profile.setSelfDescription("Builds things that stay built");

        var sections = new ArrayList<ProfileTree.SectionNode>();
        sections.add(sectionNode(SectionKind.EXPERIENCE, 4, metricAtoms(5)));
        sections.add(sectionNode(SectionKind.PROJECTS, 4, List.of()));
        sections.add(sectionNode(SectionKind.SKILLS, 0, skillAtoms(12)));

        assertThat(CompletenessCalculator.of(profile, new ProfileTree(PROFILE, sections)))
                .isEqualTo((short) 100);
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private static Profile profile() {
        return new Profile(UUID.randomUUID());
    }

    private static ProfileTree tree() {
        return new ProfileTree(PROFILE, List.of());
    }

    private static ProfileTree treeWith(SectionKind kind, int entries) {
        return new ProfileTree(PROFILE, List.of(sectionNode(kind, entries, List.of())));
    }

    private static ProfileTree treeWithSkills(int count) {
        return new ProfileTree(PROFILE,
                List.of(sectionNode(SectionKind.SKILLS, 0, skillAtoms(count))));
    }

    private static ProfileTree treeWithMetrics(int count) {
        return new ProfileTree(PROFILE,
                List.of(sectionNode(SectionKind.CUSTOM, 0, metricAtoms(count))));
    }

    private static ProfileTree.SectionNode sectionNode(
            SectionKind kind, int entries, List<ProfileTree.AtomNode> atoms) {

        var section = new Section(PROFILE, kind, kind.name(), (short) 0);
        var entryNodes = new ArrayList<ProfileTree.EntryNode>();
        for (int i = 0; i < entries; i++) {
            entryNodes.add(new ProfileTree.EntryNode(
                    new Entry(PROFILE, section.getId(), "Entry " + i, (short) i), List.of()));
        }
        return new ProfileTree.SectionNode(section, entryNodes, atoms);
    }

    private static List<ProfileTree.AtomNode> skillAtoms(int count) {
        var atoms = new ArrayList<ProfileTree.AtomNode>();
        for (int i = 0; i < count; i++) {
            atoms.add(new ProfileTree.AtomNode(
                    new Atom(PROFILE, UUID.randomUUID(), null, AtomKind.SKILL, (short) i),
                    List.of()));
        }
        return atoms;
    }

    private static List<ProfileTree.AtomNode> metricAtoms(int count) {
        var atoms = new ArrayList<ProfileTree.AtomNode>();
        for (int i = 0; i < count; i++) {
            var atom = new Atom(PROFILE, UUID.randomUUID(), null, AtomKind.BULLET, (short) i);
            atom.setMetrics(List.of("300K+"));
            atoms.add(new ProfileTree.AtomNode(atom, List.of()));
        }
        return atoms;
    }
}
