package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.shared.security.ProfileOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One position, degree or project inside a section: the row that carries a
 * title, an organization and a date range, and owns a group of atoms.
 *
 * <p>{@code profileId} is denormalized so that every user-scoped query stays a
 * flat one. A composite foreign key binds it to the parent section's profile,
 * so the two cannot disagree (Bolum 13).
 */
@Entity
@Table(name = "entries")
public class Entry implements ProfileOwned {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID profileId;

    @Column(nullable = false)
    private UUID sectionId;

    /** User content. */
    @Column(nullable = false)
    private String title;

    /** User content. */
    private String organization;

    /** User content. */
    private String location;

    private LocalDate startDate;

    /** Null means ongoing. */
    private LocalDate endDate;

    private String url;

    @Column(nullable = false)
    private short displayOrder;

    /** Between 0 and 1, enforced by a CHECK constraint as well. */
    @Column(nullable = false)
    private float importance = 0.5f;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean alwaysInclude;

    @Column(nullable = false)
    private boolean verbatim;

    /**
     * What an entry asks for when nobody says otherwise, and what the column
     * defaults to. An importer writing this over an entry that owns fewer
     * atoms is asking for that entry to be dropped, which is why the ingestion
     * side clamps it rather than taking it as given.
     */
    public static final short DEFAULT_MIN_ATOMS = 2;

    /**
     * Below this many atoms the entry is not worth printing at all: selection
     * either keeps this many or drops the entry whole (Bolum 20).
     */
    @Column(nullable = false)
    private short minAtoms = DEFAULT_MIN_ATOMS;

    /**
     * Measured height of the entry's own furniture in points, keyed by
     * {@code template:version} (Bolum 26). Empty until measured.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Double> renderCosts = new LinkedHashMap<>();

    @Version
    private Long version;

    protected Entry() {
        // JPA
    }

    public Entry(UUID profileId, UUID sectionId, String title, short displayOrder) {
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId");
        this.title = Objects.requireNonNull(title, "title");
        this.displayOrder = displayOrder;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getProfileId() {
        return profileId;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public void setSectionId(UUID sectionId) {
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId");
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public boolean isOngoing() {
        return endDate == null;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(short displayOrder) {
        this.displayOrder = displayOrder;
    }

    public float getImportance() {
        return importance;
    }

    public void setImportance(float importance) {
        if (importance < 0f || importance > 1f) {
            throw new IllegalArgumentException("Importance must be between 0 and 1, was " + importance);
        }
        this.importance = importance;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAlwaysInclude() {
        return alwaysInclude;
    }

    public void setAlwaysInclude(boolean alwaysInclude) {
        this.alwaysInclude = alwaysInclude;
    }

    public boolean isVerbatim() {
        return verbatim;
    }

    public void setVerbatim(boolean verbatim) {
        this.verbatim = verbatim;
    }

    public short getMinAtoms() {
        return minAtoms;
    }

    public void setMinAtoms(short minAtoms) {
        if (minAtoms < 0) {
            throw new IllegalArgumentException("minAtoms must not be negative, was " + minAtoms);
        }
        this.minAtoms = minAtoms;
    }

    public Map<String, Double> getRenderCosts() {
        return Map.copyOf(renderCosts);
    }

    public void setRenderCost(String templateKey, double costPt) {
        renderCosts.put(Objects.requireNonNull(templateKey, "templateKey"), costPt);
    }

    public void clearRenderCosts() {
        renderCosts = new LinkedHashMap<>();
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Entry entry && id.equals(entry.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Identity only. Title, organization and location are user content. */
    @Override
    public String toString() {
        return "Entry[" + id + "]";
    }
}
