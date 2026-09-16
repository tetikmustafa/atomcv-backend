package com.mustafatetik.atomcv.profile.domain.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The statistics half of absolute rule 4.
 *
 * <p>The test that matters is {@link #saysNothingTheSentenceSaid()}: this
 * record exists to be logged, so the property worth holding is that nothing it
 * prints can be read back as the person's writing.
 */
class ContentShapeTest {

    private static final RichContent BULLET = RichContent.of(
            Run.of("Engineered "),
            Run.of("ETL", Mark.TECHNOLOGY),
            Run.of(" pipelines processing "),
            Run.of("300K rows", Mark.METRIC),
            Run.of(" into a Lakehouse"));

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    void countsWhatItSaysItCounts() {
        var shape = ContentShape.of(
                BULLET, List.of("Microsoft Fabric", "Lakehouse"), "en", 27.7);

        assertThat(shape.charCount()).isEqualTo(BULLET.plainText().length());
        assertThat(shape.wordCount()).isEqualTo(9);
        assertThat(shape.runCount()).isEqualTo(5);
        // Two of the five runs carry a mark, whichever mark it is.
        assertThat(shape.emphasisCount()).isEqualTo(2);
        // "300K" is the only token with a digit in it.
        assertThat(shape.numericTokenCount()).isEqualTo(1);
        assertThat(shape.properNounCount()).isEqualTo(2);
        assertThat(shape.renderCostPt()).isEqualTo(27.7);
    }

    /**
     * The whole point of the record, and the one assertion that would matter
     * if a field were added carelessly.
     *
     * <p>Words of one or two letters are excluded, and the reason is worth
     * writing down rather than hiding: {@code "a"} collides with the field
     * names the line is made of ({@code lang}, {@code chars}) and would fail
     * the assertion without anything having leaked. Three letters is where a
     * token stops being a coincidence — and it still covers {@code ETL}, the
     * shortest thing in this bullet a reader would recognise.
     */
    @Test
    void saysNothingTheSentenceSaid() {
        String line = ContentShape.of(BULLET, List.of("Lakehouse"), "en", 27.7).toString();

        for (String word : BULLET.plainText().split("\\s+")) {
            if (word.length() < 3) {
                continue;
            }
            assertThat(line)
                    .as("the log line must not carry %s", word)
                    .doesNotContain(word);
        }
    }

    @Test
    void noticesTextThatTravelsBadly() {
        var turkish = ContentShape.unmeasured(
                RichContent.plain("Veri hatlarını yeniden yazdım"), List.of(), "tr");
        assertThat(turkish.hasNonAscii()).isTrue();
        assertThat(turkish.hasSpecialLatex()).isFalse();

        var escaping = ContentShape.unmeasured(
                RichContent.plain("Cut costs by 30% using C# & Bash"), List.of(), "en");
        assertThat(escaping.hasSpecialLatex()).isTrue();
    }

    @Test
    void anUnmeasuredWordingReportsNoCost() {
        assertThat(ContentShape.unmeasured(BULLET, List.of(), "en").toString())
                .doesNotContain("costPt");
        assertThat(ContentShape.of(BULLET, List.of(), "en", 27.7).toString())
                .contains("costPt=27.7");
    }

    /**
     * Absolute rule 7 reaches formatting: a Turkish default locale writes
     * {@code 27,7}, which is a different number to anything reading the log.
     */
    @Test
    void theCostIsWrittenTheSameInEveryLocale() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        assertThat(ContentShape.of(BULLET, List.of(), "tr", 27.7).toString())
                .contains("costPt=27.7");
    }

    @Test
    void anEmptyWordingIsNotAnException() {
        var shape = ContentShape.unmeasured(RichContent.EMPTY, null, null);

        assertThat(shape.charCount()).isZero();
        assertThat(shape.wordCount()).isZero();
        assertThat(shape.numericTokenCount()).isZero();
        assertThat(shape.properNounCount()).isZero();
        assertThat(shape.language()).isEmpty();
    }
}
