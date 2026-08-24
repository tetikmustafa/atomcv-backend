package com.mustafatetik.atomcv.generation.domain;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.validation.FitReport;
import com.mustafatetik.atomcv.shared.security.UserOwned;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One CV that was produced, and the record of why it looks that way
 * (Bolum 14.4-14.7).
 *
 * <p>The table is in {@code V1}; this is the mapping, and there is no
 * migration (absolute rule 2).
 *
 * <p><strong>A row exists only when a document does.</strong>
 * {@code selection_state} is {@code NOT NULL} and it is the whole point of the
 * row — a run that failed before selection has nothing to describe, and its
 * failure lives on the job. That is also why {@link GenerationStatus} has no
 * queued or running: while the work is in flight the job is the thing to look
 * at, and two state machines over one piece of work drift without ever saying
 * so.
 *
 * <p><strong>{@code pdfKey} stays null in Stage 2.</strong> Nothing stores the
 * bytes; a download re-renders from {@link #contentSnapshot}, which EK D.6.3
 * already describes as always possible. R2 and the fourteen-day expiry arrive
 * together in Stage 3, and until they do a {@code pdf_expires_at} would be a
 * promise nothing keeps.
 *
 * <p>Two snapshots and they answer different questions. {@code selectionState}
 * says <em>why</em> the page looks like this — scores, rejections, the budget —
 * and is what an edit later applies to. {@code contentSnapshot} says
 * <em>what</em> was printed, and exists because the first one names atoms by id
 * while the text under those ids goes on being edited.
 *
 * <p>No {@code @Version}: the column does not exist, and nothing updates a
 * generation concurrently — Faz G writes a <em>new</em> row and marks this one
 * superseded (Bolum 24).
 */
@Entity
@Table(name = "generations")
public class Generation implements UserOwned {

    @Id
    private UUID id = UUID.randomUUID();

    /** Null for an anonymous generation (Stage 3). */
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID profileId;

    /**
     * The posting as it was pasted, or null in general CV mode.
     *
     * <p>Stored, not logged. It is the user's own data in their own row and
     * Faz G re-runs against it; absolute rule 4 is about diagnostics leaving
     * the system, not about the record the user came for.
     */
    private String jobDescription;

    /** The same hash the analysis cache keys on, so the two agree. */
    private String jdHash;

    @JdbcTypeCode(SqlTypes.JSON)
    private JobAnalysis jdAnalysis;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> directives;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> options = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private StoredSelection selectionState;

    /** The words that were printed, so a download does not re-read the profile. */
    @JdbcTypeCode(SqlTypes.JSON)
    private RenderedContent contentSnapshot;

    private String coverLetter;

    /**
     * Faz F's coverage counts (Bolum 23.3).
     *
     * <p>Null for a general-mode generation, and that is the honest value:
     * there was no posting to be relevant to, so every count would be zero and
     * a level would be a verdict about nothing.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private FitReport fitReport;

    private Short pageCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private EngineVersion engineVersion;

    /** Per-phase telemetry, no PII (Bolum 14.6). */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> trace;

    @Convert(converter = GenerationStatus.JpaConverter.class)
    @Column(nullable = false)
    private GenerationStatus status = GenerationStatus.COMPLETED;

    private UUID parentGenerationId;

    @Column(nullable = false)
    private boolean archived;

    /** Null in Stage 2: nothing stores the bytes yet. */
    private String pdfKey;

    private Instant pdfExpiresAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Generation() {
        // JPA
    }

    /**
     * @param userId null for an anonymous generation
     */
    public Generation(UUID userId, UUID profileId, Map<String, Object> options,
            StoredSelection selectionState, EngineVersion engineVersion) {

        this.userId = userId;
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        this.options = ordered(Objects.requireNonNull(options, "options"));
        this.selectionState = Objects.requireNonNull(selectionState, "selectionState");
        this.engineVersion = Objects.requireNonNull(engineVersion, "engineVersion");
    }

    public UUID getId() {
        return id;
    }

    /** Null for an anonymous generation, which no scoped read may then return. */
    @Override
    public UUID getOwnerId() {
        return userId;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public String getJobDescription() {
        return jobDescription;
    }

    public String getJdHash() {
        return jdHash;
    }

    public JobAnalysis getJdAnalysis() {
        return jdAnalysis;
    }

    /**
     * Records the posting and what Faz A made of it.
     *
     * <p>Set together, because a hash without the text it came from cannot be
     * checked and an analysis without the hash cannot be matched to a cache
     * entry.
     */
    public void recordPosting(String jobDescription, String jdHash, JobAnalysis analysis) {
        this.jobDescription = jobDescription;
        this.jdHash = jdHash;
        this.jdAnalysis = analysis;
    }

    public Map<String, Object> getDirectives() {
        return directives == null ? null : ordered(directives);
    }

    public void setDirectives(Map<String, Object> directives) {
        this.directives = directives == null ? null : ordered(directives);
    }

    public Map<String, Object> getOptions() {
        return ordered(options);
    }

    public StoredSelection getSelectionState() {
        return selectionState;
    }

    public RenderedContent getContentSnapshot() {
        return contentSnapshot;
    }

    public void setContentSnapshot(RenderedContent contentSnapshot) {
        this.contentSnapshot = contentSnapshot;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public FitReport getFitReport() {
        return fitReport;
    }

    public void setFitReport(FitReport fitReport) {
        this.fitReport = fitReport;
    }

    public Short getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        if (pageCount < 1) {
            throw new IllegalArgumentException("A document has at least one page");
        }
        this.pageCount = (short) pageCount;
    }

    public EngineVersion getEngineVersion() {
        return engineVersion;
    }

    public Map<String, Object> getTrace() {
        return trace == null ? null : ordered(trace);
    }

    public void setTrace(Map<String, Object> trace) {
        this.trace = trace == null ? null : ordered(trace);
    }

    public GenerationStatus getStatus() {
        return status;
    }

    public UUID getParentGenerationId() {
        return parentGenerationId;
    }

    /** Faz G: this generation replaced an earlier one (Bolum 24). */
    public void supersede(UUID parentGenerationId) {
        this.parentGenerationId = Objects.requireNonNull(parentGenerationId, "parent");
    }

    public void markSuperseded() {
        this.status = GenerationStatus.SUPERSEDED;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public String getPdfKey() {
        return pdfKey;
    }

    public Instant getPdfExpiresAt() {
        return pdfExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * A defensive copy that keeps the caller's order.
     *
     * <p>Every one of these is a JSONB column, and {@code Map.copyOf} iterates
     * in an order salted per JVM run — the same generation would serialise
     * differently on every restart (CLAUDE.md).
     */
    private static Map<String, Object> ordered(Map<String, Object> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Generation generation && id.equals(generation.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Identity and shape only: the posting and the wordings are user content. */
    @Override
    public String toString() {
        return "Generation[" + id + ", " + status + ", pages=" + pageCount
                + ", atoms=" + (selectionState == null ? 0 : selectionState.selected().size())
                + "]";
    }
}
