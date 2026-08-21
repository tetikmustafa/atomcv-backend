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
import org.hibernate.annotations.Array;
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

    /** BGE-M3's dense output, and what {@code vector(1024)} declares. */
    public static final int EMBEDDING_DIMENSIONS = 1024;

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

    /**
     * BGE-M3's dense vector for the English variant (Bolum 28).
     *
     * <p>Null until something has embedded it, which is not the same as an
     * atom with no content: Bolum 28.2 computes these on a queue after the
     * fact, so a freshly written atom is scoreable before it is embeddable.
     *
     * <p>{@code SqlTypes.VECTOR} comes from {@code hibernate-vector}.
     * {@code @Array(length)} feeds DDL generation only — schema validation
     * does <em>not</em> compare it against {@code vector(1024)}, which was
     * measured by setting it to 512 and watching validation pass. The
     * dimension is therefore held by {@link #setEmbedding} and by a round trip
     * against a real database, not by Hibernate.
     */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSIONS)
    private float[] embedding;

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

    /**
     * The stored vector, or null when nothing has embedded this atom yet.
     *
     * <p>Copied on the way out: the array is mutable and Hibernate hands back
     * the field itself, so a caller that reordered it would rewrite the row on
     * the next flush without ever meaning to.
     */
    public float[] getEmbedding() {
        return embedding == null ? null : embedding.clone();
    }

    /**
     * @param embedding the vector, or null to mark the atom unembedded again
     * @param sourceContentHash the {@code content_hash} it was computed from,
     *                          which is what Bolum 28.2 compares to decide
     *                          whether this is still current
     */
    public void setEmbedding(float[] embedding, String sourceContentHash) {
        if (embedding != null && embedding.length != EMBEDDING_DIMENSIONS) {
            throw new IllegalArgumentException(
                    "The column is vector(" + EMBEDDING_DIMENSIONS + "), got "
                            + embedding.length);
        }
        this.embedding = embedding == null ? null : embedding.clone();
        this.embeddingHash = embedding == null ? null : sourceContentHash;
    }

    /**
     * Whether the stored vector still describes the given English variant
     * (Bolum 28.2).
     *
     * <p>Compared by content hash rather than by timestamp: an edit that put
     * the text back the way it was leaves the hash unchanged, and re-embedding
     * that is work bought for nothing.
     */
    public boolean needsEmbedding(String englishContentHash) {
        return englishContentHash != null
                && (embedding == null || !englishContentHash.equals(embeddingHash));
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
