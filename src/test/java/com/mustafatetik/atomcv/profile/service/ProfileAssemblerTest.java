package com.mustafatetik.atomcv.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.shared.security.CrossTenantAccessException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The joining half of the profile load, tested without a database. */
class ProfileAssemblerTest {

    private static final UUID PROFILE = UUID.randomUUID();
    private static final UUID OTHER_PROFILE = UUID.randomUUID();

    private static Section section(UUID profileId, SectionKind kind, int order) {
        return new Section(profileId, kind, kind.name(), (short) order);
    }

    private static Entry entry(UUID profileId, Section section, int order) {
        return new Entry(profileId, section.getId(), "Entry " + order, (short) order);
    }

    private static Atom atom(UUID profileId, Section section, Entry entry, int order) {
        return new Atom(profileId, section.getId(), entry == null ? null : entry.getId(),
                entry == null ? AtomKind.SKILL : AtomKind.BULLET, (short) order);
    }

    private static AtomVariant variant(Atom atom, String language, String text, boolean primary) {
        var variant = new AtomVariant(atom.getProfileId(), atom.getId(), language,
                RichContent.plain(text));
        variant.setPrimary(primary);
        return variant;
    }

    @Test
    void nestsSectionsEntriesAtomsAndVariants() {
        var experience = section(PROFILE, SectionKind.EXPERIENCE, 0);
        var skills = section(PROFILE, SectionKind.SKILLS, 1);
        var job = entry(PROFILE, experience, 0);
        var bullet = atom(PROFILE, experience, job, 0);
        var skill = atom(PROFILE, skills, null, 0);
        var wording = variant(bullet, "en", "Built ETL pipelines", true);

        var tree = ProfileAssembler.assemble(PROFILE,
                List.of(experience, skills), List.of(job), List.of(bullet, skill), List.of(wording));

        assertThat(tree.profileId()).isEqualTo(PROFILE);
        assertThat(tree.sections()).hasSize(2);
        assertThat(tree.atomCount()).isEqualTo(2);

        var experienceNode = tree.sections().get(0);
        assertThat(experienceNode.section()).isEqualTo(experience);
        assertThat(experienceNode.atoms()).isEmpty();
        assertThat(experienceNode.entries()).hasSize(1);
        assertThat(experienceNode.entries().get(0).atoms()).hasSize(1);
        assertThat(experienceNode.entries().get(0).atoms().get(0).primaryVariant()).contains(wording);

        // A section-level atom hangs off the section, not off an invented entry.
        var skillsNode = tree.sections().get(1);
        assertThat(skillsNode.entries()).isEmpty();
        assertThat(skillsNode.atoms()).hasSize(1);
        assertThat(skillsNode.atoms().get(0).atom()).isEqualTo(skill);
    }

    @Test
    void keepsTheOrderItWasGiven() {
        var first = section(PROFILE, SectionKind.EXPERIENCE, 0);
        var second = section(PROFILE, SectionKind.EDUCATION, 1);
        var third = section(PROFILE, SectionKind.PROJECTS, 2);

        var tree = ProfileAssembler.assemble(PROFILE,
                List.of(first, second, third), List.of(), List.of(), List.of());

        assertThat(tree.sections()).extracting(node -> node.section().getKind())
                .containsExactly(SectionKind.EXPERIENCE, SectionKind.EDUCATION, SectionKind.PROJECTS);
    }

    @Test
    void variantsComeBackPrimaryFirstThenByLanguage() {
        var skills = section(PROFILE, SectionKind.SKILLS, 0);
        var atom = atom(PROFILE, skills, null, 0);
        var turkish = variant(atom, "tr", "Veri hatlari", false);
        var german = variant(atom, "de", "Datenpipelines", false);
        var english = variant(atom, "en", "Data pipelines", true);

        var tree = ProfileAssembler.assemble(PROFILE,
                List.of(skills), List.of(), List.of(atom), List.of(turkish, german, english));

        assertThat(tree.sections().get(0).atoms().get(0).variants())
                .containsExactly(english, german, turkish);
    }

    @Test
    void anAtomKeepsEveryWordingOfIt() {
        var skills = section(PROFILE, SectionKind.SKILLS, 0);
        var atom = atom(PROFILE, skills, null, 0);
        var english = variant(atom, "en", "Data pipelines", true);
        var turkish = variant(atom, "tr", "Veri hatlari", false);

        var tree = ProfileAssembler.assemble(PROFILE,
                List.of(skills), List.of(), List.of(atom), List.of(english, turkish));

        var node = tree.sections().get(0).atoms().get(0);
        assertThat(node.variants()).hasSize(2);
        assertThat(node.variantIn("tr")).contains(turkish);
        assertThat(node.variantIn("fr")).isEmpty();
    }

    @Test
    void anEmptyProfileAssemblesToAnEmptyTree() {
        var tree = ProfileAssembler.assemble(PROFILE, List.of(), List.of(), List.of(), List.of());

        assertThat(tree.sections()).isEmpty();
        assertThat(tree.atomCount()).isZero();
    }

    @Test
    void theSameInputAlwaysProducesTheSameTree() {
        var experience = section(PROFILE, SectionKind.EXPERIENCE, 0);
        var job = entry(PROFILE, experience, 0);
        var bullet = atom(PROFILE, experience, job, 0);
        var wording = variant(bullet, "en", "Built ETL pipelines", true);

        var first = ProfileAssembler.assemble(PROFILE,
                List.of(experience), List.of(job), List.of(bullet), List.of(wording));
        var second = ProfileAssembler.assemble(PROFILE,
                List.of(experience), List.of(job), List.of(bullet), List.of(wording));

        assertThat(first).isEqualTo(second);
    }

    // ─── four queries mean four chances to pass the wrong scope ───

    @Test
    void aRowFromAnotherProfileIsRefused() {
        var mine = section(PROFILE, SectionKind.EXPERIENCE, 0);
        var theirs = section(OTHER_PROFILE, SectionKind.EXPERIENCE, 0);

        assertThatThrownBy(() -> ProfileAssembler.assemble(PROFILE,
                List.of(mine, theirs), List.of(), List.of(), List.of()))
                .isInstanceOf(CrossTenantAccessException.class);
    }

    @Test
    void aVariantFromAnotherProfileIsRefused() {
        var skills = section(PROFILE, SectionKind.SKILLS, 0);
        var atom = atom(PROFILE, skills, null, 0);
        var foreign = new AtomVariant(OTHER_PROFILE, atom.getId(), "en", RichContent.plain("x"));

        assertThatThrownBy(() -> ProfileAssembler.assemble(PROFILE,
                List.of(skills), List.of(), List.of(atom), List.of(foreign)))
                .isInstanceOf(CrossTenantAccessException.class);
    }

    // ─── a dangling reference is a defect, not something to silently drop ───

    @Test
    void anAtomPointingAtAnAbsentEntryIsRefused() {
        var experience = section(PROFILE, SectionKind.EXPERIENCE, 0);
        var absent = entry(PROFILE, experience, 0);
        var orphan = atom(PROFILE, experience, absent, 0);

        assertThatThrownBy(() -> ProfileAssembler.assemble(PROFILE,
                List.of(experience), List.of(), List.of(orphan), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anAtomDisagreeingWithItsEntryAboutTheSectionIsRefused() {
        var experience = section(PROFILE, SectionKind.EXPERIENCE, 0);
        var projects = section(PROFILE, SectionKind.PROJECTS, 1);
        var job = entry(PROFILE, experience, 0);
        var confused = new Atom(PROFILE, projects.getId(), job.getId(), AtomKind.BULLET, (short) 0);

        assertThatThrownBy(() -> ProfileAssembler.assemble(PROFILE,
                List.of(experience, projects), List.of(job), List.of(confused), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anEntryPointingAtAnAbsentSectionIsRefused() {
        var absent = section(PROFILE, SectionKind.EXPERIENCE, 0);
        var orphan = entry(PROFILE, absent, 0);

        assertThatThrownBy(() -> ProfileAssembler.assemble(PROFILE,
                List.of(), List.of(orphan), List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aVariantPointingAtAnAbsentAtomIsRefused() {
        var skills = section(PROFILE, SectionKind.SKILLS, 0);
        var absent = atom(PROFILE, skills, null, 0);
        var orphan = variant(absent, "en", "Data pipelines", true);

        assertThatThrownBy(() -> ProfileAssembler.assemble(PROFILE,
                List.of(skills), List.of(), List.of(), List.of(orphan)))
                .isInstanceOf(IllegalStateException.class);
    }
}
