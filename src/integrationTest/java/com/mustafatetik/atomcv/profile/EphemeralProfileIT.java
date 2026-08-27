package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.service.EphemeralProfile;
import com.mustafatetik.atomcv.profile.service.EphemeralProfileStore;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bolum 9's promise, as the build guide's ⚠️ asks for it: an anonymous
 * session writes <strong>no row to any table</strong>.
 *
 * <p>The promise is easy to make and easy to break by accident — a profile in
 * Postgres with a nullable owner and a cleanup job keeps it on paper and
 * breaks it in a backup. So the test counts rows in every table a profile
 * touches, before and after a whole profile is stored and read back.
 */
class EphemeralProfileIT extends AbstractIntegrationTest {

    private static final List<String> PROFILE_TABLES =
            List.of("profiles", "sections", "entries", "atoms", "atom_variants", "tags");

    @Autowired
    private EphemeralProfileStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    // -- the ⚠️ ------------------------------------------------------------

    @Test
    void awholeAnonymousProfileWritesNoRowToAnyTable() {
        List<Integer> before = rowCounts();
        ProfileRef anonymous = anonymousRef();

        store.save(anonymous, profile(anonymous));
        var read = store.find(anonymous).orElseThrow();

        assertThat(read.atoms()).hasSize(1);
        assertThat(rowCounts()).isEqualTo(before);
    }

    // -- what it is, and what it is not ------------------------------------

    @Test
    void theProfileSurvivesTheRoundTripThroughRedisAsATree() {
        ProfileRef anonymous = anonymousRef();
        store.save(anonymous, profile(anonymous));

        var tree = store.find(anonymous).orElseThrow().tree();

        assertThat(tree.profileId()).isEqualTo(anonymous.id());
        assertThat(tree.sections()).singleElement().satisfies(section -> {
            assertThat(section.section().getKind()).isEqualTo(SectionKind.EXPERIENCE);
            assertThat(section.atoms()).singleElement().satisfies(atom ->
                    assertThat(atom.variants()).singleElement().satisfies(variant ->
                            assertThat(variant.getPlainText()).contains("Microsoft Fabric")));
        });
    }

    @Test
    void thecontactAndTheLanguageComeBackToo() {
        ProfileRef anonymous = anonymousRef();
        store.save(anonymous, profile(anonymous));

        var read = store.find(anonymous).orElseThrow();

        assertThat(read.contact().name()).isEqualTo("Ada Lovelace");
        assertThat(read.sourceLanguage()).isEqualTo("tr");
    }

    /**
     * § 41.3: the type is what stops a write going to the wrong store. A
     * persistent profile arriving here is refused rather than quietly written
     * into Redis, where nothing would ever read it again.
     */
    @Test
    void apersistentProfileIsRefusedRatherThanStoredHere() {
        UUID userId = UUID.randomUUID();
        ProfileRef persistent = ProfileRef.persistent(
                UserContext.of(userId), UUID.randomUUID(), userId);

        assertThatThrownBy(() -> store.find(persistent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * And an ephemeral scope cannot be made from an account's session — the
     * check that makes {@code ephemeral} a controlled path rather than a
     * public constructor in disguise.
     */
    @Test
    void anAnonymousScopeCannotBeMintedForSomebodyWhoSignedIn() {
        assertThatThrownBy(() -> AnonymousSessionId.of("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -- the window --------------------------------------------------------

    @Test
    void thestoredProfileCarriesTheAnonymousSessionsOwnWindow() {
        ProfileRef anonymous = anonymousRef();
        store.save(anonymous, profile(anonymous));

        Long seconds = redis.getExpire("anon:profile:" + anonymous.id(), TimeUnit.SECONDS);

        assertThat(seconds).isNotNull();
        assertThat(seconds).isBetween(60L * 110, 60L * 121);
    }

    /** Reading is activity, so reading slides the window (EK D.6.6). */
    @Test
    void readingItSlidesTheWindow() {
        ProfileRef anonymous = anonymousRef();
        store.save(anonymous, profile(anonymous));
        String key = "anon:profile:" + anonymous.id();
        redis.expire(key, java.time.Duration.ofSeconds(30));

        store.find(anonymous);

        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isGreaterThan(60L * 110);
    }

    @Test
    void aprofileWhoseWindowHasClosedIsSimplyGone() {
        ProfileRef anonymous = anonymousRef();

        assertThat(store.find(anonymous)).isEmpty();
    }

    @Test
    void discardingItRemovesItAtOnce() {
        ProfileRef anonymous = anonymousRef();
        store.save(anonymous, profile(anonymous));

        store.discard(anonymous);

        assertThat(store.find(anonymous)).isEmpty();
    }

    // -- fixtures ----------------------------------------------------------

    private static ProfileRef anonymousRef() {
        Session session = Session.anonymous(UUID.randomUUID().toString(), Instant.now());
        return ProfileRef.ephemeral(AnonymousSessionId.of(session.id()));
    }

    private static EphemeralProfile profile(ProfileRef ref) {
        UUID profileId = ref.id();
        Section section = new Section(profileId, SectionKind.EXPERIENCE, "Deneyim", (short) 0);
        Atom atom = new Atom(profileId, section.getId(), null, AtomKind.BULLET, (short) 0);
        AtomVariant variant = new AtomVariant(profileId, atom.getId(), "tr",
                RichContent.plain("Microsoft Fabric ile 300 bin satır taşıdım"));
        variant.setPrimary(true);

        return new EphemeralProfile(profileId,
                new Contact("Ada Lovelace", null, null, null, null, null, "Istanbul"),
                "tr", List.of(section), List.of(), List.of(atom), List.of(variant));
    }

    private List<Integer> rowCounts() {
        return PROFILE_TABLES.stream()
                .map(table -> jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class))
                .toList();
    }
}
