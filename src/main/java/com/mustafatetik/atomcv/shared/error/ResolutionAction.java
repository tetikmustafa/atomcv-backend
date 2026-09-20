package com.mustafatetik.atomcv.shared.error;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * What a user can do about an error.
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

    /**
     * Send the upload again with {@code ?mode=replace}: the profile that is
     * there is discarded and the CV becomes the new one.
     *
     * <p>Offered with {@link #KEEP_EXISTING_PROFILE} and never with a merge.
     * Merging is atom-level de-duplication — Jaro-Winkler plus embeddings —
     * and belongs to Stage 4; naming it here would either bind the endpoint to
     * work it cannot do or ship a merge that silently duplicates content,
     * which is the user's effort being thrown away.
     */
    REPLACE_PROFILE,

    /** Abandon the upload. The profile that is there is left exactly as it is. */
    KEEP_EXISTING_PROFILE,

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

    /**
     * Send the same text again, this time past the preflight.
     *
     * <p>A posting that does not look like one is a question, not a refusal —
     * the user may know better than the heuristic. Distinct from {@link
     * #RETRY}, which means a transient failure is worth repeating unchanged:
     * preflight would refuse the identical text identically, so the
     * resubmission has to carry an acknowledgement.
     */
    CONTINUE_ANYWAY,

    /** Extraction failed; go to the manual profile form. */
    SWITCH_TO_MANUAL_FORM,

    /**
     * Pick the file again. The one that was sent cannot be read as it stands.
     *
     * <p>Distinct from {@link #RETRY} and from {@link #SWITCH_TO_MANUAL_FORM},
     * and both distinctions cost the user something when they are lost. An
     * encrypted PDF fails identically every time it is sent, so {@code RETRY}
     * is a button that cannot work; and the person is not out of options the
     * way {@code SWITCH_TO_MANUAL_FORM} says they are — an open copy of the
     * same document reads perfectly. Section 31.10 says exactly this: refuse,
     * and ask for an open version.
     */
    UPLOAD_ANOTHER_FILE,

    /**
     * Say which language the document is in, from {@code params.detectedCandidates}.
     *
     * <p>Section 31.10 answers an undetected language with "ask the user", and
     * until this existed there was nothing to ask with: the refusal published
     * its candidates and offered no way to act on them. The choice matters
     * more than it looks — the selected language decides which wording of every
     * atom is written, so a wrong guess produces a whole profile in the wrong
     * language with no screen saying so.
     */
    CHOOSE_LANGUAGE,

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
