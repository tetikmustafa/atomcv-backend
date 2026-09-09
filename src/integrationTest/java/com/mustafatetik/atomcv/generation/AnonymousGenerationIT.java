package com.mustafatetik.atomcv.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.repository.AnonymousGenerations;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.identity.service.SessionCookies;
import com.mustafatetik.atomcv.identity.service.SessionStore;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Generating without an account, at the two ends the worker does not sit
 * between (Bolum 9).
 *
 * <p><strong>What is not here, and why.</strong> A whole anonymous generation
 * cannot run in this lane: the {@code local} profile registers no LLM provider,
 * so Faz A answers {@code ALL_PROVIDERS_UNAVAILABLE}, and there is no compiler
 * outside {@code latexTest}. What can be asserted is everything either side of
 * that — the request is accepted and queued as the session's, the quota is taken
 * from the address rather than from nobody, the letter is refused, and a finished
 * generation is readable by the session that owns it and by no other.
 */
@AutoConfigureMockMvc
class AnonymousGenerationIT extends AbstractIntegrationTest {

    private static final String POSTING = """
            Senior Backend Engineer. We are looking for someone with strong Java
            and Spring Boot experience to work on distributed systems, REST APIs
            and PostgreSQL. You will design services, review code and mentor.
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SessionStore sessions;

    @Autowired
    private SessionCookies cookies;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AnonymousGenerations anonymousRecords;

    private Session session;

    @BeforeEach
    void anAnonymousVisitorWithAProfile() {
        jdbc.update("DELETE FROM jobs WHERE type = 'generation' AND anon_session_id IS NOT NULL");
        jdbc.update("DELETE FROM usage_counters WHERE metric = 'generation'");
        session = sessions.createAnonymous();
        // A profile with something in it, so the request gets past the preflight
        // that refuses an empty one.
        jdbc.update("INSERT INTO profiles (id, expires_at, contact, preferences,"
                + " source_language, enabled_languages, completeness, created_at, updated_at,"
                + " version) VALUES (?, now() + interval '2 hours', '{}'::jsonb, '{}'::jsonb,"
                + " 'en', ARRAY['en'], 0, now(), now(), 0)", profileId());
        UUID sectionId = jdbc.queryForObject(
                "INSERT INTO sections (profile_id, kind, title, display_order)"
                        + " VALUES (?, 'experience', 'Experience', 0) RETURNING id",
                UUID.class, profileId());
        // An atom with a wording, because ProfilePreflight refuses a profile
        // that has nothing to print before anything is queued.
        UUID atomId = jdbc.queryForObject(
                "INSERT INTO atoms (profile_id, section_id, kind, display_order)"
                        + " VALUES (?, ?, 'bullet', 0) RETURNING id",
                UUID.class, profileId(), sectionId);
        jdbc.update("INSERT INTO atom_variants (profile_id, atom_id, language, content,"
                + " plain_text, content_hash, is_primary)"
                + " VALUES (?, ?, 'en', ?::jsonb, ?, 'h', true)",
                profileId(), atomId,
                "{\"v\":1,\"runs\":[{\"t\":\"Moved 300K rows with Microsoft Fabric\"}]}",
                "Moved 300K rows with Microsoft Fabric");
    }

    // -- the request ---------------------------------------------------------

    /**
     * Queued as the session's own work, and paid for by the address.
     *
     * <p>Bolum 44.1 counts an anonymous caller by address and not by session:
     * a session is a cookie, and counting by one would give an unlimited
     * allowance to whoever clears theirs. The subject travels in the payload
     * because the worker has no request to read an address from — a refund has
     * to reach whoever paid.
     */
    @Test
    void ananonymousCallerCanQueueAGenerationAndTheJobIsTheirs() throws Exception {
        mvc.perform(post("/api/v1/generations")
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(false)))
                .andExpect(status().isAccepted());

        var job = jdbc.queryForMap("SELECT user_id, anon_session_id, payload::text AS payload"
                + " FROM jobs WHERE type = 'generation' AND anon_session_id = ?", session.id());
        assertThat(job.get("user_id")).isNull();
        assertThat(job).containsEntry("anon_session_id", session.id());
        assertThat((String) job.get("payload")).contains("\"allowanceType\": \"ip\"");
    }

    /**
     * § 35.7 gives an account the letter and a session the CV. Refused ahead of
     * the quota, for the reason the pause is: a request that will be refused
     * must not spend anybody's day.
     */
    @Test
    void thecoverLetterIsRefusedWithTheFeatureNamed() throws Exception {
        mvc.perform(post("/api/v1/generations")
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_REQUIRES_ACCOUNT"))
                .andExpect(jsonPath("$.params.feature").value("cover_letter"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM jobs WHERE anon_session_id = ?",
                Integer.class, session.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usage_counters WHERE metric ="
                + " 'generation'", Integer.class)).isZero();
    }

    // -- and reading it back -------------------------------------------------

    @Test
    void thesessionCanReadItsOwnGenerationAndNobodyElseCan() throws Exception {
        UUID generationId = aFinishedGeneration();

        mvc.perform(get("/api/v1/generations/" + generationId).cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generationId").value(generationId.toString()));

        Session somebodyElse = sessions.createAnonymous();
        mvc.perform(get("/api/v1/generations/" + generationId)
                        .cookie(new Cookie(cookies.name(), somebodyElse.id())))
                // 404 and not 403: that an id exists is itself information.
                .andExpect(status().isNotFound());
    }

    // -- the two controls that answered the wrong refusal --------------------

    /**
     * <strong>F-030, and it was a 401 until now.</strong> Both of these
     * endpoints called {@code currentUser.require()}, so a session holding a
     * perfectly good cookie and its own generation was told
     * {@code AUTHENTICATION_REQUIRED} — from which the screen writes "your
     * session ended". The session had not ended; the feature was never theirs.
     *
     * <p>The frontend had mocked {@code 403 FEATURE_REQUIRES_ACCOUNT} with
     * {@code params.feature = feedback}, said so, and asked whether the token
     * was real. It was not, and now it is.
     */
    @Test
    void feedbackFromASessionIsAMissingFeatureAndNotAMissingSession() throws Exception {
        UUID generationId = aFinishedGeneration();

        mvc.perform(post("/api/v1/generations/" + generationId + "/feedback")
                        .cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_REQUIRES_ACCOUNT"))
                .andExpect(jsonPath("$.params.feature").value("feedback"))
                .andExpect(jsonPath("$.resolutions[0].action").value("sign_up"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM generation_feedback",
                Integer.class)).isZero();
    }

    /**
     * The same refusal on the letter, and it names the same feature the
     * generation endpoint does — one token per control, whichever door it is
     * asked through.
     */
    @Test
    void regeneratingALetterFromASessionNamesTheFeatureToo() throws Exception {
        UUID generationId = aFinishedGeneration();

        mvc.perform(post("/api/v1/generations/" + generationId
                        + "/cover-letter/regenerate")
                        .cookie(cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FEATURE_REQUIRES_ACCOUNT"))
                .andExpect(jsonPath("$.params.feature").value("cover_letter"))
                .andExpect(jsonPath("$.resolutions[0].action").value("sign_up"));
    }

    /**
     * The other half, and without it the change above would just be a renamed
     * 401. A caller whose session resolves to <em>nothing</em> still gets
     * {@code AUTHENTICATION_REQUIRED}: that is the plain case EK D.6 keeps the
     * code for, and telling them they need an *account* would be the same
     * wrong sentence pointing the other way.
     *
     * <p><strong>A stale cookie and not a missing one, and the lane is why.</strong>
     * A request with no cookie at all does not reach this branch here:
     * {@code SessionCurrentUser.resolve} falls through to {@code LocalDevSessions}
     * and answers as the dev user, so the test would measure the stub rather
     * than the product — the same trap F-027's {@code 204} was hiding in. A
     * cookie that no longer resolves takes the cookie branch, is filtered to
     * empty, and is a browser state that actually happens: a revoked or expired
     * session posting a thumb.
     */
    @Test
    void acookieThatNoLongerResolvesStillGetsTheAuthenticationCode() throws Exception {
        UUID generationId = aFinishedGeneration();

        mvc.perform(post("/api/v1/generations/" + generationId + "/feedback")
                        .cookie(new Cookie(cookies.name(), UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    // -- fixtures ------------------------------------------------------------

    /**
     * A finished generation of this session's profile, written straight in: the
     * pipeline that would produce one needs a model and a compiler, and neither
     * is in this lane.
     */
    private UUID aFinishedGeneration() {
        var selection = new SelectionState(List.of(), List.of(),
                new SelectionState.BudgetBreakdown(708, 100, 608, 0), List.of(), List.of());
        var record = new Generation(null, profileId(), Map.of(),
                StoredSelection.of(selection, "en", TemplateCustomization.CLASSIC),
                new EngineVersion(null, "default", "classic", Map.of()));
        record.setPageCount((short) 1);
        // Through the same door the handler writes it, so the row is one the
        // reader can actually deserialise -- a hand-written selection_state was
        // not, and said so as a 500.
        return anonymousRecords.save(
                ProfileRef.ephemeral(AnonymousSessionId.of(session.id())), record).getId();
    }

    private static String request(boolean coverLetter) {
        return "{\"jobDescription\":\"" + POSTING.replace("\n", " ").trim()
                + "\",\"acknowledgePreflight\":true,\"coverLetter\":" + coverLetter + "}";
    }

    private Cookie cookie() {
        return new Cookie(cookies.name(), session.id());
    }

    private UUID profileId() {
        return ProfileRef.ephemeral(AnonymousSessionId.of(session.id())).id();
    }
}
