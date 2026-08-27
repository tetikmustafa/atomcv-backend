package com.mustafatetik.atomcv.ingestion.extraction;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * TXT and Markdown, which need no reader (Bolum 31.3).
 *
 * <p>Markdown's syntax is left standing on purpose. A model reads
 * {@code ## Experience} as a heading and {@code - built X} as a bullet, so
 * stripping the marks would remove structure the next stage wants — the
 * opposite of what the LaTeX reader has to do, where the markup is a
 * typesetting program rather than an outline.
 *
 * <p>Two formats, one bean: the difference between them is a decision not to
 * do anything, and two classes to say that twice would be two places to
 * change.
 */
@Component
class PlainTextExtractor implements TextExtractor {

    @Override
    public List<DocumentFormat> formats() {
        return List.of(DocumentFormat.TXT, DocumentFormat.MARKDOWN);
    }

    @Override
    public String extract(byte[] bytes) {
        // Normalising the line endings here rather than in the caller: it is
        // the only reader whose output line breaks come straight from the file,
        // and Bolum 31.3's scramble heuristic counts lines.
        return new String(bytes, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }
}
