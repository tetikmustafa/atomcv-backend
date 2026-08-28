package com.mustafatetik.atomcv.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Bolum 08b: a second CV is a 409, and the only way past it is saying so.
 *
 * <p>{@code PROFILE_ALREADY_EXISTS} sat in the catalogue with nothing producing
 * it while {@code ProfileWriter} quietly <em>added</em> the second CV to the
 * first — the sections arrived twice and the person found out in the editor,
 * which is the silent duplication design principle 8 forbids.
 *
 * <p>Refused at the door rather than in the worker: a 202 followed by a failed
 * job is a worse answer than a 409, and the caller still has the file open.
 *
 * <p><strong>Nothing here touches the seeded profile.</strong> {@code DevSeeder}
 * gives the acting user a golden profile at start-up and {@code DevSeederIT}
 * asserts against it; deleting its sections to manufacture an empty profile
 * would have broken that class whenever Gradle happened to run this one first.
 * The seeded profile already has content, which is exactly the state these
 * cases need — and the empty half is asserted through the predicate itself,
 * on a user of this class's own.
 */
@AutoConfigureMockMvc
class SecondImportIT extends AbstractIntegrationTest {

    private static final byte[] CV = ("""
            Mustafa Tetik
            Backend Engineer

            EXPERIENCE
            Data Engineer, Brisa, 2022 - 2024
            Moved 300K rows nightly with Microsoft Fabric and cut the batch to forty minutes.
            Built an ingestion pipeline in Java and PostgreSQL for twelve internal teams.

            SKILLS
            Java, Spring Boot, PostgreSQL, Docker
            """).getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProfileResolver profiles;

    /**
     * The empty row every signed-in person has is not a profile — the check is
     * "is there anything in it", not "is there a row". Refusing against a row
     * would answer 409 to somebody who has never uploaded anything, which is
     * the same mistake the anonymous upgrade was making before Adim 3.6's fix.
     *
     * <p>Asserted on the predicate rather than through the endpoint, because
     * the endpoint acts as the seeded user and manufacturing an empty profile
     * for it would mean deleting rows another class asserts on.
     */
    @Test
    void anEmptyProfileRowDoesNotCountAsAProfile() {
        UUID stranger = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, email_verified) VALUES (?, ?, true)",
                stranger, stranger + "@second-import.test");

        assertThat(profiles.hasContent(UserContext.of(stranger))).isFalse();
        assertThat(profiles.hasContent(UserContext.of(LocalDevUser.DEV_USER_ID)))
                .as("the seeded profile is the populated case these tests rely on")
                .isTrue();
    }

    @Test
    void aSecondUploadIsRefusedWithSomethingToDoAboutIt() throws Exception {
        mvc.perform(multipart("/api/v1/profile/import").file(file()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.resolutions[*].action")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "replace_profile", "keep_existing_profile")));
    }

    /** And no merge among them: Bolum 08b says replace or keep, and means it. */
    @Test
    void theRefusalNeverOffersAMerge() throws Exception {
        mvc.perform(multipart("/api/v1/profile/import").file(file()))
                .andExpect(jsonPath("$.resolutions[*].action")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem("merge_profiles"))));
    }

    @Test
    void sayingReplaceGetsThroughTheDoor() throws Exception {
        mvc.perform(multipart("/api/v1/profile/import?mode=replace").file(file()))
                .andExpect(status().isAccepted());
    }

    /** Anything else reads as absent — a typo must not become consent. */
    @Test
    void anUnknownModeIsNotConsent() throws Exception {
        mvc.perform(multipart("/api/v1/profile/import?mode=merge").file(file()))
                .andExpect(status().isConflict());
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "cv.txt", "text/plain", CV);
    }
}
