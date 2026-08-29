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
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.phases.analysis.JobDescriptionDigest;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.repository.ProfileRepository;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.ratelimit.RateLimiter;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /generations/{id}/cover-letter/regenerate} (Bolum 34.6).
 *
 * <p><strong>This lane has no LLM provider configured at all</strong>, so every
 * call here ends in Bolum 27.3's empty chain. That is not a limitation for
 * what is worth asserting: the endpoint's promise is that nothing unchecked
 * reaches {@code generations.cover_letter}, and a failure on the way to the
 * model exercises it exactly as a refused draft would. The refusal itself is
 * pinned in {@code CoverLetterServiceTest}, where the answer can be dictated.
 */
@AutoConfigureMockMvc
class CoverLetterApiIT extends AbstractIntegrationTest {

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

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redis;

    private UUID generationId;

    /**
     * The counters go too, for the reason {@code MagicLinkApiIT} records: ten
     * letters an hour is a window that outlives a test method, and
     * {@link #arefusedLetterCarriesRetryAfterAsWellAsResetsAt} spends the
     * whole of it. Left standing it would refuse a neighbouring case that has
     * nothing to do with rate limiting, and that failure reads as a flake.
     */
    @BeforeEach
    void agenerationToWriteAbout() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM generations WHERE user_id = ?", LocalDevUser.DEV_USER_ID);
        Set<String> counters = redis.keys("ratelimit:*");
        if (counters != null && !counters.isEmpty()) {
            redis.delete(counters);
        }
        // Whatever profile this user has, and one if they have none: the
        // seeder gives them one and a neighbouring test deletes it, so
        // reading it is a dependency on which test ran first.
        generationId = generations.save(user(), record(profileId())).getId();
    }

    /**
     * F-021: the frontend read {@code resetsAt} alone and built the vaguer
     * sentence, having no header to derive a duration from.
     *
     * <p>The header was in fact already sent — {@code ProblemDetailAdvice}
     * derives it from any 429 whose params carry an {@code Instant}, and this
     * endpoint's refusal goes through the advice like every other. What was
     * missing was a test saying so and an {@code @ApiResponse} publishing it,
     * and between them that is indistinguishable from the header being absent:
     * the one other place this was claimed without being asserted, the manual
     * test guide, turned out to be claiming something untrue.
     *
     * <p>The window is spent through the limiter rather than through ten
     * requests. Same layer, same subject, same numbers as the controller —
     * a bucket filled by another route would prove nothing about this one.
     */
    @Test
    void arefusedLetterCarriesRetryAfterAsWellAsResetsAt() throws Exception {
        for (int spent = 0; spent < 10; spent++) {
            rateLimiter.check("cover_letter", LocalDevUser.DEV_USER_ID.toString(),
                    10, Duration.ofHours(1));
        }

        mvc.perform(post("/api/v1/generations/" + generationId + "/cover-letter/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.params.resetsAt").exists())
                .andExpect(header().exists("Retry-After"));

        assertThat(jdbc.queryForObject(
                "SELECT cover_letter FROM generations WHERE id = ?", String.class, generationId))
                .as("a refused request writes no letter")
                .isNull();
    }

    @Test
    void aletterThatWasNeverWrittenIsNeverStored() throws Exception {
        mvc.perform(post("/api/v1/generations/" + generationId + "/cover-letter/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"style\":\"shorter\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ALL_PROVIDERS_UNAVAILABLE"));

        assertThat(jdbc.queryForObject(
                "SELECT cover_letter FROM generations WHERE id = ?", String.class, generationId))
                .isNull();
    }

    /**
     * Absolute rule 3. Somebody else's generation answers 404 rather than 403:
     * that an id exists is itself information.
     */
    @Test
    void agenerationThatIsNotYoursIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/generations/" + UUID.randomUUID()
                        + "/cover-letter/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    /** An empty body is a valid request: write it the ordinary way. */
    @Test
    void anemptyBodyIsAcceptedAsADefaultStyle() throws Exception {
        mvc.perform(post("/api/v1/generations/" + generationId + "/cover-letter/regenerate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                // It failed at the provider, which means it got past the
                // request shape — a 400 here would say it never reached the
                // writer at all.
                .andExpect(status().isServiceUnavailable());
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
        record.recordPosting("A posting", JobDescriptionDigest.of("A posting"), analysis());
        record.setPageCount(1);
        return record;
    }

    private static JobAnalysis analysis() {
        return new JobAnalysis(
                new JobAnalysis.Role("Senior Backend Engineer", JobAnalysis.Seniority.SENIOR,
                        "fintech", JobAnalysis.EmploymentType.FULL_TIME,
                        JobAnalysis.WorkMode.REMOTE),
                new JobAnalysis.Company("Acme", JobAnalysis.SizeHint.SCALEUP),
                List.of(new JobAnalysis.Skill("Go", "go", JobAnalysis.Importance.CRITICAL)),
                List.of(), List.of("scale payment systems"), List.of(),
                new JobAnalysis.ExperienceYears(5, null),
                List.of("en"), "technical", "en", 0.94, List.of());
    }
}
