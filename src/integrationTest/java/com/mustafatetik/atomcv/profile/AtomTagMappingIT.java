package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomTag;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.Tag;
import com.mustafatetik.atomcv.profile.domain.TagSource;
import com.mustafatetik.atomcv.profile.repository.TagRepository;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code tags} and {@code atom_tags} against the real schema (Bolum 13,
 * Bolum 19.2).
 *
 * <p>Two things here are only true against a database. The composite key is
 * one: an {@code @IdClass} that disagrees with the primary key compiles and
 * validates, and fails on the second insert. The scoping is the other, and it
 * is the one that matters — {@code atom_tags} carries no {@code profile_id},
 * so nothing but the join to {@code tags} keeps one profile's labels out of
 * another's scoring (absolute rule 3).
 */
@Transactional
class AtomTagMappingIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entities;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TagRepository tags;

    @Test
    void atagAndItsLinkSurviveTheRoundTrip() {
        var profile = persistedProfile();
        var atom = persistedAtom(profile.id());
        var tag = persist(new Tag(profile.id(), "payments"));
        entities.persist(new AtomTag(atom.getId(), tag.getId(), TagSource.USER));
        entities.flush();
        entities.clear();

        var reloaded = entities.find(AtomTag.class, new AtomTag.Key(atom.getId(), tag.getId()));
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getSource()).isEqualTo(TagSource.USER);
        assertThat(entities.find(Tag.class, tag.getId()).getLabel()).isEqualTo("payments");
    }

    /**
     * The label is canonicalised on the way in, because Faz B compares it
     * against a lowercase posting vocabulary and a stored "Payments" would
     * never match again.
     */
    @Test
    void thelabelIsStoredCanonical() {
        var profile = persistedProfile();
        var tag = persist(new Tag(profile.id(), "  Distributed Systems  "));
        entities.flush();
        entities.clear();

        assertThat(entities.find(Tag.class, tag.getId()).getLabel())
                .isEqualTo("distributed systems");
    }

    /** Absolute rule 7: a Turkish default locale writes "sqı" for "SQL". */
    @Test
    void canonicalisingALabelSurvivesATurkishDefaultLocale() {
        var previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(Tag.canonical("SQL")).isEqualTo("sql");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    /**
     * The reason this class exists. The link row cannot say whose it is, so a
     * read that did not join to {@code tags} would hand Faz B another
     * profile's vocabulary for the price of a colliding atom id.
     */
    @Test
    void thelabelsOfAnotherProfileAreNeverReturned() {
        var mine = persistedProfile();
        var theirs = persistedProfile();
        tagAtom(mine.id(), "payments");
        tagAtom(theirs.id(), "logistics");
        entities.flush();

        Map<UUID, Set<String>> labels = tags.labelsByAtom(mine);

        assertThat(labels.values()).containsExactly(Set.of("payments"));
        assertThat(tags.labelsByAtom(theirs).values()).containsExactly(Set.of("logistics"));
    }

    /**
     * Ordered, and it has to be asserted as order rather than as membership.
     * {@code Set.copyOf} iterates in an order salted per JVM run: the first
     * version of this passed here and failed on the CI runner, which is a
     * failure mode that looks like a flake and is not one.
     */
    @Test
    void anAtomsLabelsComeBackTogetherAndSorted() {
        var profile = persistedProfile();
        var atom = persistedAtom(profile.id());
        for (String label : new String[] {"go", "payments", "distributed systems"}) {
            var tag = persist(new Tag(profile.id(), label));
            entities.persist(new AtomTag(atom.getId(), tag.getId(), TagSource.AUTO));
        }
        entities.flush();

        assertThat(tags.labelsByAtom(profile).get(atom.getId()))
                .containsExactly("distributed systems", "go", "payments");
    }

    /**
     * An untagged atom is absent rather than present with an empty set. The
     * caller reads a missing key as "no tags", and materialising a row per
     * atom would make the map grow with the profile for nothing.
     */
    @Test
    void anuntaggedAtomIsNotInTheMap() {
        var profile = persistedProfile();
        var untagged = persistedAtom(profile.id());
        entities.flush();

        assertThat(tags.labelsByAtom(profile)).doesNotContainKey(untagged.getId());
    }

    /** {@code UNIQUE (profile_id, label)}: one profile spells a word once. */
    @Test
    void thesameLabelCannotBeAddedToAProfileTwice() {
        var profile = persistedProfile();
        persist(new Tag(profile.id(), "payments"));
        persist(new Tag(profile.id(), "payments"));

        assertThatThrownBy(() -> entities.flush())
                .hasMessageContaining("constraint");
    }

    /** The {@code CHECK} the column carries, reached through the converter. */
    @Test
    void thesourceIsStoredLowercase() {
        var profile = persistedProfile();
        var atom = persistedAtom(profile.id());
        var tag = persist(new Tag(profile.id(), "payments"));
        entities.persist(new AtomTag(atom.getId(), tag.getId(), TagSource.USER));
        entities.flush();

        assertThat(jdbc.queryForObject(
                "select source from atom_tags where atom_id = ?", String.class, atom.getId()))
                .isEqualTo("user");
    }

    private void tagAtom(UUID profileId, String label) {
        var atom = persistedAtom(profileId);
        var tag = persist(new Tag(profileId, label));
        entities.persist(new AtomTag(atom.getId(), tag.getId(), TagSource.AUTO));
    }

    private Tag persist(Tag tag) {
        entities.persist(tag);
        return tag;
    }

    private ProfileRef persistedProfile() {
        var userId = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
        var profileId = jdbc.queryForObject(
                "INSERT INTO profiles (user_id) VALUES (?) RETURNING id", UUID.class, userId);
        return ProfileRef.persistent(UserContext.of(userId), profileId, userId);
    }

    private Atom persistedAtom(UUID profileId) {
        var section = new Section(profileId, SectionKind.EXPERIENCE, "Experience", (short) 0);
        entities.persist(section);
        var atom = new Atom(profileId, section.getId(), null, AtomKind.BULLET, (short) 0);
        entities.persist(atom);
        return atom;
    }
}
