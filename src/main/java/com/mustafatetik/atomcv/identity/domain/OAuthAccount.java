package com.mustafatetik.atomcv.identity.domain;

import java.util.Objects;

/**
 * What a provider says about the person who just signed in.
 *
 * <p>Deliberately small. An adapter returns identity and nothing else — no
 * access token, no avatar, no profile payload — because the only question
 * being asked is "who is this", and everything else would be data we hold
 * without needing it.
 *
 * @param providerUid   the provider's own stable identifier. <strong>Not the
 *                      email</strong>: an email can be changed and reassigned,
 *                      and keying identity on it would hand an account to
 *                      whoever inherits the address
 * @param email         used to find an existing account, never to prove one
 * @param emailVerified whether the <em>provider</em> asserts the address is
 *                      verified. Linking turns on this and nothing else: an
 *                      unverified address a stranger added to their own
 *                      provider account is an account takeover waiting to
 *                      happen
 * @param displayName   may be null; providers are inconsistent about it
 */
public record OAuthAccount(
        OAuthProvider provider,
        String providerUid,
        String email,
        boolean emailVerified,
        String displayName) {

    public OAuthAccount {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerUid, "providerUid");
        Objects.requireNonNull(email, "email");
        if (providerUid.isBlank()) {
            throw new IllegalArgumentException("providerUid must not be blank");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }
}
