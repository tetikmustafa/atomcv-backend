package com.mustafatetik.atomcv.rendering.template;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An accent colour, as six hex digits (Bolum 33.2).
 *
 * <p>The value reaches {@code \definecolor} directly, so the pattern is the
 * whole of the defence: anything that is not six hex digits is refused here
 * rather than escaped later.
 */
public record HexColor(String value) {

    private static final Pattern SIX_HEX_DIGITS = Pattern.compile("^[0-9A-Fa-f]{6}$");

    /** A neutral near-black; visible as a colour, sober enough for an ATS. */
    public static final HexColor DEFAULT = new HexColor("1A1A1A");

    public HexColor {
        Objects.requireNonNull(value, "value");
        if (!SIX_HEX_DIGITS.matcher(value).matches()) {
            throw new IllegalArgumentException("An accent colour is six hex digits");
        }
        // Uppercase with Locale.ROOT: absolute rule 7, and a stable value means
        // two customizations that differ only in case do not measure twice.
        value = value.toUpperCase(Locale.ROOT);
    }

    @JsonValue
    @Override
    public String value() {
        return value;
    }

    @JsonCreator
    public static HexColor of(String value) {
        return new HexColor(value);
    }
}
