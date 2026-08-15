package com.mustafatetik.atomcv.shared.error;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * What a user can do about an error (Bolum 35.4, EK D.6).
 *
 * <p>The server owns this list. Buttons are generated from the
 * {@code resolutions} array rather than written per error screen, so an action
 * that is not named here cannot be offered — and one that is named here must
 * have a client behaviour. The frontend adds no resolutions of its own; a plain
 * dismiss control outside the resolution row is a different thing.
 */
public enum ResolutionAction {

    /** Raise the page limit to {@code params.maxPages} and submit again. */
    INCREASE_PAGE_LIMIT,

    /** Open the pinned-content review, filtered to pins. */
    REVIEW_PINS,

    /** Keep the top {@code params.keep} pins and submit the narrowed set. */
    KEEP_TOP_PINNED,

    /** The feature needs an account; go to sign-up, preserving state. */
    SIGN_UP,

    /** The posting was too thin to analyse; focus the job description field. */
    PASTE_FULL_POSTING,

    /** Proceed with no posting at all — general CV mode. */
    CONTINUE_AS_GENERAL_CV,

    /** Extraction failed; go to the manual profile form. */
    SWITCH_TO_MANUAL_FORM,

    /** There is too little profile to generate from; open the profile editor. */
    COMPLETE_PROFILE,

    /** Transient failure; submit again unchanged. */
    RETRY;

    /** Lowercase on the wire, as the schema publishes it. */
    @JsonValue
    public String wireValue() {
        // Locale.ROOT: absolute rule 7. A Turkish locale would emit
        // "sıgn_up" here and no client would match it.
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ResolutionAction fromWireValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
