package com.mustafatetik.atomcv.compilation;

/**
 * Source in, a document or its measurements out.
 *
 * <p>Two implementations and a profile between them: {@link
 * LatexCompilerClient} reaches the isolated container and is what runs
 * everywhere except {@code local-fake}, where {@link FakeLatexCompiler}
 * answers instead so that a clone with no Docker image can still produce a CV.
 *
 * <p><strong>The seam is here and nowhere else.</strong> Everything that needs
 * a compiler depends on this type, which is what keeps "user content is
 * compiled somewhere isolated" a property of the system: there is one
 * implementation that talks to the container, and a reader can see that it is
 * the only one.
 */
public interface LatexCompiler {

    /** The PDF, or an exception carrying the log that explains its absence. */
    CompiledDocument compile(String source);

    /**
     * The TeX log for a measurement run. No document is produced and none is
     * wanted: what matters is what TeX said about the sizes.
     */
    String measure(String source);
}
