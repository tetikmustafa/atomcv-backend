package com.mustafatetik.atomcv.shared.security;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * A profile identifier that is known to belong to the acting user
 * (Bolum 41.3).
 *
 * <p>The point of the type is that it cannot be made up. The only way to hold
 * one is to call {@link #persistent} with the acting user and the owner the
 * profile row actually carries, and that call compares them. A controller
 * therefore cannot turn a path variable into a {@code ProfileRef}, and a
 * repository that takes one does not need to re-check anything.
 *
 * <p>Not a record: a record's canonical constructor cannot be more restricted
 * than the record itself, so a public record would hand out an unchecked way
 * to build one.
 */
public final class ProfileRef {

    /**
     * Where the profile lives. Anonymous profiles are held outside the
     * database and arrive with the anonymous flow in Stage 3; the constant is
     * deliberately absent until there is a checked way to produce one.
     */
    public enum Scope {
        PERSISTENT,

        /**
         * A profile that lives only in Redis, for the length of an anonymous
         * session (Bolum 9, Adim 3.6).
         *
         * <p>Absent until Adim 3.6 for the reason § 41.3 gives: the constant
         * is worthless without a checked way to produce one, and a scope
         * anybody could construct would be a way around the ownership check
         * rather than a part of it. {@link #ephemeral} is that way — it takes
         * a session that is <em>already known to be anonymous</em>, so the
         * only thing that can produce this scope is the thing it belongs to.
         */
        EPHEMERAL
    }

    private final UUID id;
    private final Scope scope;

    private ProfileRef(UUID id, Scope scope) {
        this.id = id;
        this.scope = scope;
    }

    /**
     * @param user           who is acting
     * @param profileId      the profile being addressed
     * @param profileOwnerId {@code user_id} as stored on that profile row
     * @throws CrossTenantAccessException if the two do not agree
     */
    public static ProfileRef persistent(UserContext user, UUID profileId, UUID profileOwnerId) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(profileOwnerId, "profileOwnerId");

        if (!user.userId().equals(profileOwnerId)) {
            throw new CrossTenantAccessException("The profile belongs to a different user");
        }
        return new ProfileRef(profileId, Scope.PERSISTENT);
    }

    /**
     * The scope of an anonymous session's own profile (Adim 3.6).
     *
     * <p><strong>The id is derived from the session id, not stored beside
     * it.</strong> An anonymous session has exactly one profile and the
     * session is the only thing that identifies it, so a second identifier
     * would be a second thing to keep in step — and the one place it could
     * live is the session record, which would then have to be rewritten the
     * first time somebody uploaded a CV.
     *
     * <p>The parameter is {@link AnonymousSessionId} and not a String on
     * purpose. § 41.3 says the constant is worthless without a checked way to
     * produce one; {@code shared} cannot look at a session and check, so it
     * takes a value only the module that <em>can</em> check is able to make.
     */
    public static ProfileRef ephemeral(AnonymousSessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return new ProfileRef(
                UUID.nameUUIDFromBytes(sessionId.value().getBytes(StandardCharsets.UTF_8)),
                Scope.EPHEMERAL);
    }

    public UUID id() {
        return id;
    }

    public Scope scope() {
        return scope;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProfileRef ref && id.equals(ref.id) && scope == ref.scope;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, scope);
    }

    @Override
    public String toString() {
        return "ProfileRef[" + id + ", " + scope + "]";
    }
}
