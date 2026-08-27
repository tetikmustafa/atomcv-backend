package com.mustafatetik.atomcv.ingestion.extraction;

import java.io.ByteArrayInputStream;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * POI's text extractor, which is the whole of what we ask of a DOCX.
 *
 * <p>Bolum 42.1 lists a malicious macro as the risk and the text API as the
 * answer: {@code XWPFWordExtractor} reads document parts and has no path to
 * running one. What it does have a path to is a zip bomb, since a DOCX is a
 * zip — {@link ZipSecureFile}'s inflate ratio is POI's own guard for that, and
 * it is the "expanded size check" Bolum 42.1 asks for beside the byte limit.
 */
@Component
class DocxTextExtractor implements TextExtractor {

    /**
     * A document that expands more than a hundredfold is not a CV.
     *
     * <p>POI's own default is 1:100 and this restates it rather than trusting
     * it: the value is global mutable state on a static, so a library or a
     * later version relaxing it would silently remove the guard. Prose
     * compresses to roughly a fifth of its size, so real documents sit two
     * orders of magnitude away from this.
     */
    private static final double MAX_INFLATE_RATIO = 0.01;

    private static final Logger log = LoggerFactory.getLogger(DocxTextExtractor.class);

    DocxTextExtractor() {
        ZipSecureFile.setMinInflateRatio(MAX_INFLATE_RATIO);
    }

    @Override
    public List<DocumentFormat> formats() {
        return List.of(DocumentFormat.DOCX);
    }

    @Override
    public String extract(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (Exception notAWordDocument) {
            // The magic bytes only proved it is a zip; every zip starts the
            // same way. This is where a renamed .xlsx or a plain archive is
            // actually caught.
            log.info("A DOCX could not be parsed: {}",
                    notAWordDocument.getClass().getSimpleName());
            throw ExtractionRefusal.unsupported();
        }
    }
}
