package com.mustafatetik.atomcv.shared.error;

/**
 * Why a compilation produced no document.
 *
 * <p>Four kinds, because the caller does something different with each: a
 * document TeX refused is a defect to look at, a busy compiler is worth
 * retrying, a timeout is a pathological document or a stuck one, and an
 * unreachable compiler is an outage.
 *
 * <p>It lives in {@code shared} rather than beside the exception that raises
 * it because {@link PipelineError.CompilationFailed} carries it, and
 * {@code shared} may not depend on a business module (Bolum 10.2, rule 4).
 * The alternative — Bolum 25.2's {@code (String detail, boolean
 * rawSourceAvailable)} — drops exactly the distinction the retry decision
 * reads.
 */
public enum CompilationFailureKind {

    /** The document is wrong: TeX read it and refused. */
    INVALID_DOCUMENT,

    /** Every compilation slot was taken. Bolum 29.5 bounds them on purpose. */
    BUSY,

    /** It took too long — a pathological document, or a stuck compiler. */
    TIMEOUT,

    /** The compiler could not be reached at all. */
    UNAVAILABLE
}
