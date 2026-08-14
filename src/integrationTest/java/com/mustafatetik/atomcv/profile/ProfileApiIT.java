package com.mustafatetik.atomcv.profile;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.shared.security.LocalDevCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The first endpoint, wired the way it runs locally: the stand-in acting user,
 * the resolver that creates the profile, the ETag a write will need.
 *
 * <p>{@code local} is active on purpose. The alternative — a test-only
 * {@code CurrentUser} bean — would leave the only wiring that exists today
 * untested.
 */
@AutoConfigureMockMvc
class ProfileApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevCurrentUser localUser;

    /**
     * Other integration tests clear {@code users} after themselves, and the
     * startup runner only ran once for the shared context.
     */
    @BeforeEach
    void ensureTheStandInUserExists() {
        localUser.ensureUserExists();
    }

    @Test
    void theStandInUserExistsSoThatProfilesCanHangOffIt() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?",
                Integer.class, LocalDevCurrentUser.DEV_USER_ID)).isEqualTo(1);
    }

    @Test
    void readingTheProfileCreatesItOnFirstUseAndCarriesItsVersion() throws Exception {
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevCurrentUser.DEV_USER_ID);

        mvc.perform(get("/api/v1/profile"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.sourceLanguage").value("en"))
                .andExpect(jsonPath("$.enabledLanguages[0]").value("en"))
                .andExpect(jsonPath("$.completeness").value(0))
                .andExpect(jsonPath("$.preferences.defaults.maxPages").value(1))
                .andExpect(jsonPath("$.preferences.defaults.templateId").value("classic"))
                .andExpect(jsonPath("$.preferences.writingStyle.tone").value("formal"))
                // No identifier and no version field: ownership comes from the
                // session, and the version lives in the ETag.
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM profiles WHERE user_id = ?",
                Integer.class, LocalDevCurrentUser.DEV_USER_ID)).isEqualTo(1);
    }

    @Test
    void readingItTwiceDoesNotCreateASecondProfile() throws Exception {
        mvc.perform(get("/api/v1/profile")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/profile")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM profiles WHERE user_id = ?",
                Integer.class, LocalDevCurrentUser.DEV_USER_ID)).isEqualTo(1);
    }

    @Test
    void anUnknownApiPathAnswersWithACodeRatherThanAStackTrace() throws Exception {
        mvc.perform(get("/api/v1/profile/there-is-no-such-thing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.type").value("/errors/resource-not-found"));
    }
}
