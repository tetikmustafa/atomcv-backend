package com.mustafatetik.atomcv.shared.security;

/**
 * The role structure is deliberately small. What matters is resource
 * ownership, and that is settled in the repository layer rather than by a
 * role.
 *
 * <p>Stored uppercase in {@code users.role}, where a CHECK constraint holds the
 * same two values, so this one maps straight through {@code EnumType.STRING}.
 */
public enum UserRole {
    USER,
    ADMIN
}
