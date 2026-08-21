package com.mustafatetik.atomcv.llm.gateway;

/**
 * How much model a call is worth (Bolum 5.4, Bolum 27.1).
 *
 * <p>Two classes, not five. Bolum 5.4 assigns every phase to one of them:
 * structured extraction from a job posting, a bullet rewrite and an edit-intent
 * parse are cheap; synthesising the About paragraph and reading a whole CV are
 * mid. A third class would be a distinction no phase asks for.
 *
 * <p>The tier names a chain in configuration, never a model — model names are
 * environment variables because vendors rename them faster than a release
 * cycle (Bolum 5.4).
 */
public enum ModelTier {

    /** Narrow, structured, high volume. Faz A, atom rewrite, Faz G. */
    CHEAP,

    /** Long input or writing the user will read as prose. About, extraction. */
    MID
}
