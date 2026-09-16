package com.mustafatetik.atomcv.rendering;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Format name to writer.
 *
 * <p>The factory the pattern table named and the code did not have. Without it
 * the generation module held one field per format and the controller held a
 * chain of string comparisons, so the list of formats was written down twice
 * and in the two places least entitled to know it.
 *
 * <p><strong>Every format has a writer, and that is checked when the
 * application starts.</strong> A missing one is a wiring mistake, and the
 * alternative is finding out from a person whose download button returned
 * nothing. The same check catches two writers claiming one format, which is
 * how a format quietly starts producing whichever bean the classpath ordered
 * first.
 */
@Component
public class DocumentWriters {

    private final Map<OutputFormat, DocumentWriter> byFormat;

    DocumentWriters(List<DocumentWriter> writers) {
        // An EnumMap rather than Map.copyOf: iteration order is the enum's own
        // and does not change between JVM runs (CLAUDE.md).
        var index = new EnumMap<OutputFormat, DocumentWriter>(OutputFormat.class);
        for (DocumentWriter writer : writers) {
            DocumentWriter existing = index.put(writer.format(), writer);
            if (existing != null) {
                throw new IllegalStateException("two writers claim " + writer.format()
                        + ": " + existing.getClass().getName()
                        + " and " + writer.getClass().getName());
            }
        }
        for (OutputFormat format : OutputFormat.values()) {
            if (!index.containsKey(format)) {
                throw new IllegalStateException("no DocumentWriter for " + format);
            }
        }
        this.byFormat = index;
    }

    /**
     * The writer for a format a request named, or empty when it named one we
     * do not have.
     *
     * @see OutputFormat#ofWireName(String)
     */
    public Optional<DocumentWriter> forWireName(String wireName) {
        return OutputFormat.ofWireName(wireName).map(byFormat::get);
    }

    /** The writer for a format the caller already resolved. Always present. */
    public DocumentWriter forFormat(OutputFormat format) {
        return byFormat.get(format);
    }
}
