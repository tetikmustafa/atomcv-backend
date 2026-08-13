package com.mustafatetik.atomcv.profile.domain;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.RichContentConverter;
import com.mustafatetik.atomcv.shared.security.ProfileOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One wording of an atom: a language, an optional tone, and the content itself.
 *
 * <p>{@code plainText} and {@code contentHash} are derived, never set from
 * outside. Everything downstream keys off the hash — the embedding, the
 * measured render cost, the staleness of derived variants — so a hash that
 * disagreed with the content would break the page guarantee silently.
 */
@Entity
@Table(name = "atom_variants")
public class AtomVariant implements ProfileOwned {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID profileId;

    @Column(nullable = false, updatable = false)
    private UUID atomId;

    /** The wording used when nothing more specific is asked for. */
    @Column(nullable = false)
    private boolean isPrimary;

    @Column(nullable = false)
    private String language = "en";

    /** Null is the neutral tone. */
    @Convert(converter = Tone.JpaConverter.class)
    private Tone tone;

    @Convert(converter = RichContentConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private RichContent content;

    @Column(nullable = false)
    private String plainText;

    @Column(nullable = false)
    private String contentHash;

    /**
     * Measured height in points, keyed by {@code template:version}
     * (Bolum 26). Cleared whenever the content changes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Double> renderCosts = new LinkedHashMap<>();

    private Instant costMeasuredAt;

    // ─── derived variant tracking (Bolum 32) ───

    /** The variant this one was translated or rewritten from. */
    private UUID derivedFromVariantId;

    /** {@code contentHash} the source carried at derivation time. */
    private String sourceHash;

    /** The source has moved on since; this wording needs regenerating. */
    @Column(nullable = false)
    private boolean isStale;

    /** The user edited this wording. Nothing regenerates it silently (P8). */
    @Column(nullable = false)
    private boolean isUserEdited;

    @Convert(converter = VariantAuthor.JpaConverter.class)
    @Column(nullable = false)
    private VariantAuthor createdBy = VariantAuthor.USER;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected AtomVariant() {
        // JPA
    }

    public AtomVariant(UUID profileId, UUID atomId, String language, RichContent content) {
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        this.atomId = Objects.requireNonNull(atomId, "atomId");
        setLanguage(language);
        applyContent(Objects.requireNonNull(content, "content"));
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getProfileId() {
        return profileId;
    }

    public UUID getAtomId() {
        return atomId;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        this.isPrimary = primary;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        Objects.requireNonNull(language, "language");
        if (language.isBlank()) {
            throw new IllegalArgumentException("Language must not be blank");
        }
        this.language = language;
    }

    public Tone getTone() {
        return tone;
    }

    public void setTone(Tone tone) {
        this.tone = tone;
    }

    public RichContent getContent() {
        return content;
    }

    /**
     * Replaces the wording and re-derives everything that depends on it. A
     * changed hash invalidates the measured costs: the same words in the same
     * template occupy the same height, different words do not (Bolum 16.3).
     */
    public void setContent(RichContent content) {
        applyContent(Objects.requireNonNull(content, "content"));
    }

    private void applyContent(RichContent replacement) {
        String hash = replacement.contentHash();
        if (!hash.equals(contentHash)) {
            renderCosts = new LinkedHashMap<>();
            costMeasuredAt = null;
        }
        this.content = replacement;
        this.plainText = replacement.plainText();
        this.contentHash = hash;
    }

    public String getPlainText() {
        return plainText;
    }

    public String getContentHash() {
        return contentHash;
    }

    public Map<String, Double> getRenderCosts() {
        return Map.copyOf(renderCosts);
    }

    public void recordRenderCost(String templateKey, double costPt, Instant measuredAt) {
        renderCosts.put(Objects.requireNonNull(templateKey, "templateKey"), costPt);
        this.costMeasuredAt = Objects.requireNonNull(measuredAt, "measuredAt");
    }

    public Instant getCostMeasuredAt() {
        return costMeasuredAt;
    }

    public UUID getDerivedFromVariantId() {
        return derivedFromVariantId;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public void markDerivedFrom(AtomVariant source) {
        Objects.requireNonNull(source, "source");
        this.derivedFromVariantId = source.getId();
        this.sourceHash = source.getContentHash();
        this.isStale = false;
    }

    public boolean isStale() {
        return isStale;
    }

    public void setStale(boolean stale) {
        this.isStale = stale;
    }

    public boolean isUserEdited() {
        return isUserEdited;
    }

    public void setUserEdited(boolean userEdited) {
        this.isUserEdited = userEdited;
    }

    public VariantAuthor getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(VariantAuthor createdBy) {
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AtomVariant variant && id.equals(variant.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * Identity and shape only. The content hash is safe to print — it is a
     * one-way digest — and it is what a support conversation actually needs.
     */
    @Override
    public String toString() {
        return "AtomVariant[" + id + ", " + language + ", chars=" + plainText.length()
                + ", hash=" + contentHash.substring(0, 8) + "]";
    }
}
