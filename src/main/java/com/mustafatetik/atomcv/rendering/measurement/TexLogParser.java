package com.mustafatetik.atomcv.rendering.measurement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads what TeX said about the sizes it produced (Bolum 26.2).
 *
 * <p>TeX is the only thing that knows how wide a word is in a given font at a
 * given size, once hyphenation and justification have had their say. So it
 * measures, and this reads the answer back out of the log.
 */
public final class TexLogParser {

    /**
     * {@code ATOMCOST|key|12.3pt|4.5pt}.
     *
     * <p>TeX wraps log lines at 79 characters by default, which would split a
     * long key across two lines and lose it. Keys are ids, so they stay well
     * inside that — and the pattern refuses anything it cannot read whole
     * rather than half-parsing it.
     */
    private static final Pattern COST = Pattern.compile(
            "ATOMCOST\\|([^|\\s]+)\\|([0-9]+\\.?[0-9]*)pt\\|([0-9]+\\.?[0-9]*)pt");

    /** {@code CALIB|name|12.3pt} — the template's own geometry. */
    private static final Pattern CALIBRATION = Pattern.compile(
            "CALIB\\|([^|\\s]+)\\|(-?[0-9]+\\.?[0-9]*)pt");

    private TexLogParser() {
    }

    /**
     * Every measured item in the log, keyed as the measurement document keyed
     * it. Insertion order is kept so a caller can rely on it for logging.
     */
    public static Map<String, RenderCost> parseCosts(String texLog) {
        Map<String, RenderCost> costs = new LinkedHashMap<>();
        if (texLog == null) {
            return costs;
        }
        Matcher matcher = COST.matcher(texLog);
        while (matcher.find()) {
            costs.put(matcher.group(1), new RenderCost(
                    Double.parseDouble(matcher.group(2)),
                    Double.parseDouble(matcher.group(3))));
        }
        return costs;
    }

    /** The calibration probes: {@code textheight}, {@code baselineskip}, and the page positions. */
    public static Map<String, Double> parseCalibration(String texLog) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (texLog == null) {
            return values;
        }
        Matcher matcher = CALIBRATION.matcher(texLog);
        while (matcher.find()) {
            values.put(matcher.group(1), Double.parseDouble(matcher.group(2)));
        }
        return values;
    }
}
