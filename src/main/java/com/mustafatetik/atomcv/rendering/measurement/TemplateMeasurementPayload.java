package com.mustafatetik.atomcv.rendering.measurement;

import com.mustafatetik.atomcv.rendering.template.CustomizationFactory;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a queued calibration carries in {@code jobs.payload} (Bolum 33.3).
 *
 * <p>The geometry itself rather than a cost key, because the key is a summary:
 * the calibration document has to be rendered at these settings, and reading
 * "classic:v5:sans-9.5-0.65-1.25" backwards into five values would be parsing
 * a string we wrote for people to read.
 *
 * <p>No user and no profile. A capacity belongs to a geometry -- two people at
 * the same font size are asking one question -- so the job says what to
 * measure and nothing about who wanted it.
 */
public record TemplateMeasurementPayload(TemplateCustomization customization) {

    private static final String TEMPLATE = "templateId";
    private static final String FONT_SIZE = "fontSizePt";
    private static final String MARGIN = "marginInches";
    private static final String LINE_SPACING = "lineSpacing";
    private static final String FONT_FAMILY = "fontFamily";

    /**
     * Ordered, because the map becomes a JSONB column and the JDK's immutable
     * maps iterate in an order salted per JVM run (CLAUDE.md).
     *
     * <p>The accent colour is not written: it moves no box, so it is not part
     * of what is being measured and not part of the key the answer is filed
     * under (Bolum 33.1, layer A).
     */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(TEMPLATE, customization.baseTemplateId());
        payload.put(FONT_SIZE, customization.fontSizePt());
        payload.put(MARGIN, customization.marginInches());
        payload.put(LINE_SPACING, customization.lineSpacing());
        payload.put(FONT_FAMILY, customization.fontFamily().name());
        return payload;
    }

    public static TemplateMeasurementPayload from(Map<String, Object> payload) {
        return new TemplateMeasurementPayload(CustomizationFactory.from(
                String.valueOf(payload.get(TEMPLATE)),
                number(payload, FONT_SIZE),
                number(payload, MARGIN),
                number(payload, LINE_SPACING),
                payload.get(FONT_FAMILY) == null ? null : String.valueOf(payload.get(FONT_FAMILY)),
                null));
    }

    private static Double number(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number size)) {
            throw new IllegalArgumentException(key + " is a number, got " + value.getClass());
        }
        return size.doubleValue();
    }
}
