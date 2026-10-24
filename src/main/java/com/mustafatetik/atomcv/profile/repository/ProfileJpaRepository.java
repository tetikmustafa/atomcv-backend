package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Profile;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private; reached through {@link ProfileRepository}. */
interface ProfileJpaRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUserId(UUID userId);

    /**
     * The account's empty profile, unless one already exists.
     *
     * <p><strong>Two first uses can arrive together, and they did.</strong> A
     * screen sends several reads at once, and straight after a profile is
     * deleted — or an account is made — each of them found nothing and
     * inserted. One won; the rest met {@code profiles_user_id_key} and answered
     * 500, measured on 2026-10-24. {@code ON CONFLICT DO NOTHING} makes the
     * loser wait for the winner and then insert nothing, inside its own
     * transaction and on its own connection: catching the violation instead
     * would leave that transaction rollback-only, and a second transaction for
     * the insert would hold two connections per request and exhaust the pool
     * under exactly the burst this is for.
     *
     * <p>Every other column takes its database default, and each default reads
     * back as what {@code new Profile(userId)} would have written — an empty
     * contact, the default preferences, English.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO profiles (user_id) VALUES (:userId) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId);

    /**
     * The same for an anonymous session, whose two first requests race on the
     * primary key: its id is derived from the session, not drawn at random.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO profiles (id, expires_at) VALUES (:id, :expiresAt) "
            + "ON CONFLICT (id) DO NOTHING", nativeQuery = true)
    int insertAnonymousIfAbsent(@Param("id") UUID id, @Param("expiresAt") Instant expiresAt);
}
