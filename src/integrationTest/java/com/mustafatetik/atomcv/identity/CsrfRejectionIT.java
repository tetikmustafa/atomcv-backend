package com.mustafatetik.atomcv.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The other half of the CSRF guard (EK D.6.6).
 *
 * <p>{@code AbstractIntegrationTest} puts a valid token on every request in
 * the suite, which is what keeps the filter on everywhere without editing a
 * hundred call sites — and which means no other test can ever see it refuse.
 * A guard that has never failed is not known to work, so this class builds its
 * own {@link MockMvc} <em>without</em> that default and makes it fail.
 */
class CsrfRejectionIT extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc withoutADefaultToken;

    @BeforeEach
    void buildAMockMvcThatSendsNoToken() {
        withoutADefaultToken = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anUnsafeMethodWithoutATokenIsRefusedInTheDocumentedShape() throws Exception {
        withoutADefaultToken.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void theSameCallWithATokenGoesThrough() throws Exception {
        withoutADefaultToken.perform(post("/api/v1/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
    }

    /**
     * A safe method is never refused — and it is where the token comes from.
     * Spring Security 6 defers loading the token until something asks for it,
     * which for a JSON API is never: without the eager handler in
     * {@code SecurityConfig} the cookie would first appear only after a
     * request had already been refused for lacking it, so a fresh browser's
     * first write would always fail.
     */
    @Test
    void aReadIsAllowedAndCarriesTheTokenTheNextWriteWillNeed() throws Exception {
        withoutADefaultToken.perform(get("/api/v1/auth/session"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                // The client has to read this one to echo it back, so it is the
                // one cookie that is not HttpOnly. It is not a credential:
                // holding it proves nothing without `sid`, which is.
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().sameSite("XSRF-TOKEN", "Strict"));
    }
}
