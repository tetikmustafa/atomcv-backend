package com.mustafatetik.atomcv.identity.domain;

import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.security.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * The account itself — the row every profile, generation and quota counter
 * hangs off.
 *
 * <p>It arrives late on purpose. Stage 1 needed a {@code user_id} and had a
 * fixed one; nothing until sign-in needed to <em>read</em> the row, and an
 * entity written before anything read it would have been a guess at what it
 * should carry.
 *
 * <p>Not a {@code UserOwned}: this table has no {@code user_id} because it
 * <em>is</em> the user, so neither scoped base applies (absolute rule 3 covers
 * tables carrying {@code user_id} or {@code profile_id}). What replaces the
 * ownership check is the narrowness of {@code identity.repository}'s facade —
 * see it for why sign-in is the one flow that reads without an acting user.
 */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id = UUID.randomUUID();

    /**
     * {@code CITEXT}, so two addresses differing only in case are one account
     * — and the uniqueness that makes linking safe is the database's, not a
     * lowercase call someone has to remember (absolute rule 7 would make that
     * call locale-dependent anyway).
     */
    @Column(nullable = false, updatable = false, columnDefinition = "citext")
    private String email;

    private String displayName;

    @Column(nullable = false)
    private String locale = "tr";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private boolean emailVerified;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant lastSeenAt;

    /**
     * Whether the optional emails of Bolum 57.7 may be sent. True until the
     * person says otherwise; the deletion confirmation does not read it.
     */
    @Column(name = "lifecycle_emails", nullable = false)
    private boolean lifecycleEmails = true;

    /**
     * How {@link #lifecycleEmails} is reached from an inbox, where there is no
     * session to act under. Opaque, random and long-lived: the email it
     * travels in does not expire either.
     */
    @Column(name = "unsubscribe_token", nullable = false, updatable = false)
    private UUID unsubscribeToken = UUID.randomUUID();

    /**
     * Soft delete. A deleted account may not sign in, and its address stays
     * taken — reissuing it to a new sign-up would hand the next person
     * whatever still references the old row.
     */
    private Instant deletedAt;

    protected UserAccount() {
    }

    private UserAccount(String email, String displayName, boolean emailVerified) {
        this.email = email;
        this.displayName = displayName;
        this.emailVerified = emailVerified;
    }

    /** A first sign-in. The provider vouched for the address or we would not be here. */
    public static UserAccount signingUp(String email, String displayName) {
        return new UserAccount(email, displayName, true);
    }

    /**
     * A magic link was asked for at an address nobody has claimed. Nothing is
     * proved yet — opening the link is what proves it — so the row exists
     * unverified until then.
     */
    public static UserAccount awaitingVerification(String email) {
        return new UserAccount(email, null, false);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** Which language the emails this account receives are written in. */
    public String getLocale() {
        return locale;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public boolean wantsLifecycleEmails() {
        return lifecycleEmails;
    }

    public void setLifecycleEmails(boolean wanted) {
        this.lifecycleEmails = wanted;
    }

    public UUID getUnsubscribeToken() {
        return unsubscribeToken;
    }

    /** Whether nobody has ever signed in to this account — Bolum 57.7's trigger. */
    public boolean hasNeverSignedIn() {
        return lastSeenAt == null;
    }

    public void seenAt(Instant now) {
        this.lastSeenAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UserContext asUserContext() {
        return new UserContext(id, role);
    }
}
