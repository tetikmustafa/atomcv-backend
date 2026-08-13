package com.mustafatetik.atomcv.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;
import java.util.UUID;

/**
 * A heading in the Master Profile: Experience, Education, a custom section.
 *
 * <p>No JPA association points at the parent profile or at the child entries.
 * A profile is loaded with four flat queries and assembled in memory
 * (Bolum XI-A.3), which is what keeps the load inside its six-query budget;
 * lazy collections would defeat that silently.
 */
@Entity
@Table(name = "sections")
public class Section {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID profileId;

    @Convert(converter = SectionKind.JpaConverter.class)
    @Column(nullable = false)
    private SectionKind kind;

    /** The heading as rendered. User content. */
    @Column(nullable = false)
    private String title;

    @Convert(converter = SectionLayout.JpaConverter.class)
    @Column(nullable = false)
    private SectionLayout layout = SectionLayout.BULLET_LIST;

    @Column(nullable = false)
    private short displayOrder;

    /** Selection may not drop this section, whatever the budget says. */
    @Column(nullable = false)
    private boolean alwaysInclude;

    /** Content is rendered as written; no rewriting phase touches it. */
    @Column(nullable = false)
    private boolean verbatim;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    protected Section() {
        // JPA
    }

    public Section(UUID profileId, SectionKind kind, String title, short displayOrder) {
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.title = Objects.requireNonNull(title, "title");
        this.displayOrder = displayOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public SectionKind getKind() {
        return kind;
    }

    public void setKind(SectionKind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    public SectionLayout getLayout() {
        return layout;
    }

    public void setLayout(SectionLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(short displayOrder) {
        this.displayOrder = displayOrder;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Section section && id.equals(section.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Identity only. The title is user content (absolute rule 4). */
    @Override
    public String toString() {
        return "Section[" + id + ", " + kind + "]";
    }
}
