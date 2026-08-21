package com.mustafatetik.atomcv.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Bolum 54.2's fake: the properties the layers above it are allowed to rely on. */
class FakeEmbeddingProviderTest {

    private final FakeEmbeddingProvider provider = new FakeEmbeddingProvider();

    @Test
    void aVectorIsTheDimensionTheColumnDeclares() {
        assertThat(provider.dimensions()).isEqualTo(1024);
        assertThat(provider.embed("a bullet about payments")).hasSize(1024);
    }

    /** The point of a fake over a stub: a failing test failed for a reason. */
    @Test
    void thesameTextIsAlwaysTheSameVector() {
        assertThat(provider.embed("scaled the payment platform"))
                .isEqualTo(provider.embed("scaled the payment platform"));
    }

    @Test
    void differentTextsAreDifferentVectors() {
        assertThat(provider.embed("scaled the payment platform"))
                .isNotEqualTo(provider.embed("mentored three engineers"));
    }

    /**
     * Bolum 19 computes cosine similarity. A fake returning unnormalised
     * vectors would let a bug in that normalisation pass unnoticed for a whole
     * stage.
     */
    @Test
    void everyVectorIsUnitLength() {
        double sumOfSquares = 0;
        for (float component : provider.embed("scaled the payment platform")) {
            sumOfSquares += (double) component * component;
        }

        assertThat(Math.sqrt(sumOfSquares)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    /**
     * The seed is the text's words as a set, so Bolum 28.2's invalidation can
     * be exercised on a change that is genuinely a change rather than on a
     * reordering.
     */
    @Test
    void reorderingWordsKeepsTheVectorButChangingOneDoesNot() {
        assertThat(provider.embed("scaled the payment platform"))
                .isEqualTo(provider.embed("platform payment the scaled"));
        assertThat(provider.embed("scaled the payment platform"))
                .isNotEqualTo(provider.embed("scaled the billing platform"));
    }

    /**
     * Absolute rule 7. A Turkish default locale would seed differently here
     * than on the CI runner, and a golden fixture recorded on this machine
     * would not reproduce there — which is the kind of failure that gets
     * blamed on flakiness.
     */
    @Test
    void theVectorDoesNotDependOnTheDefaultLocale() {
        var underRoot = provider.embed("SCALED THE PAYMENT PLATFORM");
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(provider.embed("SCALED THE PAYMENT PLATFORM")).isEqualTo(underRoot);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void caseAndPunctuationDoNotChangeTheVector() {
        assertThat(provider.embed("Scaled the payment platform."))
                .isEqualTo(provider.embed("scaled  the payment   platform"));
    }

    @Test
    void abatchIsTheSameAsEmbeddingEachTextAlone() {
        var texts = List.of("one bullet", "another bullet", "a third");

        var batch = provider.embedBatch(texts);

        assertThat(batch).hasSize(3);
        for (int index = 0; index < texts.size(); index++) {
            assertThat(batch.get(index)).isEqualTo(provider.embed(texts.get(index)));
        }
    }

    @Test
    void theFakeIsAlwaysHealthy() {
        assertThat(provider.isHealthy()).isTrue();
    }
}
