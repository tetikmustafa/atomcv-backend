package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The head row of an anonymous session's profile, reached without a user
 * because there is not one (Bolum 9, Adim 3.6).
 *
 * <p><strong>Why this cannot go through {@link ProfileRepository}.</strong> That
 * one is user-scoped and correct as it stands: it compares
 * {@code user.userId()} against the row's owner, so an anonymous profile reads
 * as absent there and must. Everything <em>below</em> the head is already
 * reachable — the five scoped repositories take a {@code ProfileRef} and
 * {@code ProfileRef.ephemeral(session)} produces one — and this is the single
 * row that has no {@code profile_id} to be scoped by, because it is the profile.
 *
 * <p><strong>Two filters, and together they are the guard.</strong> The ref must
 * be {@code EPHEMERAL}, and the row must have no owner. So a persistent ref
 * cannot be handed here to reach an account's profile, and neither can an
 * ephemeral one that happens to collide with an account's profile id — the
 * second filter refuses it on the row rather than on the argument. That is
 * stronger than a package rule: this type is safe to call from anywhere,
 * including a controller, because there is nothing it can return that the caller
 * was not already holding the session for.
 *
 * <p>The reachability argument is the one {@code ProfileRef.ephemeral} makes:
 * the id is derived from the session id by a one-way function, so computing it
 * means holding the cookie. Nobody can ask for somebody else's anonymous
 * profile without already being them.
 */
@Repository
public class AnonymousProfiles {

    private final ProfileJpaRepository jpa;

    AnonymousProfiles(ProfileJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * The anonymous profile this ref names, or empty.
     *
     * <p>Empty covers three different things on purpose — no such profile, an
     * expired one the sweep has taken, and a row that turned out to have an
     * owner. None of them is a distinction a caller should act on differently:
     * in all three the answer is that this session has no profile.
     */
    @Transactional(readOnly = true)
    public Optional<Profile> find(ProfileRef profile) {
        requireEphemeral(profile);
        return jpa.findById(profile.id()).filter(Profile::isAnonymous);
    }

    /**
     * The anonymous profile this ref names, created if this session has not
     * uploaded anything yet.
     *
     * @param expiresAt when the session ends. Refreshed on every write, because
     *                  a session that is still being used has not ended — and a
     *                  profile that expired while its owner was editing it
     *                  would be the sweep deleting work in progress.
     */
    @Transactional
    public Profile findOrCreate(ProfileRef profile, Instant expiresAt) {
        return find(profile)
                .map(existing -> {
                    existing.renewUntil(expiresAt);
                    return jpa.save(existing);
                })
                .orElseGet(() -> jpa.save(
                        Profile.forAnonymousSession(profile.id(), expiresAt)));
    }

    /**
     * Writes the head back.
     *
     * <p>Takes the ref it was found with rather than trusting the entity: an
     * entity is a value a caller could have built, and the point of the ref is
     * that only the session could have produced it.
     */
    @Transactional
    public Profile save(ProfileRef profile, Profile entity) {
        requireEphemeral(profile);
        if (!entity.getId().equals(profile.id()) || !entity.isAnonymous()) {
            throw new IllegalArgumentException("This is not that session's anonymous profile");
        }
        return jpa.save(entity);
    }

    /**
     * The profile stops expiring and starts belonging to somebody (Adim 3.6).
     *
     * <p><strong>One statement, and the whole of what signing up does to a
     * profile.</strong> It used to be a copy: a new head row, then every
     * section, entry, atom and variant saved across under the same ids. The
     * rows never needed to move — they were only ever addressed by
     * {@code profile_id} — so what was actually being carried was the ownership,
     * and this carries it directly. {@code profiles_owner_xor_expiry} makes the
     * two halves inseparable: the owner cannot be set without the expiry being
     * cleared.
     *
     * @return the adopted profile, or empty if this session had none
     */
    @Transactional
    public Optional<Profile> adopt(ProfileRef profile, java.util.UUID owner) {
        return find(profile).map(waiting -> {
            waiting.adoptedBy(owner);
            return jpa.save(waiting);
        });
    }

    private static void requireEphemeral(ProfileRef profile) {
        if (profile == null || profile.scope() != ProfileRef.Scope.EPHEMERAL) {
            throw new IllegalArgumentException(
                    "An anonymous profile is addressed by an ephemeral ref and nothing else");
        }
    }
}
