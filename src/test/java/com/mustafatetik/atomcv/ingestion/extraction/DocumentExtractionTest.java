package com.mustafatetik.atomcv.ingestion.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

/**
 * Bolum 31.2's ladder and Bolum 31.3's readers, against files this test makes.
 *
 * <p><strong>Real documents, built here rather than checked in.</strong> A
 * fixture directory of binaries is a set of files nobody can read in a diff
 * and nobody dares regenerate; building each one with the library that will
 * read it keeps the input visible and exercises the pairing that actually
 * ships. It is also the only way to write the two cases that matter most —
 * an encrypted PDF and a two-column one — without committing a file whose
 * contents no reviewer can check.
 */
class DocumentExtractionTest {

    /**
     * A twenty-character floor rather than the shipped hundred.
     *
     * <p>The floor is a property, tested as one by
     * {@link #theConfiguredFloorIsWhatDecidesEmptiness} below. Every other
     * case here is about a reader, and padding a dozen fixtures to a hundred
     * characters to satisfy a rung they are not testing would bury what each
     * one is actually asserting.
     */
    private final DocumentExtraction extraction = new DocumentExtraction(
            List.of(new PdfTextExtractor(), new DocxTextExtractor(),
                    new TexTextExtractor(), new PlainTextExtractor()),
            new ExtractionProperties(0, 20));

    // -- the ladder, rung by rung (Bolum 31.2) -----------------------------

    @Test
    void everyFormatHasAReader() {
        assertThat(extraction.readers().keySet())
                .containsExactlyInAnyOrder(DocumentFormat.values());
    }

    @Test
    void aNameWithNoExtensionSaysNothingAboutItsFormat() {
        assertThatThrownBy(() -> extraction.extract("resume", "application/pdf", pdfSaying("x")))
                .isInstanceOf(ApiException.class)
                .extracting(failure -> ((ApiException) failure).error().code())
                .isEqualTo(ErrorCode.UNSUPPORTED_DOCUMENT);
    }

    @Test
    void anExtensionWeDoNotReadIsRefusedAndTheAnswerSaysWhatWeDo() {
        ApiException refused = refusalOf("photo.png", "image/png", new byte[] {1, 2, 3});

        assertThat(refused.error().code()).isEqualTo(ErrorCode.UNSUPPORTED_DOCUMENT);
        assertThat(refused.error().params().get("accepted"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .contains("pdf", "docx", "tex", "txt", "md");
    }

    /**
     * The declared type only ever contradicts.
     *
     * <p>A browser sends {@code application/octet-stream} for {@code .tex} and
     * {@code .md} as a matter of course, so treating an unrecognised value as
     * disagreement would refuse files that are perfectly fine. A value that
     * does name a format and names a different one is a real contradiction.
     */
    @Test
    void aDeclaredTypeNamingAnotherFormatIsADisagreementAndIsRefused() throws IOException {
        assertThat(refusalOf("cv.pdf", "text/plain", pdfSaying("hello")).error().code())
                .isEqualTo(ErrorCode.UNSUPPORTED_DOCUMENT);
    }

    @Test
    void aDeclaredTypeNamingNothingWeKnowIsSilenceAndNotDisagreement() {
        String text = "Ada Lovelace, Analytical Engine programmer. ".repeat(4);

        ExtractedText extracted =
                extraction.extract("cv.md", "application/octet-stream", bytesOf(text));

        assertThat(extracted.format()).isEqualTo(DocumentFormat.MARKDOWN);
    }

    @Test
    void aDeclaredTypeCarryingACharsetParameterStillNamesItsFormat() {
        String text = "Ada Lovelace, Analytical Engine programmer. ".repeat(4);

        ExtractedText extracted =
                extraction.extract("cv.txt", "text/plain; charset=utf-8", bytesOf(text));

        assertThat(extracted.format()).isEqualTo(DocumentFormat.TXT);
    }

    /**
     * Size is checked before anything reads the bytes, and this proves the
     * order rather than the limit.
     *
     * <p>The payload is not a PDF at all. Were the rungs the other way round
     * the reader would run first and answer {@code UNSUPPORTED_DOCUMENT},
     * which is why the assertion is on the code and not merely on the refusal.
     */
    @Test
    void aFileOverTheLimitIsRefusedBeforeAnythingTriesToReadIt() {
        byte[] oversized = new byte[2048];
        DocumentExtraction small =
                new DocumentExtraction(List.of(new PdfTextExtractor()),
                        new ExtractionProperties(1024, 100));

        ApiException refused = refusalOf(small, "cv.pdf", "application/pdf", oversized);

        assertThat(refused.error().code()).isEqualTo(ErrorCode.DOCUMENT_TOO_LARGE);
        assertThat(refused.error().params()).containsEntry("limitBytes", 1024);
    }

    /** The rung that catches a renamed file, which is the one Bolum 42.1 names. */
    @Test
    void aFileRenamedToLookLikeAPdfIsCaughtByItsFirstBytes() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

        assertThat(refusalOf("cv.pdf", null, png).error().code())
                .isEqualTo(ErrorCode.UNSUPPORTED_DOCUMENT);
    }

    /**
     * And renaming it to a text format does not get around the check.
     *
     * <p>The text formats have no signature to compare, so what stands in for
     * one is a NUL byte — which no text file has and almost every binary does.
     */
    @Test
    void aBinaryRenamedToLookLikeTextIsCaughtByItsNulBytes() {
        byte[] withNul = new byte[] {'h', 'i', 0, 'x'};

        assertThat(refusalOf("cv.txt", "text/plain", withNul).error().code())
                .isEqualTo(ErrorCode.UNSUPPORTED_DOCUMENT);
    }

    // -- PDF (Bolum 31.3) --------------------------------------------------

    @Test
    void aPdfGivesUpItsText() throws IOException {
        ExtractedText extracted = extraction.extract("cv.pdf", "application/pdf",
                pdfSaying("Ada Lovelace wrote the first published algorithm in 1843."));

        assertThat(extracted.text()).contains("Ada Lovelace", "1843");
        assertThat(extracted.format()).isEqualTo(DocumentFormat.PDF);
    }

    /**
     * <strong>The case the whole {@code setSortByPosition} setting exists
     * for.</strong>
     *
     * <p>This PDF writes its right column into the content stream before its
     * left one, which is what a two-column template does when the layout
     * engine emits frames in its own order. Read stream-first, the text comes
     * out as one whole column followed by the other — every experience bullet
     * separated from the job it belongs to, and nothing downstream can undo
     * it. Read by position, the lines interleave the way a person reads them.
     */
    @Test
    void twoColumnsComeOutInReadingOrderAndNotInStreamOrder() throws IOException {
        byte[] pdf = twoColumnPdf();

        String text = extraction.extract("cv.pdf", "application/pdf", pdf).text();

        assertThat(text.indexOf("LeftOne")).isLessThan(text.indexOf("RightOne"));
        assertThat(text.indexOf("RightOne")).isLessThan(text.indexOf("LeftTwo"));
        assertThat(text.indexOf("LeftTwo")).isLessThan(text.indexOf("RightTwo"));
    }

    @Test
    void anEncryptedPdfIsRefusedWithItsOwnCodeSoTheUserKnowsWhatToSend()
            throws IOException {
        assertThat(refusalOf("cv.pdf", "application/pdf", encryptedPdf()).error().code())
                .isEqualTo(ErrorCode.PDF_ENCRYPTED);
    }

    /**
     * A PDF with no text is called a scan, and everything else with no text is
     * called empty.
     *
     * <p>Bolum 31.10 splits them here and only here. The distinction is the
     * sentence the user reads: "this looks like a scanned image, send a
     * text-based PDF" is what stops them uploading the same file again.
     */
    @Test
    void aPdfWithNoTextIsCalledAScanRatherThanEmpty() throws IOException {
        assertThat(refusalOf("cv.pdf", "application/pdf", blankPdf()).error().code())
                .isEqualTo(ErrorCode.PDF_NOT_TEXT_BASED);
    }

    /** And the number it compares against is the one the deployment set. */
    @Test
    void theConfiguredFloorIsWhatDecidesEmptiness() {
        String eighty = "Ada Lovelace, mathematician and the first programmer, London. ".repeat(1)
                + "Notes here.";
        DocumentExtraction shipped = new DocumentExtraction(List.of(new PlainTextExtractor()),
                new ExtractionProperties(0, 0));

        assertThat(extraction.extract("cv.txt", "text/plain", bytesOf(eighty)).text())
                .contains("Lovelace");
        assertThat(refusalOf(shipped, "cv.txt", "text/plain", bytesOf(eighty)).error().code())
                .isEqualTo(ErrorCode.EXTRACTION_EMPTY);
    }

    @Test
    void aTextFileWithAlmostNothingInItIsCalledEmptyRatherThanAScan() {
        assertThat(refusalOf("cv.txt", "text/plain", bytesOf("Ada")).error().code())
                .isEqualTo(ErrorCode.EXTRACTION_EMPTY);
    }

    @Test
    void aPdfThatStartsRightAndIsNotOneIsRefusedAsUnsupported() {
        byte[] notReallyAPdf = bytesOf("%PDF-1.7 and then nothing that parses at all");

        assertThat(refusalOf("cv.pdf", "application/pdf", notReallyAPdf).error().code())
                .isEqualTo(ErrorCode.UNSUPPORTED_DOCUMENT);
    }

    // -- DOCX --------------------------------------------------------------

    @Test
    void aDocxGivesUpItsText() throws IOException {
        byte[] docx = docxSaying(
                "Engineered ETL pipelines processing 300K rows into a Lakehouse.");

        ExtractedText extracted = extraction.extract("cv.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx);

        assertThat(extracted.text()).contains("ETL pipelines", "300K rows");
    }

    /**
     * The magic bytes only proved it was a zip; every zip starts the same way.
     * This is the rung where a renamed archive is actually caught.
     */
    @Test
    void aZipThatIsNotAWordDocumentIsRefused() throws IOException {
        assertThat(refusalOf("cv.docx", null, plainZip()).error().code())
                .isEqualTo(ErrorCode.UNSUPPORTED_DOCUMENT);
    }

    // -- TEX ---------------------------------------------------------------

    @Test
    void commandsAreStrippedAndTheWordsInsideThemKept() {
        String source = """
                \\documentclass{article}
                \\usepackage{geometry}
                \\begin{document}
                \\section{Experience}
                \\textbf{Ada Lovelace} worked on the \\emph{Analytical Engine}.
                \\end{document}
                """;

        String text = extraction.extract("cv.tex", null, bytesOf(source)).text();

        assertThat(text).contains("Experience", "Ada Lovelace", "Analytical Engine");
        assertThat(text).doesNotContain("textbf", "emph", "section");
    }

    /** The preamble is configuration; it would reach the model as noise. */
    @Test
    void thePreambleNeverReachesTheText() {
        String source = """
                \\documentclass[11pt]{article}
                \\usepackage[utf8]{inputenc}
                \\usepackage{hyperref}
                \\begin{document}
                Ada Lovelace, mathematician, wrote the first published algorithm.
                \\end{document}
                """;

        String text = extraction.extract("cv.tex", null, bytesOf(source)).text();

        assertThat(text).doesNotContain("article", "inputenc", "hyperref", "11pt");
        assertThat(text).contains("Ada Lovelace");
    }

    @Test
    void aCommentIsDroppedAndAnEscapedPercentSignIsNot() {
        String source = """
                \\begin{document}
                Reduced processing time by 40\\% overall for the whole team.
                % TODO: mention the second project before sending this out
                \\end{document}
                """;

        String text = extraction.extract("cv.tex", null, bytesOf(source)).text();

        assertThat(text).contains("40% overall");
        assertThat(text).doesNotContain("TODO");
    }

    @Test
    void aTexFileWithNoDocumentEnvironmentIsStillRead() {
        String source = "\\textbf{Ada Lovelace} wrote the first published algorithm in 1843.";

        String text = extraction.extract("cv.tex", null, bytesOf(source)).text();

        assertThat(text).contains("Ada Lovelace", "1843");
    }

    // -- TXT and Markdown --------------------------------------------------

    /**
     * Markdown's marks are left standing, which is the opposite of what the
     * LaTeX reader does. They are an outline a model reads as structure, not a
     * typesetting program.
     */
    @Test
    void markdownKeepsItsMarksBecauseTheyAreStructure() {
        String source = """
                ## Experience
                - Engineered ETL pipelines processing 300K rows
                - Cut the nightly batch from four hours to forty minutes
                """;

        String text = extraction.extract("cv.md", "text/markdown", bytesOf(source)).text();

        assertThat(text).contains("## Experience", "- Engineered ETL pipelines");
    }

    @Test
    void windowsLineEndingsAreNormalisedSoLinesCanBeCounted() {
        String source = "Ada Lovelace\r\nAnalytical Engine programmer\r\n"
                + "Wrote the first published algorithm in 1843\r\n";

        String text = extraction.extract("cv.txt", "text/plain", bytesOf(source)).text();

        assertThat(text).doesNotContain("\r");
        assertThat(text.lines()).hasSize(3);
    }

    // -- the scramble note travels, it does not refuse ---------------------

    @Test
    void scrambledTextIsStillExtractedAndOnlyFlagged() {
        String fragments = "Ada\nLovelace\nEngine\n1843\nAlgorithm\nMathematician\n"
                + "London\nAnalytical\nProgrammer\nNotes\n";

        ExtractedText extracted =
                extraction.extract("cv.txt", "text/plain", bytesOf(fragments));

        assertThat(extracted.looksScrambled()).isTrue();
        assertThat(extracted.text()).contains("Lovelace");
    }

    @Test
    void theShapeThatReachesALogLineCarriesNoneOfTheText() {
        ExtractedText extracted = extraction.extract("cv.txt", "text/plain",
                bytesOf("Ada Lovelace wrote the first published algorithm in 1843."));

        assertThat(extracted.shape()).doesNotContain("Ada", "Lovelace", "algorithm");
        assertThat(extracted.shape()).contains("format=TXT", "chars=");
    }

    // -- fixtures ----------------------------------------------------------

    private ApiException refusalOf(String filename, String type, byte[] bytes) {
        return refusalOf(extraction, filename, type, bytes);
    }

    private static ApiException refusalOf(
            DocumentExtraction subject, String filename, String type, byte[] bytes) {
        try {
            subject.extract(filename, type, bytes);
        } catch (ApiException refused) {
            return refused;
        }
        throw new AssertionError("the upload was accepted");
    }

    private static byte[] bytesOf(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] pdfSaying(String line) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                write(content, line, 50, 700);
            }
            return toBytes(document);
        }
    }

    /** Right column first in the stream, left column second. See the test. */
    private static byte[] twoColumnPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                write(content, "RightOne", 330, 700);
                write(content, "RightTwo", 330, 660);
                write(content, "LeftOne", 60, 700);
                write(content, "LeftTwo", 60, 660);
            }
            return toBytes(document);
        }
    }

    private static byte[] blankPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            return toBytes(document);
        }
    }

    private static byte[] encryptedPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-pw", "user-pw", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return toBytes(document);
        }
    }

    private static void write(PDPageContentStream content, String text, float x, float y)
            throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private static byte[] toBytes(PDDocument document) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        document.save(bytes);
        return bytes.toByteArray();
    }

    private static byte[] docxSaying(String line) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(line);
            document.write(bytes);
            return bytes.toByteArray();
        }
    }

    private static byte[] plainZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("notes.txt"));
            zip.write(bytesOf("not a word document at all"));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
