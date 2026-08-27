package com.mustafatetik.atomcv.identity.api;

import com.mustafatetik.atomcv.identity.api.dto.MagicLinkRequest;
import com.mustafatetik.atomcv.identity.api.dto.SignInResponse;
import com.mustafatetik.atomcv.identity.api.dto.VerifyRequest;
import com.mustafatetik.atomcv.identity.challenge.Challenge;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.ratelimit.ClientIp;
import com.mustafatetik.atomcv.identity.ratelimit.SignInRateLimit;
import com.mustafatetik.atomcv.identity.service.MagicLinkService;
import com.mustafatetik.atomcv.identity.service.SessionCookies;
import com.mustafatetik.atomcv.identity.service.SignInHandover;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.profile.service.ProfileUpgrade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signing in without a provider (Bolum 40.2).
 *
 * <p><strong>Two POSTs, and the second one being a POST is the point.</strong>
 * The address in the email is a {@code GET} that renders a page on the
 * frontend; Bolum 40.3 explains why. Corporate mail scanners follow links
 * before a person ever sees them, and a one-shot token spent by a scanner is a
 * sign-in the user never got and cannot ask for again. A scanner does not
 * submit forms.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Signing in with an email link")
public class MagicLinkController {

    private final MagicLinkService magicLinks;
    private final SignInRateLimit rateLimit;
    private final Challenge challenge;
    private final SessionCookies cookies;
    private final SignInHandover handover;

    MagicLinkController(MagicLinkService magicLinks, SignInRateLimit rateLimit,
            Challenge challenge, SessionCookies cookies, SignInHandover handover) {
        this.magicLinks = magicLinks;
        this.rateLimit = rateLimit;
        this.challenge = challenge;
        this.cookies = cookies;
        this.handover = handover;
    }

    @Operation(
            summary = "Ask for a sign-in link",
            description = """
                    Always 202, and always with no body. Whether the address \
                    has an account is exactly what this must not reveal \
                    (Bolum 40.4), so the sentence the person reads is the \
                    client's to write and is the same either way.

                    The two other answers it can give reveal nothing either:
                    `429 RATE_LIMITED`, where every layer of Bolum 40.5
                    counts what this caller has already done, and
                    `403 CHALLENGE_FAILED`, which is about the token in the
                    request and not about the address in it.""")
    @PostMapping("/magic-link")
    public ResponseEntity<Void> request(@Valid @RequestBody MagicLinkRequest body,
            HttpServletRequest request) {
        // The caller's two layers here, the address's layer inside the
        // service one line after the address is normalised. Bolum 40.5's
        // three counters are not one call because they do not belong at
        // one place: the address layer has to run behind the challenge.
        rateLimit.checkCaller(ClientIp.of(request));
        if (!challenge.passed(body.challengeToken())) {
            throw ApiException.of(ErrorCode.CHALLENGE_FAILED);
        }
        magicLinks.request(body.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(
            summary = "Redeem a sign-in link",
            description = """
                    A POST, because the link in the email is not. Every \
                    refusal is the same refusal: expired, already used, wrong \
                    verifier and never existed are one answer, since telling \
                    them apart tells an attacker which half of a guess was \
                    right.""")
    @PostMapping("/verify")
    public ResponseEntity<SignInResponse> verify(@Valid @RequestBody VerifyRequest request) {
        Optional<Session> session = magicLinks.verify(request.selector(), request.verifier());
        if (session.isEmpty()) {
            throw ApiException.of(ErrorCode.MAGIC_LINK_INVALID);
        }
        // Before the cookie is replaced: the anonymous session id is readable
        // up to this line and never afterwards (Adim 3.6).
        ProfileUpgrade upgrade = handover.follow(session.get());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.get().id()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new SignInResponse(upgrade.wireValue()));
    }
}
