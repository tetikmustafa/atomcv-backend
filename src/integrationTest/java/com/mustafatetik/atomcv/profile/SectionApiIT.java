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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Section CRUD, ordering, and the preconditions that protect them. */
@AutoConfigureMockMvc
class SectionApiIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevCurrentUser localUser;

    @BeforeEach
    void startFromAnEmptyProfile() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevCurrentUser.DEV_USER_ID);
    }

    @Test
    void aSectionIsCreatedAtTheEndAndListedInOrder() throws Exception {
        create("experience", "Experience");
        create("education", "Education");
        create("skills", "Skills");

        mvc.perform(get("/api/v1/profile/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].title").value(contains("Experience", "Education", "Skills")))
                .andExpect(jsonPath("$[*].displayOrder").value(contains(0, 1, 2)))
                // Every item carries its version, so editing one needs no second read.
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[0].layout").value("bullet_list"))
                .andExpect(jsonPath("$[0].kind").value("experience"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void creatingReturns201WithAnEtag() throws Exception {
        mvc.perform(post("/api/v1/profile/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "kind": "projects", "title": "Selected projects",
                                  "layout": "entry_list", "alwaysInclude": true }"""))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.layout").value("entry_list"))
                .andExpect(jsonPath("$.alwaysInclude").value(true));
    }

    @Test
    void anUnknownVocabularyValueIsRefused() throws Exception {
        mvc.perform(post("/api/v1/profile/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"kind\": \"hobbies\", \"title\": \"Hobbies\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void patchingChangesOnlyWhatItNames() throws Exception {
        JsonNode section = create("experience", "Experience");

        mvc.perform(patch("/api/v1/profile/sections/" + section.get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"Work history\" }"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.title").value("Work history"))
                // Untouched by a patch that did not mention them.
                .andExpect(jsonPath("$.kind").value("experience"))
                .andExpect(jsonPath("$.layout").value("bullet_list"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void aPatchWithoutAPreconditionIsRefused() throws Exception {
        JsonNode section = create("experience", "Experience");

        mvc.perform(patch("/api/v1/profile/sections/" + section.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"Work history\" }"))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
    }

    @Test
    void aStalePatchIsRefused() throws Exception {
        JsonNode section = create("experience", "Experience");

        mvc.perform(patch("/api/v1/profile/sections/" + section.get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"5\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"Work history\" }"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void aBlankTitleIsRefusedButAnAbsentOneIsNot() throws Exception {
        JsonNode section = create("experience", "Experience");
        String path = "/api/v1/profile/sections/" + section.get("id").asText();

        mvc.perform(patch(path)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"  \" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(patch(path)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"active\": false }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Experience"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deletingNeedsAPreconditionAndThenRemovesIt() throws Exception {
        JsonNode section = create("experience", "Experience");
        String path = "/api/v1/profile/sections/" + section.get("id").asText();

        mvc.perform(delete(path)).andExpect(status().is(428));

        mvc.perform(delete(path).header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/profile/sections"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deletingASectionTakesItsContentWithIt() throws Exception {
        JsonNode section = create("experience", "Experience");
        var sectionId = java.util.UUID.fromString(section.get("id").asText());
        var profileId = jdbc.queryForObject("SELECT profile_id FROM sections WHERE id = ?",
                java.util.UUID.class, sectionId);
        jdbc.update("INSERT INTO entries (profile_id, section_id, title, display_order) "
                + "VALUES (?, ?, 'Backend Engineer', 0)", profileId, sectionId);

        mvc.perform(delete("/api/v1/profile/sections/" + sectionId)
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM entries WHERE section_id = ?",
                Integer.class, sectionId)).isZero();
    }

    @Test
    void aSectionThatDoesNotExistReadsAsMissing() throws Exception {
        mvc.perform(patch("/api/v1/profile/sections/" + java.util.UUID.randomUUID())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"Nowhere\" }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ─── ordering ───

    @Test
    void reorderingPutsThemInTheOrderGiven() throws Exception {
        String first = create("experience", "Experience").get("id").asText();
        String second = create("education", "Education").get("id").asText();
        String third = create("skills", "Skills").get("id").asText();

        mvc.perform(post("/api/v1/profile/sections/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"ids\": [\"" + third + "\", \"" + first + "\", \"" + second + "\"] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(contains("Skills", "Experience", "Education")))
                .andExpect(jsonPath("$[*].displayOrder").value(contains(0, 1, 2)));

        mvc.perform(get("/api/v1/profile/sections"))
                .andExpect(jsonPath("$[*].title").value(contains("Skills", "Experience", "Education")));
    }

    @Test
    void aPartialOrderIsRefused() throws Exception {
        String first = create("experience", "Experience").get("id").asText();
        create("education", "Education");

        mvc.perform(post("/api/v1/profile/sections/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"ids\": [\"" + first + "\"] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields").value(contains("ids")));
    }

    @Test
    void anOrderNamingSomethingElseIsRefused() throws Exception {
        String first = create("experience", "Experience").get("id").asText();

        mvc.perform(post("/api/v1/profile/sections/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"ids\": [\"" + first + "\", \"" + java.util.UUID.randomUUID() + "\"] }"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode create(String kind, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/profile/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"kind\": \"" + kind + "\", \"title\": \"" + title + "\" }"))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
