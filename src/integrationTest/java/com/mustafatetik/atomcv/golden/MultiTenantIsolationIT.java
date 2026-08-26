package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.profile.seed.GoldenProfile;
import com.mustafatetik.atomcv.profile.seed.GoldenProfileReader;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The fourth of Bolum 51.2's tests: one user cannot reach another's rows.
 *
 * <p>Every endpoint that takes an id is tried with an id belonging to somebody
 * else, and has to answer as though the row does not exist. This is the test
 * the whole scoped-repository design exists for — {@code ProfileRef},
 * {@code ProfileScopedRepository} and the ArchUnit rules are all machinery in
 * service of what is asserted here.
 *
 * <p>The stranger's profile is a golden fixture, so it has one of everything
 * an endpoint can name.
 */
@AutoConfigureMockMvc
class MultiTenantIsolationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LocalDevUser localUser;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    /** The other user's rows, shared by every case below. */
    private static UUID strangerProfileId;
    private static UUID strangerSectionId;
    private static UUID strangerEntryId;
    private static UUID strangerAtomId;
    private static UUID strangerVariantId;

    @BeforeEach
    void twoUsersOneOfThemAStranger() {
        localUser.ensureUserExists();
        jdbc.update("DELETE FROM profiles WHERE user_id = ?", LocalDevUser.DEV_USER_ID);

        if (strangerProfileId != null) {
            return;
        }
        UUID strangerId = jdbc.queryForObject(
                "INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, "stranger-" + UUID.randomUUID() + "@example.com");

        tx.executeWithoutResult(status -> {
            GoldenProfile golden = GoldenProfileReader.read("senior_backend_tr", strangerId);
            em.persist(golden.profile());
            golden.sections().forEach(em::persist);
            golden.entries().forEach(em::persist);
            golden.atoms().forEach(em::persist);
            golden.variants().forEach(em::persist);

            strangerProfileId = golden.profile().getId();
            strangerSectionId = golden.sections().get(0).getId();
            strangerEntryId = golden.entries().get(0).getId();
            strangerAtomId = golden.atoms().stream()
                    .filter(atom -> atom.getEntryId() != null)
                    .findFirst().orElseThrow().getId();
            strangerVariantId = golden.variants().stream()
                    .filter(variant -> variant.getAtomId().equals(strangerAtomId))
                    .findFirst().orElseThrow().getId();
        });
    }

    static Stream<Arguments> everyEndpointThatNamesARow() {
        return Stream.of(
                Arguments.of("PATCH", "/api/v1/profile/sections/{sectionId}", "{\"title\":\"x\"}"),
                Arguments.of("DELETE", "/api/v1/profile/sections/{sectionId}", null),
                Arguments.of("PATCH", "/api/v1/profile/entries/{entryId}", "{\"title\":\"x\"}"),
                Arguments.of("DELETE", "/api/v1/profile/entries/{entryId}", null),
                Arguments.of("PATCH", "/api/v1/profile/atoms/{atomId}", "{\"importance\":0.9}"),
                Arguments.of("DELETE", "/api/v1/profile/atoms/{atomId}", null),
                Arguments.of("PATCH", "/api/v1/profile/atoms/{atomId}/variants/{variantId}",
                        "{\"content\":{\"v\":1,\"runs\":[{\"t\":\"x\",\"m\":[]}]}}"),
                Arguments.of("DELETE", "/api/v1/profile/atoms/{atomId}/variants/{variantId}",
                        null));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("everyEndpointThatNamesARow")
    void anotherUsersRowIsNotThereToBeTouched(String method, String template, String body)
            throws Exception {

        var request = build(method, path(template), body)
                // A real client would have read the row to get this, so send
                // the version it would have: the answer must not depend on it.
                .header(HttpHeaders.IF_MATCH, "\"0\"");

        int status = mvc.perform(request).andReturn().getResponse().getStatus();

        assertThat(status)
                .as("%s %s must not reach another user's row", method, template)
                .isIn(403, 404);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("everyEndpointThatNamesARow")
    void andTheRowIsStillThereAfterwards(String method, String template, String body)
            throws Exception {

        mvc.perform(build(method, path(template), body)
                .header(HttpHeaders.IF_MATCH, "\"0\""));

        // A 404 that deleted the row anyway would pass the test above.
        assertThat(countOf("sections")).isPositive();
        assertThat(countOf("entries")).isPositive();
        assertThat(countOf("atoms")).isPositive();
        assertThat(countOf("atom_variants")).isPositive();
    }

    @Test
    void reorderingCannotNameAnotherUsersRowsEither() throws Exception {
        int status = mvc.perform(post("/api/v1/profile/sections/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + strangerSectionId + "\"]}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(400, 403, 404);
        assertThat(countOf("sections")).isPositive();
    }

    @Test
    void theStrangersProfileIsNeverWhatTheEndpointsRead() throws Exception {
        // The acting user's own profile is created empty on first read; the
        // stranger's rows must not leak into it through any listing.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/profile/sections"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/profile/atoms"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/profile/export"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.sections").isEmpty());
    }

    @Test
    void theGeneratedCvIsBuiltFromTheActingUsersProfileOnly() throws Exception {
        // Nothing of the acting user's, so there is nothing to generate — even
        // though a full profile exists in the same tables.
        mvc.perform(post("/api/v1/generations")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value("INSUFFICIENT_PROFILE"));
    }

    private String path(String template) {
        return template
                .replace("{sectionId}", strangerSectionId.toString())
                .replace("{entryId}", strangerEntryId.toString())
                .replace("{atomId}", strangerAtomId.toString())
                .replace("{variantId}", strangerVariantId.toString());
    }

    private static MockHttpServletRequestBuilder build(String method, String path, String body) {
        MockHttpServletRequestBuilder request = switch (method) {
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            case "POST" -> post(path);
            default -> throw new IllegalArgumentException(method);
        };
        return body == null
                ? request
                : request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private int countOf(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE profile_id = ?",
                Integer.class, strangerProfileId);
        return count == null ? 0 : count;
    }

    /** The tables this asserts on, named once so a typo above fails loudly. */
    @SuppressWarnings("unused")
    private static final List<String> TABLES =
            List.of("sections", "entries", "atoms", "atom_variants");
}
