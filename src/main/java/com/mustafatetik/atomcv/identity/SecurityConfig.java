package com.mustafatetik.atomcv.identity;

import com.mustafatetik.atomcv.identity.service.SessionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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
 * <p>What is left is the part Spring Security does better than we would: the
 * double-submit CSRF filter of EK D.6.6.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SessionProperties sessions,
            CsrfProblemHandler csrfProblems) throws Exception {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfCookies(sessions))
                        .csrfTokenRequestHandler(eagerTokens()))
                .exceptionHandling(handling -> handling.accessDeniedHandler(csrfProblems))
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
