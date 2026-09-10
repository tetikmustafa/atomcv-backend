package com.mustafatetik.atomcv.rendering.domain;

import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code template_capacities}: what a page of one geometry holds (V12,
 * Bolum 33.1).
 *
 * <p><strong>Not user data.</strong> Two people at the same font size and
 * margin are asking one question with one answer, so this row is keyed by the
 * customization and by nothing about who asked. That is why it is reached
 * without a scoped repository, and why a setting somebody else already paid to
 * measure is free for the next person.
 *
 * <p>The id is {@code TemplateCustomization.costKey()}, which carries the
 * template, its version and the four knobs that move a box. Raising a template
 * version changes every key under it, so a preamble change invalidates these
 * instead of leaving them to describe a document that no longer exists.
 */
@Entity
@Table(name = "template_capacities")
public class TemplateCapacity {

    @Id
    @Column(name = "cost_key", updatable = false)
    private String costKey;

    @Column(name = "page_text_height_pt", nullable = false)
    private double pageTextHeightPt;

    @Column(name = "text_width_pt", nullable = false)
    private double textWidthPt;

    @Column(name = "baseline_skip_pt", nullable = false)
    private double baselineSkipPt;

    @Column(name = "item_baseline_skip_pt", nullable = false)
    private double itemBaselineSkipPt;

    /**
     * The furniture, by name.
     *
     * <p>JSONB rather than a column each: the set of pieces is the renderer's
     * business and has grown twice already, and nothing is ever queried by one
     * of them — the row is read whole or not at all.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fixed_costs", nullable = false)
    private Map<String, Double> fixedCosts = Map.of();

    @CreationTimestamp
    @Column(name = "measured_at", nullable = false, updatable = false)
    private Instant measuredAt;

    protected TemplateCapacity() {
        // JPA
    }

    public TemplateCapacity(String costKey, CapacityModel capacity) {
        this.costKey = costKey;
        this.pageTextHeightPt = capacity.pageTextHeightPt();
        this.textWidthPt = capacity.textWidthPt();
        this.baselineSkipPt = capacity.baselineSkipPt();
        this.itemBaselineSkipPt = capacity.itemBaselineSkipPt();
        // Ordered, because it becomes a JSONB document and the JDK's immutable
        // maps iterate in an order salted per JVM run (CLAUDE.md). Postgres
        // sorts an object's keys itself on the way in, so this is about what
        // walks the map here rather than about the column.
        this.fixedCosts = Collections.unmodifiableMap(
                new LinkedHashMap<>(capacity.fixedCosts()));
    }

    public String getCostKey() {
        return costKey;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    /** Back to what selection works on. */
    public CapacityModel toCapacityModel() {
        return new CapacityModel(pageTextHeightPt, textWidthPt, baselineSkipPt,
                itemBaselineSkipPt, fixedCosts);
    }

    /** Shape only — though there is nothing here a person wrote. */
    @Override
    public String toString() {
        return "TemplateCapacity[" + costKey + ", costs=" + fixedCosts.size() + "]";
    }
}
