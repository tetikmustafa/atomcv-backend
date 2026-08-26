package com.mustafatetik.atomcv.identity.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * The sign-in providers (Bolum 40.6), and the exact strings the database
 * stores.
 *
 * <p>The wire value is written down rather than derived from {@code name()}:
 * {@code oauth_identities.provider} carries a CHECK over these two literals
 * (V2), and a rename of an enum constant must not silently produce a value the
 * column refuses.
 */
public enum OAuthProvider {

    GOOGLE("google"),
    GITHUB("github");

    private final String wireValue;

    OAuthProvider(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    /**
     * How a session made this way is recorded. Kept here so the two closed
     * vocabularies cannot fall out of step — a provider added without an
     * {@link AuthMethod} would not compile.
     */
    public AuthMethod authMethod() {
        return switch (this) {
            case GOOGLE -> AuthMethod.OAUTH_GOOGLE;
            case GITHUB -> AuthMethod.OAUTH_GITHUB;
        };
    }

    /**
     * Empty for anything not in the vocabulary — a path variable is client
     * input, and an unknown provider is a 404 rather than an exception.
     *
     * <p>{@code Locale.ROOT} (absolute rule 7): a Turkish default locale turns
     * {@code GITHUB} into {@code gıthub} and this lookup would fail on the
     * developer's own machine and nowhere else.
     */
    public static Optional<OAuthProvider> fromWire(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (OAuthProvider provider : values()) {
            if (provider.wireValue.equals(normalized)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
