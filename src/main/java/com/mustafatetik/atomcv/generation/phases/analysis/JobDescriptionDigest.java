package com.mustafatetik.atomcv.generation.phases.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * A posting reduced to a hash (Bolum 18.6, Bolum 13).
 *
 * <p>Two callers need exactly the same answer and it matters that they agree:
 * the analysis cache keys on it, and {@code generations.jd_hash} records it. A
 * second implementation that normalised differently would mean two generations
 * of one posting carrying different hashes while sharing a cache entry — the
 * kind of disagreement nothing ever reports.
 *
 * <p>The normalisation is the point. The same posting pasted from a PDF and
 * from a browser differs only in whitespace, and two entries for one posting
 * is a cache that misses on purpose.
 */
public final class JobDescriptionDigest {

    // Two backslashes, and it matters: since Java 15 a single-backslash \s is
    // a valid string escape meaning one plain space, so the shorter form
    // compiles and quietly narrows the pattern to runs of spaces. Line endings
    // then survive normalisation and the same posting hashes twice.
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private JobDescriptionDigest() {
    }

    /** Bolum 18.6: collapse whitespace, join line endings, trim. */
    public static String normalize(String jobDescription) {
        return WHITESPACE.matcher(jobDescription).replaceAll(" ").trim();
    }

    /** The hash of the normalised posting, lowercase hex. */
    public static String of(String jobDescription) {
        return sha256(normalize(jobDescription));
    }

    static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every JVM", impossible);
        }
    }
}
