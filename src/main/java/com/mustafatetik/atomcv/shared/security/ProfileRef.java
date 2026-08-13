package com.mustafatetik.atomcv.shared.security;

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
        PERSISTENT
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
