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
