package com.mustafatetik.atomcv.compilation;

/**
 * A compilation that did not produce a document.
 *
 * <p>Three kinds, because the caller does something different with each: a
 * document TeX refused is a defect to look at, a busy compiler is worth
 * retrying, and an unreachable one is an outage.
 */
public class CompilationException extends RuntimeException {

    private final Kind kind;
    private final String log;

    public CompilationException(Kind kind, String message, String log, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.log = log == null ? "" : log;
    }

    public Kind kind() {
        return kind;
    }

    /**
     * What TeX said. It is derived from the user's own content, so it belongs
     * in a response to them and in a developer's hands — never in a log line
     * (absolute rule 4).
     */
    public String log() {
        return log;
    }

    public enum Kind {
        /** The document is wrong: TeX read it and refused. */
        INVALID_DOCUMENT,

        /** Every compilation slot was taken. Bolum 29.5 bounds them on purpose. */
        BUSY,

        /** It took too long — a pathological document, or a stuck compiler. */
        TIMEOUT,

        /** The compiler could not be reached at all. */
        UNAVAILABLE
    }
}
