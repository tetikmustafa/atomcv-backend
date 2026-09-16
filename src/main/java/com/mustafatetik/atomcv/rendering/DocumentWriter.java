package com.mustafatetik.atomcv.rendering;

import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.shared.error.Result;

/**
 * One downloadable format.
 *
 * <p><strong>This is the abstraction the first design put on {@link
 * DocumentRenderer}, and it did not fit there.</strong> That interface hands
 * back source for a compiler and carries a capacity, because a LaTeX page has
 * to be measured before selection can promise one. Neither is true of the
 * other two formats: POI <em>is</em> the Word document and nothing measures
 * it, and HTML has no page at all. Forcing all three behind that contract
 * would have meant two implementations answering questions they cannot
 * answer.
 *
 * <p>So the contract here is only what a download actually needs: which format
 * this is, and the bytes. {@link DocumentRenderer} keeps the narrower job it
 * was really doing — the measurable typesetting backend, of which there is one.
 *
 * <p><strong>What it buys is a module boundary.</strong> The generation module
 * asks {@link DocumentWriters} for a format and gets bytes; it no longer names
 * {@code DocxDocumentWriter} or {@code HtmlDocumentWriter} and so no longer
 * knows which formats exist, which is module rule 3 and the fourth product
 * claim. An ArchUnit rule holds it, because a boundary nothing checks is a
 * boundary somebody crosses.
 */
public interface DocumentWriter {

    /** Which format this writes. One writer per value. */
    OutputFormat format();

    /**
     * The bytes a person downloads.
     *
     * <p>A {@code Result} because one of these can genuinely fail: the PDF
     * goes through a compiler. The other three cannot, and say so by never
     * returning an error — that asymmetry is real and hiding it behind an
     * exception would make the one failure that matters look like a bug
     * rather than a reportable outcome.
     */
    Result<byte[]> bytesFor(RenderRequest request);
}
