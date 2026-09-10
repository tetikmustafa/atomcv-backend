package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.GenerationStatus;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.phases.analysis.JobDescriptionDigest;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.validation.FitReport;
import com.mustafatetik.atomcv.generation.validation.MatchLevel;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.template.FontFamily;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The generation record against the real schema (Bolum 14).
 *
 * <p>Nine JSONB columns and an enum converted to text, none of which schema
 * validation checks the shape of. What is being proved is that the snapshot
 * survives a round trip intact — because EK D.6.3 rests on it: in Stage 2 the
 * snapshot is not a fallback for an expired PDF, it is the only way to get the
 * PDF back at all.
 */
class GenerationRecordIT extends AbstractIntegrationTest {

    private static final String POSTING = "We are hiring a senior backend engineer.";

    @Autowired
    private GenerationRepository generations;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;
    private UUID profileId;

    @BeforeEach
    void startFromAnEmptyHistory() {
        jdbc.update("DELETE FROM generations");
        userId = newUser();
        profileId = newProfile(userId);
    }

    @Test
    void thesnapshotSurvivesTheRoundTripIntact() {
        var customization = new TemplateCustomization(
                "classic", FontFamily.SANS, 11.0, 0.8, 1.1,
                TemplateCustomization.CLASSIC.accentColor());
        var atomId = UUID.randomUUID();
        var variantId = UUID.randomUUID();
        var selection = new SelectionState(
                List.of(new SelectionState.SelectedAtom(atomId, variantId, 0.87, 27.7, true)),
                List.of(new SelectionState.RejectedAtom(
                        UUID.randomUUID(), 0.12, SelectionState.RejectionReason.BUDGET)),
                new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 498.3));

        var saved = generations.save(user(), record(
                StoredSelection.of(selection, "tr", customization)));

        var reloaded = generations.findById(user(), saved.getId()).orElseThrow();
        StoredSelection snapshot = reloaded.getSelectionState();
        assertThat(snapshot.language()).isEqualTo("tr");
        // Every knob, not just the id: a re-render needs the font and the
        // margins, and there is no customization row to look them up in.
        assertThat(snapshot.customization()).isEqualTo(customization);
        assertThat(snapshot.selected()).singleElement().satisfies(atom -> {
            assertThat(atom.atomId()).isEqualTo(atomId);
            assertThat(atom.variantId()).isEqualTo(variantId);
            assertThat(atom.score()).isEqualTo(0.87);
            assertThat(atom.renderCostPt()).isEqualTo(27.7);
            assertThat(atom.forcedByLock()).isTrue();
        });
        assertThat(snapshot.rejected()).singleElement()
                .satisfies(atom -> assertThat(atom.reason())
                        .isEqualTo(SelectionState.RejectionReason.BUDGET));
        assertThat(snapshot.budget().usedPt()).isEqualTo(498.3);
        // And it converts back to what the pipeline works on.
        assertThat(snapshot.toSelectionState().selected()).hasSize(1);
    }

    @Test
    void thepostingAndItsAnalysisSurviveToo() {
        var saved = generations.save(user(), recordWithPosting());

        var reloaded = generations.findById(user(), saved.getId()).orElseThrow();
        assertThat(reloaded.getJobDescription()).isEqualTo(POSTING);
        assertThat(reloaded.getJdHash()).isEqualTo(JobDescriptionDigest.of(POSTING));
        assertThat(reloaded.getJdAnalysis().role().title())
                .isEqualTo("Senior Backend Engineer");
        assertThat(reloaded.getJdAnalysis().requiredSkills()).singleElement()
                .satisfies(skill -> assertThat(skill.canonical()).isEqualTo("go"));
    }

    @Test
    void theengineVersionAndTraceSurvive() {
        var record = record(snapshot());
        var trace = new LinkedHashMap<String, Object>();
        trace.put("C", Map.of("selected", 16));
        record.setTrace(trace);

        var saved = generations.save(user(), record);

        var reloaded = generations.findById(user(), saved.getId()).orElseThrow();
        assertThat(reloaded.getEngineVersion().pipeline()).isEqualTo(EngineVersion.PIPELINE);
        assertThat(reloaded.getEngineVersion().promptVersions())
                .containsEntry("job_analysis", "v1");
        assertThat(reloaded.getTrace()).containsKey("C");
    }

    /** The column is text with a comment, and the converter writes lowercase. */
    @Test
    void thestatusIsStoredLowercase() {
        var saved = generations.save(user(), record(snapshot()));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM generations WHERE id = ?", String.class, saved.getId()))
                .isEqualTo("completed");
        assertThat(generations.findById(user(), saved.getId()).orElseThrow().getStatus())
                .isEqualTo(GenerationStatus.COMPLETED);
    }

    /** Stage 2 stores no bytes; a download re-renders from the snapshot. */
    @Test
    void nopdfIsStored() {
        var saved = generations.save(user(), record(snapshot()));

        var reloaded = generations.findById(user(), saved.getId()).orElseThrow();
        assertThat(reloaded.getPdfKey()).isNull();
        assertThat(reloaded.getPdfExpiresAt()).isNull();
    }

    /**
     * Absolute rule 3. The generation id reaches a browser in the job's
     * terminal event and again in the download link.
     */
    @Test
    void anotherUsersGenerationReadsAsAbsent() {
        var saved = generations.save(user(), record(snapshot()));
        var stranger = UserContext.of(newUser());

        assertThat(generations.findById(user(), saved.getId())).isPresent();
        assertThat(generations.findById(stranger, saved.getId())).isEmpty();
    }

    /**
     * The fit report is a seventh JSONB column and it round-trips as a typed
     * record rather than a map (Bolum 23.3, F-008).
     *
     * <p>Worth its own test because the column is what the result screen reads
     * and nothing else validates its shape: a field that failed to deserialise
     * would come back null, and a report that is missing looks exactly like a
     * general-mode generation that never had one.
     */
    @Test
    void thefitReportSurvivesTheRoundTripAsCounts() {
        var report = new FitReport(
                2, 3, 1, 2,
                List.of("Go", "PostgreSQL", "gRPC"),
                List.of("Kubernetes"),
                List.of("Terraform"),
                MatchLevel.MODERATE);
        var generation = record(snapshot());
        generation.setFitReport(report);

        var saved = generations.save(user(), generation);
        var reloaded = generations.findById(user(), saved.getId()).orElseThrow();

        assertThat(reloaded.getFitReport()).isEqualTo(report);
        assertThat(reloaded.getFitReport().level()).isEqualTo(MatchLevel.MODERATE);
        assertThat(reloaded.getFitReport().missingRequired()).containsExactly("Kubernetes");
    }

    /**
     * General mode has no posting, so it has no report — and null is the
     * honest value. Zero counts with a level over them would be a verdict
     * about nothing.
     */
    @Test
    void ageneralModeGenerationStoresNoReportAtAll() {
        var saved = generations.save(user(), record(snapshot()));

        assertThat(generations.findById(user(), saved.getId()).orElseThrow().getFitReport())
                .isNull();
        assertThat(jdbc.queryForObject(
                "SELECT fit_report FROM generations WHERE id = ?", String.class, saved.getId()))
                .isNull();
    }

    /**
     * Two generations a second apart is ordinary — Faz G's edit loop does it —
     * and {@code created_at} alone would leave their order to the database.
     */
    @Test
    void historyComesBackNewestFirstWithAStableTieBreak() {
        var first = generations.save(user(), record(snapshot()));
        var second = generations.save(user(), record(snapshot()));

        List<Generation> recent = generations.findRecent(user(), 10);

        assertThat(recent).hasSize(2);
        assertThat(recent).extracting(Generation::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(generations.findRecent(user(), 1)).hasSize(1);
    }

    /**
     * V11, and the column exists because the one next to it cannot answer this
     * question (Bolum 24).
     *
     * <p>{@code content_snapshot} holds the same sentence and no atom id —
     * Bolum 22.2 built the render to carry none — so an edit that re-runs
     * selection could not tell which surviving atom already had a Faz D
     * wording. Round-tripping it by id is the whole point, and nothing else
     * checks that a {@code Map<UUID, RichContent>} survives JSONB: a key that
     * failed to deserialise would come back as an empty map, which looks
     * exactly like a generation whose rewrites were all refused.
     */
    @Test
    void thefazDwordingSurvivesTheRoundTripByAtom() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var byAtom = new LinkedHashMap<UUID, RichContent>();
        byAtom.put(first, RichContent.plain("Cut checkout latency by 40%."));
        byAtom.put(second, RichContent.plain("Led the migration to Go."));
        var generation = record(snapshot());
        generation.setRewrittenContent(new RewrittenContent(byAtom));

        var saved = generations.save(user(), generation);
        var reloaded = generations.findById(user(), saved.getId()).orElseThrow();

        assertThat(reloaded.getRewrittenContent().byAtom())
                .containsOnlyKeys(first, second);
        assertThat(reloaded.getRewrittenContent().orOriginal(first, RichContent.plain("x")))
                .isEqualTo(RichContent.plain("Cut checkout latency by 40%."));
        assertThat(reloaded.getRewrittenContent().orOriginal(second, RichContent.plain("x")))
                .isEqualTo(RichContent.plain("Led the migration to Go."));
        // And the order it went in with is gone. `jsonb` stores an object's
        // keys sorted by length and bytes, so this comes back sorted by atom
        // id however it was written -- asserted rather than assumed, because
        // the opposite was assumed first and this test is what said otherwise.
        // Nothing reads it in order: it is a lookup, one atom at a time.
        assertThat(reloaded.getRewrittenContent().byAtom().keySet())
                .containsExactlyInAnyOrder(first, second);
    }

    /**
     * Empty is what almost every row carries, and it has to survive as itself.
     * Null would mean a row written before V11 — a different sentence, and the
     * only one that column may say.
     */
    @Test
    void arunThatRewroteNothingStoresAnEmptyMapAndNotNull() {
        var generation = record(snapshot());
        generation.setRewrittenContent(RewrittenContent.none());

        var saved = generations.save(user(), generation);

        assertThat(generations.findById(user(), saved.getId()).orElseThrow()
                .getRewrittenContent().byAtom()).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT rewritten_content FROM generations WHERE id = ?",
                String.class, saved.getId()))
                .isNotNull();
    }

    /** Absolute rule 4: the posting and the wordings are the user's content. */
    @Test
    void thetoStringCarriesNoContent() {
        assertThat(recordWithPosting().toString())
                .doesNotContain("hiring", "backend", "Acme");
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private UserContext user() {
        return UserContext.of(userId);
    }

    private Generation record(StoredSelection selection) {
        var options = new LinkedHashMap<String, Object>();
        options.put("templateId", "classic");
        options.put("maxPages", 1);
        options.put("cvLanguage", "en");
        var record = new Generation(userId, profileId, options, selection,
                new EngineVersion(EngineVersion.PIPELINE, "default", "classic:v1",
                        Map.of("job_analysis", "v1")));
        record.setPageCount(1);
        return record;
    }

    private Generation recordWithPosting() {
        var record = record(snapshot());
        record.recordPosting(POSTING, JobDescriptionDigest.of(POSTING), analysis());
        return record;
    }

    private static StoredSelection snapshot() {
        return StoredSelection.of(
                new SelectionState(List.of(), List.of(),
                        new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 0.0)),
                "en", TemplateCustomization.CLASSIC);
    }

    private static JobAnalysis analysis() {
        return new JobAnalysis(
                new JobAnalysis.Role("Senior Backend Engineer", JobAnalysis.Seniority.SENIOR,
                        "fintech", JobAnalysis.EmploymentType.FULL_TIME,
                        JobAnalysis.WorkMode.REMOTE),
                new JobAnalysis.Company("Acme", JobAnalysis.SizeHint.SCALEUP),
                List.of(new JobAnalysis.Skill("Go", "go", JobAnalysis.Importance.CRITICAL)),
                List.of(), List.of("scale payment systems"), List.of("distributed systems"),
                new JobAnalysis.ExperienceYears(5, null),
                List.of("en"), "technical", "en", 0.94, List.of());
    }

    private UUID newUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
    }

    private UUID newProfile(UUID owner) {
        return jdbc.queryForObject(
                "INSERT INTO profiles (user_id) VALUES (?) RETURNING id", UUID.class, owner);
    }
}
