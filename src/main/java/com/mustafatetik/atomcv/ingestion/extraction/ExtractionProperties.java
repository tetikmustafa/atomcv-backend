package com.mustafatetik.atomcv.ingestion.extraction;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The two numbers Bolum 31.2's ladder is built from.
 *
 * @param maxBytes         Bolum 42.1's ten megabytes. Also set on Spring's
 *                         multipart limit and, in production, on Nginx —
 *                         three places because each one refuses at a
 *                         different distance, and only the innermost can name
 *                         the error the client renders
 * @param minExtractedChars Bolum 31.2's last rung. Under it, a PDF is taken to
 *                          be a scan of a page rather than a page: there is no
 *                          way to tell those apart from the text, and no
 *                          real CV is a hundred characters long
 */
@ConfigurationProperties(prefix = "atomcv.ingestion")
public record ExtractionProperties(int maxBytes, int minExtractedChars) {

    private static final int TEN_MEGABYTES = 10 * 1024 * 1024;

    public ExtractionProperties {
        maxBytes = maxBytes <= 0 ? TEN_MEGABYTES : maxBytes;
        minExtractedChars = minExtractedChars <= 0 ? 100 : minExtractedChars;
    }
}
