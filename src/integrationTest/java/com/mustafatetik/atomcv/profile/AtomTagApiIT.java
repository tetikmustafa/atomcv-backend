package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code POST} and {@code DELETE} on {@code /profile/atoms/{id}/tags}.
 *
 * <p><strong>The tables were empty and nothing said so.</strong> Both
 * endpoints were in the resource map from the start and neither existed;
 * {@code tags} and {@code atom_tags} had no writer at all, including the
 * import, so Faz B's tag overlap — a quarter of the raw score — was zero for
 * every atom this product has ever scored. A scoring component that is
 * structurally zero does not fail a test; it lowers every number together.
 */
@AutoConfigureMockMvc
class AtomTagApiIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONTENT = """
            { "runs": [ { "t": "Built ETL pipelines", "m": [] } ] }""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevUser localUser;

    private String atomId;

    @BeforeEach
    void oneAtomToLabel() throws Exception {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevUser.DEV_USER_ID);
        String sectionId = created("/api/v1/profile/sections",
                "{ \"kind\": \"experience\", \"title\": \"Experience\" }").get("id").asText();
        String entryId = created("/api/v1/profile/entries",
                "{ \"sectionId\": \"" + sectionId + "\", \"title\": \"Backend Engineer\" }")
                .get("id").asText();
        atomId = created("/api/v1/profile/atoms", """
                { "sectionId": "%s", "entryId": "%s", "kind": "bullet", "content": %s }"""
                .formatted(sectionId, entryId, CONTENT)).get("id").asText();
    }

    @Test
    void alabelIsStoredAsTheScorerWillReadIt() throws Exception {
        JsonNode tag = tag("Data-Engineering");

        // Canonical, because that is the form Faz B compares against a
        // posting. An editor showing the typed spelling would show something
        // the scorer never sees.
        assertThat(tag.get("label").asText()).isEqualTo("data-engineering");
        assertThat(tag.get("source").asText()).isEqualTo("user");

        assertThat(storedLabel(tag.get("id").asText())).isEqualTo("data-engineering");
    }

    /** The editor draws the list, so the list endpoint has to carry it. */
    @Test
    void thetagComesBackOnTheAtom() throws Exception {
        tag("etl");

        mvc.perform(get("/api/v1/profile/atoms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tags[0].label").value("etl"))
                .andExpect(jsonPath("$[0].tags[0].source").value("user"));
    }

    /** An atom nobody labelled carries an empty list, not a missing field. */
    @Test
    void anuntaggedAtomCarriesNoLabels() throws Exception {
        mvc.perform(get("/api/v1/profile/atoms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tags").isEmpty());
    }

    /**
     * <strong>One vocabulary per profile</strong> (Bolum 13's
     * {@code UNIQUE (profile_id, label)}). A second row for the same word would
     * be a tag that never matches the first one.
     */
    @Test
    void thesameLabelOnTwoAtomsIsOneTag() throws Exception {
        String first = tag("etl").get("id").asText();

        String secondAtom = created("/api/v1/profile/atoms", """
                { "sectionId": "%s", "kind": "skill", "content":
                  { "runs": [ { "t": "Airflow", "m": [] } ] } }"""
                .formatted(sectionOf(atomId))).get("id").asText();

        MvcResult result = mvc.perform(post("/api/v1/profile/atoms/" + secondAtom + "/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"ETL\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(JSON.readTree(result.getResponse().getContentAsString())
                .get("id").asText()).isEqualTo(first);
        assertThat(countTags()).isEqualTo(1);
    }

    /** Tagging something already tagged is what the caller asked for, not a conflict. */
    @Test
    void tagRandomAtomTwiceIsNotAconflict() throws Exception {
        String first = tag("etl").get("id").asText();
        assertThat(tag("etl").get("id").asText()).isEqualTo(first);

        assertThat(countLinks()).isEqualTo(1);
    }

    /**
     * <strong>The tag row goes with the last atom wearing it.</strong> A label
     * no atom carries is a suggestion nobody made, and it would sit in the
     * profile's vocabulary forever, growing with every typo.
     */
    @Test
    void removingThelastWearerRemovesTheWord() throws Exception {
        String tagId = tag("etl").get("id").asText();

        mvc.perform(delete("/api/v1/profile/atoms/" + atomId + "/tags/" + tagId))
                .andExpect(status().isNoContent());

        assertThat(countLinks()).isZero();
        assertThat(countTags()).isZero();
    }

    /** A removal that did not happen is not reported as one. */
    @Test
    void removingAtagTheAtomIsNotWearingIsNotFound() throws Exception {
        mvc.perform(delete("/api/v1/profile/atoms/" + atomId + "/tags/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    /** A label that is nothing but whitespace is the client's mistake. */
    @Test
    void ablankLabelIsRefused() throws Exception {
        mvc.perform(post("/api/v1/profile/atoms/" + atomId + "/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields[0]").value("label"));
    }

    /** Absolute rule 3: an atom that is not yours is not found. */
    @Test
    void taggingAnatomThatIsNotYoursIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/profile/atoms/" + UUID.randomUUID() + "/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"etl\"}"))
                .andExpect(status().isNotFound());
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private JsonNode tag(String label) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/profile/atoms/" + atomId + "/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"" + label + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode created(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private String sectionOf(String atom) {
        return jdbc.queryForObject("SELECT section_id FROM atoms WHERE id = ?",
                String.class, UUID.fromString(atom));
    }

    private String storedLabel(String tagId) {
        return jdbc.queryForObject("SELECT label FROM tags WHERE id = ?",
                String.class, UUID.fromString(tagId));
    }

    /**
     * Scoped to this profile, and it has to be: {@code DevSeeder} imports the
     * golden profiles into the same database and they carry tags of their own
     * now. A count over the whole table would be measuring the seed.
     */
    private Integer countTags() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM tags
                WHERE profile_id = (SELECT id FROM profiles WHERE user_id = ?)
                """, Integer.class, LocalDevUser.DEV_USER_ID);
    }

    private Integer countLinks() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM atom_tags link
                JOIN tags tag ON tag.id = link.tag_id
                WHERE tag.profile_id = (SELECT id FROM profiles WHERE user_id = ?)
                """, Integer.class, LocalDevUser.DEV_USER_ID);
    }
}
