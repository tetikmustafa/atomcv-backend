package com.mustafatetik.atomcv.identity.service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The {@code sid} cookie, written the one way Bolum 40.1 specifies.
 *
 * <p>In one place so that the attributes cannot drift apart: a sign-in that
 * sets {@code SameSite=Strict} and a sign-out that clears without it leaves
 * the cookie standing in exactly the cross-site case it was meant to block.
 *
 * <p><strong>{@code domain} carries no leading dot.</strong> Adim 3.3 spells
 * this out and {@link SessionProperties} refuses the dotted form: a cookie
 * scoped to {@code .mustafatetik.com} would be sent to the portfolio site on
 * the same apex, which has no use for a session and every opportunity to leak
 * one.
 */
@Component
public class SessionCookies {

    private final SessionProperties properties;

    SessionCookies(SessionProperties properties) {
        this.properties = properties;
    }

    public String name() {
        return properties.cookieName();
    }

    /** The value the browser sent, if it sent one. */
    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> properties.cookieName().equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public ResponseCookie issue(String sessionId) {
        return base(sessionId).maxAge(properties.ttl()).build();
    }

    /**
     * Clears it. Same attributes, empty value, zero age — a browser matches a
     * replacement on name, domain and path, so a clear that differs in any of
     * them writes a second cookie and leaves the first.
     */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(
                        properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite("Strict")
                .path("/");
        return properties.domain() == null ? builder : builder.domain(properties.domain());
    }
}
