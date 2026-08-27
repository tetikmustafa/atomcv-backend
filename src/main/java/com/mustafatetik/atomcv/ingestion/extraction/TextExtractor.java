package com.mustafatetik.atomcv.ingestion.extraction;

import java.util.List;

/**
 * One format's reader (Bolum 31.3).
 *
 * <p>Returns text and throws {@link com.mustafatetik.atomcv.shared.error.ApiException}
 * for the failures that are the user's to act on — an encrypted PDF, a
 * document that is not the kind it claimed. Everything else is left to the
 * ladder in {@link DocumentExtraction}, which is where "nothing came out" is
 * decided: an extractor cannot tell an empty CV from a scanned one, and the
 * length threshold that can is Bolum 31.2's last rung.
 */
interface TextExtractor {

    /**
     * Which formats this reader answers for.
     *
     * <p>A list and not one value, because {@code PlainTextExtractor} answers
     * for two: the difference between TXT and Markdown is a decision not to do
     * anything, and saying that twice would be two places to change.
     */
    List<DocumentFormat> formats();

    /**
     * @param bytes the file, already checked for size and signature
     * @return whatever text the file holds, possibly empty
     */
    String extract(byte[] bytes);
}
