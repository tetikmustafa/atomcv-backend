package com.mustafatetik.atomcv.ingestion.extraction;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The five things a CV may arrive as (Bolum 31.3).
 *
 * <p>Each one carries the three facts the validation ladder of Bolum 31.2
 * needs: the extensions a user's file may end in, the media types a client may
 * honestly declare, and the bytes the file actually starts with.
 *
 * <p><strong>The bytes are the only one of the three that is ours.</strong>
 * The extension is the user's word and the declared type is the client's, and
 * Bolum 42.1 lists "wrong type" among the risks precisely because both can be
 * made to say anything. So the extension chooses which format to try and the
 * magic bytes decide whether it was true.
 */
public enum DocumentFormat {

    PDF(List.of("pdf"),
            List.of("application/pdf"),
            new byte[] {'%', 'P', 'D', 'F', '-'}),

    /**
     * A DOCX is a zip, so its signature is a zip's. That is as far as bytes
     * can go here — every other zip in the world starts the same way — and
     * what settles it is POI refusing to open one that is not a Word
     * document.
     */
    DOCX(List.of("docx"),
            List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            new byte[] {'P', 'K', 0x03, 0x04}),

    TEX(List.of("tex"), List.of("application/x-tex", "text/x-tex"), null),

    TXT(List.of("txt"), List.of("text/plain"), null),

    MARKDOWN(List.of("md", "markdown"), List.of("text/markdown"), null);

    private final List<String> extensions;
    private final List<String> mediaTypes;
    private final byte[] signature;

    DocumentFormat(List<String> extensions, List<String> mediaTypes, byte[] signature) {
        this.extensions = List.copyOf(extensions);
        this.mediaTypes = List.copyOf(mediaTypes);
        this.signature = signature;
    }

    /**
     * Which format a filename claims to be.
     *
     * <p>{@code Locale.ROOT} on the extension, absolute rule 7: a Turkish
     * default locale lowercases {@code CV.TXT} to {@code cv.txt} correctly but
     * {@code .TIF} to {@code .tıf}, and the day an extension carries an I the
     * match would fail for Turkish users only.
     */
    public static Optional<DocumentFormat> ofFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return List.of(values()).stream()
                .filter(format -> format.extensions.contains(extension))
                .findFirst();
    }

    /**
     * Which format a declared media type names, if it names one we know.
     *
     * <p>Empty for {@code application/octet-stream}, for a blank value and for
     * anything unrecognised — all of which browsers send routinely for
     * {@code .tex} and {@code .md}. Treating those as a refusal would reject
     * files that are perfectly fine; see {@code DocumentValidation} for what
     * is done with the answer.
     */
    public static Optional<DocumentFormat> ofMediaType(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return Optional.empty();
        }
        // "text/plain; charset=utf-8" is one type with a parameter on it.
        String bare = mediaType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return List.of(values()).stream()
                .filter(format -> format.mediaTypes.contains(bare))
                .findFirst();
    }

    /**
     * Whether the bytes agree with the claim.
     *
     * <p>The three text formats have no signature to check, and inventing one
     * would be inventing a fact. What stands in for it is
     * {@link #looksLikeText}: a renamed binary is what the check exists to
     * catch, and a binary is exactly what fails to decode.
     */
    public boolean matches(byte[] bytes) {
        if (signature == null) {
            return looksLikeText(bytes);
        }
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * A NUL byte in the first kilobyte, which no text file has and almost
     * every binary does.
     *
     * <p>Not a decode attempt: {@code new String(bytes, UTF_8)} replaces bad
     * sequences rather than failing, so it would answer yes for a JPEG. A
     * strict decode of ten megabytes to learn one fact is also the wrong price
     * for the cheapest rung of Bolum 31.2's ladder.
     */
    private static boolean looksLikeText(byte[] bytes) {
        int examined = Math.min(bytes.length, 1024);
        for (int i = 0; i < examined; i++) {
            if (bytes[i] == 0) {
                return false;
            }
        }
        return true;
    }

    /** What the client may put in its file picker, and what an error publishes. */
    public List<String> extensions() {
        return extensions;
    }

    public static List<String> allExtensions() {
        return List.of(values()).stream().flatMap(format -> format.extensions.stream()).toList();
    }
}
