package com.mustafatetik.atomcv.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.service.SessionCookies;
import com.mustafatetik.atomcv.identity.service.SessionStore;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The profile editor, driven by somebody who has not signed up (Bolum 9).
 *
 * <p><strong>Through HTTP on purpose.</strong> The services underneath were
 * already caller-agnostic — they take a {@code ProfileRef} — so what this slice
 * actually changed is one line in each of three controllers and the seam behind
 * it. Bolum 51.2's rule about wiring applies exactly here: asserting on
 * {@code CallerProfiles} would prove the seam and nothing about whether an
 * anonymous cookie reaches it.
 *
 * <p>§ 35.7's limits are asserted here too, at the layer that publishes them.
 * The capability block says {@code canEditAtomControls: false} and
 * {@code canAddAlternatives: false}, and the section ends with the sentence this
 * file exists for: <em>"Sunucu yine de doğrular."</em>
 */
@AutoConfigureMockMvc
class AnonymousEditorIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SessionStore sessions;

    @Autowired
    private SessionCookies cookies;

    @Autowired
    private JdbcTemplate jdbc;

    private static final ObjectMapper JSON = new ObjectMapper();

    private Session session;

    @BeforeEach
    void anAnonymousVisitor() {
        session = sessions.createAnonymous();
    }

    // -- the editor works --------------------------------------------------

    /**
     * And the profile comes into being on the way, exactly as an account's does
     * on its first request — with an expiry rather than an owner.
     */
    @Test
    void listingSectionsCreatesTheProfileAndAnswersEmpty() throws Exception {
        mvc.perform(get("/api/v1/profile/sections").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM profiles WHERE id = ? AND user_id IS NULL"
                        + " AND expires_at IS NOT NULL",
                Integer.class, profileId())).isEqualTo(1);
    }

    @Test
    void asectionAnAtomAndAWordingCanAllBeWritten() throws Exception {
        UUID sectionId = createSection();

        var created = JSON.readTree(mvc.perform(post("/api/v1/profile/atoms")
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"" + sectionId + "\",\"kind\":\"bullet\","
                                + "\"content\":{\"runs\":[{\"t\":\"Moved 300K rows\"}]}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(created.path("id").asText()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atoms WHERE profile_id = ?",
                Integer.class, profileId())).isEqualTo(1);
    }

    // -- and § 35.7's limits bite -------------------------------------------

    /**
     * An atom control, refused with the name of the feature so the screen can
     * say which button needs an account.
     */
    @Test
    void anatomControlIsRefusedWithTheFeatureNamed() throws Exception {
        var atom = createAtom(createSection());

        mvc.perform(patch("/api/v1/profile/atoms/" + atom.id())
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"importance\":0.9}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_REQUIRES_ACCOUNT"))
                .andExpect(jsonPath("$.params.feature").value("atom_controls"));
    }

    /**
     * And what the CV says is not a control: the same patch shape goes through.
     *
     * <p>This one carries {@code If-Match} and the refusal above does not, which
     * is the right way round rather than an oversight — a capability the caller
     * does not have is refused before the version is looked at, because "sign up
     * to do this" does not depend on which version they were looking at.
     */
    @Test
    void thewordsOfTheCvAreStillTheirsToChange() throws Exception {
        var atom = createAtom(createSection());

        mvc.perform(patch("/api/v1/profile/atoms/" + atom.id())
                        .cookie(cookie())
                        .header(org.springframework.http.HttpHeaders.IF_MATCH, atom.etag())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[\"Spring Boot\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void asecondWordingIsRefusedBecauseAlternativesNeedAnAccount() throws Exception {
        var atom = createAtom(createSection());

        mvc.perform(post("/api/v1/profile/atoms/" + atom.id() + "/variants")
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":{\"runs\":[{\"t\":\"Another wording\"}]},"
                                + "\"language\":\"en\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.params.feature").value("alternatives"));
    }

    // -- and it is still their own profile and nobody else's ----------------

    @Test
    void anotherVisitorSeesNoneOfIt() throws Exception {
        createSection();
        Session somebodyElse = sessions.createAnonymous();

        mvc.perform(get("/api/v1/profile/sections")
                        .cookie(new Cookie(cookies.name(), somebodyElse.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // A caller with no session at all is refused with `sign_up`, and that is
    // NOT asserted here: this lane runs the `local` profile, where LocalDevUser
    // and LocalDevSessions answer "who is acting" without a credential (EK C.1
    // guards them with @Profile for exactly that reason). A request with no
    // cookie is served as the dev user and returns 200, so a test of the refusal
    // here would be a test of the stand-in. CallerProfiles' own refusal is the
    // one JobOwner.of already gives and AnonymousLimitsTest reaches directly.

    // -- fixtures ----------------------------------------------------------

    private UUID createSection() throws Exception {
        JsonNode created = JSON.readTree(mvc.perform(post("/api/v1/profile/sections")
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"experience\",\"title\":\"Experience\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return UUID.fromString(created.path("id").asText());
    }

    /** An atom and the ETag it was created with, which a patch needs. */
    private record CreatedAtom(UUID id, String etag) {
    }

    private CreatedAtom createAtom(UUID sectionId) throws Exception {
        var response = mvc.perform(post("/api/v1/profile/atoms")
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"" + sectionId + "\",\"kind\":\"bullet\","
                                + "\"content\":{\"runs\":[{\"t\":\"Moved 300K rows\"}]}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        JsonNode created = JSON.readTree(response.getContentAsString());
        return new CreatedAtom(UUID.fromString(created.path("id").asText()),
                response.getHeader(org.springframework.http.HttpHeaders.ETAG));
    }

    private Cookie cookie() {
        return new Cookie(cookies.name(), session.id());
    }

    private UUID profileId() {
        return ProfileRef.ephemeral(AnonymousSessionId.of(session.id())).id();
    }
}
