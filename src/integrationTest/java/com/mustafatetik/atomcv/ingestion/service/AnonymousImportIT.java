package com.mustafatetik.atomcv.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.service.SessionCookies;
import com.mustafatetik.atomcv.identity.service.SessionStore;
import com.mustafatetik.atomcv.ingestion.normalization.ProfileNormalizer;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile;
import com.mustafatetik.atomcv.ingestion.structuring.ProfileStructuring;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobOwner;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobRepository;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.service.EphemeralProfile;
import com.mustafatetik.atomcv.profile.service.EphemeralProfileStore;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Bolum 9's promise, end to end: somebody who has not signed up can import a
 * CV, and leaves nothing behind.
 *
 * <p><strong>The second half is what this class exists for.</strong> The first
 * — a 202 and a job — would be satisfied by writing an anonymous profile into
 * Postgres with a nullable owner and a nightly cleanup, which keeps the promise
 * on paper and breaks it in a backup. So the assertion is not that the store
 * was used; it is that the tables a persistent import writes hold exactly what
 * they held before.
 *
 * <p><strong>The real handler decides.</strong> An earlier draft of this class
 * called the ephemeral writer itself, which made it pass with the branch
 * deliberately broken — it was asserting about the writer, and the writer was
 * never the part that could get this wrong. Only the LLM stage is stubbed:
 * structuring has no provider under {@code local}, and it has its own tests.
 * Everything from the cookie to Redis is the real thing.
 */
@AutoConfigureMockMvc
class AnonymousImportIT extends AbstractIntegrationTest {

    private static final String LOCAL_ADDRESS = "127.0.0.1";

    /** Everything a persistent import writes, and an anonymous one must not. */
    private static final List<String> PROFILE_TABLES =
            List.of("profiles", "sections", "entries", "atoms", "atom_variants");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JobRepository jobs;

    @Autowired
    private SessionStore sessions;

    @Autowired
    private SessionCookies cookies;

    @Autowired
    private ProfileNormalizer normalizer;

    @Autowired
    private ProfileWriter writer;

    @Autowired
    private EphemeralProfileWriter ephemeral;

    @Autowired
    private EphemeralProfileStore store;

    @Autowired
    private QuotaService quotas;

    @Autowired
    private JobQueue queue;

    @Autowired
    private Clock clock;

    /**
     * Spring built one too, and this class does not use it — the point of
     * holding the reference is that a constructor the context cannot satisfy
     * fails here rather than at the first upload in production.
     */
    @Autowired
    private ProfileExtractionJobHandler wiredBySpring;

    private final ProfileStructuring structuring = mock(ProfileStructuring.class);

    private ProfileExtractionJobHandler handler;

    private Session session;

    @BeforeEach
    void anAnonymousCaller() {
        jdbc.update("DELETE FROM jobs WHERE type = 'profile_extract'");
        jdbc.update("DELETE FROM usage_counters WHERE metric = 'profile_extract'");
        session = sessions.createAnonymous();
        handler = new ProfileExtractionJobHandler(
                structuring, normalizer, writer, ephemeral, quotas, queue, clock);
    }

    @Test
    void thehandlerSpringBuiltHasEverythingItNeeds() {
        assertThat(wiredBySpring).isNotNull();
    }

    /**
     * The upload itself. Nothing on this path is user-scoped any more, and
     * that is the change: the endpoint used to call {@code currentUser
     * .require()}, so an anonymous caller got a sign-up prompt instead of a
     * job.
     */
    @Test
    void ananonymousCallerCanImportACvAndTheJobIsTheirs() throws Exception {
        mvc.perform(upload().cookie(sessionCookie()))
                .andExpect(status().isAccepted());

        Job queued = onlyJob();
        assertThat(queued.getOwnerId()).isNull();
        assertThat(queued.getAnonSessionId()).isEqualTo(session.id());
    }

    /**
     * Bolum 44.1: an anonymous upload is counted against the address, because
     * a session is a cookie and counting by one would hand an unlimited
     * allowance to whoever clears theirs.
     */
    @Test
    void theunitIsSpentByTheAddressAndByNoUser() throws Exception {
        mvc.perform(upload().cookie(sessionCookie()))
                .andExpect(status().isAccepted());

        assertThat(unitsSpentBy("ip", LOCAL_ADDRESS)).isEqualTo(1);
        assertThat(subjectTypesCharged()).containsExactly("ip");
    }

    /**
     * <strong>The promise, asserted as a database that did not change.</strong>
     *
     * <p>Counted rather than asserted empty: the {@code local} profile seeds a
     * development profile at startup, so "no rows anywhere" would fail for a
     * reason that has nothing to do with this. What must be zero is the
     * difference.
     */
    @Test
    void thefinishedProfileIsInRedisAndAddsNoRowAnywhere() throws Exception {
        Map<String, Integer> before = profileTableCounts();

        JobOutcome outcome = runTheExtraction(uploadAndClaim(), Result.ok(oneEntry()));

        assertThat(outcome).isInstanceOf(JobOutcome.Completed.class);
        Optional<EphemeralProfile> stored = store.find(profileOfTheSession());
        assertThat(stored).isPresent();
        assertThat(stored.get().atoms()).isNotEmpty();

        assertThat(profileTableCounts()).isEqualTo(before);
        // And nothing carries its id either — a write under a different owner
        // would leave rows behind while these counts still moved together.
        assertThat(rowsCarrying(profileOfTheSession())).isZero();
    }

    /**
     * And the person is told where their profile is, in the same terminal
     * event an account gets: Bolum 30.6's client renders one screen from it,
     * and an anonymous run answering without a {@code profileId} would have
     * nowhere to send them next.
     */
    @Test
    void theterminalEventNamesTheProfileTheStoreHolds() throws Exception {
        var completed = (JobOutcome.Completed)
                runTheExtraction(uploadAndClaim(), Result.ok(oneEntry()));

        assertThat(completed.result())
                .containsEntry("profileId", profileOfTheSession().id().toString());
    }

    /**
     * It is scoped by the session rather than merely stored under it: the
     * profile id is derived from the session id, so another caller's ref
     * addresses another key and finds nothing.
     */
    @Test
    void anotherAnonymousCallerFindsNothingUnderTheirOwnRef() throws Exception {
        runTheExtraction(uploadAndClaim(), Result.ok(oneEntry()));

        ProfileRef somebodyElse =
                ProfileRef.ephemeral(AnonymousSessionId.of(sessions.createAnonymous().id()));

        assertThat(store.find(somebodyElse)).isEmpty();
    }

    /**
     * <strong>Bolum 44.2, and the reason the payload carries a subject at
     * all.</strong> The unit was spent by an address, and the worker runs
     * outside the request that knew it — without the subject travelling in the
     * payload there is nothing to give it back to, and a person whose
     * extraction failed would keep paying for it.
     */
    @Test
    void arefusedAnonymousExtractionGivesTheAddressItsUnitBack() throws Exception {
        Job job = uploadAndClaim();
        assertThat(unitsSpentBy("ip", LOCAL_ADDRESS)).isEqualTo(1);

        runTheExtraction(job, Result.err(new PipelineError.NothingExtracted()));

        assertThat(unitsSpentBy("ip", LOCAL_ADDRESS)).isZero();
    }

    // -- driving it --------------------------------------------------------

    private Job uploadAndClaim() throws Exception {
        mvc.perform(upload().cookie(sessionCookie())).andExpect(status().isAccepted());
        return onlyJob();
    }

    private JobOutcome runTheExtraction(Job job, Result<ExtractedProfile> structured) {
        when(structuring.structure(any(), any(), any())).thenReturn(structured);
        return handler.handle(job, progress -> { });
    }

    private ProfileRef profileOfTheSession() {
        return ProfileRef.ephemeral(AnonymousSessionId.of(session.id()));
    }

    // -- fixtures ----------------------------------------------------------

    /** One entry with one bullet: enough to have something to look for. */
    private static ExtractedProfile oneEntry() {
        var atom = new ExtractedProfile.ExtractedAtom(
                "Designed the first published algorithm for a machine.", null,
                List.of(), List.of(), List.of("algorithms"), List.of(), List.of(), List.of());
        var entry = new ExtractedProfile.ExtractedEntry(
                "Analytical Engine programmer", "Menabrea", "London",
                "1842-01", "1843-12", List.of(atom));
        return new ExtractedProfile("en", 0.99, ExtractedProfile.ExtractedContact.EMPTY,
                List.of(new ExtractedProfile.ExtractedSection(
                        SectionKind.EXPERIENCE, "Experience", List.of(entry))),
                List.of());
    }

    private Cookie sessionCookie() {
        return new Cookie(cookies.name(), session.id());
    }

    private Job onlyJob() {
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT id FROM jobs WHERE type = 'profile_extract'");
        assertThat(rows).hasSize(1);
        // Read back as the caller, not around them: an anonymous owner
        // reaching their own job is half of what this slice made possible.
        JobOwner owner = JobOwner.anonymous(AnonymousSessionId.of(session.id()));
        return jobs.findById(owner, (UUID) rows.get(0).get("id")).orElseThrow();
    }

    private Map<String, Integer> profileTableCounts() {
        var counts = new LinkedHashMap<String, Integer>();
        for (String table : PROFILE_TABLES) {
            counts.put(table, rowsIn(table));
        }
        return counts;
    }

    private int rowsCarrying(ProfileRef profile) {
        int found = 0;
        for (String table : PROFILE_TABLES) {
            String column = "profiles".equals(table) ? "id" : "profile_id";
            Integer rows = jdbc.queryForObject(
                    "SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                    Integer.class, profile.id());
            found += rows == null ? 0 : rows;
        }
        return found;
    }

    private int rowsIn(String table) {
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return rows == null ? 0 : rows;
    }

    private int unitsSpentBy(String subjectType, String subjectId) {
        Integer used = jdbc.queryForObject("""
                SELECT coalesce(sum(count), 0) FROM usage_counters
                WHERE metric = 'profile_extract'
                  AND subject_type = ? AND subject_id = ?""",
                Integer.class, subjectType, subjectId);
        return used == null ? 0 : used;
    }

    private List<String> subjectTypesCharged() {
        return jdbc.queryForList("""
                SELECT DISTINCT subject_type FROM usage_counters
                WHERE metric = 'profile_extract'""", String.class);
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
            upload() throws IOException {
        var builder = multipart("/api/v1/profile/import");
        builder.file(new MockMultipartFile("file", "cv.pdf", "application/pdf", pdf()));
        return builder;
    }

    private static byte[] pdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Ada Lovelace, Analytical Engine programmer, London. "
                        + "Engineered the first published algorithm intended for a machine, "
                        + "and translated the memoir of Menabrea in 1843.");
                content.endText();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
