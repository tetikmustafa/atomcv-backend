package com.mustafatetik.atomcv.identity.api;

import com.mustafatetik.atomcv.identity.api.dto.SessionResponse;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.service.Capabilities;
import com.mustafatetik.atomcv.identity.service.SessionCookies;
import com.mustafatetik.atomcv.identity.service.SessionCurrentUser;
import com.mustafatetik.atomcv.identity.service.SessionStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The session, as the client asks about it (§ 35.7, Bolum 40.1).
 *
 * <p>Both endpoints answer for a caller with no session at all — that is the
 * point of the first one, and the second has to be safe to call twice. Neither
 * uses {@code CurrentUser.require()} for that reason.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Who is signed in, and signing out")
public class SessionController {

    private final SessionCurrentUser currentUser;
    private final SessionStore sessions;
    private final SessionCookies cookies;
    private final Capabilities capabilities;

    SessionController(SessionCurrentUser currentUser, SessionStore sessions,
            SessionCookies cookies, Capabilities capabilities) {
        this.currentUser = currentUser;
        this.sessions = sessions;
        this.cookies = cookies;
        this.capabilities = capabilities;
    }

    @Operation(
            summary = "Whether anyone is signed in, and what they may do",
            description = """
                    Answers for every caller, signed in or not — the client \
                    calls this first and decides what to render from \
                    `capabilities`. Never cached: it is the one response whose \
                    staleness shows the user a screen they are not entitled to.""")
    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new SessionResponse(
                        currentUser.find().isPresent(),
                        capabilities.of(currentUser.find())));
    }

    @Operation(
            summary = "Sign out",
            description = """
                    Revokes the session server-side and clears the cookie. \
                    Idempotent: calling it without a session is a 204 as well, \
                    because a client whose cookie has already expired is \
                    exactly the client that calls this.""")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        currentUser.session().map(Session::id).ifPresent(sessions::revoke);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }
}
