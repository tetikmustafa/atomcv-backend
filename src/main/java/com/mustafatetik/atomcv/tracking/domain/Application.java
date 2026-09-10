package com.mustafatetik.atomcv.tracking.domain;

import com.mustafatetik.atomcv.shared.security.UserOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One job somebody applied to (Bolum 13, Bolum 55's tracking).
 *
 * <p>The table is in {@code V1}; this is the mapping, and there is no
 * migration (absolute rule 2). It was written before anything used it, which
 * is why every column this needs is already there.
 *
 * <p><strong>The generation is a link and not a part.</strong>
 * {@code ON DELETE SET NULL}: deleting a CV must not delete the record of
 * having applied with it, because the application is the thing a person is
 * keeping. A row whose generation is gone still says where they applied and
 * what happened.
 *
 * <p><strong>No PDF here, and none needed.</strong> Bolum 55 pairs tracking
 * with archiving and the archive is an R2 question this does not wait on: a
 * generation re-renders from its own {@code content_snapshot}, which
 * EK D.6.3 already describes as always possible. What a person wants back is
 * the document they sent, and that is reachable through the link above.
 *
 * <p>{@code @Version}, because this is the one resource a person edits in
 * place. Bolum 35.6's {@code If-Match} is enforced at the endpoint and this is
 * the number it compares against.
 */
@Entity
@Table(name = "applications")
public class Application implements UserOwned {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private UUID userId;

    /** Null once the CV it was sent with has been deleted. */
    private UUID generationId;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String position;

    @Convert(converter = ApplicationStatus.JpaConverter.class)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    /**
     * A date and not a timestamp: "when did I apply" is a day, and the column
     * has said so since V1. A person in another timezone recording yesterday
     * is not recording an instant.
     */
    @Column(name = "applied_at", nullable = false)
    private LocalDate appliedAt = LocalDate.now();

    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Application() {
        // JPA
    }

    public Application(UUID userId, String company, String position) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.company = Objects.requireNonNull(company, "company");
        this.position = Objects.requireNonNull(position, "position");
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getOwnerId() {
        return userId;
    }

    public UUID getGenerationId() {
        return generationId;
    }

    public void setGenerationId(UUID generationId) {
        this.generationId = generationId;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = Objects.requireNonNull(company, "company");
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = Objects.requireNonNull(position, "position");
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public LocalDate getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDate appliedAt) {
        this.appliedAt = Objects.requireNonNull(appliedAt, "appliedAt");
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Shape only. A company name and a person's own notes about applying to it
     * are theirs, and this is the kind of row a support read would print
     * (absolute rule 4).
     */
    @Override
    public String toString() {
        return "Application[" + id + ", status=" + status
                + ", notes=" + (notes == null ? "none" : "set") + "]";
    }
}
