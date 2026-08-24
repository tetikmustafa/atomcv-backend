package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.shared.security.ProfileOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One label in a profile's own vocabulary (Bolum 13, Bolum 19.2).
 *
 * <p>Tags belong to a profile rather than to the installation: two users may
 * both write "payments" and mean different things, and a shared dictionary
 * would let one person's spelling decide the other's scoring.
 *
 * <p>The label is stored canonical — trimmed and lowercase — because that is
 * the form Faz B compares against the posting's, and canonicalising at the
 * comparison would mean the scorer lowercased text in four places instead of
 * one. {@code Locale.ROOT} is absolute rule 7: a Turkish default locale writes
 * "sqı" for "SQL" and the tag would never match again.
 */
@Entity
@Table(name = "tags")
public class Tag implements ProfileOwned {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID profileId;

    @Column(nullable = false)
    private String label;

    protected Tag() {
        // JPA
    }

    public Tag(UUID profileId, String label) {
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        setLabel(label);
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getProfileId() {
        return profileId;
    }

    public String getLabel() {
        return label;
    }

    public final void setLabel(String label) {
        this.label = canonical(label);
    }

    /** The form the column holds and the form Faz B matches on. */
    public static String canonical(String label) {
        Objects.requireNonNull(label, "label");
        String canonical = label.strip().toLowerCase(Locale.ROOT);
        if (canonical.isEmpty()) {
            throw new IllegalArgumentException("A tag has a label");
        }
        return canonical;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Tag tag && id.equals(tag.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Identity and shape only. A label is a word the user chose and is treated
     * as their content (absolute rule 4).
     */
    @Override
    public String toString() {
        return "Tag[" + id + ", length=" + label.length() + "]";
    }
}
