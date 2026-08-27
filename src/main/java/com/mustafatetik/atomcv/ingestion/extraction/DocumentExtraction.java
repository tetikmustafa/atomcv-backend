package com.mustafatetik.atomcv.ingestion.extraction;

import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Bolum 31.2's ladder, cheapest rung first, and the text at the top of it.
 *
 * <p>The order is the whole design. Deciding the format from a filename costs
 * nothing; comparing four bytes costs nothing; opening a ten-megabyte PDF
 * costs a second of a request thread. A ladder that ran them the other way
 * would spend that second on every file a user dragged in by mistake.
 *
 * <p><strong>It throws rather than returning a {@code Result}.</strong>
 * {@code Result} carries a {@link com.mustafatetik.atomcv.shared.error.PipelineError},
 * which is sealed around what can go wrong on the way to a CV — widening it
 * with an encrypted PDF would force {@code ErrorPresenter} to answer for a
 * case the generation pipeline cannot raise. Every failure here ends the
 * request with a code the user acts on, which is the case
 * {@link ApiException} exists for.
 *
 * <p><strong>Nothing here logs the text.</strong> Absolute rule 4, and
 * extraction is the first place in the system where a whole CV exists as one
 * string. What reaches a log line is {@link ExtractedText#shape()}.
 */
@Service
public class DocumentExtraction {

    private static final Logger log = LoggerFactory.getLogger(DocumentExtraction.class);

    private final Map<DocumentFormat, TextExtractor> readers =
            new EnumMap<>(DocumentFormat.class);

    private final ExtractionProperties limits;

    DocumentExtraction(List<TextExtractor> extractors, ExtractionProperties limits) {
        this.limits = limits;
        for (TextExtractor extractor : extractors) {
            extractor.formats().forEach(format -> readers.put(format, extractor));
        }
    }

    /**
     * Reads a CV, or refuses in a way the user can act on.
     *
     * @param filename            the name the upload arrived under; the user's
     *                            word, and the only thing that says which
     *                            format to try
     * @param declaredContentType what the client called it, which may be
     *                            absent or wrong and is treated accordingly
     * @param bytes               the file
     * @throws ApiException {@code UNSUPPORTED_DOCUMENT}, {@code DOCUMENT_TOO_LARGE},
     *                      {@code PDF_ENCRYPTED}, {@code PDF_NOT_TEXT_BASED} or
     *                      {@code EXTRACTION_EMPTY}
     */
    public ExtractedText extract(String filename, String declaredContentType, byte[] bytes) {
        // 1. The extension chooses; nothing else can, since the bytes of a TXT
        //    and a TEX are the same bytes.
        DocumentFormat format = DocumentFormat.ofFilename(filename)
                .orElseThrow(ExtractionRefusal::unsupported);

        // 2. The declared type only ever contradicts. A browser sends
        //    application/octet-stream for .tex and .md as a matter of course,
        //    so an unrecognised value is silence and not a disagreement —
        //    treating it as one would refuse files that are perfectly fine.
        Optional<DocumentFormat> declared = DocumentFormat.ofMediaType(declaredContentType);
        if (declared.isPresent() && declared.get() != format) {
            log.info("Upload refused: extension says {}, declared type says {}",
                    format, declared.get());
            throw ExtractionRefusal.unsupported();
        }

        // 3. Size, before anything reads the bytes.
        if (bytes.length > limits.maxBytes()) {
            throw ExtractionRefusal.tooLarge(limits.maxBytes());
        }

        // 4. The bytes themselves, which are the only claim that is ours.
        if (!format.matches(bytes)) {
            log.info("Upload refused: the file does not start like a {}", format);
            throw ExtractionRefusal.unsupported();
        }

        // 5. And only now, the expensive part.
        String text = readers.get(format).extract(bytes).strip();
        ExtractedText extracted =
                new ExtractedText(text, format, ScrambleHeuristic.looksScrambled(text));

        if (text.length() < limits.minExtractedChars()) {
            log.info("Nothing usable came out of an upload: {}", extracted.shape());
            // Bolum 31.10 tells a PDF apart from the rest here, and only here:
            // a PDF with no text is almost always a scan, and the sentence
            // that says so is the one that saves the user from trying the same
            // file again. A short TXT is just short.
            throw ApiException.of(format == DocumentFormat.PDF
                    ? ErrorCode.PDF_NOT_TEXT_BASED
                    : ErrorCode.EXTRACTION_EMPTY);
        }

        log.info("Extracted a document: {}", extracted.shape());
        return extracted;
    }

    /** What a format registry has to cover, asserted by its test. */
    Map<DocumentFormat, TextExtractor> readers() {
        return Map.copyOf(readers);
    }
}
