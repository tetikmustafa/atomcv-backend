package com.mustafatetik.atomcv.shared.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Base for repositories over entities that hang off a profile rather than off
 * a user: sections, entries, atoms, atom variants.
 *
 * <p>Their tables carry {@code profile_id} and no {@code user_id}, so the
 * ownership check happens once, when the {@link ProfileRef} is resolved from
 * the acting user. Everything below that point scopes by profile — and since a
 * {@code ProfileRef} cannot be constructed without that check, scoping by
 * profile here is scoping by user.
 */
public abstract class ProfileScopedRepository<T extends ProfileOwned> {

    protected abstract JpaRepository<T, UUID> delegate();

    /** A row from another profile reads as absent, never as forbidden. */
    public Optional<T> findById(ProfileRef profile, UUID id) {
        requireProfile(profile);
        return delegate().findById(id).filter(row -> row.getProfileId().equals(profile.id()));
    }

    public boolean exists(ProfileRef profile, UUID id) {
        return findById(profile, id).isPresent();
    }

    public T save(ProfileRef profile, T entity) {
        requireSameProfile(profile, entity);
        return delegate().save(entity);
    }

    public void delete(ProfileRef profile, T entity) {
        requireSameProfile(profile, entity);
        delegate().delete(entity);
    }

    private void requireSameProfile(ProfileRef profile, T entity) {
        requireProfile(profile);
        if (entity == null) {
            throw new IllegalArgumentException("entity");
        }
        if (!entity.getProfileId().equals(profile.id())) {
            throw new CrossTenantAccessException("The row belongs to a different profile");
        }
    }

    private static void requireProfile(ProfileRef profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile");
        }
    }
}
