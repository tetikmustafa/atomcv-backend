package com.mustafatetik.atomcv.rendering;

import java.util.Locale;
import java.util.Optional;

/**
 * What a person can download a generation as.
 *
 * <p>The whole vocabulary lives here, and that is the point. It used to live
 * in a chain of {@code equalsIgnoreCase} in the controller and in a set of
 * concretely injected writers in the generation module, so adding a format
 * meant editing two modules and the one that had no business knowing the list
 * was the generation module (module rule 3).
 *
 * <p>The media type carries its charset where the format is text. A response
 * without one is read as ISO-8859-1 and a Turkish name arrives broken — the
 * same trap the Markdown export already paid for.
 */
public enum OutputFormat {

    /** The typeset document, and the only one the page guarantee is exact for. */
    PDF("pdf", "application/pdf", "pdf"),

    /**
     * The same content as a Word document.
     *
     * <p>The page limit is approximate here: the atoms are the ones that
     * fitted a typeset page, and Word sets them in whatever room its own fonts
     * take.
     */
    DOCX("docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "docx"),

    /**
     * One self-contained file, nothing fetched.
     *
     * <p>The page limit does not apply at all — HTML has no page, so this is
     * not a weaker promise but a different kind of document.
     */
    HTML("html", "text/html;charset=UTF-8", "html"),

    /**
     * The LaTeX the PDF was compiled from.
     *
     * <p>Reading it back is not the forbidden layer: nobody is allowed to
     * <em>write</em> LaTeX, because user markup reaching a compiler is an
     * execution surface. Handing back what this product generated runs the
     * other way and nothing is read back in.
     */
    SOURCE("source", "application/x-tex;charset=UTF-8", "tex");

    private final String wireName;
    private final String mediaType;
    private final String fileExtension;

    OutputFormat(String wireName, String mediaType, String fileExtension) {
        this.wireName = wireName;
        this.mediaType = mediaType;
        this.fileExtension = fileExtension;
    }

    /** What {@code ?format=} carries. */
    public String wireName() {
        return wireName;
    }

    public String mediaType() {
        return mediaType;
    }

    public String fileExtension() {
        return fileExtension;
    }

    /**
     * The format a request asked for, or empty when it named one we do not
     * have.
     *
     * <p>Empty rather than a default: a client asking for {@code tex} and
     * silently getting a PDF would ship a button that downloads the wrong
     * document. The caller turns this into {@code VALIDATION_FAILED}.
     *
     * <p>{@code Locale.ROOT} because absolute rule 7 has no exceptions — a
     * Turkish default locale turns {@code "HTML"} into {@code "htmı"} and the
     * lookup misses.
     */
    public static Optional<OutputFormat> ofWireName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalised = name.toLowerCase(Locale.ROOT);
        for (OutputFormat format : values()) {
            if (format.wireName.equals(normalised)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }
}
