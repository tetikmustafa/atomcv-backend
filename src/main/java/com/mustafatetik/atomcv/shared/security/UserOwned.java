package com.mustafatetik.atomcv.shared.security;

import java.util.UUID;

/**
 * An entity whose table carries {@code user_id}: the profile itself,
 * generations, applications. Reached through {@link UserScopedRepository}.
 */
public interface UserOwned {

    UUID getOwnerId();
}
