package com.mustafatetik.atomcv.generation.scoring;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The projection Faz B scores: what it reads off the tree, and what it skips. */
class ScorableAtomFactoryTest {

    private static final UUID PROFILE = UUID.randomUUID();

    @Test
    void everyActiveAtomBecomesOneScorableAtom() {
        var fixture = new Fixture();
        var section = fixture.section();
        var entry = fixture.entry(section);
        fixture.bullet(section, entry, "en", "Built ETL pipelines");
        fixture.looseAtom(section, "en", "Go");

        assertThat(ScorableAtomFactory.from(fixture.tree(), Map.of())).hasSize(2);
    }

    /**
     * Bolum 19.5: an inactive atom is not scored. Selection rejects it as
     * INACTIVE before the number would matter, and scoring it would spend a
     * cosine per atom the user switched off.
     */
    @Test
    void aninactiveAtomIsNotScored() {
        var fixture = new Fixture();
        var section = fixture.section();
        var atom = fixture.looseAtom(section, "en", "Go");
        atom.setActive(false);

        assertThat(ScorableAtomFactory.from(fixture.tree(), Map.of())).isEmpty();
    }

    @Test
    void thetagsComeFromTheMapAndAMissingAtomHasNone() {
        var fixture = new Fixture();
        var section = fixture.section();
        var tagged = fixture.looseAtom(section, "en", "Go");
        var untagged = fixture.looseAtom(section, "en", "Rust");

        List<ScorableAtom> atoms = ScorableAtomFactory.from(fixture.tree(),
                Map.of(tagged.getId(), Set.of("payments")));

        assertThat(byId(atoms, tagged).tags()).containsExactly("payments");
        assertThat(byId(atoms, untagged).tags()).isEmpty();
    }

    /** The vector travels with the atom; an unembedded one is normal. */
    @Test
    void theembeddingIsCarriedThroughWhenThereIsOne() {
        var fixture = new Fixture();
        var section = fixture.section();
        var embedded = fixture.looseAtom(section, "en", "Go");
        embedded.setEmbedding(new float[Atom.EMBEDDING_DIMENSIONS], "hash");
        var bare = fixture.looseAtom(section, "en", "Rust");

        List<ScorableAtom> atoms = ScorableAtomFactory.from(fixture.tree(), Map.of());

        assertThat(byId(atoms, embedded).hasEmbedding()).isTrue();
        assertThat(byId(atoms, bare).hasEmbedding()).isFalse();
    }

    // -- contentTokens ---------------------------------------------------

    @Test
    void theatomsOwnWordsAndItsHeadingBothBecomeTokens() {
        var fixture = new Fixture();
        var section = fixture.section();
        var entry = fixture.entry(section);
        entry.setOrganization("Acme Payments");
        var atom = fixture.bullet(section, entry, "en", "Built ETL pipelines");

        List<String> tokens = byId(
                ScorableAtomFactory.from(fixture.tree(), Map.of()), atom).contentTokens();

        assertThat(tokens).contains("built", "etl", "pipelines", "engineer", "acme", "payments");
    }

    /**
     * A section-level atom has no heading of its own, and reaching for one
     * would be a null dereference on every skill in every profile.
     */
    @Test
    void asectionLevelAtomHasOnlyItsOwnWords() {
        var fixture = new Fixture();
        var atom = fixture.looseAtom(fixture.section(), "en", "Kubernetes");

        assertThat(byId(ScorableAtomFactory.from(fixture.tree(), Map.of()), atom)
                .contentTokens()).containsExactly("kubernetes");
    }

    /**
     * Bolum 18.2: keywords are always English, and so is the vector. The
     * English wording is read when there is one, whatever the primary is.
     */
    @Test
    void theenglishWordingIsPreferredOverThePrimary() {
        var fixture = new Fixture();
        var section = fixture.section();
        var atom = fixture.looseAtom(section, "tr", "Odeme altyapisi");
        fixture.wordingFor(atom, "en", "Payment infrastructure");

        assertThat(byId(ScorableAtomFactory.from(fixture.tree(), Map.of()), atom)
                .contentTokens()).contains("payment", "infrastructure");
    }

    /**
     * Without an English wording the primary is read anyway rather than
     * nothing: technology names and proper nouns are spelled the same in both
     * languages, and they are most of what a keyword list contains.
     */
    @Test
    void aturkishOnlyAtomStillContributesItsProperNouns() {
        var fixture = new Fixture();
        var atom = fixture.looseAtom(fixture.section(), "tr", "Kubernetes kumesi kurdum");

        assertThat(byId(ScorableAtomFactory.from(fixture.tree(), Map.of()), atom)
                .contentTokens()).contains("kubernetes");
    }

    /**
     * Absolute rule 7. A Turkish default locale writes a dotless i for "SQL"
     * and the skill stops matching the posting — the score drops for every
     * user on a Turkish server and nothing looks broken.
     */
    @Test
    void canonicalisingSurvivesATurkishDefaultLocale() {
        var fixture = new Fixture();
        var atom = fixture.looseAtom(fixture.section(), "en", "SQL and INDEXING");
        atom.setSkills(List.of("SQL"));

        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var scorable = byId(ScorableAtomFactory.from(fixture.tree(), Map.of()), atom);

            assertThat(scorable.skills()).containsExactly("sql");
            assertThat(scorable.contentTokens()).contains("sql", "indexing");
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static ScorableAtom byId(List<ScorableAtom> atoms, Atom atom) {
        return atoms.stream().filter(candidate -> candidate.atomId().equals(atom.getId()))
                .findFirst().orElseThrow();
    }

    /** A profile under construction, flat, the way the repositories return it. */
    private static final class Fixture {

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

        Atom bullet(Section section, Entry entry, String language, String text) {
            return atom(section, entry, AtomKind.BULLET, language, text);
        }

        Atom looseAtom(Section section, String language, String text) {
            return atom(section, null, AtomKind.SKILL, language, text);
        }

        void wordingFor(Atom atom, String language, String text) {
            variants.add(new AtomVariant(PROFILE, atom.getId(), language, RichContent.plain(text)));
        }

        private Atom atom(
                Section section, Entry entry, AtomKind kind, String language, String text) {

            var atom = new Atom(PROFILE, section.getId(),
                    entry == null ? null : entry.getId(), kind, (short) atoms.size());
            var variant = new AtomVariant(PROFILE, atom.getId(), language, RichContent.plain(text));
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
