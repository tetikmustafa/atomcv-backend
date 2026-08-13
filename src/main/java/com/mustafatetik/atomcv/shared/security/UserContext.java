package com.mustafatetik.atomcv.shared.security;

import java.util.Objects;
import java.util.UUID;

/**
 * Who is acting. Every scoped read and write takes one of these, so that
 * "whose data is this" is a parameter the compiler insists on rather than a
 * {@code WHERE} clause someone has to remember (Bolum 41.2).
 */
public record UserContext(UUID userId, UserRole role) {

    public UserContext {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
    }

    public static UserContext of(UUID userId) {
        return new UserContext(userId, UserRole.USER);
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
