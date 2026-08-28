package com.mustafatetik.atomcv.generation.coverletter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * The three buttons of Bolum 34.6, as one parameter.
 *
 * <p>A letter is cheap to write again and a person's judgement about their own
 * covering letter is better than ours, so the product's answer to "not quite"
 * is another draft rather than an editor. Each of these is one call.
 */
public enum CoverLetterStyle {

    /** Write it again. Same constraints, a different draft. */
    DEFAULT,

    /**
     * <strong>Ekleme.</strong> Shorter, within Bolum 34.4's band rather than
     * below it. The floor of 250 words is a rule about what a covering letter
     * has to do — greet, say what the job is, give two or three pieces of
     * evidence, close — and a draft that dropped under it would have stopped
     * doing one of them. So "shorter" aims at the bottom of the band and the
     * check that follows is unchanged.
     */
    SHORTER,

    /** More formal, whatever the profile's usual tone is. */
    MORE_FORMAL;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static CoverLetterStyle fromWireValue(String value) {
        return value == null || value.isBlank()
                ? DEFAULT
                : valueOf(value.strip().toUpperCase(Locale.ROOT));
    }
}
