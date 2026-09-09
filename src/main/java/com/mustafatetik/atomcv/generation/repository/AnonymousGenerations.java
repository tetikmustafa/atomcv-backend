package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * A generation an anonymous session made, reached without a user because there
 * is not one (Bolum 9).
 *
 * <p><strong>Profile-scoped, and that is not a workaround.</strong>
 * {@link GenerationRepository} is user-scoped and correct as it stands — a row
 * with no owner reads as absent there, and must. But a generation already
 * carries {@code profile_id NOT NULL}, so the scope an anonymous session has is
 * the one the row was already filed under. This is
 * {@code ProfileScopedRepository}'s shape written out by hand rather than a new
 * idea, because {@code Generation} is {@code UserOwned} and cannot extend both.
 *
 * <p><strong>Two filters, and together they are the guard.</strong> The ref must
 * be {@code EPHEMERAL}, and the row must have no owner <em>and</em> belong to
 * that profile. So an account's generation cannot be reached here even if its id
 * is guessed, and neither can another session's: the ref is derived from the
 * session id by a one-way function, so computing it means holding the cookie.
 *
 * <p><strong>Nothing expires here.</strong> {@code generations.profile_id}
 * references {@code profiles(id) ON DELETE CASCADE}, so these rows go when the
 * anonymous profile does — which the sweep deletes within minutes of the session
 * ending (§ 51.6.1). A second expiry column would be a second thing to keep in
 * step with the first.
 */
@Repository
public class AnonymousGenerations {

    private final GenerationJpaRepository jpa;

    AnonymousGenerations(GenerationJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * One generation of this session's profile, or empty.
     *
     * <p>Empty covers "no such generation", "it belongs to an account" and "the
     * session's profile is gone" without distinguishing them, because none of
     * the three is a different answer to the caller: this session has no such
     * generation.
     */
    @Transactional(readOnly = true)
    public Optional<Generation> findById(ProfileRef profile, UUID id) {
        requireEphemeral(profile);
        return jpa.findById(id)
                .filter(row -> row.getOwnerId() == null)
                .filter(row -> row.getProfileId().equals(profile.id()));
    }

    /**
     * Writes one back.
     *
     * <p>Takes the ref rather than trusting the entity, for the reason
     * {@code AnonymousProfiles.save} gives: an entity is a value a caller could
     * have built, and the ref is the thing only the session could have produced.
     */
    @Transactional
    public Generation save(ProfileRef profile, Generation generation) {
        requireEphemeral(profile);
        if (generation.getOwnerId() != null
                || !generation.getProfileId().equals(profile.id())) {
            throw new IllegalArgumentException("This is not that session's generation");
        }
        return jpa.save(generation);
    }

    /**
     * Every generation this session made becomes the account's (Adim 3.6).
     *
     * <p><strong>Rows loaded rather than updated in bulk, on purpose.</strong> A
     * session may make five (§ 35.7), so the whole set is a handful and the
     * domain rule stays where it belongs: {@code Generation.adoptedBy} refuses a
     * row that already has an owner, and a {@code @Modifying} update would have
     * moved that rule into a WHERE clause and taken the version and the
     * timestamps with it.
     *
     * <p>Called inside the same transaction that adopts the profile, and it has
     * to be: a CV made from that profile is the reason somebody signs up, and
     * carrying the profile while leaving the generations behind would delete the
     * very thing they were keeping.
     *
     * @return how many were carried across, for the log line -- a count, never
     *         a line of anybody's CV (absolute rule 4)
     */
    @Transactional
    public int adoptAll(ProfileRef profile, UUID owner) {
        requireEphemeral(profile);
        java.util.Objects.requireNonNull(owner, "owner");

        var waiting = jpa.findByProfileIdAndUserIdIsNullOrderByCreatedAtAsc(profile.id());
        waiting.forEach(generation -> generation.adoptedBy(owner));
        jpa.saveAll(waiting);
        return waiting.size();
    }

    private static void requireEphemeral(ProfileRef profile) {
        if (profile == null || profile.scope() != ProfileRef.Scope.EPHEMERAL) {
            throw new IllegalArgumentException(
                    "An anonymous generation is addressed by an ephemeral ref and nothing else");
        }
    }
}
