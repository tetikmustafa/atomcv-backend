package com.mustafatetik.atomcv.ingestion.extraction;

import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PDFBox, with the one setting Bolum 31.3 singles out.
 *
 * <p><strong>{@code setSortByPosition(true)}.</strong> Without it the stripper
 * emits text in the order the file happens to store it, which for the
 * two-column layout half of all CV templates use means a line of the left
 * column followed by a line of the right. The result reads as ruined to a
 * person and as noise to a model, and nothing downstream can undo it.
 *
 * <p>PDFBox executes nothing while reading — no JavaScript, no embedded action
 * (Bolum 42.1). That is the reason it is here rather than a renderer.
 */
@Component
class PdfTextExtractor implements TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    @Override
    public List<DocumentFormat> formats() {
        return List.of(DocumentFormat.PDF);
    }

    @Override
    public String extract(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidPasswordException encrypted) {
            // Bolum 31.10: refuse and ask for an open copy. There is nothing
            // to try — we have no password and would not want one.
            throw ApiException.of(ErrorCode.PDF_ENCRYPTED);
        } catch (IOException unreadable) {
            // A file that starts with %PDF- and is not a PDF, or one that is
            // damaged. The user cannot tell those apart either, and the answer
            // is the same: this is not a file we can read.
            log.info("A PDF could not be parsed: {}", unreadable.getClass().getSimpleName());
            throw ExtractionRefusal.unsupported();
        }
    }
}
