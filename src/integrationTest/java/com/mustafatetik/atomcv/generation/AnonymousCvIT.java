package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mustafatetik.atomcv.AbstractLatexTest;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.service.SessionCookies;
import com.mustafatetik.atomcv.identity.service.SessionStore;
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
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * A whole CV made by somebody who has not signed up, through a real compiler.
 *
 * <p><strong>The gap the other lanes named and could not close.</strong>
 * {@code AnonymousGenerationIT} asserts both ends of the flow and says why it
 * cannot assert the middle: the ordinary integration lane registers no LLM
 * provider and has no TeX. This lane has both — {@code local-fake} synthesises
 * the model's answers and the compiler is real — so it is the only place the
 * anonymous flow's actual promise can be put to the test. That promise is that
 * the process is <em>identical</em> to an account's, and the way to check an
 * identical process is to run it and look at the PDF.
 *
 * <p><strong>And the page guarantee, which nothing else verifies.</strong>
 * Bolum 9's flow was allowed to keep it because {@code measureMissing} runs
 * inside the pipeline and is profile-scoped, so an anonymous profile's wordings
 * are measured like anybody else's rather than estimated. That was an argument
 * from reading the code; here it is a row in {@code atom_variants} carrying a
 * cost it did not have before the generation ran.
 */
@Tag("latex")
@AutoConfigureMockMvc
@ActiveProfiles({"local", "local-fake"})
class AnonymousCvIT extends AbstractLatexTest {

    private static final String POSTING = posting("senior-backend-go.txt");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SessionStore sessions;

    @Autowired
    private SessionCookies cookies;

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

    private Session session;

    @BeforeEach
    void anAnonymousVisitorWithACareer() {
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM usage_counters");
        jdbc.update("DELETE FROM profiles WHERE expires_at IS NOT NULL");
        session = sessions.createAnonymous();
        seedCareer();
    }

    /**
     * The whole flow, and the only assertion that can settle "identical to an
     * account's": a real XeLaTeX run produces a real one-page PDF for a caller
     * with no account.
     */
    @Test
    void apostingBecomesARealOnePagePdfWithoutAnAccount() throws Exception {
        String jobId = enqueue();

        assertThat(worker().runOne()).as("the queued generation was taken").isTrue();

        String generationId = completedGenerationId(jobId);
        byte[] pdf = download(generationId);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).as("a real document, not an error page").isGreaterThan(2000);
        assertThat(jdbc.queryForObject(
                "SELECT page_count FROM generations WHERE id = ?::uuid",
                Integer.class, generationId)).isEqualTo(1);
    }

    /**
     * <strong>The page guarantee, as a measurement rather than an argument.</strong>
     * The wordings are seeded with no cost at all; a generation that estimated
     * them would produce the same PDF here and leave these columns empty, and the
     * promise would be broken in the one way nothing else would notice.
     */
    @Test
    void thefirstAnonymousGenerationMeasuresRatherThanEstimates() throws Exception {
        assertThat(measuredWordings()).as("seeded with no costs").isZero();
        enqueue();

        // Asserted, because without it this case passed for the wrong reason:
        // a worker with nothing to take measures nothing either.
        assertThat(worker().runOne()).as("the generation ran").isTrue();

        assertThat(measuredWordings())
                .as("measureMissing ran for a profile nobody owns")
                .isEqualTo(6);
    }

    /**
     * And the row it leaves behind belongs to nobody, which is what makes the
     * sweep able to take it: {@code generations.profile_id} cascades from a
     * profile that expires (§ 51.6.1).
     */
    @Test
    void therecordBelongsToNobodyAndToThatSessionsProfile() throws Exception {
        String jobId = enqueue();
        worker().runOne();
        String generationId = completedGenerationId(jobId);

        var row = jdbc.queryForMap(
                "SELECT user_id, profile_id FROM generations WHERE id = ?::uuid", generationId);
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("profile_id")).isEqualTo(profileId());
    }

    // -- fixtures ------------------------------------------------------------

    /**
     * The same two jobs {@code JobSpecificCvIT} seeds, under a profile nobody
     * owns. Deliberately the same career: if the anonymous page came out
     * differently from the account's, the difference would be in the flow rather
     * than in the CV.
     */
    private void seedCareer() {
        tx.executeWithoutResult(status -> {
            var profile = Profile.forAnonymousSession(
                    profileId(), Instant.now(clock).plus(Duration.ofHours(2)));
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

    /**
     * No challenge token, and that is worth a sentence rather than a silence:
     * § 44.4's check is real and this lane has no secret, so
     * {@code ChallengeConfig} hands out a challenge that passes everything.
     * {@code CallerChallengeTest} is where the refusal is exercised.
     */
    private String enqueue() throws Exception {
        String accepted = mvc.perform(post("/api/v1/generations")
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobDescription\":" + quoted(POSTING) + "}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(accepted, "$.jobId");
    }

    private String completedGenerationId(String jobId) throws Exception {
        String status = mvc.perform(get("/api/v1/jobs/" + jobId).cookie(cookie()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(status, "$.status"))
                .as("the generation finished")
                .isEqualTo("completed");
        return JsonPath.read(status, "$.generationId");
    }

    private byte[] download(String generationId) throws Exception {
        return mvc.perform(get("/api/v1/generations/" + generationId + "/download")
                        .cookie(cookie()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private int measuredWordings() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM atom_variants WHERE profile_id = ?"
                        + " AND render_costs <> '{}'::jsonb",
                Integer.class, profileId());
    }

    private JobWorker worker() {
        return new JobWorker(queue, JobEvents.NONE, handlers,
                new JobWorkerProperties(true, 1, null, null, null, Duration.ofSeconds(5)),
                clock);
    }

    private Cookie cookie() {
        return new Cookie(cookies.name(), session.id());
    }

    private UUID profileId() {
        return ProfileRef.ephemeral(AnonymousSessionId.of(session.id())).id();
    }

    private static String posting(String name) {
        try (var in = AnonymousCvIT.class.getResourceAsStream("/postings/" + name)) {
            if (in == null) {
                throw new IllegalStateException("No posting resource /postings/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }
}
