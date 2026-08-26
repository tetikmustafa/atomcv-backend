package com.mustafatetik.atomcv.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One sign-in link, split in two (Bolum 40.2).
 *
 * <p><strong>The split is a timing defence.</strong> Looking a token up by the
 * whole secret means comparing it against every row until one matches, and how
 * long that takes depends on the secret — a difference an attacker can measure
 * and walk. The selector is a public handle with its own unique index, so the
 * row is found in one indexed lookup; the verifier is then compared in
 * constant time against a hash, and the hash is what makes a stolen database
 * dump useless for signing in.
 *
 * <p>{@code created_ip} is deliberately not mapped. It is an {@code INET}, and
 * Hibernate would need a type contribution to validate it; nothing reads the
 * column yet, and the rate limiting of Bolum 40.5 counts in Redis rather than
 * here.
 */
@Entity
@Table(name = "magic_link_tokens")
public class MagicLinkToken {

    @Id
    private UUID id = UUID.randomUUID();

    /** Public: it travels in the URL and is only a way to find the row. */
    @Column(nullable = false, updatable = false, unique = true)
    private String selector;

    /** SHA-256 of the verifier. The verifier itself exists only in the email. */
    @Column(name = "verifier_hash", nullable = false, updatable = false)
    private String verifierHash;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Set once, by the conditional update that makes redemption single-use. */
    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MagicLinkToken() {
    }

    public static MagicLinkToken issued(
            String selector, String verifierHash, UUID userId, Instant expiresAt) {
        var token = new MagicLinkToken();
        token.selector = selector;
        token.verifierHash = verifierHash;
        token.userId = userId;
        token.expiresAt = expiresAt;
        return token;
    }

    public UUID getId() {
        return id;
    }

    public String getVerifierHash() {
        return verifierHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
