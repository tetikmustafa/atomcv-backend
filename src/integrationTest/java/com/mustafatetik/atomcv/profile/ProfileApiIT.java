package com.mustafatetik.atomcv.profile;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.shared.security.LocalDevCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The first endpoint, wired the way it runs locally: the stand-in acting user,
 * the resolver that creates the profile, the ETag a write will need.
 *
 * <p>{@code local} is active on purpose. The alternative — a test-only
 * {@code CurrentUser} bean — would leave the only wiring that exists today
 * untested.
 */
@AutoConfigureMockMvc
class ProfileApiIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevCurrentUser localUser;

    /**
     * Other integration tests clear {@code users} after themselves, and the
     * startup runner only ran once for the shared context.
     *
     * <p>The profile goes too: these tests read a figure computed from the
     * whole profile, so one starting where another finished would assert on
     * whatever ran before it.
     */
    @BeforeEach
    void startFromAnEmptyProfile() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevCurrentUser.DEV_USER_ID);
    }

    @Test
    void theStandInUserExistsSoThatProfilesCanHangOffIt() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?",
                Integer.class, LocalDevCurrentUser.DEV_USER_ID)).isEqualTo(1);
    }

    @Test
    void readingTheProfileCreatesItOnFirstUseAndCarriesItsVersion() throws Exception {
        mvc.perform(get("/api/v1/profile"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.sourceLanguage").value("en"))
                .andExpect(jsonPath("$.enabledLanguages[0]").value("en"))
                .andExpect(jsonPath("$.completeness").value(0))
                .andExpect(jsonPath("$.preferences.defaults.maxPages").value(1))
                .andExpect(jsonPath("$.preferences.defaults.templateId").value("classic"))
                .andExpect(jsonPath("$.preferences.writingStyle.tone").value("formal"))
                // No identifier and no version field: ownership comes from the
                // session, and the version lives in the ETag.
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM profiles WHERE user_id = ?",
                Integer.class, LocalDevCurrentUser.DEV_USER_ID)).isEqualTo(1);
    }

    @Test
    void readingItTwiceDoesNotCreateASecondProfile() throws Exception {
        mvc.perform(get("/api/v1/profile")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/profile")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM profiles WHERE user_id = ?",
                Integer.class, LocalDevCurrentUser.DEV_USER_ID)).isEqualTo(1);
    }

    @Test
    void anUnknownApiPathAnswersWithACodeRatherThanAStackTrace() throws Exception {
        mvc.perform(get("/api/v1/profile/there-is-no-such-thing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.type").value("/errors/resource-not-found"));
    }

    // ─── writes carry a precondition (Bolum 35.6, P8) ───

    @Test
    void replacingTheHeadStoresItAndMovesTheVersionOn() throws Exception {
        String etag = currentEtag();

        mvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "headline": "Backend Engineer",
                                  "contact": { "name": "Mustafa Tetik",
                                               "email": "mustafa@example.com",
                                               "location": "İstanbul, Türkiye" },
                                  "selfDescription": "Builds things that stay built",
                                  "sourceLanguage": "en",
                                  "enabledLanguages": ["en", "tr"]
                                }"""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", not(etag)))
                .andExpect(jsonPath("$.headline").value("Backend Engineer"))
                .andExpect(jsonPath("$.contact.name").value("Mustafa Tetik"))
                .andExpect(jsonPath("$.contact.location").value("İstanbul, Türkiye"))
                .andExpect(jsonPath("$.enabledLanguages").value(contains("en", "tr")));

        mvc.perform(get("/api/v1/profile"))
                .andExpect(jsonPath("$.headline").value("Backend Engineer"))
                .andExpect(jsonPath("$.selfDescription").value("Builds things that stay built"));
    }

    @Test
    void aWriteWithoutAPreconditionIsRefused() throws Exception {
        mvc.perform(put("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody("No precondition")))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));

        mvc.perform(get("/api/v1/profile"))
                .andExpect(jsonPath("$.headline").value(not("No precondition")));
    }

    @Test
    void aStalePreconditionIsRefusedAndOffersARetry() throws Exception {
        mvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.IF_MATCH, "\"9999\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody("Someone else was first")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.resolutions[0].action").value("retry"));
    }

    @Test
    void replacingTheHeadClearsWhatWasLeftOut() throws Exception {
        mvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.IF_MATCH, currentEtag())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody("Only a headline")))
                .andExpect(status().isOk())
                // PUT replaces: the previous self-description is gone, not kept.
                .andExpect(jsonPath("$.selfDescription").doesNotExist())
                .andExpect(jsonPath("$.contact").isEmpty());
    }

    @Test
    void replacingTheHeadLeavesPreferencesAlone() throws Exception {
        mvc.perform(put("/api/v1/profile/preferences")
                        .header(HttpHeaders.IF_MATCH, currentEtag())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "writingStyle": { "emphasizeMetrics": false, "tone": "casual",
                                                    "conciseSentences": true },
                                  "defaults": { "maxPages": 2, "templateId": "modern",
                                                "cvLanguage": "tr", "coverLetterLanguage": "tr" } }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.defaults.maxPages").value(2));

        mvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.IF_MATCH, currentEtag())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalBody("Still two pages")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.defaults.maxPages").value(2))
                .andExpect(jsonPath("$.preferences.writingStyle.tone").value("casual"));
    }

    // ─── invalid input is the client's problem, and says which field ───

    @Test
    void anInvalidFieldIsNamedButItsValueIsNot() throws Exception {
        mvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.IF_MATCH, currentEtag())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "headline": "Backend Engineer",
                                  "contact": { "email": "not-an-address" },
                                  "enabledLanguages": ["en"] }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields").value(contains("contact.email")))
                .andExpect(content().string(not(containsString("not-an-address"))));
    }

    @Test
    void anEmptyLanguageListIsRefused() throws Exception {
        mvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.IF_MATCH, currentEtag())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"headline\": \"x\", \"enabledLanguages\": [] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.params.fields").value(contains("enabledLanguages")));
    }

    @Test
    void aPageCountBelowOneIsRefused() throws Exception {
        mvc.perform(put("/api/v1/profile/preferences")
                        .header(HttpHeaders.IF_MATCH, currentEtag())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"defaults\": { \"maxPages\": 0 } }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aBodyThatCannotBeParsedIsNotAServerFailure() throws Exception {
        mvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.IF_MATCH, currentEtag())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"enabledLanguages\": 7 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ─── completeness and deletion ───

    @Test
    void completenessFollowsWhatTheProfileActuallyHolds() throws Exception {
        mvc.perform(get("/api/v1/profile")).andExpect(jsonPath("$.completeness").value(0));

        mvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.IF_MATCH, currentEtag())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "headline": "Backend Engineer",
                                  "contact": { "name": "Mustafa Tetik",
                                               "email": "mustafa@example.com" },
                                  "enabledLanguages": ["en"] }"""))
                .andExpect(status().isOk());

        // Contact alone is worth 15 (Bolum 31.9).
        mvc.perform(get("/api/v1/profile")).andExpect(jsonPath("$.completeness").value(15));

        String section = JSON.readTree(mvc.perform(post("/api/v1/profile/sections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ \"kind\": \"experience\", \"title\": \"Experience\" }"))
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(post("/api/v1/profile/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"sectionId\": \"" + section + "\", \"title\": \"Engineer\" }"))
                .andExpect(status().isCreated());

        // Plus 20 for having any, plus 10 for the position itself.
        mvc.perform(get("/api/v1/profile")).andExpect(jsonPath("$.completeness").value(45));

        assertThat(jdbc.queryForObject("SELECT completeness FROM profiles WHERE user_id = ?",
                Integer.class, LocalDevCurrentUser.DEV_USER_ID))
                .as("stored for the preflight gate, not only rendered")
                .isEqualTo(45);
    }

    @Test
    void deletingTheProfileTakesItsContentAndLeavesTheAccount() throws Exception {
        String section = JSON.readTree(mvc.perform(post("/api/v1/profile/sections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ \"kind\": \"experience\", \"title\": \"Experience\" }"))
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();

        mvc.perform(delete("/api/v1/profile")).andExpect(status().is(428));

        mvc.perform(delete("/api/v1/profile").header(HttpHeaders.IF_MATCH, currentEtag()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sections WHERE id = ?",
                Integer.class, java.util.UUID.fromString(section))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?",
                Integer.class, LocalDevCurrentUser.DEV_USER_ID))
                .as("the account survives its profile")
                .isEqualTo(1);

        // And the next read simply starts again.
        mvc.perform(get("/api/v1/profile"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.completeness").value(0));
    }

    // ─── export (Bolum 13.1: leaving has to be possible) ───

    @Test
    void theProfileCanBeTakenAwayAsJsonOrAsMarkdown() throws Exception {
        mvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.IF_MATCH, currentEtag())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "headline": "Backend Engineer",
                                  "contact": { "name": "Mustafa Tetik",
                                               "email": "mustafa@example.com",
                                               "location": "İstanbul" },
                                  "enabledLanguages": ["en"] }"""))
                .andExpect(status().isOk());

        String section = JSON.readTree(mvc.perform(post("/api/v1/profile/sections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ \"kind\": \"experience\", \"title\": \"Experience\" }"))
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();
        String entry = JSON.readTree(mvc.perform(post("/api/v1/profile/entries")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        { "sectionId": "%s", "title": "Backend Engineer",
                                          "organization": "Acme", "startDate": "2023-03-01" }"""
                                        .formatted(section)))
                        .andReturn().getResponse().getContentAsString())
                .get("id").asText();
        mvc.perform(post("/api/v1/profile/atoms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sectionId": "%s", "entryId": "%s", "kind": "bullet",
                                  "content": { "runs": [ { "t": "Built " },
                                                         { "t": "ETL", "m": ["technology"] },
                                                         { "t": " pipelines" } ] } }"""
                                .formatted(section, entry)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/profile/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"atomcv-profile-")))
                .andExpect(jsonPath("$.exportedAt").exists())
                .andExpect(jsonPath("$.profile.headline").value("Backend Engineer"))
                .andExpect(jsonPath("$.sections[0].section.title").value("Experience"))
                .andExpect(jsonPath("$.sections[0].entries[0].entry.organization").value("Acme"))
                .andExpect(jsonPath("$.sections[0].entries[0].atoms[0].variants[0].plainText")
                        .value("Built ETL pipelines"));

        String markdown = mvc.perform(get("/api/v1/profile/export").param("format", "markdown"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString(".md")))
                .andReturn().getResponse().getContentAsString();

        assertThat(markdown)
                .contains("# Mustafa Tetik")
                .contains("mustafa@example.com")
                // Turkish text survives the round trip, which it only does
                // because the response names its charset.
                .contains("İstanbul")
                .contains("## Experience")
                .contains("Acme")
                .contains("2023-03 – present")
                .contains("- Built ETL pipelines");
    }

    @Test
    void markdownDoesNotTurnUserTextIntoFormatting() throws Exception {
        mvc.perform(put("/api/v1/profile")
                        .header(HttpHeaders.IF_MATCH, currentEtag())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "headline": "C++ and *not* italics",
                                  "contact": { "name": "Mustafa Tetik" },
                                  "enabledLanguages": ["en"] }"""))
                .andExpect(status().isOk());

        String markdown = mvc.perform(get("/api/v1/profile/export").param("format", "markdown"))
                .andReturn().getResponse().getContentAsString();

        // The asterisks are escaped; the plus signs are left alone, because a
        // file people read should not be littered with backslashes.
        assertThat(markdown).contains("C++ and \\*not\\* italics");
    }

    @Test
    void anUnknownExportFormatIsRefused() throws Exception {
        mvc.perform(get("/api/v1/profile/export").param("format", "pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields").value(contains("format")));
    }

    private String minimalBody(String headline) {
        return "{ \"headline\": \"" + headline + "\", \"enabledLanguages\": [\"en\"] }";
    }

    private String currentEtag() throws Exception {
        return mvc.perform(get("/api/v1/profile"))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }
}
