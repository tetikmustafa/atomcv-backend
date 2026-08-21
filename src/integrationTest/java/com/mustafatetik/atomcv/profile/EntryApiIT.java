package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.shared.security.LocalDevCurrentUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Entry CRUD, and the part sections did not need: telling "leave this alone"
 * apart from "clear it".
 */
@AutoConfigureMockMvc
class EntryApiIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevCurrentUser localUser;

    private String sectionId;

    @BeforeEach
    void startFromOneEmptySection() throws Exception {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevCurrentUser.DEV_USER_ID);
        sectionId = post("/api/v1/profile/sections",
                "{ \"kind\": \"experience\", \"title\": \"Experience\" }")
                .get("id").asText();
    }

    @Test
    void anEntryIsCreatedAtTheEndOfItsSection() throws Exception {
        createEntry("Backend Engineer");
        createEntry("Data Engineer");

        mvc.perform(get("/api/v1/profile/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(contains("Backend Engineer", "Data Engineer")))
                .andExpect(jsonPath("$[*].displayOrder").value(contains(0, 1)))
                .andExpect(jsonPath("$[0].importance").value(0.5))
                .andExpect(jsonPath("$[0].minAtoms").value(2))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].version").value(0));
    }

    @Test
    void theListCanBeNarrowedToOneSection() throws Exception {
        createEntry("Backend Engineer");
        String other = post("/api/v1/profile/sections",
                "{ \"kind\": \"projects\", \"title\": \"Projects\" }").get("id").asText();
        post("/api/v1/profile/entries",
                "{ \"sectionId\": \"" + other + "\", \"title\": \"AtomCV\" }");

        mvc.perform(get("/api/v1/profile/entries").param("sectionId", other))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("AtomCV"));
    }

    @Test
    void anEntryCannotBeHungOffASectionThatIsNotYours() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/profile/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"sectionId\": \"" + UUID.randomUUID() + "\", \"title\": \"x\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.fields").value(contains("sectionId")));
    }

    @Test
    void anOngoingEntryHasNoEndDate() throws Exception {
        JsonNode entry = post("/api/v1/profile/entries", """
                { "sectionId": "%s", "title": "Backend Engineer", "organization": "Acme",
                  "startDate": "2023-03-01" }""".formatted(sectionId));

        assertThat(entry.has("endDate")).isFalse();
        assertThat(entry.get("startDate").asText()).isEqualTo("2023-03-01");
    }

    // ─── the distinction sections did not need ───

    @Test
    void anAbsentFieldIsLeftAloneAndANullOneIsCleared() throws Exception {
        JsonNode entry = post("/api/v1/profile/entries", """
                { "sectionId": "%s", "title": "Backend Engineer", "organization": "Acme",
                  "location": "İstanbul", "startDate": "2023-03-01", "endDate": "2025-01-31" }"""
                .formatted(sectionId));
        String path = "/api/v1/profile/entries/" + entry.get("id").asText();

        // Mentions neither organization nor location: both survive.
        mvc.perform(patch(path)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"Senior Backend Engineer\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.organization").value("Acme"))
                .andExpect(jsonPath("$.location").value("İstanbul"));

        // Sends endDate as null: the job is ongoing again.
        mvc.perform(patch(path)
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"endDate\": null }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").doesNotExist())
                .andExpect(jsonPath("$.startDate").value("2023-03-01"));

        // And clearing a wrongly typed organization is possible at all.
        mvc.perform(patch(path)
                        .header(HttpHeaders.IF_MATCH, "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"organization\": null }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization").doesNotExist())
                .andExpect(jsonPath("$.location").value("İstanbul"));
    }

    @Test
    void theUserControlsAreEditable() throws Exception {
        JsonNode entry = createEntry("Backend Engineer");

        mvc.perform(patch("/api/v1/profile/entries/" + entry.get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "importance": 0.9, "alwaysInclude": true, "verbatim": true,
                                  "active": false, "minAtoms": 3 }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importance").value(0.9))
                .andExpect(jsonPath("$.alwaysInclude").value(true))
                .andExpect(jsonPath("$.verbatim").value(true))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.minAtoms").value(3));
    }

    @Test
    void anImportanceOutsideTheRangeIsRefused() throws Exception {
        JsonNode entry = createEntry("Backend Engineer");

        mvc.perform(patch("/api/v1/profile/entries/" + entry.get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"importance\": 1.5 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.fields").value(contains("importance")));
    }

    @Test
    void writesNeedAPrecondition() throws Exception {
        JsonNode entry = createEntry("Backend Engineer");
        String path = "/api/v1/profile/entries/" + entry.get("id").asText();

        mvc.perform(patch(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"Nope\" }"))
                .andExpect(status().is(428));

        mvc.perform(delete(path)).andExpect(status().is(428));

        mvc.perform(patch(path)
                        .header(HttpHeaders.IF_MATCH, "\"9\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"Nope\" }"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void deletingAnEntryTakesItsAtomsWithIt() throws Exception {
        JsonNode entry = createEntry("Backend Engineer");
        UUID entryId = UUID.fromString(entry.get("id").asText());
        UUID profileId = jdbc.queryForObject("SELECT profile_id FROM entries WHERE id = ?",
                UUID.class, entryId);
        jdbc.update("INSERT INTO atoms (profile_id, section_id, entry_id, kind, display_order) "
                        + "VALUES (?, ?, ?, 'bullet', 0)",
                profileId, UUID.fromString(sectionId), entryId);

        mvc.perform(delete("/api/v1/profile/entries/" + entryId)
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM atoms WHERE entry_id = ?",
                Integer.class, entryId)).isZero();
    }

    @Test
    void reorderingIsScopedToOneSection() throws Exception {
        String first = createEntry("Backend Engineer").get("id").asText();
        String second = createEntry("Data Engineer").get("id").asText();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/profile/entries/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"sectionId\": \"" + sectionId + "\", \"ids\": [\""
                                + second + "\", \"" + first + "\"] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(contains("Data Engineer", "Backend Engineer")))
                .andExpect(jsonPath("$[*].displayOrder").value(contains(0, 1)));
    }

    @Test
    void aPartialOrderIsRefused() throws Exception {
        String first = createEntry("Backend Engineer").get("id").asText();
        createEntry("Data Engineer");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/profile/entries/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"sectionId\": \"" + sectionId + "\", \"ids\": [\"" + first + "\"] }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anEntryCannotEndBeforeItStarts() throws Exception {
        // This was a 201 before F-002. Nothing downstream refuses the range:
        // it renders as "Jan 2022 - Jan 2019" and reaches generation that way,
        // and a date line that looks plausible is not read twice.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/profile/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sectionId": "%s", "title": "Backwards",
                                  "startDate": "2022-01-01", "endDate": "2019-01-01" }"""
                                .formatted(sectionId)))
                .andExpect(status().isBadRequest())
                // Both ends came in one body, so both are named: either is a
                // field the create form can put the message next to (F-005).
                .andExpect(jsonPath("$.params.fields")
                        .value(contains("startDate", "endDate")));
    }

    @Test
    void aPatchCannotReverseTheRangeByMovingOneEnd() throws Exception {
        JsonNode entry = post("/api/v1/profile/entries", """
                { "sectionId": "%s", "title": "Backend Engineer",
                  "startDate": "2023-03-01", "endDate": "2025-01-31" }"""
                .formatted(sectionId));
        String path = "/api/v1/profile/entries/" + entry.get("id").asText();

        // Only endDate is sent, so the start it has to clear is the stored one.
        mvc.perform(patch(path)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"endDate\": \"2020-01-01\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.fields").value(contains("endDate")));

        // And the mirror case: the start moves past the stored end. The field
        // named is the one this request sent (F-005) — it used to say endDate
        // here, which a single-field form can only mark on the wrong input.
        mvc.perform(patch(path)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"startDate\": \"2026-01-01\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.fields").value(contains("startDate")));

        // Both ends in one patch: both are named, same as on create.
        mvc.perform(patch(path)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"startDate\": \"2025-01-01\", \"endDate\": \"2024-01-01\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.fields")
                        .value(contains("startDate", "endDate")));

        // Clearing the end date is still how a job becomes ongoing: there is
        // no longer a second date, so there is nothing left to order.
        mvc.perform(patch(path)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"endDate\": null }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endDate").doesNotExist());
    }

    @Test
    void anEntryThatBeginsAndEndsOnOneDayIsAccepted() throws Exception {
        // The rule is endDate >= startDate. A one-day certificate or hackathon
        // is a real entry and an exclusive comparison would refuse it.
        post("/api/v1/profile/entries", """
                { "sectionId": "%s", "title": "Hackathon",
                  "startDate": "2024-05-04", "endDate": "2024-05-04" }"""
                .formatted(sectionId));
    }

    private JsonNode createEntry(String title) throws Exception {
        return post("/api/v1/profile/entries",
                "{ \"sectionId\": \"" + sectionId + "\", \"title\": \"" + title + "\" }");
    }

    private JsonNode post(String path, String body) throws Exception {
        MvcResult result = mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
