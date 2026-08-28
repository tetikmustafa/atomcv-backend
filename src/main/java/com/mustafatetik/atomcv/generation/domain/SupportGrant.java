package com.mustafatetik.atomcv.generation.domain;

import com.mustafatetik.atomcv.shared.security.UserOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Permission to read one person's CV, for two days (Bolum 48.4).
 *
 * <p>Everything else in this product is diagnosed from shapes — character
 * counts, run counts, render costs — because absolute rule 4 keeps content out
 * of the places an operator looks. This is the one door through it, and it
 * opens from the inside: a person who cannot get a good CV out of the product
 * ticks a box, and for forty-eight hours their document can be looked at.
 *
 * <p><strong>The audit trail is the point, not the paperwork.</strong>
 * {@code accessedAt} is written when somebody actually looks, and the person
 * can see it. A consent nobody can check the use of is not consent, it is a
 * checkbox.
 */
@Entity
@Table(name = "support_grants")
public class SupportGrant implements UserOwned {

    /** Bolum 13's forty-eight hours. */
    public static final Duration LIFETIME = Duration.ofHours(48);

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID generationId;

    @Column(nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    /** When the content was actually read, and null until it is. */
    private Instant accessedAt;

    /** When the person changed their mind, which they may do at any time. */
    private Instant revokedAt;

    protected SupportGrant() {
    }

    public SupportGrant(UUID userId, UUID generationId, Instant now) {
        this.userId = userId;
        this.generationId = generationId;
        this.grantedAt = now;
        this.expiresAt = now.plus(LIFETIME);
    }

    @Override
    public UUID getOwnerId() {
        return userId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGenerationId() {
        return generationId;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    /** Taken back. The row stays: withdrawing consent is part of its history. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    /**
     * Whether the content may be read right now.
     *
     * <p>Three ways to be closed and they are not the same event: never
     * granted, taken back, and run out. The row distinguishes them because the
     * person is shown which one happened.
     */
    public boolean isOpenAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    /** Shape only: whose CV this opens is not something to log. */
    @Override
    public String toString() {
        return "SupportGrant[generation=" + generationId
                + ", open=" + (revokedAt == null)
                + ", accessed=" + (accessedAt != null) + "]";
    }
}
