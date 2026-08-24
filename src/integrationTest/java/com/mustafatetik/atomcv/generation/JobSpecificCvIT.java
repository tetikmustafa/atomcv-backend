package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mustafatetik.atomcv.AbstractLatexTest;
import com.mustafatetik.atomcv.jobs.queue.JobEvents;
import com.mustafatetik.atomcv.jobs.queue.JobHandler;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.workers.JobWorker;
import com.mustafatetik.atomcv.jobs.workers.JobWorkerProperties;
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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stage 2's headline: a posting goes in and a CV comes out (XI-A.5).
 *
 * <p>The one box on the closing checklist that was still half. Everything
 * happens for real except the provider: Faz A analyses the posting through the
 * fake, Faz B scores the whole profile against the analysis, selection fills a
 * measured page, TeX compiles it, and the PDF comes back from the download
 * endpoint — which re-renders the stored snapshot rather than the profile.
 *
 * <p><strong>Two profiles, so a second Spring context.</strong> The rest of
 * the suite runs on {@code local} and would call a provider with no key here,
 * failing every generation with ALL_PROVIDERS_UNAVAILABLE. {@code local-fake}
 * answers from a fixture, or synthesises a schema-shaped answer when no
 * fixture covers the posting (Bolum 54.2) — which is what makes this runnable
 * on a fresh clone. The extra context costs seconds in a lane that already
 * spends minutes building an image.
 *
 * <p>Mocking the chain instead would have been cheaper and would have mocked
 * away the thing being tested.
 */
@Tag("latex")
@AutoConfigureMockMvc
@ActiveProfiles({"local", "local-fake"})
class JobSpecificCvIT extends AbstractLatexTest {

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
    private List<JobHandler> handlers;

    @Autowired
    private Clock clock;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void startFromAnEmptyProfile() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM generations WHERE user_id = ?", LocalDevCurrentUser.DEV_USER_ID);
        jdbc.update("DELETE FROM usage_counters");
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevCurrentUser.DEV_USER_ID);
    }

    /**
     * "A posting is pasted and a CV is produced." The whole of Stage 2 in one
     * request, and the first time Faz A, Faz B and TeX have run together.
     */
    @Test
    void apostingBecomesARealOnePagePdf() throws Exception {
        seedCareer();

        String jobId = enqueue();
        assertThat(worker().runOne()).as("the queued generation was taken").isTrue();

        String generationId = completedGenerationId(jobId);
        byte[] pdf = download(generationId);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).as("a real document, not an error page").isGreaterThan(2000);
    }

    /**
     * The record has to describe a job-specific run, not a general one. An
     * empty {@code jd_analysis} here would mean Faz A ran and its answer was
     * thrown away — the CV would still look fine and nothing else would say so.
     */
    @Test
    void thegenerationRecordsThePostingAndWhatWasMadeOfIt() throws Exception {
        seedCareer();

        String jobId = enqueue();
        worker().runOne();
        String generationId = completedGenerationId(jobId);

        var row = jdbc.queryForMap(
                "SELECT job_description, jd_hash, jd_analysis, page_count, engine_version"
                        + " FROM generations WHERE id = ?::uuid", generationId);
        assertThat((String) row.get("job_description")).contains("payments team");
        assertThat(row.get("jd_hash")).isNotNull();
        assertThat(row.get("jd_analysis")).asString().isNotBlank().isNotEqualTo("null");
        assertThat(((Number) row.get("page_count")).intValue()).isEqualTo(1);
        // Faz B really ran: general mode would have written "general-mode".
        assertThat(row.get("engine_version")).asString().doesNotContain("general-mode");
    }

    /**
     * Faz F's report, through the whole pipeline and back out of the endpoint
     * that publishes it (Bolum 23.3, F-008).
     *
     * <p>The counts themselves are a unit test's job. What only this lane can
     * show is that the report survives every hop it has to make — computed
     * against a real analysis, written to JSONB, read back as a typed record,
     * and serialised — and that it is measured on the page rather than on the
     * ranking. That last part is the one worth a real run: the profile here
     * has more atoms than a page holds.
     */
    @Test
    void thefitReportReachesTheEndpointAndDescribesThePage() throws Exception {
        seedCareer();

        String jobId = enqueue();
        worker().runOne();
        String generationId = completedGenerationId(jobId);

        String body = mvc.perform(get("/api/v1/generations/" + generationId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(body, "$.generationId")).isEqualTo(generationId);
        assertThat(((Number) JsonPath.read(body, "$.pageCount")).intValue()).isEqualTo(1);
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("completed");

        int requiredTotal = ((Number) JsonPath.read(body, "$.fitReport.requiredTotal")).intValue();
        int requiredCovered =
                ((Number) JsonPath.read(body, "$.fitReport.requiredCovered")).intValue();
        assertThat(requiredTotal).as("Faz A found requirements to report on").isPositive();
        assertThat(requiredCovered).isBetween(0, requiredTotal);
        assertThat((String) JsonPath.read(body, "$.fitReport.level"))
                .isIn("WEAK", "MODERATE", "GOOD", "STRONG");

        // Bolum 23.3 forbids a percentage by name, and the schema is where
        // one would quietly appear.
        assertThat(body).doesNotContain("percent").doesNotContain("score");

        // The heading rides the terminal event so the result screen can print
        // it without a second round trip.
        String status = mvc.perform(get("/api/v1/jobs/" + jobId))
                .andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(status, "$.pageCount")).intValue()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT result ->> 'matchLevel' FROM jobs WHERE id = ?::uuid",
                String.class, jobId))
                .isEqualTo(JsonPath.read(body, "$.fitReport.level"));
    }

    /** Bolum 44.2: the unit is spent when the work is queued, and kept on success. */
    @Test
    void asuccessfulGenerationKeepsItsQuotaUnit() throws Exception {
        seedCareer();

        enqueue();
        worker().runOne();

        assertThat(jdbc.queryForObject(
                "SELECT count FROM usage_counters WHERE metric = 'generation'", Integer.class))
                .isEqualTo(1);
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private String enqueue() throws Exception {
        String accepted = mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobDescription\":" + quoted(POSTING) + "}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(accepted, "$.jobId");
    }

    private String completedGenerationId(String jobId) throws Exception {
        String status = mvc.perform(get("/api/v1/jobs/" + jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(status, "$.status"))
                .as("the job finished; a failure here carries its own error")
                .isEqualTo("completed");
        return JsonPath.read(status, "$.generationId");
    }

    private byte[] download(String generationId) throws Exception {
        return mvc.perform(get("/api/v1/generations/" + generationId + "/download"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private JobWorker worker() {
        return new JobWorker(queue, JobEvents.NONE, handlers,
                new JobWorkerProperties(true, 1, null, null, null, Duration.ofSeconds(5)),
                clock);
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }

    /** Two jobs with bullets that a backend posting should have opinions about. */
    private void seedCareer() {
        tx.executeWithoutResult(status -> {
            var profile = new Profile(LocalDevCurrentUser.DEV_USER_ID);
            em.persist(profile);
            UUID profileId = profile.getId();

            var section = new Section(profileId, SectionKind.EXPERIENCE, "Experience", (short) 0);
            em.persist(section);

            for (int job = 0; job < 2; job++) {
                var entry = new Entry(profileId, section.getId(),
                        "Backend Engineer", (short) job);
                entry.setOrganization("Company " + job);
                entry.setStartDate(LocalDate.of(2019 + job, 3, 1));
                entry.setEndDate(job == 1 ? null : LocalDate.of(2021, 6, 1));
                em.persist(entry);

                for (int bullet = 0; bullet < 3; bullet++) {
                    var atom = new Atom(profileId, section.getId(), entry.getId(),
                            AtomKind.BULLET, (short) bullet);
                    atom.setSkills(List.of("go", "postgresql"));
                    em.persist(atom);
                    var variant = new AtomVariant(profileId, atom.getId(), "en",
                            RichContent.plain("Ran distributed Go services on PostgreSQL, "
                                    + "cutting the nightly ledger window from six hours to "
                                    + "fifty minutes (" + job + "." + bullet + ")"));
                    variant.setPrimary(true);
                    em.persist(variant);
                }
            }
        });
    }
}
