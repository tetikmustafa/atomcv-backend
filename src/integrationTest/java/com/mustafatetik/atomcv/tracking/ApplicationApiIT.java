package com.mustafatetik.atomcv.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.tracking.domain.Application;
import com.mustafatetik.atomcv.tracking.domain.ApplicationStatus;
import com.mustafatetik.atomcv.tracking.repository.ApplicationRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Keeping track of where a CV went (Bolum 55, Bolum 35.3).
 *
 * <p>The endpoint is deliberately dull, so what is worth testing is not the
 * four verbs but the two things that are easy to get wrong on a resource a
 * person edits in place: whose rows they are, and what happens when two tabs
 * disagree.
 */
@AutoConfigureMockMvc
class ApplicationApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevUser localUser;

    @Autowired
    private ApplicationRepository applications;

    @BeforeEach
    void startFromAnEmptyList() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM applications");
    }

    // ── the ordinary road ─────────────────────────────────────────────────

    @Test
    void anapplicationIsRecordedAndComesBack() throws Exception {
        String created = mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme Payments\",\"position\":\"Backend Engineer\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(created, "$.status"))
                .as("somebody who just pressed the button has applied")
                .isEqualTo("applied");
        assertThat((String) JsonPath.read(created, "$.appliedAt"))
                .as("and they applied today")
                .isNotNull();

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].company").value("Acme Payments"));
    }

    @Test
    void thelistIsNewestFirst() throws Exception {
        record("Older", "Engineer", "2026-01-05");
        record("Newer", "Engineer", "2026-03-20");

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company").value("Newer"))
                .andExpect(jsonPath("$[1].company").value("Older"));
    }

    @Test
    void astatusMovesForward() throws Exception {
        var recorded = record("Acme", "Engineer", null);

        mvc.perform(patch("/api/v1/applications/" + recorded.getId())
                        .header(HttpHeaders.IF_MATCH, "\"" + recorded.getVersion() + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"interview\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("interview"))
                .andExpect(jsonPath("$.company").value("Acme"));
    }

    /**
     * A company that reopens a closed process is not a data error. Nothing
     * here forbids a transition, and a tracker that argued with its user about
     * what happened to them would be worse than one that believed them.
     */
    @Test
    void astatusMovesBackwardsToo() throws Exception {
        var recorded = record("Acme", "Engineer", null);
        recorded.setStatus(ApplicationStatus.REJECTED);
        var rejected = applications.save(user(), recorded);

        mvc.perform(patch("/api/v1/applications/" + rejected.getId())
                        .header(HttpHeaders.IF_MATCH, "\"" + rejected.getVersion() + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"interview\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("interview"));
    }

    // ── what a PATCH must not do ──────────────────────────────────────────

    /**
     * The reason this is a PATCH: a screen moving one row from applied to
     * interview should not have to send the notes back with it and risk
     * overwriting an edit made in another tab.
     */
    @Test
    void anomittedFieldIsLeftAlone() throws Exception {
        var recorded = record("Acme", "Engineer", null);
        recorded.setNotes("Referred by a friend");
        var withNotes = applications.save(user(), recorded);

        mvc.perform(patch("/api/v1/applications/" + withNotes.getId())
                        .header(HttpHeaders.IF_MATCH, "\"" + withNotes.getVersion() + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"offer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Referred by a friend"));
    }

    /** And null cannot mean both "leave them" and "empty them". */
    @Test
    void clearingTheNotesTakesAwordOfItsOwn() throws Exception {
        var recorded = record("Acme", "Engineer", null);
        recorded.setNotes("Referred by a friend");
        var withNotes = applications.save(user(), recorded);

        mvc.perform(patch("/api/v1/applications/" + withNotes.getId())
                        .header(HttpHeaders.IF_MATCH, "\"" + withNotes.getVersion() + "\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearNotes\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").doesNotExist());
    }

    // ── two tabs (Bolum 35.6) ─────────────────────────────────────────────

    @Test
    void aneditWithoutAnIfMatchIsRefused() throws Exception {
        var recorded = record("Acme", "Engineer", null);

        mvc.perform(patch("/api/v1/applications/" + recorded.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"interview\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
    }

    @Test
    void astaleIfMatchIsAconflict() throws Exception {
        var recorded = record("Acme", "Engineer", null);

        mvc.perform(patch("/api/v1/applications/" + recorded.getId())
                        .header(HttpHeaders.IF_MATCH, "\"99\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"interview\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    /** A row deleted from a stale screen is a row another tab had just changed. */
    @Test
    void adeleteIsGuardedTheSameWay() throws Exception {
        var recorded = record("Acme", "Engineer", null);

        mvc.perform(delete("/api/v1/applications/" + recorded.getId())
                        .header(HttpHeaders.IF_MATCH, "\"99\""))
                .andExpect(status().isPreconditionFailed());

        mvc.perform(delete("/api/v1/applications/" + recorded.getId())
                        .header(HttpHeaders.IF_MATCH, "\"" + recorded.getVersion() + "\""))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM applications", Integer.class)).isZero();
    }

    // ── whose rows they are (absolute rule 3) ─────────────────────────────

    @Test
    void anotherAccountsApplicationIsNotInTheList() throws Exception {
        UUID stranger = newUser();
        applications.save(UserContext.of(stranger),
                new Application(stranger, "Somebody Else Ltd", "Engineer"));

        mvc.perform(get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anotherAccountsApplicationReadsAsAbsentOnAnEdit() throws Exception {
        UUID stranger = newUser();
        var theirs = applications.save(UserContext.of(stranger),
                new Application(stranger, "Somebody Else Ltd", "Engineer"));

        mvc.perform(patch("/api/v1/applications/" + theirs.getId())
                        .header(HttpHeaders.IF_MATCH, "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"offer\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/applications/" + theirs.getId())
                        .header(HttpHeaders.IF_MATCH, "*"))
                .andExpect(status().isNotFound());
    }

    /**
     * <strong>And a generation cannot be linked across accounts.</strong> A
     * generation id reaches a browser in the job's terminal event and in the
     * download link, so it is an id somebody can change — and a row linking a
     * stranger's CV would put its download one hop from a resource this person
     * owns.
     */
    @Test
    void astrangersGenerationCannotBeLinked() throws Exception {
        UUID stranger = newUser();
        UUID theirGeneration = jdbc.queryForObject("""
                INSERT INTO generations (user_id, profile_id, options, selection_state,
                                         engine_version, status)
                VALUES (?, ?, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, 'completed')
                RETURNING id""",
                UUID.class, stranger, newProfile(stranger));

        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme\",\"position\":\"Engineer\","
                                + "\"generationId\":\"" + theirGeneration + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields[0]").value("generationId"));
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private static UserContext user() {
        return UserContext.of(LocalDevUser.DEV_USER_ID);
    }

    private Application record(String company, String position, String appliedAt) {
        var application = new Application(LocalDevUser.DEV_USER_ID, company, position);
        if (appliedAt != null) {
            application.setAppliedAt(java.time.LocalDate.parse(appliedAt));
        }
        return applications.save(user(), application);
    }

    private UUID newUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
    }

    private UUID newProfile(UUID owner) {
        return jdbc.queryForObject(
                "INSERT INTO profiles (user_id) VALUES (?) RETURNING id", UUID.class, owner);
    }
}
