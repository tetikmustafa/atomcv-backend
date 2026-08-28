package com.mustafatetik.atomcv.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * EK C.1: "Guvenlik header'lari (HSTS, CSP, X-Frame-Options) dogrulandi."
 *
 * <p>Two of these are Spring Security's defaults rather than ours, and they are
 * asserted for exactly that reason: a default is a decision somebody else can
 * change in a minor upgrade, and the checklist item says the deployment sends
 * them, not that a framework version happened to.
 */
@AutoConfigureMockMvc
class SecurityHeadersIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    /**
     * Ours, and the one Spring does not send on its own.
     *
     * <p>No status is asserted anywhere in this class, deliberately. These
     * headers are written by a filter that runs before anything decides on a
     * status, so a policy that appeared only on 200 would be a bug — and health
     * reports 503 in the full suite whenever a probe's dependency is down,
     * which would make this class fail for a reason that has nothing to do
     * with headers.
     */
    @Test
    void everyResponseCarriesAContentSecurityPolicyThatFramesNothing() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'none'; base-uri 'none'; "
                        + "form-action 'self'; object-src 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    /** Spring's defaults. Asserted so that losing one is a failing test. */
    @Test
    void theFrameworkDefaultsThisDeploymentReliesOnAreStillSent() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    /**
     * HSTS is skipped on plain http by design — {@code make dev} serves http
     * and a browser told to remember localhost as https-only would break every
     * later run. Nginx terminates TLS in production, so the forwarded scheme is
     * what decides.
     */
    @Test
    void strictTransportSecurityFollowsTheForwardedScheme() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));

        mvc.perform(get("/actuator/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security",
                        "max-age=31536000 ; includeSubDomains"));
    }
}
