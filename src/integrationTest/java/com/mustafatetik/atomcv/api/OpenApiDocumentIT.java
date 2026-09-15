package com.mustafatetik.atomcv.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The schema in git, against the schema the application publishes
 * (Bolum 35.8, Bolum 47.1).
 *
 * <p><strong>Why a committed file at all.</strong> Bolum 47.1 gives the
 * frontend a {@code contract-check} job that fetches
 * {@code raw.githubusercontent.com/.../build/openapi.json} — and {@code build/}
 * is generated and ignored, so that URL has always answered 404 and the job has
 * always taken its {@code || echo "schema fetch failed, skipping"} branch. The
 * one guard against the two repositories drifting apart was skipping itself.
 * The file lives at the repository root instead, where it can be committed.
 *
 * <p><strong>Why a test rather than the springdoc Gradle plugin.</strong> That
 * plugin starts the application to read the document, which means a database,
 * which is what this lane already has. Two ways to produce one file would be
 * two answers on the day one of them lagged.
 *
 * <p><strong>Recording.</strong> {@code make openapi} — or
 * {@code -Dopenapi.record=true} — rewrites the file instead of asserting on it.
 * The same idiom as {@code -Dgolden.record=true} for the measured render costs,
 * and for the same reason: the check and the thing that satisfies it must be
 * one piece of code.
 */
@AutoConfigureMockMvc
class OpenApiDocumentIT extends AbstractIntegrationTest {

    /** From the repository root, which is where Gradle runs a test from. */
    private static final Path COMMITTED = Path.of("openapi.json");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    @Test
    void theschemaInGitIsTheSchemaTheApplicationPublishes() throws Exception {
        String published = pretty(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        if (Boolean.getBoolean("openapi.record")) {
            Files.writeString(COMMITTED, published, StandardCharsets.UTF_8);
            return;
        }

        assertThat(COMMITTED)
                .as("openapi.json is missing; run `make openapi`")
                .exists();
        assertThat(Files.readString(COMMITTED, StandardCharsets.UTF_8))
                .as("the published schema has moved on from the committed one -- "
                        + "run `make openapi` and commit it, so the frontend's "
                        + "contract check has something true to fetch")
                .isEqualTo(published);
    }

    /**
     * Pretty, and with LF endings written by hand.
     *
     * <p>The editor tool and Python's text mode write CRLF on this machine and
     * git normalises on commit, so a file written with platform endings differs
     * from the committed one on every local run and matches on the runner —
     * a failure that reads as a flake and is not one (CLAUDE.md).
     */
    private static String pretty(String document) throws Exception {
        return JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsString(JSON.readTree(document))
                .replace("\r\n", "\n") + "\n";
    }
}
