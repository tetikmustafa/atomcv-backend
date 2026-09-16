package com.mustafatetik.atomcv.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * {@code /templates} and {@code /customizations}.
 *
 * <p><strong>Five endpoints the resource map has always listed, against a
 * table that has existed since V1 with no writer.</strong> The settings a
 * person is working with live in {@code preferences.appearance} and always
 * did; what was missing is the other half — keeping more than one.
 */
@AutoConfigureMockMvc
class CustomizationApiIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevUser localUser;

    @BeforeEach
    void anemptyProfile() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevUser.DEV_USER_ID);
    }

    /**
     * The three templates are described by how much they hold, so the capacity
     * is what makes this list a chooser rather than three names.
     */
    @Test
    void thetemplateListCarriesWhatEachOneHolds() throws Exception {
        mvc.perform(get("/api/v1/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'classic')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'compact')]").exists())
                .andExpect(jsonPath("$[?(@.id == 'modern')]").exists())
                .andExpect(jsonPath("$[0].pageTextHeightPt")
                        .value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$[0].approximateLinesPerPage")
                        .value(org.hamcrest.Matchers.greaterThan(20)))
                .andExpect(jsonPath("$[0].version")
                        .value(org.hamcrest.Matchers.greaterThan(0)));
    }

    /**
     * <strong>Compact really is denser.</strong> The catalogue is a claim
     * about the measured constants, and a list that reported the same figure
     * for all three would be publishing a number nobody measured.
     */
    @Test
    void thedensestTemplateReportsTheMostLines() throws Exception {
        String body = mvc.perform(get("/api/v1/templates"))
                .andReturn().getResponse().getContentAsString();
        JsonNode templates = JSON.readTree(body);

        int compact = linesOf(templates, "compact");
        int modern = linesOf(templates, "modern");

        assertThat(compact)
                .as("compact is the high-density one")
                .isGreaterThan(modern);
    }

    @Test
    void asetIsKeptUnderAname() throws Exception {
        JsonNode saved = created("""
                { "name": "Compact, one page", "baseTemplateId": "compact",
                  "fontSizePt": 9.5, "marginInches": 0.45, "accentColor": "1D4ED8" }""");

        assertThat(saved.get("name").asText()).isEqualTo("Compact, one page");
        assertThat(saved.get("fontSizePt").asDouble()).isEqualTo(9.5);
        // Omitted, so it takes the base template's own default rather than
        // zero -- which is what somebody who moved two sliders means.
        assertThat(saved.get("lineSpacing").asDouble()).isEqualTo(0.95);
        assertThat(saved.get("templateVersion").asInt()).isPositive();

        mvc.perform(get("/api/v1/customizations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Compact, one page"));
    }

    /**
     * <strong>The ranges are the safety, and they are enforced at the
     * door.</strong> They are kept narrow so a bad result is physically
     * impossible; a value outside one must not reach a preamble.
     */
    @Test
    void avalueOutsideTheAllowedRangeIsRefused() throws Exception {
        mvc.perform(post("/api/v1/customizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Tiny", "baseTemplateId": "classic",
                                  "fontSizePt": 4.0 }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(post("/api/v1/customizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Edgeless", "baseTemplateId": "classic",
                                  "marginInches": 0.05 }"""))
                .andExpect(status().isBadRequest());
    }

    /** An unknown template names the field rather than failing on a lookup later. */
    @Test
    void anunknownTemplateIsRefusedByName() throws Exception {
        mvc.perform(post("/api/v1/customizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"X\", \"baseTemplateId\": \"brutalist\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.fields[0]").value("baseTemplateId"));
    }

    /** UNIQUE (profile_id, name), answered before the constraint sees it. */
    @Test
    void twosetsCannotShareAname() throws Exception {
        created("{ \"name\": \"Mine\", \"baseTemplateId\": \"classic\" }");

        mvc.perform(post("/api/v1/customizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"Mine\", \"baseTemplateId\": \"modern\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.fields[0]").value("name"));
    }

    /** A patch with only a name renames and leaves the geometry alone. */
    @Test
    void anameOnlyPatchIsArename() throws Exception {
        JsonNode saved = created("""
                { "name": "Old", "baseTemplateId": "compact", "fontSizePt": 9.5 }""");

        mvc.perform(patch("/api/v1/customizations/" + saved.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"New\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.fontSizePt").value(9.5))
                .andExpect(jsonPath("$.baseTemplateId").value("compact"));
    }

    /** And one naming a template replaces the settings whole. */
    @Test
    void apatchNamingAtemplateReplacesTheSettings() throws Exception {
        JsonNode saved = created("""
                { "name": "Set", "baseTemplateId": "compact", "fontSizePt": 9.5 }""");

        mvc.perform(patch("/api/v1/customizations/" + saved.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"baseTemplateId\": \"modern\", \"fontSizePt\": 11.5 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseTemplateId").value("modern"))
                .andExpect(jsonPath("$.fontSizePt").value(11.5))
                // Not carried over from the old set: the parameters are read
                // together, so a half-applied geometry is a page nobody chose.
                .andExpect(jsonPath("$.marginInches").value(0.55));
    }

    @Test
    void asetCanBeForgotten() throws Exception {
        JsonNode saved = created("{ \"name\": \"Gone\", \"baseTemplateId\": \"classic\" }");

        mvc.perform(delete("/api/v1/customizations/" + saved.get("id").asText()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/customizations"))
                .andExpect(jsonPath("$").isEmpty());
    }

    /** Absolute rule 3: somebody else's set is not found. */
    @Test
    void asetThatIsNotYoursIsNotFound() throws Exception {
        mvc.perform(delete("/api/v1/customizations/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/customizations/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"X\" }"))
                .andExpect(status().isNotFound());
    }

    private static int linesOf(JsonNode templates, String id) {
        for (JsonNode template : templates) {
            if (id.equals(template.get("id").asText())) {
                return template.get("approximateLinesPerPage").asInt();
            }
        }
        throw new IllegalStateException("No template " + id);
    }

    private JsonNode created(String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/customizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
