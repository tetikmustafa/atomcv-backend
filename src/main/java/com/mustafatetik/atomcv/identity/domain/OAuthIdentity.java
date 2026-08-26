package com.mustafatetik.atomcv.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One provider account, bound to one of ours.
 *
 * <p>{@code UNIQUE (provider, provider_uid)} in V1 is what makes sign-in
 * deterministic: a provider's subject belongs to exactly one account here, and
 * the second attempt to bind it fails in the database rather than quietly
 * creating a duplicate.
 *
 * <p><strong>{@code access_token_enc} stays null.</strong> The column exists
 * for a future ingestion that reads a person's repositories; signing in needs
 * no token after the exchange, there is no key to encrypt one with, and a
 * token that is never stored is a token that cannot leak. It arrives with the
 * feature that needs it, and with the key management that has to come first.
 */
@Entity
@Table(name = "oauth_identities")
public class OAuthIdentity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /**
     * The wire value, not the enum name: the column carries a CHECK over
     * {@code 'google'} and {@code 'github'} (V2).
     */
    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(name = "provider_uid", nullable = false, updatable = false)
    private String providerUid;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant connectedAt;

    protected OAuthIdentity() {
    }

    public static OAuthIdentity binding(UUID userId, OAuthProvider provider, String providerUid) {
        var identity = new OAuthIdentity();
        identity.userId = userId;
        identity.provider = provider.wireValue();
        identity.providerUid = providerUid;
        return identity;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUid() {
        return providerUid;
    }
}
