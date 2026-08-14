package com.mustafatetik.atomcv.rendering.model;

import java.util.Objects;

/**
 * A finished document, as source, on its way to the compiler.
 *
 * <p>Not a byte array and not a file: what leaves the renderer is text, and it
 * is the compilation module that turns it into a PDF. Keeping them apart is
 * what lets the renderer be tested without Docker.
 */
public record RenderedSource(String value) {

    public RenderedSource {
        Objects.requireNonNull(value, "value");
    }

    public int byteLength() {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /** Shape only: the source contains the user's whole CV (absolute rule 4). */
    @Override
    public String toString() {
        return "RenderedSource[bytes=" + byteLength() + "]";
    }
}
