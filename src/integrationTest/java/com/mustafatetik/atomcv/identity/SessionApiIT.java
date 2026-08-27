package com.mustafatetik.atomcv.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.service.SessionStore;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.shared.security.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The session endpoints against a real Redis (Bolum 40.1, § 35.7).
 *
 * <p>This is also where {@code SessionStore}'s wiring is proved. The local
 * stand-in of {@code LocalDevSessions} answers a cookieless request without
 * touching Redis, so every other test in the suite runs past the store — these
 * sign in through it and drive the API with the cookie they get back.
 */
@AutoConfigureMockMvc
class SessionApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SessionStore sessions;

    @Autowired
    private LocalDevUser localUser;

    @Test
    void theSessionEndpointAnswersForACallerAndPublishesTheAccountCapabilities()
            throws Exception {
        localUser.ensureUserExists();

        mvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                // Never cached: this is the one response whose staleness shows
                // a user a screen they are not entitled to.
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.capabilities.canSaveHistory").value(true))
                .andExpect(jsonPath("$.capabilities.allowedTemplates[0]").value("classic"))
                .andExpect(jsonPath("$.capabilities.dailyGenerationQuota").value(20))
                // No ceiling on an account, so the field is absent rather than
                // a number the client would draw a bar against.
                .andExpect(jsonPath("$.capabilities.maxAtoms").doesNotExist())
                .andExpect(jsonPath("$.capabilities.anonymousExpiresAt").doesNotExist());
    }

    /**
     * Adim 3.6: a caller with no session gets one, and it is anonymous.
     *
     * <p>The bogus cookie is how this is reached without a second application
     * context. Under {@code local} every cookie-less request is the dev user,
     * but a cookie that does not resolve is deliberately <em>not</em> a
     * local-dev request — the browser held a session that expired, and
     * answering as the dev user would hide exactly that. So it arrives here
     * with no session, which is the state a first-time visitor is in.
     */
    @Test
    void acallerWithNoSessionIsGivenAnAnonymousOneAndToldWhenItRunsOut()
            throws Exception {
        var response = mvc.perform(get("/api/v1/auth/session")
                        .cookie(new Cookie("sid", "not-a-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                // Bolum 9's limits, not an account's.
                .andExpect(jsonPath("$.capabilities.canSaveHistory").value(false))
                .andExpect(jsonPath("$.capabilities.maxAtoms").value(60))
                .andExpect(jsonPath("$.capabilities.dailyGenerationQuota").value(5))
                // EK D.6.6: the countdown the client renders.
                .andExpect(jsonPath("$.capabilities.anonymousExpiresAt").exists())
                .andReturn();

        // And the cookie is issued, or the session it just minted would be
        // one nobody could come back to.
        assertThat(response.getResponse().getCookie("sid")).isNotNull();
        assertThat(response.getResponse().getCookie("sid").getValue())
                .isNotEqualTo("not-a-session");
    }

    /** A caller who already has one is not given a second. */
    @Test
    void asecondAskDoesNotMintAnotherSession() throws Exception {
        var first = mvc.perform(get("/api/v1/auth/session")
                        .cookie(new Cookie("sid", "not-a-session")))
                .andExpect(status().isOk()).andReturn();
        String minted = first.getResponse().getCookie("sid").getValue();

        var second = mvc.perform(get("/api/v1/auth/session").cookie(new Cookie("sid", minted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andReturn();

        // Nothing to set: the caller already carries the session.
        assertThat(second.getResponse().getCookie("sid")).isNull();
    }

    @Test
    void aSessionMintedInTheStoreIsTheActingUserOnTheNextRequest() throws Exception {
        localUser.ensureUserExists();
        Session session = sessions.create(
                LocalDevUser.DEV_USER_ID, UserRole.USER, AuthMethod.MAGIC_LINK);

        mvc.perform(get("/api/v1/account/usage").cookie(new Cookie("sid", session.id())))
                .andExpect(status().isOk());

        assertThat(sessions.find(session.id())).isPresent();
    }

    /**
     * Bolum 40.1 chose Redis over a JWT for exactly this: the session is gone
     * server-side, not merely forgotten by the browser.
     */
    @Test
    void signingOutRevokesTheSessionAndNotOnlyTheCookie() throws Exception {
        localUser.ensureUserExists();
        Session session = sessions.create(
                LocalDevUser.DEV_USER_ID, UserRole.USER, AuthMethod.OAUTH_GOOGLE);

        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("sid", session.id())))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(HttpHeaders.SET_COOKIE,
                                org.hamcrest.Matchers.containsString("sid=;")));

        assertThat(sessions.find(session.id())).isEmpty();
    }

    /**
     * The guard, made to fail. A cookie that no longer resolves must not fall
     * through to the local stand-in — if it did, the revocation above would
     * look like it worked while the browser stayed signed in as the dev user.
     */
    @Test
    void aRevokedCookieIsRefusedRatherThanFallingBackToTheLocalStandIn() throws Exception {
        localUser.ensureUserExists();
        Session session = sessions.create(
                LocalDevUser.DEV_USER_ID, UserRole.USER, AuthMethod.OAUTH_GITHUB);
        sessions.revoke(session.id());

        mvc.perform(get("/api/v1/account/usage").cookie(new Cookie("sid", session.id())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.resolutions[0].action").value("sign_up"));
    }

    @Test
    void aCookieThatNeverExistedIsTheSameAnswer() throws Exception {
        mvc.perform(get("/api/v1/account/usage").cookie(new Cookie("sid", "not-a-session")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void signingOutWithoutASessionIsStillANoContent() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("sid", "not-a-session")))
                .andExpect(status().isNoContent());
    }
}
