package com.mustafatetik.atomcv.generation.coverletter;

/**
 * What was wrong with a draft (Bolum 34.4).
 *
 * <p>A kind and nothing else, for the reason {@code RewriteIssue} gives: the
 * value that went wrong is the person's own letter, and naming it would put
 * their content into something that gets logged (absolute rule 4).
 *
 * <p>Unlike a rewrite, these do travel to the user — a letter that could not
 * be written has no original to fall back on, so the caller is told, and the
 * kinds are what the message is built from.
 */
public enum CoverLetterIssue {

    /**
     * <strong>The one with zero tolerance.</strong> The letter names a
     * technology that is on no atom of this page. Worse here than on a CV
     * line: this is claimed in the first person, in prose, and it is what an
     * interview opens with.
     */
    UNSUPPORTED_CLAIM,

    /** A number the page does not carry. */
    NUMBER_INVENTED,

    /**
     * <strong>Bolum 34.4's "most common fabrication".</strong> A claim about
     * how long the person has been working that their own dates do not
     * support.
     */
    EXPERIENCE_OVERSTATED,

    /**
     * The greeting names one of this person's own employers rather than the
     * one they are writing to — a model reusing a name it read a paragraph
     * ago, and the mistake a reader sees first.
     */
    WRONG_COMPANY,

    /** Outside Bolum 34.4's 250-400 words. */
    LENGTH_OUT_OF_RANGE,

    /** One of Bolum 34.4's banned openings, which say nothing about anybody. */
    CLICHE
}
