package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.shared.security.UserOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * The head of the Master Profile: one row per user (Bolum 14.2, 14.3).
 *
 * <p>The first {@link UserOwned} entity, and the reason the two scoped bases
 * exist: everything below a profile is reached with a {@code ProfileRef}, and a
 * {@code ProfileRef} can only be produced by checking this row's owner.
 */
@Entity
@Table(name = "profiles")
public class Profile implements UserOwned {

    @Id
    private UUID id = UUID.randomUUID();

    /**
     * Null for an anonymous session's profile (Bolum 9), which is the one kind
     * of profile nobody owns.
     *
     * <p><strong>Updatable, and only for one statement.</strong> It was
     * {@code updatable = false} while every profile had an owner from the
     * moment it existed. Signing up from an anonymous session is the case that
     * changes: the rows are already written and what happens is that they gain
     * an owner. The database refuses to let that happen halfway —
     * {@code profiles_owner_xor_expiry} means setting this must clear
     * {@link #expiresAt} in the same statement.
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * When this profile stops existing, or null for one that is kept
     * (Bolum 9, Bolum 57.4).
     *
     * <p>Exactly one of this and {@link #userId} is set, enforced by
     * {@code profiles_owner_xor_expiry}: a row with neither could not be
     * reached and would never be removed, and a row with both would be an
     * account's profile with a deletion date on it.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** One line under the name. User content. */
    private String headline;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Contact contact = Contact.EMPTY;

    /**
     * What this profile's header block measured, by geometry and language
     * (Bolum 26.4).
     *
     * <p>The header is text and text wraps, so how tall it is depends on the
     * words in it, on the width they are set at, and on the language its
     * contact labels are printed in. It was a single measured constant for
     * every profile in every template, calibrated for a name and two centred
     * lines, and a header that ran to three was charged for two.
     *
     * <p>Cleared when the header's own text changes, exactly as an atom
     * variant's costs are cleared when its wording does. An unmeasured header
     * falls back and is measured again; a stale one would be charged with
     * confidence.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "header_costs", nullable = false)
    private Map<String, Double> headerCosts = new LinkedHashMap<>();

    /** Free text the user writes about themselves. User content. */
    private String selfDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Preferences preferences = Preferences.DEFAULTS;

    /** The language the profile was authored in; English is the working language. */
    @Column(nullable = false)
    private String sourceLanguage = "en";

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private String[] enabledLanguages = {"en"};

    /** 0-100. Recomputed from the profile's contents, never set by a client. */
    @Column(nullable = false)
    private short completeness;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Profile() {
        // JPA
    }

    public Profile(UUID userId) {
        this.userId = Objects.requireNonNull(userId, "userId");
    }

    /**
     * A profile that already has an id (Adim 3.6).
     *
     * <p>The one caller is the upgrade from an anonymous session: the rows
     * below it were built while the profile lived in Redis and each carries
     * that id, so the row they belong to has to be written with it. Copying
     * them under a fresh id instead would mean a copier naming every field it
     * carried across, and the next field added to an atom would be dropped by
     * it without a word.
     */
    public Profile(UUID userId, UUID id) {
        this(userId);
        this.id = Objects.requireNonNull(id, "id");
    }

    /**
     * An anonymous session's profile: no owner, and an id derived from the
     * session rather than drawn at random (Bolum 9, Adim 3.6).
     *
     * <p>The id <em>is</em> {@code ProfileRef.ephemeral(session).id()}, which is
     * what makes this row reachable without an owner to scope by: holding the
     * session is the only way to compute the id, and the derivation is one-way.
     * A second identifier stored beside the session would be a second thing to
     * keep in step.
     *
     * @param id        the session-derived profile id
     * @param expiresAt when the session ends, and with it this
     */
    public static Profile forAnonymousSession(UUID id, Instant expiresAt) {
        var profile = new Profile();
        profile.id = Objects.requireNonNull(id, "id");
        profile.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        return profile;
    }

    /**
     * The one statement the XOR constraint is written for: this profile stops
     * expiring and starts belonging to somebody (Adim 3.6).
     *
     * @throws IllegalStateException if it already has an owner. Signing in
     *         twice from one session is not a second upgrade, and quietly
     *         reassigning a profile from one account to another is the shape of
     *         a much worse bug than a refusal.
     */
    public void adoptedBy(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        if (userId != null) {
            throw new IllegalStateException("This profile already belongs to somebody");
        }
        this.userId = owner;
        this.expiresAt = null;
    }

    /**
     * Pushes the expiry out to match a session that is still in use.
     *
     * @throws IllegalStateException if this profile has an owner. An account's
     *         profile has no expiry and the XOR constraint would refuse one, so
     *         failing here says which call was wrong instead of leaving the
     *         database to say that something was.
     */
    public void renewUntil(Instant when) {
        Objects.requireNonNull(when, "when");
        if (userId != null) {
            throw new IllegalStateException("An owned profile does not expire");
        }
        this.expiresAt = when;
    }

    /** Null for an account's profile; when the session ends for an anonymous one. */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** Whether this profile belongs to nobody and will be swept (Bolum 9). */
    public boolean isAnonymous() {
        return userId == null;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return userId;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        if (!Objects.equals(this.headline, headline)) {
            headerCosts = new LinkedHashMap<>();
        }
        this.headline = headline;
    }

    public Contact getContact() {
        return contact;
    }

    /**
     * Where a measured header is filed: the geometry it was set at and the
     * language its labels were printed in.
     *
     * <p>Here because this is the entity that holds the map, and a key format
     * written twice is a cost stored under one spelling and looked for under
     * another.
     */
    public static String headerKey(String costKey, String languageTag) {
        return Objects.requireNonNull(costKey, "costKey")
                + "|" + (languageTag == null || languageTag.isBlank() ? "en" : languageTag);
    }

    /** What the header measured, by geometry and language. Never null. */
    public Map<String, Double> getHeaderCosts() {
        return Map.copyOf(headerCosts);
    }

    /**
     * @param key what geometry and language this was measured at
     * @param costPt the height of the whole header block, in points
     */
    public void recordHeaderCost(String key, double costPt) {
        headerCosts.put(Objects.requireNonNull(key, "key"), costPt);
    }

    public void setContact(Contact contact) {
        Contact replacement = contact == null ? Contact.EMPTY : contact;
        if (!replacement.equals(this.contact)) {
            headerCosts = new LinkedHashMap<>();
        }
        this.contact = replacement;
    }

    public String getSelfDescription() {
        return selfDescription;
    }

    public void setSelfDescription(String selfDescription) {
        this.selfDescription = selfDescription;
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences == null ? Preferences.DEFAULTS : preferences;
    }

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        Objects.requireNonNull(sourceLanguage, "sourceLanguage");
        if (sourceLanguage.isBlank()) {
            throw new IllegalArgumentException("Source language must not be blank");
        }
        this.sourceLanguage = sourceLanguage;
    }

    public List<String> getEnabledLanguages() {
        return List.of(enabledLanguages);
    }

    public void setEnabledLanguages(List<String> languages) {
        Objects.requireNonNull(languages, "languages");
        if (languages.isEmpty()) {
            throw new IllegalArgumentException("A profile needs at least one enabled language");
        }
        this.enabledLanguages = languages.toArray(String[]::new);
    }

    public short getCompleteness() {
        return completeness;
    }

    public void setCompleteness(short completeness) {
        if (completeness < 0 || completeness > 100) {
            throw new IllegalArgumentException(
                    "Completeness is a percentage, was " + completeness);
        }
        this.completeness = completeness;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Profile profile && id.equals(profile.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Identity only. Headline, contact and self-description are user content. */
    @Override
    public String toString() {
        return "Profile[" + id + "]";
    }
}
