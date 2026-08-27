package com.mustafatetik.atomcv.generation.rewrite;

/**
 * What was wrong with a rewrite (Bolum 21.6).
 *
 * <p><strong>A kind and nothing else, on purpose.</strong> Naming the number
 * that went missing or quoting the sentence that drifted would put the user's
 * own CV into a value that gets logged and counted (absolute rule 4), and no
 * caller needs it: the reaction to every one of these is the same two steps —
 * try once more, then print what the person originally wrote.
 */
public enum RewriteIssue {

    /** A number the sentence claimed is not in the answer. */
    NUMBER_LOST,

    /** A name that may not be reworded is not in the answer. */
    PROPER_NOUN_LOST,

    /**
     * <strong>The one with zero tolerance.</strong> The answer mentions a
     * technology this atom does not have. That is the failure this whole phase
     * is built to prevent: a CV that claims a skill because the posting asked
     * for it is a lie the person will have to defend in an interview.
     */
    UNSUPPORTED_CLAIM,

    /** Longer than Bolum 21.3 allows, which would spend a page already promised. */
    TOO_LONG,

    /** It no longer says what it used to say. */
    SEMANTIC_DRIFT
}
