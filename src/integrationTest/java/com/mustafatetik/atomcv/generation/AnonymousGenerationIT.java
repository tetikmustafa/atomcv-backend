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
