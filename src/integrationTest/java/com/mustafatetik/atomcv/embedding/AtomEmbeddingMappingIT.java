package com.mustafatetik.atomcv.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code atoms.embedding} against a real pgvector column (Bolum 28).
 *
 * <p>This exists because schema validation does not cover it. Setting
 * {@code @Array(length)} to 512 and running the suite passes — the annotation
 * feeds DDL generation, not validation — so a mapping that disagrees with the
 * column would otherwise be found by the first production write.
 */
@Transactional
class AtomEmbeddingMappingIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entities;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void avectorSurvivesTheRoundTrip() {
        var atom = persistedAtom();
        var vector = vectorOf(1024, 0.25f);

        atom.setEmbedding(vector, "hash-of-the-english-variant");
        entities.flush();
        entities.clear();

        var reloaded = entities.find(Atom.class, atom.getId());
        assertThat(reloaded.getEmbedding()).hasSize(1024);
        assertThat(reloaded.getEmbedding()[0]).isEqualTo(0.25f);
        assertThat(reloaded.getEmbedding()[1023]).isEqualTo(0.25f);
        assertThat(reloaded.getEmbeddingHash()).isEqualTo("hash-of-the-english-variant");
    }

    /** The column really is a vector, not a float array in disguise. */
    @Test
    void theStoredValueIsUsableByPgvector() {
        var atom = persistedAtom();
        atom.setEmbedding(vectorOf(1024, 0.5f), "hash");
        entities.flush();

        // If the column held anything but a vector, the operator would not
        // resolve and this would fail rather than return a number.
        Double distance = jdbc.queryForObject(
                "select embedding <=> embedding from atoms where id = ?",
                Double.class, atom.getId());

        assertThat(distance).isNotNull().isCloseTo(0.0,
                org.assertj.core.data.Offset.offset(1e-6));
    }

    /**
     * The dimension guard Hibernate does not provide. Without it a shorter
     * array reaches Postgres and fails there, which is a stack trace from the
     * driver rather than a sentence naming the column.
     */
    @Test
    void avectorOfTheWrongLengthIsRefusedBeforeItReachesTheDatabase() {
        var atom = persistedAtom();

        assertThatThrownBy(() -> atom.setEmbedding(vectorOf(512, 0.1f), "hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vector(1024)");
    }

    @Test
    void anAtomWithNoEmbeddingIsNormalRatherThanBroken() {
        var atom = persistedAtom();
        entities.flush();
        entities.clear();

        var reloaded = entities.find(Atom.class, atom.getId());
        assertThat(reloaded.getEmbedding()).isNull();
        assertThat(reloaded.getEmbeddingHash()).isNull();
    }

    /** Bolum 28.2: the hash is what says whether the vector is still current. */
    @Test
    void anAtomKnowsWhenItsVectorIsStale() {
        var atom = persistedAtom();
        assertThat(atom.needsEmbedding("hash-1")).isTrue();

        atom.setEmbedding(vectorOf(1024, 0.1f), "hash-1");
        assertThat(atom.needsEmbedding("hash-1")).isFalse();
        assertThat(atom.needsEmbedding("hash-2")).isTrue();

        // Clearing the vector clears the hash with it, or the atom would
        // report itself current while holding nothing.
        atom.setEmbedding(null, "hash-1");
        assertThat(atom.getEmbeddingHash()).isNull();
        assertThat(atom.needsEmbedding("hash-1")).isTrue();
    }

    /**
     * The array is mutable and Hibernate hands back the field itself. A caller
     * that reordered what it got would rewrite the row on the next flush
     * without ever meaning to.
     */
    @Test
    void theStoredVectorCannotBeChangedThroughTheAccessor() {
        var atom = persistedAtom();
        var original = vectorOf(1024, 0.3f);
        atom.setEmbedding(original, "hash");

        original[0] = 99f;
        atom.getEmbedding()[1] = 99f;

        assertThat(atom.getEmbedding()[0]).isEqualTo(0.3f);
        assertThat(atom.getEmbedding()[1]).isEqualTo(0.3f);
    }

    private Atom persistedAtom() {
        var userId = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
        var profileId = jdbc.queryForObject(
                "INSERT INTO profiles (user_id) VALUES (?) RETURNING id", UUID.class, userId);
        var section = new Section(profileId, SectionKind.EXPERIENCE, "Experience", (short) 0);
        entities.persist(section);
        var atom = new Atom(profileId, section.getId(), null, AtomKind.BULLET, (short) 0);
        entities.persist(atom);
        return atom;
    }

    private static float[] vectorOf(int size, float value) {
        var vector = new float[size];
        java.util.Arrays.fill(vector, value);
        return vector;
    }
}
