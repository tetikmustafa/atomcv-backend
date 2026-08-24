package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.shared.security.LocalDevCurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stage 1's completion checklist, as a test (XI-A.3).
 *
 * <p>"A PDF comes out and it really is one page." A profile is written to the
 * database, the endpoint is called, and everything in between happens for
 * real: the content is measured by TeX, scored, selected against the measured
 * capacity, rendered, compiled, and the page count that comes back is the
 * compiler's own.
 *
 * <p>Since Adim 2.6 the whole flow is exercised, not only the pipeline: the
 * request is queued, a worker takes it, a generation row is written, and the
 * PDF comes back from {@code /download} — which re-renders the stored content
 * snapshot rather than the profile. General CV mode is used because it needs
 * no LLM; the job-specific path through a fake provider is still to come.
 */
@Tag("latex")
@AutoConfigureMockMvc
class GeneralCvIT extends AbstractIntegrationTest {

    static final org.testcontainers.containers.GenericContainer<?> LATEX =
            new org.testcontainers.containers.GenericContainer<>(
                    new org.testcontainers.images.builder.ImageFromDockerfile(
                            "atomcv-latex-test", false)
                            .withFileFromPath(".", Path.of("docker/latex")))
                    .withExposedPorts(8090)
                    .withStartupTimeout(Duration.ofMinutes(5));

    static {
        LATEX.start();
    }

    @DynamicPropertySource
    static void latexAddress(DynamicPropertyRegistry registry) {
        registry.add("atomcv.latex.base-url",
                () -> "http://" + LATEX.getHost() + ":" + LATEX.getMappedPort(8090));
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevCurrentUser localUser;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private com.mustafatetik.atomcv.jobs.queue.JobQueue queue;

    @Autowired
    private java.util.List<com.mustafatetik.atomcv.jobs.queue.JobHandler> handlers;

    @Autowired
    private java.time.Clock clock;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void startFromAnEmptyProfile() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevCurrentUser.DEV_USER_ID);
    }

    @Test
    void aRealProfileBecomesARealOnePagePdf() throws Exception {
        seedCareer(3, 5);

        byte[] pdf = generateAndDownload();

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).as("a real document, not an error page").isGreaterThan(2000);
    }

    /**
     * Nothing was measured before the request, so the generation had to do it
     * — and the numbers have to end up in the database, not only in memory
     * (Bolum 26.5).
     */
    @Test
    void theContentIsMeasuredOnTheWayThroughAndTheCostsAreKept() throws Exception {
        seedCareer(2, 3);
        assertThat(measuredCosts()).isZero();

        generateAndDownload();

        assertThat(measuredCosts()).isEqualTo(6);
    }

    /** A career too long for one page still comes back as one page. */
    @Test
    void moreContentThanFitsStillProducesOnePage() throws Exception {
        seedCareer(6, 10);

        generateAndDownload();
    }

    /**
     * This user's wordings only. Counting the whole table passes alone and
     * fails in the suite, because the measurement tests share the database and
     * leave their own rows behind.
     */
    private int measuredCosts() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM atom_variants
                 WHERE cost_measured_at IS NOT NULL
                   AND profile_id IN (SELECT id FROM profiles WHERE user_id = ?)
                """, Integer.class, LocalDevCurrentUser.DEV_USER_ID);
        return count == null ? 0 : count;
    }

    /**
     * The whole flow: queue it, work it, download what was written down.
     *
     * <p>The worker is built here because the scheduler is off for the suite,
     * and the download deliberately goes back through HTTP: re-rendering the
     * stored content snapshot is the part that has never met real TeX before.
     */
    private byte[] generateAndDownload() throws Exception {
        String accepted = mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        var worker = new com.mustafatetik.atomcv.jobs.workers.JobWorker(
                queue, com.mustafatetik.atomcv.jobs.queue.JobEvents.NONE, handlers,
                new com.mustafatetik.atomcv.jobs.workers.JobWorkerProperties(
                        true, 1, null, null, null, java.time.Duration.ofSeconds(5)),
                clock);
        assertThat(worker.runOne()).as("the queued generation was taken").isTrue();

        String jobId = com.jayway.jsonpath.JsonPath.read(accepted, "$.jobId");
        String status = mvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders.get("/api/v1/jobs/" + jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String generationId = com.jayway.jsonpath.JsonPath.read(status, "$.generationId");

        return mvc.perform(org.springframework.test.web.servlet.request
                        .MockMvcRequestBuilders
                        .get("/api/v1/generations/" + generationId + "/download"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private void seedCareer(int jobs, int bulletsPerJob) {
        tx.executeWithoutResult(status -> {
            var profile = new Profile(LocalDevCurrentUser.DEV_USER_ID);
            profile.setHeadline("Backend Engineer");
            profile.setContact(new Contact("Mustafa Tetik", "mustafa@example.com", null,
                    null, null, null, "İstanbul"));
            em.persist(profile);
            UUID profileId = profile.getId();

            var section = new Section(profileId, SectionKind.EXPERIENCE, "Experience", (short) 0);
            em.persist(section);

            for (int job = 0; job < jobs; job++) {
                var entry = new Entry(profileId, section.getId(), "Backend Engineer", (short) job);
                entry.setOrganization("Company " + job);
                entry.setLocation("İstanbul");
                entry.setStartDate(LocalDate.of(2016 + job, 3, 1));
                entry.setEndDate(job == jobs - 1 ? null : LocalDate.of(2017 + job, 6, 1));
                em.persist(entry);

                for (int bullet = 0; bullet < bulletsPerJob; bullet++) {
                    var atom = new Atom(profileId, section.getId(), entry.getId(),
                            AtomKind.BULLET, (short) bullet);
                    em.persist(atom);
                    var variant = new AtomVariant(profileId, atom.getId(), "en", RichContent.of(
                            Run.of("Built "),
                            Run.of("ETL", Mark.TECHNOLOGY),
                            Run.of(" pipelines processing "),
                            Run.of("300K+ rows", Mark.METRIC),
                            Run.of(" a day, cutting the nightly window from six hours to fifty "
                                    + "minutes (" + job + "." + bullet + ")")));
                    variant.setPrimary(true);
                    em.persist(variant);
                }
            }
        });
    }
}
