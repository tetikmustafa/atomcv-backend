package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.embedding.EmbeddingProvider;
import com.mustafatetik.atomcv.ingestion.normalization.NormalizedProfile;
import com.mustafatetik.atomcv.ingestion.service.ProfileWriter;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.service.AtomEmbeddingService;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The first thing in this system that writes {@code atoms.embedding}.
 *
 * <p>Its own context, because the shared one has a provider pointed at a
 * service that is not running — and what has to be seen here is a vector
 * making the round trip through a {@code vector(1024)} column. CLAUDE.md
 * records why that is worth a real database: {@code @Array(length)} is read
 * during DDL generation only, so a mapping of 512 validates perfectly clean
 * against a column of 1024 and fails on the first write.
 */
class AtomEmbeddingIT extends AbstractIntegrationTest {

    /** Nested here, on the class actually being run, which is where it is found. */
    @TestConfiguration
    static class CountingEmbeddings {

        @Bean
        @Primary
        EmbeddingProvider countingEmbeddingProvider(List<String> embedded) {
            return new EmbeddingProvider() {
                @Override
                public int dimensions() {
                    return Atom.EMBEDDING_DIMENSIONS;
                }

                @Override
                public boolean isHealthy() {
                    return true;
                }

                @Override
                public float[] embed(String text) {
                    embedded.add(text);
                    return vectorFor(text);
                }

                @Override
                public List<float[]> embedBatch(List<String> texts) {
                    embedded.addAll(texts);
                    return texts.stream().map(AtomEmbeddingIT::vectorFor).toList();
                }
            };
        }

        @Bean
        List<String> embedded() {
            return new ArrayList<>();
        }
    }

    @Autowired
    private AtomEmbeddingService embeddings;

    @Autowired
    private ProfileWriter writer;

    @Autowired
    private ProfileResolver profiles;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private List<String> embedded;

    private UserContext user;

    @BeforeEach
    void freshUser() {
        embedded.clear();
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, email_verified) VALUES (?, ?, true)",
                userId, userId + "@embedding.test");
        user = UserContext.of(userId);
    }

    @Test
    void everyAtomWithAnEnglishWordingGetsAVector() {
        ProfileRef profile = importCv();

        assertThat(embeddings.embedMissing(profile)).isEqualTo(2);
        assertThat(embeddedAtoms(profile)).isEqualTo(2);
    }

    /**
     * Bolum 28: the comparison happens in one language, and a similarity
     * between a Turkish sentence and an English one measures the languages
     * rather than the match. An atom with no English wording is left alone —
     * a vector in the wrong space is worse than none, because scoring would
     * use it.
     */
    @Test
    void theTextThatIsEmbeddedIsTheEnglishOneAndNotTheSource() {
        embeddings.embedMissing(importCv());

        assertThat(embedded).allSatisfy(text -> assertThat(text).doesNotContain("tasidim"));
        assertThat(embedded).anySatisfy(text -> assertThat(text).contains("Moved 300K rows"));
    }

    @Test
    void anAtomWithNoEnglishWordingIsSkippedRatherThanEmbeddedFromItsSource() {
        ProfileRef profile = importOneTurkishOnlyAtom();

        assertThat(embeddings.embedMissing(profile)).isZero();
        assertThat(embedded).isEmpty();
        assertThat(embeddedAtoms(profile)).isZero();
    }

    /**
     * Bolum 28.2 compares by content hash, not by timestamp. Running twice
     * over an unchanged profile must cost nothing — this job is queued after
     * every import and every edit.
     */
    @Test
    void asecondRunOverAnUnchangedProfileEmbedsNothing() {
        ProfileRef profile = importCv();
        embeddings.embedMissing(profile);
        embedded.clear();

        assertThat(embeddings.embedMissing(profile)).isZero();
        assertThat(embedded).isEmpty();
    }

    /** And the vector survives the column, which is the whole reason for a real database. */
    @Test
    void thevectorComesBackTheLengthTheColumnDeclares() {
        ProfileRef profile = importCv();
        embeddings.embedMissing(profile);

        String stored = jdbc.queryForObject("""
                SELECT embedding::text FROM atoms
                WHERE profile_id = ? AND embedding IS NOT NULL LIMIT 1""",
                String.class, profile.id());

        assertThat(stored).isNotNull();
        assertThat(stored.split(",")).hasSize(Atom.EMBEDDING_DIMENSIONS);
    }

    @Test
    void thehashItWasComputedFromIsRecordedBesideIt() {
        ProfileRef profile = importCv();
        embeddings.embedMissing(profile);

        var row = jdbc.queryForMap("""
                SELECT a.embedding_hash, v.content_hash FROM atoms a
                JOIN atom_variants v ON v.atom_id = a.id AND v.language = 'en'
                WHERE a.profile_id = ? AND a.embedding IS NOT NULL LIMIT 1""",
                profile.id());

        assertThat(row.get("embedding_hash")).isEqualTo(row.get("content_hash"));
    }

    // -- fixtures ----------------------------------------------------------

    private ProfileRef importCv() {
        writer.write(user, new NormalizedProfile("tr", Contact.EMPTY,
                List.of(new NormalizedProfile.NormalizedSection(
                        SectionKind.EXPERIENCE, "Deneyim", (short) 0,
                        List.of(new NormalizedProfile.NormalizedEntry(
                                "Data Engineer", "Brisa", "Istanbul", null, null, (short) 0,
                                List.of(
                                        atom("300 bin satiri tasidim", "Moved 300K rows"),
                                        atom("Gece isini kisalttim", "Cut the nightly batch")))))),
                List.of()));
        return profiles.resolve(user);
    }

    private ProfileRef importOneTurkishOnlyAtom() {
        writer.write(user, new NormalizedProfile("tr", Contact.EMPTY,
                List.of(new NormalizedProfile.NormalizedSection(
                        SectionKind.EXPERIENCE, "Deneyim", (short) 0,
                        List.of(new NormalizedProfile.NormalizedEntry(
                                "Data Engineer", "Brisa", "Istanbul", null, null, (short) 0,
                                List.of(atom("300 bin satiri tasidim", null)))))),
                List.of()));
        return profiles.resolve(user);
    }

    private static NormalizedProfile.NormalizedAtom atom(String source, String english) {
        return new NormalizedProfile.NormalizedAtom(
                RichContent.plain(source),
                english == null ? RichContent.EMPTY : RichContent.plain(english),
                List.of(), List.of(), List.of(), List.of(), (short) 0);
    }

    private int embeddedAtoms(ProfileRef profile) {
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM atoms WHERE profile_id = ? AND embedding IS NOT NULL",
                Integer.class, profile.id());
        return rows == null ? 0 : rows;
    }

    /** A vector that differs per text, so a mix-up would be visible. */
    private static float[] vectorFor(String text) {
        float[] vector = new float[Atom.EMBEDDING_DIMENSIONS];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = ((text.hashCode() + i) % 100) / 100f;
        }
        return vector;
    }
}
