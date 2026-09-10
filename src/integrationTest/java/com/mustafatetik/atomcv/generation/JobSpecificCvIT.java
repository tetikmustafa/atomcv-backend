package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
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

    /**
     * A file rather than a text block, and the reason is the fixture key.
     *
     * <p>{@code FixtureStore} names a recording after a digest of the prompt it
     * answered, so a recorded {@code job_analysis} is found again only for
     * <em>byte-identical</em> input. While this posting lived in a constant,
     * recording a fixture for it meant retyping it into
     * {@code scripts/dev-record.sh} and hoping the two never drifted — and a
     * drift would not fail loudly, it would quietly miss the fixture and fall
     * back to a synthetic answer the plausibility gate then refuses.
     *
     * <p>One file, read by the test and passed to the recorder, cannot drift:
     *
     * <pre>{@code
     * ./scripts/dev-record.sh <cv> src/integrationTest/resources/postings/senior-backend-go.txt
     * }</pre>
     *
     * <p>{@code .gitattributes} normalises the repository to LF, so the digest
     * is the same on this machine and on the runner.
     */
    private static final String POSTING = posting("senior-backend-go.txt");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevUser localUser;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JobQueue queue;

    @Autowired
    private List<JobHandler> handlers;

    @Autowired
    private Clock clock;

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meters;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void startFromAnEmptyProfile() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM generations WHERE user_id = ?", LocalDevUser.DEV_USER_ID);
        jdbc.update("DELETE FROM usage_counters");
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevUser.DEV_USER_ID);
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

        // Bolum 23.2, and this lane is the only place it can be asked: a real
        // XeLaTeX run with the real fonts. Everything else in this repository
        // measures the CV before it is a PDF, so a template that lays out
        // beautifully and carries no text layer would pass every other test in
        // the suite and reach an applicant tracking system as an empty page.
        //
        // Asserted through the counter the pipeline itself increments rather
        // than by rebuilding the render request here: a reconstruction would
        // be a second opinion about what was printed, and the one that matters
        // is the pipeline's own.
        assertThat(atsDefects()).as("the compiled PDF read back cleanly").isZero();
        assertThat(atsChecks()).as("the check actually ran").isPositive();
    }

    /**
     * <strong>Compact, as a document (Bolum 33.5).</strong>
     *
     * <p>{@code CompactCalibrationIT} proves the seventeen numbers are what
     * the compiler says. It does not prove that a CV comes out of them: the
     * preamble could define a command the renderer never calls, or fail to
     * define one it does, and every calibration probe would still measure
     * cleanly. This is the only place that can tell — a real profile, the real
     * renderer, a real XeLaTeX run.
     *
     * <p>The template is chosen the way a person chooses it: through the
     * profile's own preference. No request field carries one.
     */
    @Test
    void thecompactTemplateProducesArealOnePagePdf() throws Exception {
        seedCareer();
        prefer("compact");

        String jobId = enqueue();
        assertThat(worker().runOne()).as("the queued generation was taken").isTrue();

        String generationId = completedGenerationId(jobId);
        byte[] pdf = download(generationId);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).as("a real document, not an error page").isGreaterThan(2000);
        assertThat(jdbc.queryForObject(
                "SELECT page_count FROM generations WHERE id = ?::uuid",
                Integer.class, generationId)).isEqualTo(1);
        // The measured costs are keyed by it, so a run that quietly fell back
        // to classic would look identical here without this.
        assertThat(jdbc.queryForObject(
                "SELECT engine_version->>'template' FROM generations WHERE id = ?::uuid",
                String.class, generationId)).isEqualTo("compact:v2");
        assertThat(atsDefects()).as("the compiled PDF read back cleanly").isZero();
        assertThat(atsChecks()).as("the check actually ran").isPositive();
    }

    /**
     * <strong>Layer B, as a document (Bolum 33.1, 33.3).</strong>
     *
     * <p>Nobody has ever compiled this geometry, so there is no measured
     * capacity for it: the run is made against an estimate that spends a
     * little less of the page, and the page limit still holds. That last
     * clause is the whole promise, and this is the only lane that can check
     * it — everything else measures a CV before it is a PDF.
     *
     * <p>The cost key carries the sliders, so the row also proves the
     * customization reached the document rather than falling back.
     */
    @Test
    void amovedSliderProducesArealOnePagePdfOnAnEstimate() throws Exception {
        seedCareer();
        moveTheSliders();

        String jobId = enqueue();
        assertThat(worker().runOne()).as("the queued generation was taken").isTrue();

        String generationId = completedGenerationId(jobId);
        byte[] pdf = download(generationId);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).as("a real document, not an error page").isGreaterThan(2000);
        assertThat(jdbc.queryForObject(
                "SELECT page_count FROM generations WHERE id = ?::uuid",
                Integer.class, generationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT engine_version->>'template' FROM generations WHERE id = ?::uuid",
                String.class, generationId))
                .as("the sliders reached the document rather than falling back")
                .isEqualTo("classic:v6:sans-9.5-0.60-1.10");
        assertThat(atsDefects()).as("the compiled PDF read back cleanly").isZero();
    }

    /**
     * Modern, as a document (Bolum 33.5).
     *
     * <p>The one template whose accent is not black, so this is also the only
     * place that compiles a coloured rule at all — a {@code \color} that did
     * not resolve would take the whole document down, and every other lane
     * measures a CV before it is a PDF.
     */
    @Test
    void themodernTemplateProducesArealOnePagePdf() throws Exception {
        seedCareer();
        prefer("modern");

        String jobId = enqueue();
        assertThat(worker().runOne()).as("the queued generation was taken").isTrue();

        String generationId = completedGenerationId(jobId);
        byte[] pdf = download(generationId);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).as("a real document, not an error page").isGreaterThan(2000);
        assertThat(jdbc.queryForObject(
                "SELECT page_count FROM generations WHERE id = ?::uuid",
                Integer.class, generationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT engine_version->>'template' FROM generations WHERE id = ?::uuid",
                String.class, generationId)).isEqualTo("modern:v3");
        assertThat(atsDefects())
                .as("a coloured rule does not disturb the text layer")
                .isZero();
    }

    /**
     * <strong>The same CV as a Word document (Bolum 22.6).</strong>
     *
     * <p>Downloaded from a generation that really ran, so what is checked is
     * the thing a unit test cannot reach: that a document made from a real
     * snapshot -- one Faz D wrote and a compiler set -- opens, and that its
     * words are in the text layer where an applicant tracking system will
     * look for them.
     *
     * <p>The page limit does not travel with it and this does not pretend
     * otherwise: no page count is asserted, because Word decides that and
     * nothing here measures it.
     */
    @Test
    void thesameGenerationDownloadsAsAwordDocument() throws Exception {
        seedCareer();

        String jobId = enqueue();
        assertThat(worker().runOne()).isTrue();
        String generationId = completedGenerationId(jobId);

        byte[] docx = mvc.perform(get("/api/v1/generations/" + generationId + "/download")
                        .param("format", "docx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".docx")))
                .andReturn().getResponse().getContentAsByteArray();

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                        new java.io.ByteArrayInputStream(docx));
                var extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(document)) {

            assertThat(extractor.getText())
                    .as("the bullets this profile was seeded with reach the text layer")
                    .contains("Ran distributed Go services on PostgreSQL");
        }
    }

    /**
     * Bolum 35.3's map offers `source` too and nothing serves it. Named rather
     * than ignored: a client asking for one and silently getting a PDF would
     * ship a .tex button that downloads a PDF.
     */
    @Test
    void aformatNobodyServesIsRefusedRatherThanSubstituted() throws Exception {
        seedCareer();
        String jobId = enqueue();
        worker().runOne();
        String generationId = completedGenerationId(jobId);

        mvc.perform(get("/api/v1/generations/" + generationId + "/download")
                        .param("format", "source"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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

    /** Read as bytes and decoded explicitly: the digest must not depend on a default charset. */
    /** What a person does in the settings screen, as one statement. */
    private void prefer(String templateId) {
        jdbc.update("""
                UPDATE profiles
                SET preferences = jsonb_set(
                        COALESCE(preferences, '{}'::jsonb),
                        '{defaults}',
                        ('{"maxPages":1,"cvLanguage":"en","coverLetterLanguage":"auto",'
                                || '"templateId":"' || ? || '"}')::jsonb,
                        true)
                WHERE user_id = ?""", templateId, LocalDevUser.DEV_USER_ID);
    }

    /** What a person does with the sliders, as one statement. */
    private void moveTheSliders() {
        jdbc.update("""
                UPDATE profiles
                SET preferences = jsonb_set(
                        COALESCE(preferences, '{}'::jsonb),
                        '{defaults}',
                        '{"maxPages":1,"templateId":"classic","cvLanguage":"en","coverLetterLanguage":"auto","appearance":{"fontSizePt":9.5,"marginInches":0.6,"lineSpacing":1.1,"fontFamily":"SANS"}}'::jsonb,
                        true)
                WHERE user_id = ?""", LocalDevUser.DEV_USER_ID);
    }

    private static String posting(String name) {
        try (var in = JobSpecificCvIT.class.getResourceAsStream("/postings/" + name)) {
            if (in == null) {
                throw new IllegalStateException("No posting resource /postings/" + name);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

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

    private double atsChecks() {
        return counter("generation.ats.clean") + counter("generation.ats.defect");
    }

    private double atsDefects() {
        return counter("generation.ats.defect");
    }

    private double counter(String name) {
        var counter = meters.find(name).counter();
        return counter == null ? 0 : counter.count();
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
            var profile = new Profile(LocalDevUser.DEV_USER_ID);
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
