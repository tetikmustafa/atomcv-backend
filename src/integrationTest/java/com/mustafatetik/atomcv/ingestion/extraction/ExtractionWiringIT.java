package com.mustafatetik.atomcv.ingestion.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * That the registry Spring builds is the one the unit test builds by hand.
 *
 * <p>{@code DocumentExtractionTest} constructs the four readers itself, which
 * is what makes it fast and what makes it blind: a reader that lost its
 * {@code @Component} would still be in that list and would be absent from the
 * running application, and every extraction of that format would fail on a
 * null reader in production while the suite stayed green. The registry is
 * built from injected beans, so asking the real context for it is the whole
 * check.
 */
class ExtractionWiringIT extends AbstractIntegrationTest {

    @Autowired
    private DocumentExtraction extraction;

    @Autowired
    private ExtractionProperties limits;

    @Test
    void everyFormatHasAReaderInTheRunningApplication() {
        assertThat(extraction.readers().keySet())
                .containsExactlyInAnyOrder(DocumentFormat.values());
    }

    /** And the shipped numbers are Bolum 42.1's and Bolum 31.2's, not the defaults of a record. */
    @Test
    void theShippedLimitsAreTheOnesTheSectionsName() {
        assertThat(limits.maxBytes()).isEqualTo(10 * 1024 * 1024);
        assertThat(limits.minExtractedChars()).isEqualTo(100);
    }
}
