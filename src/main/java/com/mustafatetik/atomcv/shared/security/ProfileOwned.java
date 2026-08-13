package com.mustafatetik.atomcv.shared.security;

import java.util.UUID;

/**
 * An entity that hangs off a profile rather than off a user: sections,
 * entries, atoms, atom variants. Their tables carry {@code profile_id} and no
 * {@code user_id}, so they are reached through {@link ProfileScopedRepository}
 * with a {@link ProfileRef} that was resolved from the acting user once.
 */
public interface ProfileOwned {

    UUID getProfileId();
}
