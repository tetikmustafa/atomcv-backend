package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * {@code POST /generations/{id}/feedback} (Bolum 13, Bolum 48.4).
 *
 * <p>Two things are being protected here and only one of them is the row. The
 * other is Bolum 48.4's consent: it opens the single door through absolute
 * rule 4, so it has to close again when the person says so, and it has to be
 * visible to them either way.
 */
@AutoConfigureMockMvc
class FeedbackApiIT extends AbstractIntegrationTest {

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
    void agenerationToJudge() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM generations WHERE user_id = ?", LocalDevUser.DEV_USER_ID);
        generationId = generations.save(user(), record(profileId())).getId();
    }

    @Test
    void athumbIsAllItTakes() throws Exception {
        mvc.perform(feedback("{\"rating\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(1))
                .andExpect(jsonPath("$.contentGrant").doesNotExist());

        assertThat(countFeedback()).isEqualTo(1);
    }

    /**
     * <strong>Changing your mind is not a second opinion.</strong> A feedback
     * rate that counted both presses would be measuring clicks.
     */
    @Test
    void pressingTheOtherThumbReplacesTheVerdict() throws Exception {
        mvc.perform(feedback("{\"rating\":1}")).andExpect(status().isOk());
        mvc.perform(feedback("{\"rating\":-1,\"category\":\"density\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(-1))
                .andExpect(jsonPath("$.category").value("density"));

        assertThat(countFeedback()).isEqualTo(1);
    }

    /** Bolum 13 allows exactly two values, and zero is neither of them. */
    @Test
    void aratingThatIsNeitherThumbIsRefused() throws Exception {
        mvc.perform(feedback("{\"rating\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields[0]").value("rating"));

        assertThat(countFeedback()).isZero();
    }

    /**
     * <strong>Bolum 48.4's door, and the audit trail that makes it a
     * consent.</strong> Everything else in this product is diagnosed from
     * shapes; this is the one way to the content, and the person is shown
     * when it runs out and whether it was used.
     */
    @Test
    void grantingAccessOpensAWindowThePersonCanSee() throws Exception {
        mvc.perform(feedback("{\"rating\":-1,\"contentGranted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentGrant.open").value(true))
                .andExpect(jsonPath("$.contentGrant.expiresAt").isNotEmpty())
                // Null until somebody actually looks. That is the promise.
                .andExpect(jsonPath("$.contentGrant.accessedAt").doesNotExist());

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM support_grants
                WHERE generation_id = ? AND revoked_at IS NULL
                """, Integer.class, generationId)).isEqualTo(1);
    }

    /**
     * Forty-eight hours from the first yes, and a second yes does not move it.
     * A form saved twice would otherwise keep pushing the end back without the
     * person meaning to.
     */
    @Test
    void sayingYesTwiceDoesNotPushTheWindowBack() throws Exception {
        mvc.perform(feedback("{\"rating\":-1,\"contentGranted\":true}"))
                .andExpect(status().isOk());
        var firstExpiry = expiresAt();

        mvc.perform(feedback("{\"rating\":-1,\"contentGranted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentGrant.open").value(true));

        assertThat(countGrants()).isEqualTo(1);
        assertThat(expiresAt()).isEqualTo(firstExpiry);
    }

    /**
     * <strong>A consent that cannot be withdrawn is a switch.</strong> The row
     * stays — taking it back is part of its history — and the door closes.
     */
    @Test
    void theconsentCanBeTakenBack() throws Exception {
        mvc.perform(feedback("{\"rating\":-1,\"contentGranted\":true}"))
                .andExpect(jsonPath("$.contentGrant.open").value(true));

        mvc.perform(feedback("{\"rating\":-1,\"contentGranted\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentGrant.open").value(false))
                .andExpect(jsonPath("$.contentGrant.revokedAt").isNotEmpty());

        assertThat(countGrants()).isEqualTo(1);
    }

    /** Absolute rule 3: somebody else's generation is not found. */
    @Test
    void feedbackOnAgenerationThatIsNotYoursIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/generations/" + UUID.randomUUID() + "/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":1}"))
                .andExpect(status().isNotFound());
    }

    /**
     * The comment is stored because they wrote it for us to read, and not sent
     * back because they already have it (absolute rule 4).
     */
    @Test
    void thecommentIsStoredAndNotEchoed() throws Exception {
        mvc.perform(feedback("{\"rating\":-1,\"comment\":\"The dates were wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").doesNotExist());

        assertThat(jdbc.queryForObject(
                "SELECT comment FROM generation_feedback WHERE generation_id = ?",
                String.class, generationId)).isEqualTo("The dates were wrong");
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.RequestBuilder feedback(String body) {
        return post("/api/v1/generations/" + generationId + "/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private Integer countFeedback() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM generation_feedback WHERE generation_id = ?",
                Integer.class, generationId);
    }

    private java.time.Instant expiresAt() {
        return jdbc.queryForObject(
                "SELECT expires_at FROM support_grants WHERE generation_id = ?",
                java.time.Instant.class, generationId);
    }

    private Integer countGrants() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM support_grants WHERE generation_id = ?",
                Integer.class, generationId);
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
