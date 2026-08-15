package com.mustafatetik.atomcv.rendering.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TexLogParserTest {

    /** A real fragment: TeX writes a great deal around the lines that matter. */
    private static final String LOG = """
            This is XeTeX, Version 3.141592653-2.6-0.999995
            (./doc.tex
            LaTeX2e <2023-11-01>
            ATOMCOST|var-1|10.5pt|3.25pt
            Overfull \\hbox (2.0pt too wide) in paragraph at lines 12--13
            ATOMCOST|var-2|21.0pt|0.0pt
            ) )
            Output written on doc.pdf (1 page).
            """;

    @Test
    void readsEveryMeasuredItemOutOfTheNoise() {
        var costs = TexLogParser.parseCosts(LOG);

        assertThat(costs).containsOnlyKeys("var-1", "var-2");
        assertThat(costs.get("var-1")).isEqualTo(new RenderCost(10.5, 3.25));
        assertThat(costs.get("var-2")).isEqualTo(new RenderCost(21.0, 0.0));
    }

    @Test
    void keepsTheOrderTheDocumentAskedFor() {
        assertThat(TexLogParser.parseCosts(LOG).keySet()).containsExactly("var-1", "var-2");
    }

    @Test
    void aCostIsTheLinesItTakesPlusWhatTheListAddsBetweenItems() {
        // 13.75pt of box is two lines at a twelve point baseline, and the list
        // adds one point between items: 24 + 1 (EK D.8.10).
        assertThat(new RenderCost(10.5, 3.25).totalPt(12.0, 1.0)).isEqualTo(25.0);
        assertThat(new RenderCost(10.5, 3.25).lines(12.0)).isEqualTo(2);
    }

    @Test
    void aSingleLineCostsOneBaselineHoweverShortItIs() {
        // The failure this replaced charged height + depth + a whole baseline,
        // which is about eight points more than TeX advances the page by —
        // a third of a page of bullets left blank for nothing.
        assertThat(new RenderCost(6.8, 0.0).totalPt(12.0, 1.0)).isEqualTo(13.0);
        assertThat(new RenderCost(0.0, 0.0).lines(12.0)).isEqualTo(1);
    }

    @Test
    void anEmptyOrMissingLogMeasuresNothingRatherThanFailing() {
        assertThat(TexLogParser.parseCosts(null)).isEmpty();
        assertThat(TexLogParser.parseCosts("")).isEmpty();
        assertThat(TexLogParser.parseCosts("no costs here")).isEmpty();
    }

    @Test
    void aHalfWrittenLineIsIgnoredRatherThanHalfRead() {
        // TeX wraps long log lines; a split record must not become a number
        // that looks plausible.
        assertThat(TexLogParser.parseCosts("ATOMCOST|var-1|10.5pt|")).isEmpty();
        assertThat(TexLogParser.parseCosts("ATOMCOST|var-1|10.5|3.0")).isEmpty();
        assertThat(TexLogParser.parseCosts("ATOMCOST|var 1|10.5pt|3.0pt")).isEmpty();
    }

    @Test
    void integerPointValuesAreRead() {
        assertThat(TexLogParser.parseCosts("ATOMCOST|var-1|12pt|0pt"))
                .containsEntry("var-1", new RenderCost(12.0, 0.0));
    }

    @Test
    void calibrationProbesAreReadTheSameWay() {
        var values = TexLogParser.parseCalibration("""
                CALIB|textheight|708.24513pt
                CALIB|baselineskip|12.0pt
                CALIB|start|10.0pt
                """);

        assertThat(values)
                .containsEntry("textheight", 708.24513)
                .containsEntry("baselineskip", 12.0)
                .containsEntry("start", 10.0);
    }

    @Test
    void aNegativeDimensionIsNotAMeasurement() {
        assertThatThrownBy(() -> new RenderCost(-1.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
