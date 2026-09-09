package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Who a generation is for — an account, or an anonymous session (Bolum 9).
 *
 * <p><strong>Six uses of {@code UserContext}, three different reasons.</strong>
 * The pipeline took a user and did three unrelated things with it: resolved the
 * profile, bucketed an A/B experiment, and attributed the LLM spend. Only one of
 * those needs an account, so passing a user was asking for the one thing an
 * anonymous session cannot supply in order to get two it can.
 *
 * @param owned    the profile and the scope below it, resolved once — the
 *                 pipeline reads the head for its preferences and the ref for
 *                 everything under it
 * @param userId   for attribution, and null for an anonymous session.
 *                 {@code llm_invocations.user_id} is nullable and § 51.6's note
 *                 says a row there from an anonymous generation is expected: the
 *                 cost was real and the person was not an account.
 * @param bucketKey which prompt variant this caller keeps seeing (Bolum 53.3).
 *                 The profile id for an anonymous session, following the
 *                 convention {@code ProfileExtractionJobHandler} set — both it
 *                 and the session id are stable for the session, and one of them
 *                 is the cookie, which has no business being passed around as an
 *                 identifier.
 */
public record GenerationSubject(
        ProfileResolver.OwnedProfile owned, UUID userId, String bucketKey) {

    public GenerationSubject {
        Objects.requireNonNull(owned, "owned");
        Objects.requireNonNull(bucketKey, "bucketKey");
    }

    /** An account, bucketed and attributed by its own id. */
    public static GenerationSubject account(ProfileResolver.OwnedProfile owned, UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return new GenerationSubject(owned, userId, userId.toString());
    }

    /**
     * An anonymous session: no user to attribute to, bucketed by the profile the
     * session owns.
     */
    public static GenerationSubject anonymous(ProfileResolver.OwnedProfile owned) {
        return new GenerationSubject(owned, null, owned.ref().id().toString());
    }

    public Profile head() {
        return owned.profile();
    }

    public ProfileRef profile() {
        return owned.ref();
    }

    /**
     * Whether this generation belongs to nobody.
     *
     * <p>Read off the scope rather than off {@link #userId()} being null, so
     * there is one answer to "is this anonymous" in the codebase and it is the
     * same one {@code AnonymousLimits} asks.
     */
    public boolean isAnonymous() {
        return profile().scope() == ProfileRef.Scope.EPHEMERAL;
    }

    /** For the calls that record a user and tolerate there not being one. */
    public Optional<UUID> attribution() {
        return Optional.ofNullable(userId);
    }
}
