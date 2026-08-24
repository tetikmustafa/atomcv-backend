package com.mustafatetik.atomcv.shared.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Base for repositories over entities whose table carries {@code user_id}
 * (Bolum 41.2).
 *
 * <p>IDOR is the risk this exists for: a user changing an identifier in a URL
 * to reach someone else's row. The defense is structural rather than a rule to
 * remember — the ownership check lives here, and the delegate stays
 * {@code protected} so no caller can route around it.
 *
 * <p>Subclasses add their own finders on top of a Spring Data interface that
 * already narrows by owner, for example
 * {@code findByUserIdOrderByCreatedAtDesc}. Whatever the query, the owner
 * belongs in it.
 */
public abstract class UserScopedRepository<T extends UserOwned> {

    protected abstract JpaRepository<T, UUID> delegate();

    /**
     * A row belonging to someone else reads as absent, never as forbidden.
     *
     * <p>The comparison is written owner-side-second on purpose. A
     * {@code jobs} row for an anonymous request carries no {@code user_id} at
     * all, and asking a null owner whether it equals anyone would throw where
     * the honest answer is "this is not yours".
     */
    public Optional<T> findById(UserContext user, UUID id) {
        requireUser(user);
        return delegate().findById(id).filter(row -> user.userId().equals(row.getOwnerId()));
    }

    public boolean exists(UserContext user, UUID id) {
        return findById(user, id).isPresent();
    }

    public T save(UserContext user, T entity) {
        requireOwnership(user, entity);
        return delegate().save(entity);
    }

    public void delete(UserContext user, T entity) {
        requireOwnership(user, entity);
        delegate().delete(entity);
    }

    private void requireOwnership(UserContext user, T entity) {
        requireUser(user);
        if (entity == null) {
            throw new IllegalArgumentException("entity");
        }
        if (!user.userId().equals(entity.getOwnerId())) {
            // Includes an unowned row: an anonymous job may not be written
            // through the user-scoped path, and saying so beats an NPE.
            throw new CrossTenantAccessException("The row belongs to a different user");
        }
    }

    private static void requireUser(UserContext user) {
        if (user == null) {
            throw new IllegalArgumentException("user");
        }
    }
}
