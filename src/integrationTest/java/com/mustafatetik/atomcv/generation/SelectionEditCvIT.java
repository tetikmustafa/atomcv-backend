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
 * A hand edit, all the way to a PDF (Bolum 24.4).
 *
 * <p><strong>The gap the other lanes named and could not close.</strong>
 * {@code SelectionEditApiIT} proves what the request did and
 * {@code SelectionEditHandlerTest} proves what the row becomes, and neither
 * can answer the only question the feature is actually about: does the
 * document change, and does it still fit. That needs a real compiler, which
 * lives here.
 *
 * <p><strong>No model runs in any of this.</strong> A manual toggle re-runs
 * selection, the renderer and the compiler and asks nothing of Faz D beyond
 * the sentences it already wrote — so unlike every other case in this lane,
 * the second half of each test below would produce the same result with no LLM
 * configured at all. That is the claim being checked, not a limitation of the
 * setup.
 */
@Tag("latex")
@AutoConfigureMockMvc
@ActiveProfiles({"local", "local-fake"})
class SelectionEditCvIT extends AbstractLatexTest {

    private static final String POSTING = posting("senior-backend-go.txt");

    /** Unmistakable in a PDF and in a JSONB column, and not a word the seed uses. */
    private static final String PLANTED = "Reduced quarterly reconciliation toil substantially";

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

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void startFromAnEmptyProfile() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM jobs");
        jdbc.update("DELETE FROM generations WHERE user_id = ?", LocalDevUser.DEV_USER_ID);
        jdbc.update("DELETE FROM usage_counters");
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevUser.DEV_USER_ID);
        seedCareer();
    }

    /**
     * The whole promise of Bolum 24, as a document: a bullet is taken off, a
     * real XeLaTeX run produces a real PDF, and the page limit still holds.
     */
    @Test
    void abulletIsTakenOffAndTheDocumentIsMadeAgain() throws Exception {
        String first = generate();
        UUID dropped = anAtomOnThePage(first);

        String edited = edit(first, "{\"exclude\":[\"" + dropped + "\"]}");

        assertThat(edited).isNotEqualTo(first);
        assertThat(selectedAtoms(edited))
                .as("the bullet the person took off is not on the page")
                .doesNotContain(dropped)
                .isNotEmpty();

        byte[] pdf = download(edited);
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).as("a real document, not an error page").isGreaterThan(2000);
        assertThat(pageCount(edited)).isEqualTo(1);
    }

    /**
     * <strong>Why V11 exists, and the one place it is proved end to end.</strong>
     *
     * <p>Faz D's wording is planted rather than produced, and that is
     * deliberate: this lane's fake provider answers {@code bullet_rewrite}
     * with a synthetic sentence that the validator refuses, so a real run here
     * prints the original and leaves {@code rewritten_content} empty
     * (docs/notes). Waiting for a fixture would mean this path is exercised by
     * unit tests and nothing else — the shape § 51.7 calls unverified wiring.
     *
     * <p>What is being checked is therefore the carry itself, through the real
     * worker, the real renderer and a real compiler: a sentence that was on the
     * parent row is on the child row and <em>in the document that comes out</em>.
     * Without the column the edit would print what the person typed instead —
     * a change they did not ask for, in a CV they are about to send somebody.
     */
    @Test
    void thebulletsThatStayedKeepTheWordingFazDgaveThem() throws Exception {
        String first = generate();
        List<UUID> onThePage = selectedAtoms(first);
        assertThat(onThePage).as("two bullets, so one can go and one can stay").hasSizeGreaterThan(1);
        UUID dropped = onThePage.get(0);
        UUID survivor = onThePage.get(1);
        plantFazDwording(first, survivor, PLANTED);

        String edited = edit(first, "{\"exclude\":[\"" + dropped + "\"]}");

        assertThat(selectedAtoms(edited)).contains(survivor);
        assertThat(rewrittenContent(edited))
                .as("the wording came across to the row the edit wrote")
                .contains(survivor.toString())
                .contains(PLANTED);
        assertThat(contentSnapshot(edited))
                .as("and into the document that was actually printed")
                .contains(PLANTED);
        assertThat(rewrittenContent(edited))
                .as("the atom that went is not carried")
                .doesNotContain(dropped.toString());
    }

    /**
     * The edited row is retired rather than rewritten, so the CV that was
     * already sent to somebody is still downloadable (EK D.6.3).
     */
    @Test
    void theeditedGenerationIsRetiredAndStillDownloads() throws Exception {
        String first = generate();
        UUID dropped = anAtomOnThePage(first);

        edit(first, "{\"exclude\":[\"" + dropped + "\"]}");

        assertThat(jdbc.queryForObject(
                "SELECT status FROM generations WHERE id = ?::uuid", String.class, first))
                .isEqualTo("superseded");
        assertThat(download(first).length).isGreaterThan(2000);
    }

    /**
     * And putting it back is the same road in the other direction, which is
     * what "twenty edits and the page still holds" rests on.
     */
    @Test
    void abulletCanBePutBackAndThePageStillHolds() throws Exception {
        String first = generate();
        UUID dropped = anAtomOnThePage(first);
        String without = edit(first, "{\"exclude\":[\"" + dropped + "\"]}");

        String restored = edit(without, "{\"include\":[\"" + dropped + "\"]}");

        assertThat(selectedAtoms(restored)).contains(dropped);
        assertThat(pageCount(restored)).isEqualTo(1);
        assertThat(download(restored).length).isGreaterThan(2000);
    }

    // -- fixtures ------------------------------------------------------------

    /** A finished generation, through the queue and a real compiler. */
    private String generate() throws Exception {
        String accepted = mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobDescription\":" + quoted(POSTING) + "}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertThat(worker().runOne()).as("the queued generation was taken").isTrue();
        return completedGenerationId(JsonPath.read(accepted, "$.jobId"));
    }

    /** One edit, through the same queue and the same compiler. */
    private String edit(String generationId, String body) throws Exception {
        String accepted = mvc.perform(
                        post("/api/v1/generations/" + generationId + "/selection")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        assertThat(worker().runOne()).as("the queued edit was taken").isTrue();
        return completedGenerationId(JsonPath.read(accepted, "$.jobId"));
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

    private UUID anAtomOnThePage(String generationId) {
        List<UUID> selected = selectedAtoms(generationId);
        assertThat(selected).as("something reached the page to be taken off it")
                .isNotEmpty();
        return selected.get(0);
    }

    /**
     * Read out of the snapshot rather than out of the PDF: the ids are what an
     * edit names, and a PDF carries text.
     */
    private List<UUID> selectedAtoms(String generationId) {
        // Read back as text and parsed here: `->>` yields text, and asking
        // JdbcTemplate for a UUID column that is not one fails with a cast.
        return jdbc.queryForList(
                        "SELECT jsonb_array_elements(selection_state->'selected')->>'atomId' "
                                + "FROM generations WHERE id = ?::uuid",
                        String.class, generationId)
                .stream().map(UUID::fromString).toList();
    }

    /**
     * A Faz D answer, written straight onto the parent row.
     *
     * <p>The shape is {@code RewrittenContent}'s own: a map of atom id to
     * {@code RichContent}, which is a list of runs. Written as SQL rather than
     * through the entity because what is under test is what the <em>worker</em>
     * reads back out of the column.
     */
    private void plantFazDwording(String generationId, UUID atomId, String text) {
        jdbc.update("UPDATE generations SET rewritten_content = ?::jsonb WHERE id = ?::uuid",
                "{\"byAtom\":{\"" + atomId + "\":{\"runs\":[{\"text\":\"" + text
                        + "\",\"marks\":[],\"href\":null}]}}}",
                generationId);
    }

    private String contentSnapshot(String generationId) {
        return jdbc.queryForObject(
                "SELECT content_snapshot::text FROM generations WHERE id = ?::uuid",
                String.class, generationId);
    }

    private String rewrittenContent(String generationId) {
        return jdbc.queryForObject(
                "SELECT rewritten_content::text FROM generations WHERE id = ?::uuid",
                String.class, generationId);
    }

    private Integer pageCount(String generationId) {
        return jdbc.queryForObject(
                "SELECT page_count FROM generations WHERE id = ?::uuid",
                Integer.class, generationId);
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

    private static String posting(String name) {
        try (var in = SelectionEditCvIT.class.getResourceAsStream("/postings/" + name)) {
            if (in == null) {
                throw new IllegalStateException("No posting resource /postings/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }

    /** The same two jobs {@code JobSpecificCvIT} seeds, for the same reasons. */
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
