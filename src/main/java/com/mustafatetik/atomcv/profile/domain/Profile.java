package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.shared.security.UserOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
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

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** One line under the name. User content. */
    private String headline;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Contact contact = Contact.EMPTY;

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
        this.headline = headline;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact == null ? Contact.EMPTY : contact;
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
