package com.mustafatetik.atomcv.compilation;

import com.mustafatetik.atomcv.shared.error.CompilationFailureKind;

/**
 * A compilation that did not produce a document.
 *
 * <p>The four kinds are {@link CompilationFailureKind}; it sits in
 * {@code shared} because the pipeline's error type carries the same value and
 * {@code shared} may not reach back into this module.
 */
public class CompilationException extends RuntimeException {

    private final CompilationFailureKind kind;
    private final String log;

    public CompilationException(
            CompilationFailureKind kind, String message, String log, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.log = log == null ? "" : log;
    }

    public CompilationFailureKind kind() {
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
}
