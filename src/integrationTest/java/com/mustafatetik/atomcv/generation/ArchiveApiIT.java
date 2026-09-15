package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.repository.ProfileRepository;
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
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * {@code POST /generations/{id}/archive} (Bolum 35.2, Bolum 13).
 *
 * <p>The endpoint was in the resource map from the start and the column was in
 * {@code V1}; neither had ever met the other, so {@code generations.archived}
 * was a column with a setter nobody called. What the mark buys arrives with
 * object storage — Bolum 13 pairs it with {@code pdf_expires_at} and EK D.6.3
 * defers the whole expiry path — so what is tested here is the mark itself:
 * that it is set, cleared, published on both shapes a client reads, and
 * reachable only by the person whose generation it is.
 */
@AutoConfigureMockMvc
class ArchiveApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevUser localUser;

    @Autowired
    private GenerationRepository generations;

    @Autowired
    private ProfileRepository profiles;

    private UUID generationId;

    @BeforeEach
    void agenerationToKeep() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM generations WHERE user_id = ?", LocalDevUser.DEV_USER_ID);
        generationId = generations.save(user(), record(profileId())).getId();
    }

    /** A generation is not archived when it is made; the mark is a decision. */
    @Test
    void agenerationStartsUnmarked() throws Exception {
        mvc.perform(get("/api/v1/generations/" + generationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));

        assertThat(storedFlag()).isFalse();
    }

    /** An omitted body archives: the path already says what it does. */
    @Test
    void abarePostMarksIt() throws Exception {
        mvc.perform(post("/api/v1/generations/" + generationId + "/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        assertThat(storedFlag()).isTrue();
    }

    /**
     * <strong>A mark that cannot be taken off is a trap.</strong> The resource
     * map names no undo and the support grant of Bolum 48.4.1 answered the
     * same question the same way: one endpoint, a boolean in the body.
     */
    @Test
    void themarkComesOffThroughTheSameEndpoint() throws Exception {
        mvc.perform(archive("{\"archived\":true}")).andExpect(status().isOk());

        mvc.perform(archive("{\"archived\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false));

        assertThat(storedFlag()).isFalse();
    }

    /**
     * Idempotent, and not by accident of the write: the caller asked for a
     * state and the row is in it. The same answer as the second press of
     * account deletion (Bolum 57.4.1).
     */
    @Test
    void archivingSomethingAlreadyArchivedIsNotAconflict() throws Exception {
        mvc.perform(archive("{\"archived\":true}")).andExpect(status().isOk());

        mvc.perform(archive("{\"archived\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        assertThat(storedFlag()).isTrue();
    }

    /**
     * <strong>The list is where the mark is read.</strong> Publishing it only
     * on the single generation would leave the history — the screen somebody
     * opens to find the one they kept — unable to show which one that was.
     */
    @Test
    void thehistoryRowCarriesTheMark() throws Exception {
        mvc.perform(archive("{\"archived\":true}")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/generations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].generationId").value(generationId.toString()))
                .andExpect(jsonPath("$.items[0].archived").value(true));
    }

    /** Absolute rule 3: somebody else's generation is not found, not forbidden. */
    @Test
    void archivingAgenerationThatIsNotYoursIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/generations/" + UUID.randomUUID() + "/archive"))
                .andExpect(status().isNotFound());
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private RequestBuilder archive(String body) {
        return post("/api/v1/generations/" + generationId + "/archive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private Boolean storedFlag() {
        return jdbc.queryForObject(
                "SELECT archived FROM generations WHERE id = ?", Boolean.class, generationId);
    }

    private UserContext user() {
        return UserContext.of(LocalDevUser.DEV_USER_ID);
    }

    private UUID profileId() {
        List<UUID> existing = jdbc.queryForList(
                "SELECT id FROM profiles WHERE user_id = ?", UUID.class,
                LocalDevUser.DEV_USER_ID);
        return existing.isEmpty()
                ? profiles.save(user(), new Profile(LocalDevUser.DEV_USER_ID)).getId()
                : existing.get(0);
    }

    private Generation record(UUID profileId) {
        var options = new LinkedHashMap<String, Object>();
        options.put("templateId", "classic");
        options.put("maxPages", 1);
        options.put("cvLanguage", "en");

        var record = new Generation(LocalDevUser.DEV_USER_ID, profileId, options,
                StoredSelection.of(new SelectionState(List.of(), List.of(),
                                new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 0.0)),
                        "en", TemplateCustomization.CLASSIC),
                new EngineVersion(EngineVersion.PIPELINE, "default", "classic:v1",
                        Map.of("job_analysis", "v1")));
        record.setPageCount(1);
        return record;
    }
}
