package com.mustafatetik.atomcv.rendering.template;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * The fonts a document may ask for (Bolum 33.2, Bolum 22.5).
 *
 * <p>The enum <em>is</em> the whitelist. No user string reaches
 * {@code \setmainfont}: a family that is not one of these constants cannot be
 * named, so the one place a font name enters LaTeX takes its value from here.
 *
 * <p>Every one of these ships with the container image. A font that is not
 * installed silently falls back to another at compile time, which would make
 * every measured render cost wrong without a single error.
 */
public enum FontFamily {

    /**
     * Computer Modern, which is what a {@code article} sets by default and so
     * what the reference CV this template was taken from is set in.
     *
     * <p>Latin Modern is the OpenType cut of it: the same design and the same
     * metrics, in the format {@code fontspec} can load. Naming Computer Modern
     * itself would find nothing, and a font {@code \setmainfont} cannot find
     * is substituted silently — which makes every stored render cost wrong
     * without a single error.
     */
    MODERN("Latin Modern Roman"),

    /** Times-like. */
    SERIF("TeX Gyre Termes"),

    /** Helvetica-like. */
    SANS("TeX Gyre Heros"),

    /** Palatino-like: wider, warmer, and noticeably more expensive per line. */
    BOOK("TeX Gyre Pagella");

    private final String latexName;

    FontFamily(String latexName) {
        this.latexName = latexName;
    }

    /** The name {@code \setmainfont} is given. */
    public String latexName() {
        return latexName;
    }

    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static FontFamily fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
