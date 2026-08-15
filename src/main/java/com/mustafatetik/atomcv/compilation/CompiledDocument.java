package com.mustafatetik.atomcv.compilation;

import java.util.Objects;

/**
 * A PDF and the one fact about it the pipeline has to know.
 *
 * <p>The page count comes from the compiler rather than from reading the PDF:
 * a modern xelatex writes its page tree into an object stream, so counting
 * {@code /Type /Page} in the bytes is unreliable, and a page count that is
 * quietly wrong would break the product's central promise without any signal
 * (EK D.8.6).
 */
public record CompiledDocument(byte[] pdf, int pageCount) {

    public CompiledDocument {
        Objects.requireNonNull(pdf, "pdf");
        if (pageCount < 1) {
            throw new IllegalArgumentException("A compiled document has pages");
        }
    }

    public int sizeBytes() {
        return pdf.length;
    }

    /** Shape only: the document is built from the user's own content. */
    @Override
    public String toString() {
        return "CompiledDocument[pages=" + pageCount + ", bytes=" + pdf.length + "]";
    }
}
