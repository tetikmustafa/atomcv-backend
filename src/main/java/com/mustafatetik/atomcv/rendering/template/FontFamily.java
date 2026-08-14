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

    /** Times-like. The default: it is what an ATS has seen a million times. */
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
