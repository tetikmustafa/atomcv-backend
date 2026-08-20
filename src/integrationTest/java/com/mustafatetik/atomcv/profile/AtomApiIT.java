package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Atoms, their controls, and the wordings that carry the text. */
@AutoConfigureMockMvc
class AtomApiIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String ETL_CONTENT = """
            { "runs": [ { "t": "Built ", "m": [] },
                        { "t": "ETL", "m": ["technology"] },
                        { "t": " pipelines processing ", "m": [] },
                        { "t": "300K+ rows", "m": ["metric"] } ] }""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevCurrentUser localUser;

    private String sectionId;
    private String entryId;

    @BeforeEach
    void startFromOneEmptyEntry() throws Exception {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevCurrentUser.DEV_USER_ID);
        sectionId = created("/api/v1/profile/sections",
                "{ \"kind\": \"experience\", \"title\": \"Experience\" }").get("id").asText();
        entryId = created("/api/v1/profile/entries",
                "{ \"sectionId\": \"" + sectionId + "\", \"title\": \"Backend Engineer\" }")
                .get("id").asText();
    }

    @Test
    void anAtomIsCreatedWithItsFirstWording() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);

        assertThat(atom.get("kind").asText()).isEqualTo("bullet");
        assertThat(atom.get("importance").asDouble()).isEqualTo(0.5);
        assertThat(atom.get("source").asText()).isEqualTo("manual");
        assertThat(atom.get("variants")).hasSize(1);

        JsonNode variant = atom.get("variants").get(0);
        assertThat(variant.get("primary").asBoolean()).isTrue();
        assertThat(variant.get("language").asText()).isEqualTo("en");
        assertThat(variant.get("plainText").asText())
                .isEqualTo("Built ETL pipelines processing 300K+ rows");
        assertThat(variant.get("content").get("v").asInt()).isEqualTo(1);
        assertThat(variant.get("content").get("runs").get(1).get("m").get(0).asText())
                .isEqualTo("technology");
        assertThat(variant.get("createdBy").asText()).isEqualTo("user");
    }

    @Test
    void anAtomCannotBeCreatedWithoutContent() throws Exception {
        mvc.perform(post("/api/v1/profile/atoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"sectionId\": \"" + sectionId + "\", \"kind\": \"bullet\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aLinkRunNeedsItsHref() throws Exception {
        mvc.perform(post("/api/v1/profile/atoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sectionId": "%s", "kind": "bullet",
                                  "content": { "runs": [ { "t": "atomcv", "m": ["link"] } ] } }"""
                                .formatted(sectionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void anUnknownMarkIsKeptRatherThanDropped() throws Exception {
        JsonNode atom = createAtom("""
                { "runs": [ { "t": "Go", "m": ["technology", "sarcasm"] } ] }""");

        assertThat(atom.get("variants").get(0).get("content").get("runs").get(0).get("m"))
                .hasSize(2);
    }

    @Test
    void contentFromANewerBuildIsRefused() throws Exception {
        mvc.perform(post("/api/v1/profile/atoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sectionId": "%s", "kind": "bullet",
                                  "content": { "v": 2, "runs": [ { "t": "From the future" } ] } }"""
                                .formatted(sectionId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAtomCannotBeHungOffAnEntryFromAnotherSection() throws Exception {
        String otherSection = created("/api/v1/profile/sections",
                "{ \"kind\": \"projects\", \"title\": \"Projects\" }").get("id").asText();

        mvc.perform(post("/api/v1/profile/atoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sectionId": "%s", "entryId": "%s", "kind": "bullet",
                                  "content": { "runs": [ { "t": "Mismatched" } ] } }"""
                                .formatted(otherSection, entryId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.fields").value(contains("entryId")));
    }

    @Test
    void aSectionLevelAtomNeedsNoEntry() throws Exception {
        String skills = created("/api/v1/profile/sections",
                "{ \"kind\": \"skills\", \"title\": \"Skills\" }").get("id").asText();

        JsonNode atom = created("/api/v1/profile/atoms", """
                { "sectionId": "%s", "kind": "skill",
                  "content": { "runs": [ { "t": "Go" } ] } }""".formatted(skills));

        assertThat(atom.has("entryId")).isFalse();
    }

    // ─── controls ───

    @Test
    void theControlsAreEditableAndTheTextIsNotAmongThem() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);

        mvc.perform(patch("/api/v1/profile/atoms/" + atom.get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "importance": 0.9, "alwaysInclude": true, "verbatim": true,
                                  "verified": true, "active": false,
                                  "skills": ["go", "postgresql"], "metrics": ["300K+"],
                                  "properNouns": ["Acme"] }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importance").value(0.9))
                .andExpect(jsonPath("$.alwaysInclude").value(true))
                .andExpect(jsonPath("$.verbatim").value(true))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.skills").value(contains("go", "postgresql")))
                // The wording is untouched by a control change.
                .andExpect(jsonPath("$.variants[0].plainText")
                        .value("Built ETL pipelines processing 300K+ rows"));
    }

    @Test
    void writesNeedAPrecondition() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);
        String path = "/api/v1/profile/atoms/" + atom.get("id").asText();

        mvc.perform(patch(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"importance\": 0.9 }"))
                .andExpect(status().is(428));
        mvc.perform(delete(path)).andExpect(status().is(428));
    }

    @Test
    void deletingAnAtomTakesItsWordingsWithIt() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);
        UUID atomId = UUID.fromString(atom.get("id").asText());

        mvc.perform(delete("/api/v1/profile/atoms/" + atomId)
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM atom_variants WHERE atom_id = ?",
                Integer.class, atomId)).isZero();
    }

    @Test
    void reorderingIsScopedToOneGroup() throws Exception {
        String first = createAtom(ETL_CONTENT).get("id").asText();
        String second = createAtom("{ \"runs\": [ { \"t\": \"Second\" } ] }").get("id").asText();

        mvc.perform(post("/api/v1/profile/atoms/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sectionId": "%s", "entryId": "%s", "ids": ["%s", "%s"] }"""
                                .formatted(sectionId, entryId, second, first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].displayOrder").value(contains(0, 1)))
                .andExpect(jsonPath("$[0].id").value(second));
    }

    // ─── wordings ───

    @Test
    void aSecondLanguageIsAddedAlongsideTheFirst() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);
        String path = "/api/v1/profile/atoms/" + atom.get("id").asText() + "/variants";

        JsonNode turkish = created(path, """
                { "language": "tr",
                  "content": { "runs": [ { "t": "300B+ satır işleyen veri hatları kurdum" } ] } }""");

        assertThat(turkish.get("primary").asBoolean()).isFalse();
        assertThat(turkish.get("language").asText()).isEqualTo("tr");

        mvc.perform(get("/api/v1/profile/atoms"))
                .andExpect(jsonPath("$[0].variants.length()").value(2))
                // Primary first, whatever order they were written in.
                .andExpect(jsonPath("$[0].variants[0].primary").value(true));
    }

    @Test
    void twoWordingsCannotClaimTheSameLanguageAndTone() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);

        mvc.perform(post("/api/v1/profile/atoms/" + atom.get("id").asText() + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"language\": \"en\", \"content\": " + ETL_CONTENT + " }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.fields").value(contains("language")));
    }

    @Test
    void editingTheTextRederivesWhatDependsOnIt() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);
        String atomId = atom.get("id").asText();
        String variantId = atom.get("variants").get(0).get("id").asText();
        String hashBefore = atom.get("variants").get(0).get("contentHash").asText();

        mvc.perform(patch("/api/v1/profile/atoms/" + atomId + "/variants/" + variantId)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": { "runs": [
                                    { "t": "Built ETL pipelines processing " },
                                    { "t": "450K+ rows", "m": ["metric"] } ] } }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plainText")
                        .value("Built ETL pipelines processing 450K+ rows"))
                .andExpect(jsonPath("$.contentHash").value(org.hamcrest.Matchers.not(hashBefore)))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void promotingAWordingDemotesTheOther() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);
        String atomId = atom.get("id").asText();
        JsonNode turkish = created("/api/v1/profile/atoms/" + atomId + "/variants", """
                { "language": "tr", "content": { "runs": [ { "t": "Veri hatları" } ] } }""");

        mvc.perform(patch("/api/v1/profile/atoms/" + atomId + "/variants/"
                        + turkish.get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "primary": true, "language": "tr",
                                  "content": { "runs": [ { "t": "Veri hatları" } ] } }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary").value(true));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM atom_variants WHERE atom_id = ? AND is_primary",
                Integer.class, UUID.fromString(atomId))).isEqualTo(1);
    }

    @Test
    void promotingAWordingCostsOneBooleanAndLeavesEverythingElseAlone() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);
        String atomId = atom.get("id").asText();
        JsonNode turkish = created("/api/v1/profile/atoms/" + atomId + "/variants", """
                { "language": "tr", "tone": "technical",
                  "content": { "runs": [ { "t": "Veri hatları" } ] } }""");

        // No content, no language, no tone: this write is about one boolean,
        // and it used to demand the whole sentence back (EK D.6.8).
        mvc.perform(patch("/api/v1/profile/atoms/" + atomId + "/variants/"
                        + turkish.get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"primary\": true }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary").value(true))
                // The tone was the user's choice. Writing it unconditionally
                // wiped it on every promote (P8).
                .andExpect(jsonPath("$.tone").value("technical"))
                .andExpect(jsonPath("$.plainText").value("Veri hatları"));
    }

    @Test
    void aDefinedNullToneReturnsTheWordingToTheNeutralRegister() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);
        String atomId = atom.get("id").asText();
        JsonNode turkish = created("/api/v1/profile/atoms/" + atomId + "/variants", """
                { "language": "tr", "tone": "formal",
                  "content": { "runs": [ { "t": "Veri hatları" } ] } }""");

        // Leaving the field out means "leave it alone"; sending null means
        // "clear it". Without the difference there is no way back to neutral.
        mvc.perform(patch("/api/v1/profile/atoms/" + atomId + "/variants/"
                        + turkish.get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tone\": null }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tone").doesNotExist());
    }

    @Test
    void anAtomKeepsAWordingAndADefaultAmongThem() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);
        String atomId = atom.get("id").asText();
        String primaryId = atom.get("variants").get(0).get("id").asText();

        // The last one cannot go: the atom would have nothing to say.
        mvc.perform(delete("/api/v1/profile/atoms/" + atomId + "/variants/" + primaryId)
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isBadRequest());

        JsonNode second = created("/api/v1/profile/atoms/" + atomId + "/variants", """
                { "language": "tr", "content": { "runs": [ { "t": "Veri hatları" } ] } }""");

        // Nor the primary while another remains — promote first.
        mvc.perform(delete("/api/v1/profile/atoms/" + atomId + "/variants/" + primaryId)
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.fields").value(contains("primary")));

        mvc.perform(delete("/api/v1/profile/atoms/" + atomId + "/variants/"
                        + second.get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"0\""))
                .andExpect(status().isNoContent());
    }

    @Test
    void aWordingOfAnotherAtomIsNotReachableThroughThisOne() throws Exception {
        JsonNode first = createAtom(ETL_CONTENT);
        JsonNode second = createAtom("{ \"runs\": [ { \"t\": \"Second\" } ] }");

        mvc.perform(patch("/api/v1/profile/atoms/" + first.get("id").asText() + "/variants/"
                        + second.get("variants").get(0).get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"content\": { \"runs\": [ { \"t\": \"x\" } ] } }"))
                .andExpect(status().isNotFound());
    }

    @Test
    void demotingAWordingIsAVersionChangeLikeAnyOther() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);
        String atomId = atom.get("id").asText();
        String englishId = atom.get("variants").get(0).get("id").asText();
        assertThat(atom.get("variants").get(0).get("version").asLong()).isZero();

        JsonNode turkish = created("/api/v1/profile/atoms/" + atomId + "/variants", """
                { "language": "tr", "content": { "runs": [ { "t": "Veri hatları" } ] } }""");

        mvc.perform(patch("/api/v1/profile/atoms/" + atomId + "/variants/"
                        + turkish.get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"primary\": true }"))
                .andExpect(status().isOk());

        // The demote is a bulk update, and a bulk update walks past @Version
        // unless it is asked not to. The row changed, so its etag has to
        // change with it (F-001).
        assertThat(jdbc.queryForObject("SELECT version FROM atom_variants WHERE id = ?",
                Long.class, UUID.fromString(englishId))).isEqualTo(1L);

        // Which is the whole point: a client still holding "0" for the demoted
        // wording used to write straight over a change it never read.
        mvc.perform(patch("/api/v1/profile/atoms/" + atomId + "/variants/" + englishId)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"tone\": \"formal\" }"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void aPromoteLeavesTheWordingsItDidNotTouchAtTheirVersion() throws Exception {
        JsonNode atom = createAtom(ETL_CONTENT);
        String atomId = atom.get("id").asText();

        JsonNode turkish = created("/api/v1/profile/atoms/" + atomId + "/variants", """
                { "language": "tr", "content": { "runs": [ { "t": "Veri hatları" } ] } }""");
        JsonNode german = created("/api/v1/profile/atoms/" + atomId + "/variants", """
                { "language": "de", "content": { "runs": [ { "t": "Datenpipelines" } ] } }""");

        mvc.perform(patch("/api/v1/profile/atoms/" + atomId + "/variants/"
                        + turkish.get("id").asText())
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"primary\": true }"))
                .andExpect(status().isOk());

        // German was never primary and never changed. Bumping every wording of
        // the atom would invalidate etags no write earned, and would make
        // every promote look like a conflict to a client reading the list.
        assertThat(jdbc.queryForObject("SELECT version FROM atom_variants WHERE id = ?",
                Long.class, UUID.fromString(german.get("id").asText()))).isZero();
    }

    private JsonNode createAtom(String content) throws Exception {
        return created("/api/v1/profile/atoms", """
                { "sectionId": "%s", "entryId": "%s", "kind": "bullet", "content": %s }"""
                .formatted(sectionId, entryId, content));
    }

    private JsonNode created(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
