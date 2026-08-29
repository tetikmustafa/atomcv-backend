package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.repository.ProfileRepository;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/v1/generations} (F-020, EK D.8.7, Bolum 41.2).
 *
 * <p>The endpoint was on Bolum 35's resource map from the beginning and was
 * never written, so {@code capabilities.canSaveHistory} was true against
 * nothing a person could open. It also cost the account-deletion screen a
 * number: that screen has to say what goes, and a wrong count in the one
 * irreversible place is worse than no count.
 *
 * <p><strong>Cursor, not offset.</strong> EK D.8.7 settled it and the reason
 * is here as a test: this list grows from the top, and an offset page two
 * taken after a new row lands repeats a row and hides another.
 */
@AutoConfigureMockMvc
class GenerationListApiIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

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

    @BeforeEach
    void anemptyHistory() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM generations WHERE user_id = ?", LocalDevUser.DEV_USER_ID);
    }

    @Test
    void anemptyHistoryIsAnEmptyListAndNotAnError() throws Exception {
        mvc.perform(get("/api/v1/generations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void thelistIsNewestFirstAndCarriesTheSummaryFields() throws Exception {
        UUID older = save().getId();
        UUID newer = save().getId();

        mvc.perform(get("/api/v1/generations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].generationId").value(newer.toString()))
                .andExpect(jsonPath("$.items[1].generationId").value(older.toString()))
                .andExpect(jsonPath("$.items[0].pageCount").value(1))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].status").exists());
    }

    /**
     * <strong>Nothing the user pasted, here either.</strong> The list is a new
     * place for the posting to leak into and it does not: absolute rule 4 and
     * the rule {@code GenerationResponse} already keeps.
     */
    @Test
    void thelistCarriesNoPostingAndNoLetter() throws Exception {
        save();

        mvc.perform(get("/api/v1/generations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].jobDescription").doesNotExist())
                .andExpect(jsonPath("$.items[0].coverLetter").doesNotExist())
                // Whether there is one is a boolean; the letter itself is not
                // list material even though it is the person's own.
                .andExpect(jsonPath("$.items[0].hasCoverLetter").value(false));
    }

    /**
     * F-022: the row is labelled, and by the two fields Faz A read rather than
     * by the posting.
     *
     * <p>The list shipped without a label on purpose — every label a history
     * screen wants is read out of the posting, and putting one here would have
     * answered absolute rule 4's question by accident instead of on the record.
     * The frontend built the screen, saw that a row saying "one page · a date ·
     * strong" tells nobody which application it was, and asked. Bolum 57 now
     * says where the exception stops, and this is the half of it that runs.
     */
    @Test
    void arowIsLabelledByWhatFazAReadAndNotByThePosting() throws Exception {
        Generation generation = save();
        generation.recordPosting("A long posting nobody should ever see again",
                "hash", analysis("Backend Engineer", "Atlas Yazilim"));
        generations.save(user(), generation);

        mvc.perform(get("/api/v1/generations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].roleTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$.items[0].companyName").value("Atlas Yazilim"))
                // The line Bolum 57 draws: enough to name the row, never the
                // posting it was named from.
                .andExpect(jsonPath("$.items[0].jobDescription").doesNotExist());
    }

    /**
     * Absent rather than empty, and the two are independent.
     *
     * <p>{@code JobAnalysis} normalises a missing title or company to {@code ""},
     * so without {@code blankToNull} a general-mode row would carry two empty
     * strings and the screen would render a label that says nothing. F-022
     * asked for the field to be gone in that case, which is also F-010's rule.
     */
    @Test
    void arowWithNothingToNameItCarriesNoLabelRatherThanAnEmptyOne() throws Exception {
        save();
        Generation named = save();
        named.recordPosting("A posting that never says who is offering the work",
                "hash", analysis("Backend Engineer", null));
        generations.save(user(), named);

        mvc.perform(get("/api/v1/generations"))
                .andExpect(status().isOk())
                // Newest first: the one that named the work but not the company.
                .andExpect(jsonPath("$.items[0].roleTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$.items[0].companyName").doesNotExist())
                // General mode. There was no posting, so there is nothing to
                // read a label out of.
                .andExpect(jsonPath("$.items[1].roleTitle").doesNotExist())
                .andExpect(jsonPath("$.items[1].companyName").doesNotExist());
    }

    /**
     * The whole reason for a cursor. Followed to the end it visits every row
     * exactly once — no repeat, no gap — and that is the property offset
     * pagination loses the moment a row lands on top mid-walk.
     */
    @Test
    void followingTheCursorVisitsEveryRowExactlyOnce() throws Exception {
        List<UUID> saved = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            saved.add(save().getId());
        }

        List<UUID> walked = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 10 && (page == 0 || cursor != null); page++) {
            JsonNode body = JSON.readTree(mvc.perform(get("/api/v1/generations")
                            .param("limit", "2")
                            .param("cursor", cursor == null ? "" : cursor))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            body.get("items").forEach(item ->
                    walked.add(UUID.fromString(item.get("generationId").asText())));
            cursor = body.hasNonNull("nextCursor") ? body.get("nextCursor").asText() : null;
        }

        assertThat(walked).doesNotHaveDuplicates();
        assertThat(walked).containsExactlyInAnyOrderElementsOf(saved);
        assertThat(cursor).as("the walk ends without a cursor").isNull();
    }

    /**
     * The tie-break, which is the half a cursor on {@code created_at} alone
     * would get wrong. Five rows written in one transaction share a timestamp
     * often enough that this is not a contrived case — Faz G's edit loop makes
     * them a second apart, and the clock's resolution does the rest.
     */
    @Test
    void rowsSharingATimestampAreNotSkipped() throws Exception {
        for (int i = 0; i < 4; i++) {
            save();
        }
        jdbc.update("UPDATE generations SET created_at = now() WHERE user_id = ?",
                LocalDevUser.DEV_USER_ID);

        JsonNode first = JSON.readTree(mvc.perform(get("/api/v1/generations")
                        .param("limit", "2"))
                .andReturn().getResponse().getContentAsString());
        JsonNode second = JSON.readTree(mvc.perform(get("/api/v1/generations")
                        .param("limit", "2")
                        .param("cursor", first.get("nextCursor").asText()))
                .andReturn().getResponse().getContentAsString());

        List<String> walked = new ArrayList<>();
        first.get("items").forEach(item -> walked.add(item.get("generationId").asText()));
        second.get("items").forEach(item -> walked.add(item.get("generationId").asText()));

        assertThat(walked).doesNotHaveDuplicates().hasSize(4);
    }

    /**
     * Absolute rule 3. The listing is the one endpoint here that names no id
     * of its own, so the scope is the whole of its defence — a missing
     * {@code user_id} would hand somebody the history of everyone.
     */
    @Test
    void anotherUsersHistoryIsNotInTheList() throws Exception {
        save();
        UUID stranger = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, "stranger-" + UUID.randomUUID() + "@example.com");
        UUID strangersProfile = profiles.save(UserContext.of(stranger),
                new Profile(stranger)).getId();
        UUID theirs = generations.save(UserContext.of(stranger),
                new Generation(stranger, strangersProfile, options(), selection(), engine()))
                .getId();

        mvc.perform(get("/api/v1/generations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].generationId").value(
                        org.hamcrest.Matchers.not(theirs.toString())));
    }

    /** The count is of the history, not of the page — the deletion screen asks it. */
    @Test
    void thetotalCountsEverythingAndNotJustThePage() throws Exception {
        for (int i = 0; i < 3; i++) {
            save();
        }

        mvc.perform(get("/api/v1/generations").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(3));
    }

    /**
     * A cursor is opaque and a client only ever echoes one back, so a broken
     * one is the client's mistake and is answered as one. Left alone it would
     * reach the catch-all and say the server was at fault.
     */
    @Test
    void acursorThatWasNotOursIsRefusedAsTheClientsMistake() throws Exception {
        mvc.perform(get("/api/v1/generations").param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields").value(
                        org.hamcrest.Matchers.contains("cursor")));
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private Generation save() {
        var record = new Generation(
                LocalDevUser.DEV_USER_ID, profileId(), options(), selection(), engine());
        record.setPageCount(1);
        return generations.save(user(), record);
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

    private static Map<String, Object> options() {
        var options = new LinkedHashMap<String, Object>();
        options.put("templateId", "classic");
        options.put("maxPages", 1);
        options.put("cvLanguage", "en");
        return options;
    }

    private static StoredSelection selection() {
        return StoredSelection.of(new SelectionState(List.of(), List.of(),
                        new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 0.0)),
                "en", TemplateCustomization.CLASSIC);
    }

    private static JobAnalysis analysis(String title, String company) {
        return new JobAnalysis(
                new JobAnalysis.Role(title, null, null, null, null),
                new JobAnalysis.Company(company, null),
                List.of(), List.of(), List.of(), List.of(),
                new JobAnalysis.ExperienceYears(null, null),
                List.of(), null, "en", 0.9, List.of());
    }

    private static EngineVersion engine() {
        return new EngineVersion(EngineVersion.PIPELINE, "default", "classic:v1",
                Map.of("job_analysis", "v1"));
    }
}
