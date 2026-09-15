package com.mustafatetik.atomcv.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * What the LaTeX image must and must not contain.
 *
 * <p><strong>The one it must not is a line the specification still
 * shows.</strong> The Dockerfile snippet ends with a preamble format dump —
 *
 * <pre>xelatex -ini -jobname="cvfmt" "&amp;xelatex preamble.tex\dump"</pre>
 *
 * — and it is repeated elsewhere with a claimed one-to-two-second saving
 * beside it. <strong>It cannot work under XeTeX.</strong> Measured against the
 * real image on 2026-09-15, the engine refuses it outright:
 *
 * <pre>! Can't \dump a format with native fonts or font-mappings.</pre>
 *
 * <p>That is not a configuration problem. XeTeX will not dump a format from a
 * session that has loaded native fonts, and the {@code xelatex} format has
 * loaded them already through the {@code TU} encoding — so a file consisting of
 * nothing but {@code \documentclass} and {@code \endofdump} raises it thirteen
 * times, with or without {@code fontspec}, with or without
 * {@code mylatexformat}. The dump still writes a {@code .fmt}, which is the
 * dangerous part: the build step looks like it worked, and the first compile
 * that tries to read the file fails with
 * {@code Could not undump 512303 8-byte item(s)}.
 *
 * <p><strong>Why a test rather than only a correction in the spec.</strong> The
 * snippet is still there to be read and it is two lines to paste. A build that
 * quietly produces an unusable format costs an afternoon to trace, and the
 * failure appears in the compiler rather than in the build. This is the cheaper
 * end of that.
 *
 * <p>The promised saving was measured too, and it is not there to be had: a
 * minimal document compiles end to end in 620-925 ms in this image, which is
 * less than the 1-2 seconds the dump was supposed to remove from it. The cold
 * cost is paid by the container warm-up instead.
 */
class LatexImageTest {

    private static final Path DOCKERFILE = Path.of("docker", "latex", "Dockerfile");

    /** The wrapper the image runs, which is where the compiler flags live. */
    private static final Path COMPILE_SERVER =
            Path.of("docker", "latex", "server", "CompileServer.java");

    private static final String WHY = """
            The documented snippet does not work under XeTeX: the engine answers \
            "Can't \\dump a format with native fonts or font-mappings", because \
            the xelatex format has already loaded native fonts through TU. The \
            dump writes a .fmt anyway and the first compile that reads it fails \
            with "Could not undump". Measured against this image on 2026-09-15; \
            the spec carries the correction beside the snippet.""";

    @Test
    void theimageDoesNotTryToDumpApreambleFormat() {
        String dockerfile = read();

        assertThat(dockerfile)
                .as(WHY)
                .doesNotContain("-ini")
                .doesNotContain("\\dump")
                .doesNotContain("cvfmt");
    }

    /**
     * The half of that advice that is real and worth keeping: the font cache
     * is built once at build time rather than by the first compile that needs
     * it.
     */
    @Test
    void thefontCacheIsBuiltIntoTheImage() {
        assertThat(read())
                .as("fc-cache at build time, not on the first render")
                .contains("fc-cache");
    }

    /**
     * <strong>Absolute rule 8, at the one line that decides it.</strong> Every
     * compilation is {@code -no-shell-escape}, and the flag lives in the
     * wrapper the image runs rather than anywhere the application can see:
     * nothing in the Java build would fail if it were deleted, and what
     * reaches that compiler is user content.
     */
    @Test
    void everyCompilationRefusesShellEscape() {
        String server = read(COMPILE_SERVER);

        assertThat(server)
                .as("the flag absolute rule 8 is about")
                .contains("\"-no-shell-escape\"");
        assertThat(server)
                .as("and nothing that would put it back")
                .doesNotContain("--shell-escape")
                .doesNotContain("-shell-escape=1");
        assertThat(read())
                .doesNotContain("--shell-escape")
                .doesNotContain("shell_escape = t");
    }

    private static String read() {
        return read(DOCKERFILE);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new AssertionError("Could not read " + file, unreadable);
        }
    }
}
