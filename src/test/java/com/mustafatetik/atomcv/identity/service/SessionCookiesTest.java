package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

/** Bolum 40.1's cookie, written once so it cannot drift between the two calls. */
class SessionCookiesTest {

    private static final SessionProperties PRODUCTION =
            new SessionProperties(Duration.ofDays(30), null, null, "atomcv.example.com", true);

    @Test
    void theSessionCookieCarriesEveryAttributeTheSectionNames() {
        ResponseCookie cookie = new SessionCookies(PRODUCTION).issue("a-session-id");

        assertThat(cookie.getName()).isEqualTo("sid");
        assertThat(cookie.getValue()).isEqualTo("a-session-id");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isEqualTo("atomcv.example.com");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(30));
    }

    /**
     * A browser replaces a cookie only when name, domain and path all match.
     * A clear that differed in any of them would write a second cookie and
     * leave the live one standing — a sign-out that signs nobody out.
     */
    @Test
    void clearingMatchesTheIssuedCookieInEveryFieldThatIdentifiesIt() {
        SessionCookies cookies = new SessionCookies(PRODUCTION);
        ResponseCookie issued = cookies.issue("a-session-id");
        ResponseCookie cleared = cookies.clear();

        assertThat(cleared.getName()).isEqualTo(issued.getName());
        assertThat(cleared.getDomain()).isEqualTo(issued.getDomain());
        assertThat(cleared.getPath()).isEqualTo(issued.getPath());
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isEqualTo(Duration.ZERO);
    }

    @Test
    void withoutAConfiguredDomainTheCookieIsHostOnly() {
        var local = new SessionProperties(null, null, null, null, false);

        ResponseCookie cookie = new SessionCookies(local).issue("a-session-id");

        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.isSecure()).isFalse();
    }

    @Test
    void aRequestWithoutCookiesCarriesNoSession() {
        var cookies = new SessionCookies(PRODUCTION);

        assertThat(cookies.read(new MockHttpServletRequest())).isEmpty();
    }

    @Test
    void anEmptyCookieValueIsReadAsNoSessionRatherThanAsAnEmptyOne() {
        var request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("sid", ""));

        assertThat(new SessionCookies(PRODUCTION).read(request)).isEmpty();
    }

    @Test
    void theCookieIsFoundAmongOthers() {
        var request = new MockHttpServletRequest();
        request.setCookies(
                new jakarta.servlet.http.Cookie("XSRF-TOKEN", "irrelevant"),
                new jakarta.servlet.http.Cookie("sid", "a-session-id"));

        assertThat(new SessionCookies(PRODUCTION).read(request)).contains("a-session-id");
    }
}
