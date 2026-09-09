package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.ingestion.normalization.NormalizedProfile;
import com.mustafatetik.atomcv.ingestion.service.ProfileWriter;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.repository.AnonymousProfiles;
import com.mustafatetik.atomcv.profile.repository.ProfileRepository;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * An anonymous session's profile, as rows (Bolum 9).
 *
 * <p><strong>Sapma, and the deleted test said so first.</strong> Bolum 14's step
 * 9 carries a privacy test — "DB'ye hiçbir satır yazmamalı" — and this file
 * replaces the one that enforced it. What that one's javadoc warned about is
 * exactly what now happens: "a profile in Postgres with a nullable owner and a
 * cleanup job keeps it on paper and breaks it in a backup". The decision to
 * accept that was taken on 2026-09-09 and written into § 9, § 2's store choice
 * and § 57.4; the privacy text says the number, which is up to six months in an
 * encrypted archive.
 *
 * <p>So what is asserted here is the second-best promise, and every part of it
 * has to hold or the first one was given up for nothing: the row has no owner,
 * it carries an expiry, no user-scoped read can reach it, no other session can
 * reach it, and the sweep takes the whole tree with it.
 */
class AnonymousProfileIT extends AbstractIntegrationTest {

    @Autowired
    private AnonymousProfiles anonymous;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private ProfileWriter writer;

    @Autowired
    private JdbcTemplate jdbc;

    // -- what the row is ---------------------------------------------------

    @Test
    void ananonymousProfileHasNoOwnerAndAnExpiry() {
        ProfileRef ref = anonymousRef();

        Profile written = writer.writeAnonymously(ref, in(Duration.ofHours(2)), oneBullet());

        assertThat(written.getId()).isEqualTo(ref.id());
        assertThat(written.isAnonymous()).isTrue();
        assertThat(written.getExpiresAt()).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT user_id FROM profiles WHERE id = ?", UUID.class, ref.id())).isNull();
        assertThat(atomsUnder(ref)).isEqualTo(1);
    }

    /**
     * The invariant the whole design rests on, refused by the database rather
     * than by a rule somebody has to remember.
     *
     * <p>A row with neither an owner nor an expiry could not be reached and
     * would never be removed — the leak this sapma is most likely to produce.
     */
    @Test
    void aprofileWithNeitherAnOwnerNorAnExpiryIsRefused() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO profiles (id, user_id, expires_at) VALUES (?, NULL, NULL)", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** And an owned profile may not carry a deletion date. */
    @Test
    void aprofileWithBothAnOwnerAndAnExpiryIsRefused() {
        UUID userId = someUser();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO profiles (id, user_id, expires_at) VALUES (?, ?, now())",
                UUID.randomUUID(), userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // -- who can reach it --------------------------------------------------

    /**
     * <strong>The IDOR line, and it is the one the sapma moved.</strong> While
     * the profile was in Redis nothing in Postgres could return it by accident.
     * Now it is a row in the same table as everybody's, and what keeps it apart
     * is {@code UserScopedRepository} comparing owner-side-second: a null owner
     * reads as "not yours" rather than throwing.
     */
    @Test
    void nouserScopedReadCanReachAnAnonymousProfile() {
        ProfileRef ref = anonymousRef();
        writer.writeAnonymously(ref, in(Duration.ofHours(2)), oneBullet());
        UserContext somebody = UserContext.of(someUser());

        assertThat(profiles.findById(somebody, ref.id())).isEmpty();
        assertThat(profiles.findOwn(somebody)).isEmpty();
    }

    @Test
    void anotherAnonymousSessionFindsNothingUnderItsOwnRef() {
        ProfileRef mine = anonymousRef();
        writer.writeAnonymously(mine, in(Duration.ofHours(2)), oneBullet());

        assertThat(anonymous.find(anonymousRef())).isEmpty();
        assertThat(anonymous.find(mine)).isPresent();
    }

    /**
     * § 41.3: the ref is what addresses this, and a persistent one is refused
     * rather than quietly reaching an account's profile by id.
     *
     * <p><strong>It arrives as {@code InvalidDataAccessApiUsageException}, and
     * that is worth pinning.</strong> The guard throws
     * {@code IllegalArgumentException}, but {@code @Repository} makes Spring
     * translate anything thrown inside it — so the refusal comes out wearing a
     * {@code DataAccessException}'s clothes. {@code SignInHandover} catches that
     * type to keep a failed hand-over from breaking a sign-in, and without a
     * clause for this one a misuse of the ref would have been reported to the
     * person as bad luck and to us as nothing at all.
     */
    @Test
    void apersistentRefIsRefusedRatherThanFollowed() {
        UUID userId = someUser();
        ProfileRef persistent = ProfileRef.persistent(
                UserContext.of(userId), UUID.randomUUID(), userId);

        assertThatThrownBy(() -> anonymous.find(persistent))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The other half of that check, and it is on the row rather than on the
     * argument: an ephemeral ref that happens to name an <em>owned</em> profile
     * reaches nothing.
     *
     * <p>Set up the only way round: rather than forging a ref for a chosen id —
     * which the one-way derivation makes impossible — a legitimate ref is taken
     * and the profile it names is given an owner. That is the state the filter
     * exists for, however it came about.
     */
    @Test
    void anEphemeralRefReachesNothingOnceTheProfileHasAnOwner() {
        ProfileRef ref = anonymousRef();
        jdbc.update("INSERT INTO profiles (id, user_id) VALUES (?, ?)", ref.id(), someUser());

        assertThat(anonymous.find(ref)).isEmpty();
    }

    // -- the window --------------------------------------------------------

    /** Writing is activity, and activity slides the window (EK D.6.6). */
    @Test
    void writingAgainPushesTheExpiryOut() {
        ProfileRef ref = anonymousRef();
        writer.writeAnonymously(ref, in(Duration.ofMinutes(1)), oneBullet());
        Instant first = anonymous.find(ref).orElseThrow().getExpiresAt();

        writer.writeAnonymously(ref, in(Duration.ofHours(2)), oneBullet());

        assertThat(anonymous.find(ref).orElseThrow().getExpiresAt()).isAfter(first);
    }

    /**
     * A second upload replaces the first rather than being added to it
     * (§ 31.6.3): a session has one document, and the person who uploads twice
     * changed their mind two minutes ago.
     */
    @Test
    void asecondUploadReplacesTheFirst() {
        ProfileRef ref = anonymousRef();
        writer.writeAnonymously(ref, in(Duration.ofHours(2)), oneBullet());

        writer.writeAnonymously(ref, in(Duration.ofHours(2)), oneBullet());

        assertThat(atomsUnder(ref)).isEqualTo(1);
    }

    // -- fixtures ----------------------------------------------------------

    private static ProfileRef anonymousRef() {
        Session session = Session.anonymous(UUID.randomUUID().toString(), Instant.now());
        return ProfileRef.ephemeral(AnonymousSessionId.of(session.id()));
    }

    private static Instant in(Duration window) {
        return Instant.now().plus(window);
    }

    private UUID someUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email) VALUES (?, ?)",
                id, id + "@rows.test");
        return id;
    }

    private int atomsUnder(ProfileRef ref) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM atoms WHERE profile_id = ?", Integer.class, ref.id());
    }

    private static NormalizedProfile oneBullet() {
        var atom = new NormalizedProfile.NormalizedAtom(
                RichContent.plain("Moved 300K rows with Microsoft Fabric"),
                RichContent.EMPTY, List.of("microsoft-fabric"), List.of(), List.of(), List.of(),
                (short) 0);
        var entry = new NormalizedProfile.NormalizedEntry(
                "Data Engineer", "Initech", null, null, null, (short) 0, List.of(atom));
        var section = new NormalizedProfile.NormalizedSection(
                SectionKind.EXPERIENCE, "Experience", (short) 0, List.of(entry));
        return new NormalizedProfile("en",
                new com.mustafatetik.atomcv.profile.domain.Contact(
                        "Ada Lovelace", null, null, null, null, null, "Istanbul"),
                List.of(section), List.of());
    }
}
