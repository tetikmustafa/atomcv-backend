package com.mustafatetik.atomcv.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.shared.security.LocalDevUser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Bolum 31.2's ladder and Bolum 31.1's split, over the wire.
 *
 * <p>The split is what these cases are about: a file this deployment cannot
 * read is refused <em>here</em>, and a file it can read is answered 202 with a
 * job. Bolum 31.10's first three failures are things a person acts on at once,
 * and a queued extraction would deliver them eight seconds later where they
 * are least useful.
 */
@AutoConfigureMockMvc
class ProfileImportApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM jobs WHERE type = 'profile_extract'");
        jdbc.update("DELETE FROM usage_counters WHERE metric = 'profile_extract'");
    }

    // -- what gets through -------------------------------------------------

    @Test
    void areadableCvIsQueuedAndTheAnswerSaysWhereToFollowIt() throws Exception {
        mvc.perform(upload("cv.pdf", "application/pdf", pdf()))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.jobId").exists());

        assertThat(queuedJobs()).isEqualTo(1);
    }

    /**
     * <strong>The text is queued, never the file.</strong> Adim 3.4's first
     * slice decided the bytes are not stored, and this is the assertion that
     * keeps it true: a payload carrying the PDF would be the decision quietly
     * reversed by a later refactor.
     */
    @Test
    void thePayloadCarriesTheTextAndNoneOfTheBytes() throws Exception {
        mvc.perform(upload("cv.pdf", "application/pdf", pdf()))
                .andExpect(status().isAccepted());

        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM jobs WHERE type = 'profile_extract'", String.class);

        assertThat(payload).contains("Ada Lovelace").contains("\"format\": \"pdf\"");
        assertThat(payload).doesNotContain("%PDF");
    }

    /**
     * Bolum 30.7. An upload is the request a flaky connection repeats most
     * easily, and profile extraction has the smallest allowance in the
     * product — a second unit spent on the same file would be the user paying
     * for their own bad wifi.
     */
    @Test
    void thesameUploadTwiceUnderOneKeyIsOneJobAndOneUnit() throws Exception {
        var first = mvc.perform(upload("cv.pdf", "application/pdf", pdf())
                        .header("Idempotency-Key", "the-same-upload"))
                .andExpect(status().isAccepted()).andReturn();
        var second = mvc.perform(upload("cv.pdf", "application/pdf", pdf())
                        .header("Idempotency-Key", "the-same-upload"))
                .andExpect(status().isAccepted()).andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        assertThat(queuedJobs()).isEqualTo(1);
        assertThat(unitsSpent()).isEqualTo(1);
    }

    // -- what does not (Bolum 31.2, Bolum 31.10) ---------------------------

    @Test
    void aFormatWeDoNotReadIsRefusedHereAndSaysWhatWeDo() throws Exception {
        mvc.perform(upload("photo.png", "image/png", new byte[] {1, 2, 3}))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_DOCUMENT"))
                .andExpect(jsonPath("$.params.accepted").isArray());

        assertThat(queuedJobs()).isZero();
    }

    /** The sentence that stops somebody uploading the same scan a second time. */
    @Test
    void aScannedPdfIsRefusedHereRatherThanEightSecondsLater() throws Exception {
        mvc.perform(upload("cv.pdf", "application/pdf", blankPdf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PDF_NOT_TEXT_BASED"));

        assertThat(queuedJobs()).isZero();
    }

    @Test
    void aFileOverTheLimitIsRefusedBeforeAnythingReadsIt() throws Exception {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];

        mvc.perform(upload("cv.pdf", "application/pdf", oversized))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("DOCUMENT_TOO_LARGE"))
                .andExpect(jsonPath("$.params.limitBytes").value(10 * 1024 * 1024));
    }

    /**
     * Bolum 44.2: nothing was extracted, so nothing was spent. Without the
     * refund a person could burn a day's allowance on files that never made it
     * past the first rung.
     */
    @Test
    void arefusedFileGivesTheAllowanceBack() throws Exception {
        mvc.perform(upload("photo.png", "image/png", new byte[] {1, 2, 3}))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(unitsSpent()).isZero();
    }

    // -- fixtures ----------------------------------------------------------

    /**
     * {@code mode=replace}, and it is not incidental: {@code DevSeeder} gives
     * the acting user a golden profile at start-up, so every upload here is by
     * definition a second one and would be refused with 409 (Bolum 08b). These
     * cases are about the door — the format ladder, the allowance, the
     * idempotency key — and the second-CV rule has {@code SecondImportIT} to
     * itself. Nothing is actually overwritten: the worker is off in this
     * suite, so the job is queued and never run.
     */
    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
            upload(String filename, String contentType, byte[] bytes) {
        var builder = multipart("/api/v1/profile/import?mode=replace");
        builder.file(new MockMultipartFile("file", filename, contentType, bytes));
        return builder;
    }

    private int queuedJobs() {
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM jobs WHERE type = 'profile_extract'", Integer.class);
        return rows == null ? 0 : rows;
    }

    private int unitsSpent() {
        Integer used = jdbc.queryForObject("""
                SELECT coalesce(sum(count), 0) FROM usage_counters
                WHERE metric = 'profile_extract' AND subject_id = ?""",
                Integer.class, LocalDevUser.DEV_USER_ID.toString());
        return used == null ? 0 : used;
    }

    private static byte[] pdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                // Over Bolum 31.2's hundred-character floor on purpose: a
                // shorter fixture would be refused by the last rung and this
                // class would be testing that instead of the happy path.
                content.showText("Ada Lovelace, Analytical Engine programmer, London. "
                        + "Engineered the first published algorithm intended for a machine, "
                        + "and translated Menabrea's memoir in 1843.");
                content.endText();
            }
            return bytesOf(document);
        }
    }

    private static byte[] blankPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            return bytesOf(document);
        }
    }

    private static byte[] bytesOf(PDDocument document) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        document.save(bytes);
        return bytes.toByteArray();
    }
}
