package com.mustafatetik.atomcv.identity;

import com.mustafatetik.atomcv.identity.service.SessionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * The filter chain, and what Spring Security is here for (EK D.6.6).
 *
 * <p><strong>It does not own authentication.</strong> Who is acting comes from
 * the {@code sid} cookie and Redis — see {@code identity.service} — and
 * authorisation is resource ownership, which Bolum 41.4 puts in the scoped
 * repositories and not in a URL pattern. So every request is permitted here
 * and refused, if at all, by the endpoint asking for a user it does not get.
 * A second list of protected paths would be a list that drifts from the first.
 *
 * <p>What is left is two things Spring Security does better than we would: the
 * double-submit CSRF filter of EK D.6.6, and the response headers EK C.1 asks
 * to see verified.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SessionProperties sessions,
            CsrfProblemHandler csrfProblems) throws Exception {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfCookies(sessions))
                        .csrfTokenRequestHandler(eagerTokens())
                        // The one exception, and it is narrow on purpose.
                        // A provider's webhook has no session and no token to
                        // double-submit, so CSRF cannot apply to it -- and it
                        // does not need to: CSRF defends a request that rides
                        // on a cookie, and this one has none. What stands in
                        // its place is the signature, which is stronger,
                        // because it authenticates the *sender* rather than
                        // merely proving the caller could read a cookie.
                        // See ResendSignature; an unverified delivery is 401.
                        .ignoringRequestMatchers("/api/v1/webhooks/**"))
                .exceptionHandling(handling -> handling.accessDeniedHandler(csrfProblems))
                .headers(SecurityConfig::responseHeaders)
                // Ownership is the gate (Bolum 41.4). Paths listed here would
                // be a second, drifting copy of that decision.
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                // No servlet session, ever: ours lives in Redis, and a
                // JSESSIONID appearing beside sid would be a second identity
                // with none of the revocation Bolum 40.1 chose Redis for.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Neither exists, and a default login page answering an API
                // path with 302 to /login is a redirect the frontend would
                // parse as a successful response.
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .build();
    }

    /**
     * EK C.1's header row, and what each one is doing on a JSON API.
     *
     * <p>{@code X-Content-Type-Options: nosniff} and {@code X-Frame-Options:
     * DENY} are Spring Security's own defaults and are not repeated here — a
     * re-declaration that matched the default would hide the day it stopped
     * matching. {@code SecurityHeadersIT} asserts them anyway, so the day an
     * upgrade drops one is the day a test says so.
     *
     * <p><strong>HSTS</strong> is on with a year and subdomains. Nginx
     * terminates TLS (Bolum 11.2) and could send it instead, but a header the
     * application depends on for its own safety should not live in a file the
     * application does not ship. It is skipped on a plain-http request, so
     * {@code make dev} is unaffected.
     *
     * <p><strong>CSP</strong> is the one Spring does not send. This API serves
     * JSON, and the two exceptions are Swagger UI — disabled in production —
     * and an error page. So the policy denies everything and then allows only
     * what a same-origin document needs: nothing loads from another host, no
     * inline script runs, and no page may frame this one. It is a second lock
     * on {@code frame-ancestors} because {@code X-Frame-Options} has no
     * standard behaviour for a nested frame.
     *
     * <p><strong>Referrer-Policy</strong> matters more here than it looks:
     * Bolum 40.3's sign-in link carries the verifier in a query string, and a
     * full referrer would hand it to every host an image on that page came
     * from. The frontend owns that page, but a redirect through this API must
     * not be the leak either.
     */
    private static void responseHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers.httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; "
                        + "frame-ancestors 'none'; "
                        + "base-uri 'none'; "
                        + "form-action 'self'; "
                        + "object-src 'none'"))
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER));
    }

    /**
     * The readable half of the double submit. {@code HttpOnly} is off by
     * design — the client has to read this one to echo it in
     * {@code X-XSRF-TOKEN} — which is safe because the token is not a
     * credential: holding it proves nothing without the session cookie beside
     * it, and that one stays {@code HttpOnly}.
     */
    private CookieCsrfTokenRepository csrfCookies(SessionProperties sessions) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> {
            cookie.sameSite("Strict");
            cookie.secure(sessions.secure());
            cookie.path("/");
            if (sessions.domain() != null) {
                cookie.domain(sessions.domain());
            }
        });
        return repository;
    }

    /**
     * Spring Security 6 defers loading the token until something asks for it,
     * which for a JSON API is never — so the cookie would only appear after a
     * request had already been refused for lacking it, and the first write of
     * a fresh browser would always fail. Naming no request attribute opts out
     * of the deferral, so every response carries the cookie.
     *
     * <p>The plain handler rather than the XOR one: the BREACH masking exists
     * for a token rendered into an HTML body, and nothing here renders HTML.
     * The masked value would only have to be un-masked by the client.
     */
    private CsrfTokenRequestAttributeHandler eagerTokens() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
}
