package com.mustafatetik.atomcv.rendering.model;

import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.List;
import java.util.Objects;

/**
 * A batch of content to measure (Bolum 22.4).
 *
 * <p>The same customization as the final document, because that is the whole
 * point: a measurement taken under a different preamble measures a document
 * nobody will ever print.
 */
public record MeasurementRequest(
        List<MeasurableItem> items,
        TemplateCustomization customization) {

    public MeasurementRequest {
        Objects.requireNonNull(customization, "customization");
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * One thing to measure.
     *
     * @param key     {@code {variantId}:{customizationId}:{templateVersion}}
     *                (Bolum 22.4) — it comes back in the log, so it has to
     *                survive a TeX {@code \typeout} unchanged
     * @param content what will be printed
     * @param layout  how the section holding it is set (Bolum 33.4). Bolum
     *                22.4's third rule is that the measurement is taken in the
     *                same environment the page prints in, and an
     *                {@code INLINE_LIST} row is not printed the way a bullet
     *                is: its label is set in bold, and bold is wider. Measured
     *                without it, every skills matrix reports a row narrower
     *                than the one that reaches the page — which is the one
     *                direction a cost may never be wrong in. Defaults to
     *                {@code BULLET_LIST}, which is what the column defaults to
     */
    public record MeasurableItem(String key, RichContent content, SectionLayout layout) {

        private static final String FORBIDDEN = "|%\\{}#$&^~ ";

        public MeasurableItem(String key, RichContent content) {
            this(key, content, SectionLayout.BULLET_LIST);
        }

        public MeasurableItem {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(content, "content");
            layout = layout == null ? SectionLayout.BULLET_LIST : layout;
            if (key.isBlank()) {
                throw new IllegalArgumentException("A measurable item needs a key");
            }
            // The key is parsed back out of a log line split on '|', and TeX
            // would eat the rest. Keys are built by this codebase from ids, so
            // this catches a mistake rather than an attack.
            for (int index = 0; index < key.length(); index++) {
                if (FORBIDDEN.indexOf(key.charAt(index)) >= 0) {
                    throw new IllegalArgumentException(
                            "A measurement key may not contain '" + key.charAt(index) + "'");
                }
            }
        }
    }
}
