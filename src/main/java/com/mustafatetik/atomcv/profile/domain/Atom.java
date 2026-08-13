package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.shared.security.ProfileOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hibernate.type.SqlTypes;

/**
 * The smallest independently selectable unit of the Master Profile: one bullet,
 * one skill, one certification.
 *
 * <p>An atom carries no text of its own. The text lives in its
 * {@link AtomVariant}s, one per language and tone, so that selection can reason
 * about "which fact" separately from "how it is worded".
 *
 * <p>{@code entryId} is null for an atom attached straight to a section, which
 * is how skill and language sections work.
 */
@Entity
@Table(name = "atoms")
public class Atom implements ProfileOwned {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID profileId;

    @Column(nullable = false)
    private UUID sectionId;

    /** Null when the atom hangs directly off the section. */
    private UUID entryId;

    @Convert(converter = AtomKind.JpaConverter.class)
    @Column(nullable = false)
    private AtomKind kind;

    @Column(nullable = false)
    private short displayOrder;

    // ─── user controls (Bolum 20) ───

    @Column(nullable = false)
    private float importance = 0.5f;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean alwaysInclude;

    @Column(nullable = false)
    private boolean verbatim;

    // ─── scoring inputs ───

    /** Canonical skill names, produced by normalization rather than typed in. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private String[] skills = new String[0];

    /** Numbers the atom claims; a rewrite that loses one is rejected. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private String[] metrics = new String[0];

    /** Names a rewrite may not invent or alter. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private String[] properNouns = new String[0];

    // The atoms.embedding column is deliberately unmapped: nothing computes an
    // embedding before Stage 2, and vector(1024) has no Hibernate type yet.

    /** {@code content_hash} of the English variant the embedding was built from. */
    private String embeddingHash;

    // ─── provenance ───

    @Convert(converter = AtomSource.JpaConverter.class)
    @Column(nullable = false)
    private AtomSource source = AtomSource.MANUAL;

    /** The user has confirmed the fact. Feeds the general-mode score. */
    @Column(nullable = false)
    private boolean verified;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Atom() {
        // JPA
    }

    public Atom(UUID profileId, UUID sectionId, UUID entryId, AtomKind kind, short displayOrder) {
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        this.sectionId = Objects.requireNonNull(sectionId, "sectionId");
        this.entryId = entryId;
        this.kind = Objects.requireNonNull(kind, "kind");
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

    public UUID getEntryId() {
        return entryId;
    }

    public void setEntryId(UUID entryId) {
        this.entryId = entryId;
    }

    public boolean isSectionLevel() {
        return entryId == null;
    }

    public AtomKind getKind() {
        return kind;
    }

    public void setKind(AtomKind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
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

    public List<String> getSkills() {
        return List.of(skills);
    }

    public void setSkills(List<String> skills) {
        this.skills = toArray(skills, "skills");
    }

    public List<String> getMetrics() {
        return List.of(metrics);
    }

    public void setMetrics(List<String> metrics) {
        this.metrics = toArray(metrics, "metrics");
    }

    public List<String> getProperNouns() {
        return List.of(properNouns);
    }

    public void setProperNouns(List<String> properNouns) {
        this.properNouns = toArray(properNouns, "properNouns");
    }

    public String getEmbeddingHash() {
        return embeddingHash;
    }

    public void setEmbeddingHash(String embeddingHash) {
        this.embeddingHash = embeddingHash;
    }

    public AtomSource getSource() {
        return source;
    }

    public void setSource(AtomSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    private static String[] toArray(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        return values.toArray(String[]::new);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Atom atom && id.equals(atom.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Identity and shape only. Skills and proper nouns are extracted from user
     * content and are treated as user content themselves (absolute rule 4).
     */
    @Override
    public String toString() {
        return "Atom[" + id + ", " + kind + ", skills=" + skills.length + "]";
    }
}
