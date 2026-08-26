package com.mustafatetik.atomcv.identity.api;

import com.mustafatetik.atomcv.identity.api.dto.MagicLinkRequest;
import com.mustafatetik.atomcv.identity.api.dto.VerifyRequest;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.service.MagicLinkService;
import com.mustafatetik.atomcv.identity.service.SessionCookies;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private final SessionCookies cookies;

    MagicLinkController(MagicLinkService magicLinks, SessionCookies cookies) {
        this.magicLinks = magicLinks;
        this.cookies = cookies;
    }

    @Operation(
            summary = "Ask for a sign-in link",
            description = """
                    Always 202, and always with no body. Whether the address \
                    has an account is exactly what this must not reveal \
                    (Bolum 40.4), so the sentence the person reads is the \
                    client's to write and is the same either way.""")
    @PostMapping("/magic-link")
    public ResponseEntity<Void> request(@Valid @RequestBody MagicLinkRequest request) {
        magicLinks.request(request.email());
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
    public ResponseEntity<Void> verify(@Valid @RequestBody VerifyRequest request) {
        Optional<Session> session = magicLinks.verify(request.selector(), request.verifier());
        if (session.isEmpty()) {
            throw ApiException.of(ErrorCode.MAGIC_LINK_INVALID);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.get().id()).toString())
                .build();
    }
}
