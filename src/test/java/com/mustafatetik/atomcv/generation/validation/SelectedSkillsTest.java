package com.mustafatetik.atomcv.generation.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What the page claims, as opposed to what was considered (Bolum 23.3).
 *
 * <p>This is the difference between an honest report and a flattering one: Faz
 * B ranks the whole profile and Faz C prints a fraction of it, so a report
 * reading the ranking would credit skills the document does not mention.
 */
class SelectedSkillsTest {

    @Test
    void onlyTheAtomsThatReachedThePageCount() {
        var fixture = new Fixture();
        var section = fixture.section();
        var entry = fixture.entry(section);
        Atom printed = fixture.bullet(section, entry, "Built payment systems", List.of("Go"));
        fixture.bullet(section, entry, "Ran the cluster", List.of("Kubernetes"));

        var skills = SelectedSkills.onThePage(fixture.tree(), selecting(printed));

        assertThat(skills).containsExactly("go");
    }

    @Test
    void skillsAreCanonicalisedTheWayTheScorerDoes() {
        // Absolute rule 7. The column holds whatever normalization produced,
        // and "SQL" under a Turkish locale becomes "sqı" — the report would
        // then call a skill missing that Faz B had counted.
        //
        // Adim 3.4 added Bolum 31.5's alias dictionary to that same rule, so
        // "PostgreSQL" now reduces to "postgres" here as well. That it changed
        // this assertion is the point: a dictionary applied on one side of a
        // comparison and not the other would break exactly the pairs it was
        // added to fix.
        var fixture = new Fixture();
        var section = fixture.section();
        Atom printed = fixture.looseAtom(section, "Query tuning", List.of("  SQL  ", "PostgreSQL"));

        var skills = SelectedSkills.onThePage(fixture.tree(), selecting(printed));

        assertThat(skills).containsExactlyInAnyOrder("sql", "postgres");
    }

    @Test
    void looseAtomsUnderASectionAreReadTooAndSkillsAreUnioned() {
        // A skill section's atoms hang off the section rather than an entry.
        // Walking only the entries would drop exactly the atoms whose whole
        // purpose is to name skills.
        var fixture = new Fixture();
        var section = fixture.section();
        var entry = fixture.entry(section);
        Atom bullet = fixture.bullet(section, entry, "Built it", List.of("Go"));
        Atom loose = fixture.looseAtom(section, "Go, Kubernetes", List.of("Go", "Kubernetes"));

        var skills = SelectedSkills.onThePage(fixture.tree(), selecting(bullet, loose));

        assertThat(skills).containsExactlyInAnyOrder("go", "kubernetes");
    }

    @Test
    void anEmptySelectionClaimsNothing() {
        var fixture = new Fixture();
        var section = fixture.section();
        fixture.looseAtom(section, "Go", List.of("Go"));

        var skills = SelectedSkills.onThePage(
                fixture.tree(), new SelectionState(List.of(), List.of(), budget()));

        assertThat(skills).isEmpty();
    }

    private static SelectionState selecting(Atom... atoms) {
        List<SelectionState.SelectedAtom> selected = new ArrayList<>();
        for (Atom atom : atoms) {
            selected.add(new SelectionState.SelectedAtom(
                    atom.getId(), UUID.randomUUID(), 0.8, 12.0, false));
        }
        return new SelectionState(selected, List.of(), budget());
    }

    private static SelectionState.BudgetBreakdown budget() {
        return new SelectionState.BudgetBreakdown(600, 100, 500, 12);
    }

    private static final class Fixture {

        private static final UUID PROFILE = UUID.randomUUID();

        private final List<Section> sections = new ArrayList<>();
        private final List<Entry> entries = new ArrayList<>();
        private final List<Atom> atoms = new ArrayList<>();
        private final List<AtomVariant> variants = new ArrayList<>();

        Section section() {
            var section = new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
            sections.add(section);
            return section;
        }

        Entry entry(Section section) {
            var entry = new Entry(PROFILE, section.getId(), "Engineer", (short) entries.size());
            entry.setStartDate(LocalDate.of(2020, 1, 1));
            entries.add(entry);
            return entry;
        }

        Atom bullet(Section section, Entry entry, String text, List<String> skills) {
            return atom(section, entry, AtomKind.BULLET, text, skills);
        }

        Atom looseAtom(Section section, String text, List<String> skills) {
            return atom(section, null, AtomKind.SKILL, text, skills);
        }

        private Atom atom(
                Section section, Entry entry, AtomKind kind, String text, List<String> skills) {

            var atom = new Atom(PROFILE, section.getId(),
                    entry == null ? null : entry.getId(), kind, (short) atoms.size());
            atom.setSkills(skills);
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
