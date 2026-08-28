package com.mustafatetik.atomcv.generation.domain;

import com.mustafatetik.atomcv.shared.security.UserOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * What somebody thought of one generation (Bolum 13, Adim 3.9).
 *
 * <p>A thumb, and everything after it is optional. The pair of buttons is the
 * only part most people will touch, and a form that demanded a category before
 * it accepted the verdict would collect fewer verdicts and worse ones.
 *
 * <p><strong>One per person per generation.</strong> Pressing the other thumb
 * is changing your mind rather than adding a second opinion, and a rate that
 * counted both would be measuring clicks. The unique index arrived in V4.
 */
@Entity
@Table(name = "generation_feedback")
public class GenerationFeedback implements UserOwned {

    /** Bolum 13's column: the whole vocabulary, and it is a closed one. */
    public enum Category {
        SELECTION, WRITING, FORMAT, DENSITY, OTHER
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID generationId;

    /** Null for an anonymous generation, which nothing writes yet. */
    private UUID userId;

    /** {@code 1} or {@code -1}, and the column has the check to prove it. */
    @Column(nullable = false)
    private short rating;

    private String category;

    /**
     * Free text the person wrote.
     *
     * <p><strong>Never logged</strong> (absolute rule 4). It is stored because
     * they wrote it for us to read, which is not the same as it appearing in a
     * diagnostic.
     */
    private String comment;

    /**
     * Bolum 48.4: whether they agreed to the content being looked at.
     *
     * <p>The flag lives here so the verdict and the consent are one row — the
     * grant that carries the expiry and the audit trail is written next to it
     * in {@code support_grants}.
     */
    @Column(name = "content_granted", nullable = false)
    private boolean contentGranted;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GenerationFeedback() {
    }

    public GenerationFeedback(UUID userId, UUID generationId, short rating, Instant now) {
        this.userId = userId;
        this.generationId = generationId;
        this.rating = rating;
        this.createdAt = now;
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

    public short getRating() {
        return rating;
    }

    public void setRating(short rating) {
        this.rating = rating;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isContentGranted() {
        return contentGranted;
    }

    public void setContentGranted(boolean contentGranted) {
        this.contentGranted = contentGranted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Shape only: the comment is the user's own writing and the rating is
     * theirs to give, not something to find in a log line.
     */
    @Override
    public String toString() {
        return "GenerationFeedback[generation=" + generationId
                + ", rated=" + (rating > 0 ? "up" : "down")
                + ", category=" + (category == null ? "none" : category)
                + ", comment=" + (comment == null || comment.isBlank() ? "none" : "set")
                + ", granted=" + contentGranted + "]";
    }
}
