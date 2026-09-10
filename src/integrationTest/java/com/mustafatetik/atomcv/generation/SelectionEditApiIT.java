package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /generations/{id}/selection}: the 202 and the four ways in front
 * of it (Bolum 24.4).
 *
 * <p>The worker is off for the whole suite, so what is proved here is what the
 * request did — which edits are answerable, which are refused before a
 * compilation is spent on them, and what reaches the queue when one is
 * accepted.
 */
@AutoConfigureMockMvc
class SelectionEditApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevUser localUser;

    @Autowired
    private GenerationRepository generations;

    @Autowired
    private JobQueue queue;

    private UUID onThePage;
    private UUID rejected;
    private Generation parent;

    @BeforeEach
    void startFromOneFinishedGeneration() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM generations");

        onThePage = UUID.randomUUID();
        rejected = UUID.randomUUID();
        parent = generations.save(user(), aGeneration());
    }

    @Test
    void anEditIsAcceptedAndQueued() throws Exception {
        mvc.perform(post("/api/v1/generations/" + parent.getId() + "/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exclude\":[\"" + onThePage + "\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.jobId").exists());
    }

    @Test
    void thequeuedJobNamesTheGenerationItEdits() throws Exception {
        mvc.perform(post("/api/v1/generations/" + parent.getId() + "/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"include\":[\"" + rejected + "\"]}"))
                .andExpect(status().isAccepted());

        Job job = queue.claim("test-reader").flatMap(queue::find).orElseThrow();
        Map<String, Object> payload = job.getPayload();
        assertThat(payload).containsEntry("parentGenerationId", parent.getId().toString());
        assertThat(payload.get("includeAtoms")).isEqualTo(List.of(rejected.toString()));
    }

    /**
     * Bolum 44: a toggle re-runs selection, the renderer and the compiler and
     * asks no model anything, so there is nothing here for a day's allowance to
     * be spent on.
     */
    @Test
    void aneditSpendsNothing() throws Exception {
        jdbc.update("DELETE FROM usage_counters");

        mvc.perform(post("/api/v1/generations/" + parent.getId() + "/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exclude\":[\"" + onThePage + "\"]}"))
                .andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM usage_counters", Integer.class)).isZero();
    }

    /** Queuing it would spend a compilation to produce the same document. */
    @Test
    void anemptyEditIsRefused() throws Exception {
        mvc.perform(post("/api/v1/generations/" + parent.getId() + "/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void anatomInBothListsIsRefused() throws Exception {
        mvc.perform(post("/api/v1/generations/" + parent.getId() + "/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"include\":[\"" + onThePage + "\"],"
                                + "\"exclude\":[\"" + onThePage + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /**
     * Ignoring it would answer 202 and hand back the document the person is
     * already looking at, which reads as the feature being broken.
     */
    @Test
    void anatomThisGenerationNeverWeighedIsRefused() throws Exception {
        UUID stranger = UUID.randomUUID();

        mvc.perform(post("/api/v1/generations/" + parent.getId() + "/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exclude\":[\"" + stranger + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields[0]").value(stranger.toString()));
    }

    /** Applying it would fork the lineage: two finished children of one parent. */
    @Test
    void aneditOfAgenerationThatHasAlreadyBeenReplacedIsRefused() throws Exception {
        parent.markSuperseded();
        generations.save(user(), parent);

        mvc.perform(post("/api/v1/generations/" + parent.getId() + "/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exclude\":[\"" + onThePage + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENERATION_SUPERSEDED"));
    }

    // ── the natural-language half (Bolum 24.2) ───────────────────────────

    @Test
    void asentenceIsAcceptedAndQueuedWithTheSentenceOnIt() throws Exception {
        mvc.perform(post("/api/v1/generations/" + parent.getId() + "/edits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"take out the android bullet\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"));

        Job job = queue.claim("test-reader").flatMap(queue::find).orElseThrow();
        assertThat(job.getPayload())
                .containsEntry("parentGenerationId", parent.getId().toString())
                .containsEntry("instruction", "take out the android bullet");
    }

    /**
     * Bolum 44.2, and it is the difference between the two endpoints: reading
     * a sentence is a model call, so it comes off the day's generations. The
     * toggle next door takes nothing.
     */
    @Test
    void asentenceSpendsAgeneration() throws Exception {
        jdbc.update("DELETE FROM usage_counters");

        mvc.perform(post("/api/v1/generations/" + parent.getId() + "/edits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"drop the last one\"}"))
                .andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM usage_counters", Integer.class)).isPositive();
    }

    @Test
    void anemptySentenceIsRefused() throws Exception {
        mvc.perform(post("/api/v1/generations/" + parent.getId() + "/edits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void asentenceAimedAtAreplacedGenerationIsRefused() throws Exception {
        parent.markSuperseded();
        generations.save(user(), parent);

        mvc.perform(post("/api/v1/generations/" + parent.getId() + "/edits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"drop the last one\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENERATION_SUPERSEDED"));
    }

    /** Absolute rule 3, and it is the whole IDOR defence on this endpoint. */
    @Test
    void anotherAccountsGenerationReadsAsAbsent() throws Exception {
        UUID stranger = newUser();
        var theirs = generations.save(UserContext.of(stranger),
                new Generation(stranger, newProfile(stranger), options(), snapshot(),
                        engineVersion()));

        mvc.perform(post("/api/v1/generations/" + theirs.getId() + "/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exclude\":[\"" + onThePage + "\"]}"))
                .andExpect(status().isNotFound());
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    /**
     * The dev user already has one — {@code DevSeeder} makes it, and the
     * profiles table is never empty in this package. Inserting a second would
     * hit the one-profile-per-account unique index.
     */
    private UUID devProfile() {
        return jdbc.queryForObject("SELECT id FROM profiles WHERE user_id = ?",
                UUID.class, LocalDevUser.DEV_USER_ID);
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

    private static UserContext user() {
        return UserContext.of(LocalDevUser.DEV_USER_ID);
    }

    private Generation aGeneration() {
        var record = new Generation(LocalDevUser.DEV_USER_ID,
                devProfile(), options(), snapshot(), engineVersion());
        record.setPageCount(1);
        return record;
    }

    private static Map<String, Object> options() {
        var options = new LinkedHashMap<String, Object>();
        options.put("templateId", "classic");
        options.put("maxPages", 1);
        options.put("cvLanguage", "en");
        return options;
    }

    private static EngineVersion engineVersion() {
        return new EngineVersion(EngineVersion.PIPELINE, "default", "classic:v5",
                Map.of("job_analysis", "v1"));
    }

    private StoredSelection snapshot() {
        return StoredSelection.of(
                new SelectionState(
                        List.of(new SelectionState.SelectedAtom(
                                onThePage, UUID.randomUUID(), 0.81, 27.7, false)),
                        List.of(new SelectionState.RejectedAtom(
                                rejected, 0.12, SelectionState.RejectionReason.BUDGET)),
                        new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 27.7)),
                "en", TemplateCustomization.CLASSIC);
    }
}
