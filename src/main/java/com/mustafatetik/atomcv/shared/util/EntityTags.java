package com.mustafatetik.atomcv.shared.util;

import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;

/**
 * The {@code ETag} / {@code If-Match} pair over a JPA version column
 * (Bolum 35.6).
 *
 * <p>Writes require {@code If-Match}. The eighth design principle is that a
 * user's own work is never silently overwritten, and a write with no
 * precondition is exactly that: two tabs open, the second save wins, and the
 * first edit is gone without anyone being told. The client always has the
 * version — single resources send it as an {@code ETag}, collections carry it
 * per item — so requiring it costs nothing.
 */
public final class EntityTags {

    private EntityTags() {
    }

    /** The entity tag for a version, quoted as RFC 9110 requires. */
    public static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * @param ifMatch the raw header, or null when the client sent none
     * @param current the version the row carries right now
     * @throws ApiException {@code PRECONDITION_REQUIRED} when the header is
     *                      missing, {@code VERSION_CONFLICT} when it is stale
     */
    public static void requireMatch(String ifMatch, Long current) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiException.of(ErrorCode.PRECONDITION_REQUIRED);
        }
        long version = current == null ? 0L : current;
        String candidate = ifMatch.trim();
        if (candidate.equals("*") || candidate.equals(of(version))) {
            return;
        }
        // A proxy may weaken a tag on the way through; the version inside is
        // what identifies the row either way.
        if (candidate.startsWith("W/") && candidate.substring(2).equals(of(version))) {
            return;
        }
        throw ApiException.of(ErrorCode.VERSION_CONFLICT, Resolution.of(ResolutionAction.RETRY));
    }
}
