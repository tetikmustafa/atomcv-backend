package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.shared.security.LocalDevCurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code POST /generations}: the 202, the gates in front of it, and the job it
 * makes (Bolum 35.3, EK D.6.4).
 *
 * <p>The worker is off for the whole suite, so a queued job stays queued and
 * every assertion here is about what the request did rather than about what a
 * timer got round to.
 */
@AutoConfigureMockMvc
class QueuedGenerationApiIT extends AbstractIntegrationTest {

    /**
     * Long enough, varied enough and full of the words Bolum 18.1 looks for.
     * Anything less and the preflight refuses it — which is its own test.
     */
    private static final String POSTING = """
            We are seeking a senior backend engineer to join our payments team.

            Responsibilities: design and operate distributed services in Go,
            own the reliability of a high throughput ledger, and mentor other
            engineers as the team grows.

            Requirements: several years of production experience with Go and
            PostgreSQL, comfort with observability tooling, and a track record
            of shipping. Preferred qualifications include Kubernetes and
            Terraform. Apply with a short note about the systems you have run.
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevCurrentUser localUser;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JobQueue queue;

    @Autowired
    private Clock clock;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void startFromAnEmptyProfile() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM usage_counters");
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevCurrentUser.DEV_USER_ID);
    }

    // ── the happy path ───────────────────────────────────────────────────

    @Test
    void apostingIsAcceptedAndQueued() throws Exception {
        seedCareer();

        mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(POSTING)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("queued"));

        assertThat(queuedJobs()).isEqualTo(1);
    }

    /** The Location is where the job can actually be followed. */
    @Test
    void thelocationPointsAtTheJob() throws Exception {
        seedCareer();

        String location = mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(POSTING)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("queued"));
    }

    /** What the worker will read. A payload the handler cannot parse is a job that fails. */
    @Test
    void thejobCarriesWhatTheHandlerNeeds() throws Exception {
        seedCareer();

        mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobDescription\":" + quoted(POSTING)
                                + ",\"maxPages\":2,\"language\":\"tr\"}"))
                .andExpect(status().isAccepted());

        Job job = queue.claim("test-reader").flatMap(queue::find).orElseThrow();
        assertThat(job.getType()).isEqualTo(JobType.GENERATION);
        assertThat(job.getOwnerId()).isEqualTo(LocalDevCurrentUser.DEV_USER_ID);
        Map<String, Object> payload = job.getPayload();
        assertThat(payload).containsEntry("maxPages", 2)
                .containsEntry("language", "tr")
                .containsEntry("preflightAcknowledged", false);
        assertThat((String) payload.get("jobDescription")).contains("payments team");
    }

    // ── Bolum 35.3: the preflights are synchronous ───────────────────────

    /**
     * The point of doing this here rather than in the worker: a request that
     * was never going to work is a 4xx now, not a job accepted, watched for
     * half a minute and then failed.
     */
    @Test
    void atextThatIsNotAPostingIsRefusedWithoutQueueingAnything() throws Exception {
        seedCareer();

        mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("hire me plz")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPARSEABLE_JOB_DESCRIPTION"));

        assertThat(queuedJobs()).isZero();
    }

    /** An empty profile fails every attempt the retry budget allows. */
    @Test
    void anemptyProfileIsRefusedWithoutQueueingAnything() throws Exception {
        mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(POSTING)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PROFILE"));

        assertThat(queuedJobs()).isZero();
    }

    /**
     * EK D.6.1: the heuristics are cheap on purpose and a person may know
     * better than they do.
     */
    @Test
    void acknowledgingThePreflightGetsPastIt() throws Exception {
        seedCareer();

        mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobDescription\":\"hire me plz\","
                                + "\"acknowledgePreflight\":true}"))
                .andExpect(status().isAccepted());

        assertThat(queuedJobs()).isEqualTo(1);
    }

    /**
     * Bolum 19.4: no posting is not a bad request, it is a general CV. The
     * column agrees — {@code generations.job_description} is NULL for exactly
     * this case — and it is the same endpoint because everything from
     * selection onwards is the same pipeline.
     */
    @Test
    void nopostingIsAGeneralCvRatherThanARequestError() throws Exception {
        seedCareer();

        mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobDescription\":\"   \"}"))
                .andExpect(status().isAccepted());

        assertThat(queuedJobs()).isEqualTo(2);
    }

    /**
     * Bolum 44.1 on the wire, and EK D.6.5 asks for both numbers.
     *
     * <p>{@code resetsAt} is an absolute instant the client renders in the
     * user's own locale; {@code Retry-After} is a duration, and it is the only
     * one of the two that is right when the client's clock is wrong — which is
     * exactly the client that would otherwise retry at once and be refused
     * again. Written after the manual-test guide claimed the header existed
     * and nothing sent it.
     */
    @Test
    void aquotaRefusalCarriesRetryAfterAsWellAsResetsAt() throws Exception {
        seedCareer();
        jdbc.update("""
                INSERT INTO usage_counters (subject_type, subject_id, metric, period, count)
                VALUES ('user', ?, 'generation', (now() at time zone 'utc')::date, 100000)
                ON CONFLICT (subject_type, subject_id, metric, period)
                DO UPDATE SET count = 100000
                """, LocalDevCurrentUser.DEV_USER_ID.toString());

        mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(POSTING)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.params.metric").value("generation"))
                .andExpect(jsonPath("$.params.resetsAt").exists())
                .andExpect(header().exists("Retry-After"));

        assertThat(queuedJobs()).as("a refused request queues nothing").isZero();
    }

    // ── Bolum 30.7: idempotency ──────────────────────────────────────────

    /** A double click produces one CV, not two identical ones a second apart. */
    @Test
    void thesameIdempotencyKeyAnswersWithTheJobItAlreadyMade() throws Exception {
        seedCareer();

        String first = jobIdFrom(postWithKey("key-1"));
        String second = jobIdFrom(postWithKey("key-1"));

        assertThat(second).isEqualTo(first);
        assertThat(queuedJobs()).isEqualTo(1);
    }

    @Test
    void adifferentKeyIsADifferentGeneration() throws Exception {
        seedCareer();

        assertThat(jobIdFrom(postWithKey("key-1")))
                .isNotEqualTo(jobIdFrom(postWithKey("key-2")));
        assertThat(queuedJobs()).isEqualTo(2);
    }

    /** No key is no promise: two requests are two generations. */
    @Test
    void withoutAKeyEveryRequestIsItsOwnJob() throws Exception {
        seedCareer();

        assertThat(jobIdFrom(postWithKey(null)))
                .isNotEqualTo(jobIdFrom(postWithKey(null)));
    }

    // ── EK D.6.4: following it ───────────────────────────────────────────

    @Test
    void aqueuedJobReportsItselfWithoutAGenerationOrAnError() throws Exception {
        seedCareer();
        String jobId = jobIdFrom(postWithKey(null));

        mvc.perform(get("/api/v1/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.generationId").doesNotExist())
                .andExpect(jsonPath("$.pageCount").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist())
                // F-010: an empty translation key is worse than no field.
                .andExpect(jsonPath("$.phase").doesNotExist())
                .andExpect(jsonPath("$.label").doesNotExist())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(jsonPath("$.pct").value(0));
    }

    /**
     * Absolute rule 3, on the one identifier this system hands to a browser.
     * 404 rather than 403: telling a stranger that an id exists is itself
     * information.
     */
    @Test
    void anotherUsersJobIsNotFound() throws Exception {
        UUID stranger = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
        Job theirs = queue.enqueue(
                new Job(JobType.GENERATION, stranger, Map.of(), clock.instant()));

        mvc.perform(get("/api/v1/jobs/" + theirs.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void ajobThatDoesNotExistIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/jobs/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    /**
     * Absolute rule 3 on the endpoint F-008 added. The generation id is the
     * second identifier this system hands to a browser, and reading one is now
     * a third thing it can be spent on — the report names what a profile is
     * missing.
     */
    @Test
    void anotherUsersGenerationIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/generations/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    /** Bolum 30.6's progress, read back through the polling fallback. */
    @Test
    void reportedProgressShowsUpInTheStatus() throws Exception {
        seedCareer();
        String jobId = jobIdFrom(postWithKey(null));
        Job job = queue.find(UUID.fromString(jobId)).orElseThrow();
        job.setProgress(new com.mustafatetik.atomcv.jobs.queue.JobProgress(
                "B", "generation.phase.SCORING", 50));
        queue.save(job);

        mvc.perform(get("/api/v1/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("B"))
                // A key, never a sentence: the frontend owns the words.
                .andExpect(jsonPath("$.label").value("generation.phase.SCORING"))
                .andExpect(jsonPath("$.pct").value(50));
    }

    @Test
    void acompletedJobCarriesTheGenerationToOpen() throws Exception {
        seedCareer();
        String jobId = jobIdFrom(postWithKey(null));
        UUID generationId = UUID.randomUUID();
        Job job = queue.find(UUID.fromString(jobId)).orElseThrow();
        job.succeed(Map.of("generationId", generationId.toString(), "pageCount", 1),
                clock.instant());
        queue.save(job);

        mvc.perform(get("/api/v1/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.generationId").value(generationId.toString()))
                // F-008: a client that fell back to polling could reach the
                // generation but not the number printed beside it.
                .andExpect(jsonPath("$.pageCount").value(1))
                .andExpect(jsonPath("$.error").doesNotExist())
                // A bar that stopped at the last reported phase would argue
                // with the word beside it.
                .andExpect(jsonPath("$.pct").value(100))
                .andExpect(jsonPath("$.phase").doesNotExist());
    }

    @Test
    void afailedJobCarriesTheErrorAndNoGeneration() throws Exception {
        seedCareer();
        String jobId = jobIdFrom(postWithKey(null));
        Job job = queue.find(UUID.fromString(jobId)).orElseThrow();
        job.fail(Map.of("code", "ALL_PROVIDERS_UNAVAILABLE"), clock.instant());
        queue.save(job);

        mvc.perform(get("/api/v1/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.error.code").value("ALL_PROVIDERS_UNAVAILABLE"))
                .andExpect(jsonPath("$.generationId").doesNotExist());
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private String postWithKey(String key) throws Exception {
        var request = post("/api/v1/generations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(POSTING));
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return mvc.perform(request)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
    }

    private static String jobIdFrom(String responseBody) {
        return com.jayway.jsonpath.JsonPath.read(responseBody, "$.jobId");
    }

    private int queuedJobs() {
        return jdbc.queryForObject("SELECT count(*) FROM jobs", Integer.class);
    }

    private static String body(String posting) {
        return "{\"jobDescription\":" + quoted(posting) + "}";
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }

    /** Three bullets under one job, already measured so no compiler is needed. */
    private void seedCareer() {
        tx.executeWithoutResult(status -> {
            var profile = new Profile(LocalDevCurrentUser.DEV_USER_ID);
            em.persist(profile);
            UUID profileId = profile.getId();

            var section = new Section(profileId, SectionKind.EXPERIENCE, "Experience", (short) 0);
            em.persist(section);
            var entry = new Entry(profileId, section.getId(), "Backend Engineer", (short) 0);
            entry.setOrganization("Acme");
            entry.setStartDate(java.time.LocalDate.of(2021, 3, 1));
            em.persist(entry);

            for (int index = 0; index < 3; index++) {
                var atom = new Atom(profileId, section.getId(), entry.getId(),
                        AtomKind.BULLET, (short) index);
                em.persist(atom);
                var variant = new AtomVariant(profileId, atom.getId(), "en",
                        RichContent.plain("Built ETL pipelines processing 300K+ rows " + index));
                variant.setPrimary(true);
                variant.recordRenderCost("classic:v1", 25.0, Instant.now());
                em.persist(variant);
            }
        });
    }
}
