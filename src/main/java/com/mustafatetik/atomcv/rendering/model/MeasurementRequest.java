package com.mustafatetik.atomcv.rendering.model;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.template.CapacityModel.RowShape;
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
     * @param shape   where the page sets it (Bolum 33.4). Bolum 22.4's third
     *                rule is that a measurement is taken in the environment the
     *                page prints in, and the three shapes are not printed
     *                alike: an inline row carries its label in bold, and bold
     *                is wider. Measured as anything else, every skills matrix
     *                reports a row narrower than the one that reaches the page
     *                — the one direction a cost may never be wrong in
     */
    public record MeasurableItem(String key, RichContent content, RowShape shape) {

        private static final String FORBIDDEN = "|%\\{}#$&^~ ";

        /** A bullet under an entry, which is what most content is. */
        public MeasurableItem(String key, RichContent content) {
            this(key, content, RowShape.ENTRY_BULLET);
        }

        public MeasurableItem {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(content, "content");
            shape = shape == null ? RowShape.ENTRY_BULLET : shape;
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
