package com.mustafatetik.atomcv.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.ingestion.github.GitHubRepositories;
import com.mustafatetik.atomcv.ingestion.github.GitHubRepository;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Both halves: what GitHub offers and what a person chose.
 *
 * <p><strong>GitHub itself is stubbed and the rest is real.</strong> What is
 * under test is the matching and the writing — whether a repository lands on
 * the project somebody already described or next to it, and whether their own
 * sentences survive it. A real call would test GitHub's uptime.
 */
@AutoConfigureMockMvc
@Import(GitHubImportApiIT.StubbedGitHub.class)
class GitHubImportApiIT extends AbstractIntegrationTest {

    /** Reads whatever the test put in the list, for any account. */
    @TestConfiguration
    static class StubbedGitHub {

        @Bean
        @Primary
        GitHubRepositories stubbedGitHub(List<GitHubRepository> repositories) {
            return login -> List.copyOf(repositories);
        }

        @Bean
        List<GitHubRepository> repositories() {
            return new ArrayList<>();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevUser localUser;

    @Autowired
    private List<GitHubRepository> repositories;

    private String projectsSectionId;

    @BeforeEach
    void aprofileWithOneProject() throws Exception {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevUser.DEV_USER_ID);
        repositories.clear();

        projectsSectionId = created("/api/v1/profile/sections",
                "{ \"kind\": \"projects\", \"title\": \"Projects\", \"layout\": \"entry_list\" }");
        String entryId = created("/api/v1/profile/entries", """
                { "sectionId": "%s", "title": "Order Management System" }"""
                .formatted(projectsSectionId));
        created("/api/v1/profile/atoms", """
                { "sectionId": "%s", "entryId": "%s", "kind": "bullet",
                  "content": { "runs": [ { "t": "Split the monolith into four services" } ] } }"""
                .formatted(projectsSectionId, entryId));

        // The account the CV names, which is what the endpoint reads when the
        // request does not say.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/profile")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sourceLanguage": "en", "enabledLanguages": ["en"],
                                  "contact": { "name": "Ada Lovelace", "email": "ada@example.com",
                                               "github": "https://github.com/ada" } }"""))
                .andExpect(status().isOk());
    }

    /**
     * A repository whose name is the project's title, spelled the way a
     * repository is spelled. Jaro-Winkler is what closes that gap.
     */
    @Test
    void arepositoryThatMatchesAprojectIsOfferedAsAmerge() throws Exception {
        repositories.add(repository("order-management-system", "Order pipeline", 12));

        mvc.perform(post("/api/v1/profile/github/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("order-management-system"))
                .andExpect(jsonPath("$[0].matchedEntryId").isNotEmpty())
                .andExpect(jsonPath("$[0].confidence").value(
                        org.hamcrest.Matchers.greaterThan(0.85)));
    }

    /** And one that matches nothing is offered as what it is. */
    @Test
    void anunrelatedRepositoryIsOfferedAsAnewProject() throws Exception {
        repositories.add(repository("weather-radar", "Rain maps from open data", 3));

        mvc.perform(post("/api/v1/profile/github/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchedEntryId").doesNotExist())
                .andExpect(jsonPath("$[0].confidence").doesNotExist());
    }

    /** The filter, through the endpoint: a tutorial is not an offer. */
    @Test
    void alessonIsNotOffered() throws Exception {
        repositories.add(repository("react-tutorial", "following along", 0));

        mvc.perform(post("/api/v1/profile/github/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    /**
     * <strong>The sentence is the person's and stays the person's.</strong>
     * The narrative comes from the CV. What a merge adds is the skills GitHub
     * can vouch for and the link -- and the bullet reads exactly as it did.
     */
    @Test
    void amergeAddsSkillsAndAlinkAndTouchesNoSentence() throws Exception {
        repositories.add(repository("order-management-system", "Order pipeline", 12));

        mvc.perform(post("/api/v1/profile/github/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositories\":[\"order-management-system\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(1));

        assertThat(jdbc.queryForObject("""
                SELECT plain_text FROM atom_variants v
                JOIN atoms a ON a.id = v.atom_id
                WHERE a.profile_id = (SELECT id FROM profiles WHERE user_id = ?)
                """, String.class, LocalDevUser.DEV_USER_ID))
                .as("the bullet is untouched")
                .isEqualTo("Split the monolith into four services");

        assertThat(jdbc.queryForObject("""
                SELECT url FROM entries
                WHERE profile_id = (SELECT id FROM profiles WHERE user_id = ?)
                """, String.class, LocalDevUser.DEV_USER_ID))
                .contains("order-management-system");

        assertThat(jdbc.queryForList("""
                SELECT unnest(skills) AS skill FROM atoms
                WHERE profile_id = (SELECT id FROM profiles WHERE user_id = ?)
                """, String.class, LocalDevUser.DEV_USER_ID))
                .as("what GitHub can vouch for, canonical as Faz B reads it")
                .contains("java");

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM entries
                WHERE profile_id = (SELECT id FROM profiles WHERE user_id = ?)
                """, Integer.class, LocalDevUser.DEV_USER_ID))
                .as("a merge writes no second project")
                .isEqualTo(1);
    }

    /** And an unmatched one becomes a project, with GitHub's description as its line. */
    @Test
    void anunmatchedRepositoryBecomesAprojectWithItsOwnDescription() throws Exception {
        repositories.add(repository("weather-radar", "Rain maps from open data", 3));

        mvc.perform(post("/api/v1/profile/github/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositories\":[\"weather-radar\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(1));

        assertThat(jdbc.queryForList("""
                SELECT title FROM entries
                WHERE profile_id = (SELECT id FROM profiles WHERE user_id = ?)
                ORDER BY display_order
                """, String.class, LocalDevUser.DEV_USER_ID))
                .containsExactly("Order Management System", "weather-radar");

        assertThat(jdbc.queryForList("""
                SELECT plain_text FROM atom_variants v
                JOIN atoms a ON a.id = v.atom_id
                WHERE a.profile_id = (SELECT id FROM profiles WHERE user_id = ?)
                """, String.class, LocalDevUser.DEV_USER_ID))
                .contains("Rain maps from open data");
    }

    /** Nothing is written until a request names it. */
    @Test
    void lookingWritesNothing() throws Exception {
        repositories.add(repository("weather-radar", "Rain maps from open data", 3));

        mvc.perform(post("/api/v1/profile/github/suggestions")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM entries
                WHERE profile_id = (SELECT id FROM profiles WHERE user_id = ?)
                """, Integer.class, LocalDevUser.DEV_USER_ID)).isEqualTo(1);
    }

    /**
     * A name the account no longer has is skipped rather than refused: the
     * suggestion list is a moment old and a repository can be renamed.
     */
    @Test
    void anameThatIsNoLongerThereIsSkipped() throws Exception {
        repositories.add(repository("weather-radar", "Rain maps", 3));

        mvc.perform(post("/api/v1/profile/github/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositories\":[\"renamed-since\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(0));
    }

    /** No account named and none on the profile is a field to fill in, not a 404. */
    @Test
    void noaccountAnywhereIsAvalidationFailure() throws Exception {
        jdbc.update("""
                UPDATE profiles SET contact = contact - 'github'
                WHERE user_id = ?
                """, LocalDevUser.DEV_USER_ID);

        mvc.perform(post("/api/v1/profile/github/suggestions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.params.fields[0]").value("username"));
    }

    /** A login that is not one never reaches a URL. */
    @Test
    void ausernameThatCouldLeaveThePathIsRefused() throws Exception {
        mvc.perform(post("/api/v1/profile/github/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"../../admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private static GitHubRepository repository(String name, String description, int stars) {
        return new GitHubRepository(name, description,
                "https://github.com/ada/" + name, false, false, 900, stars,
                "Java", List.of(), List.of("Java"));
    }

    private String created(String path, String body) throws Exception {
        String response = mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("id").asText()).toString();
    }
}
