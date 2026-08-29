package com.mustafatetik.atomcv.generation.repository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Where a page of history left off (EK D.8.7).
 *
 * <p><strong>Both halves of the sort key, not just the timestamp.</strong> The
 * list is ordered {@code created_at DESC, id DESC} because two generations a
 * second apart is ordinary — Faz G's edit loop does exactly that — and a
 * cursor carrying only the instant would either skip the rows sharing it or
 * hand them back twice, depending on which way the comparison leaned. There is
 * no third option: a key that does not identify a row cannot resume from one.
 *
 * <p><strong>Opaque on the wire.</strong> Base64 of the two values, which is
 * not encryption and is not meant to be: it says "this is ours to read, and
 * yours to echo back". A client that took the timestamp apart and built its
 * own would be depending on the ordering, and the ordering is the server's to
 * change.
 *
 * <p>Nothing here is user content — an instant and a row id (absolute rule 4).
 */
public record GenerationCursor(Instant createdAt, UUID id) {

    private static final String SEPARATOR = "|";

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (createdAt.toString() + SEPARATOR + id).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A cursor from a client, or empty when there is none.
     *
     * <p><strong>Empty and absent are the same thing</strong>, and deliberately:
     * a client walking a list holds a nullable string and would otherwise have
     * to decide whether to send the parameter at all on the first page. Every
     * other malformed value is a failure, because the only way to hold a cursor
     * is to have been given one.
     *
     * @throws IllegalArgumentException when it is not one of ours
     */
    public static Optional<GenerationCursor> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = decoded.lastIndexOf(SEPARATOR);
            if (separator < 0) {
                throw new IllegalArgumentException("a cursor carries two values");
            }
            return Optional.of(new GenerationCursor(
                    Instant.parse(decoded.substring(0, separator)),
                    UUID.fromString(decoded.substring(separator + 1))));
        } catch (RuntimeException malformed) {
            // One exception type out, whatever went wrong on the way in: the
            // caller's answer is the same for every shape of broken cursor,
            // and the detail is not something a client can act on.
            throw new IllegalArgumentException("not a cursor this server issued", malformed);
        }
    }
}
