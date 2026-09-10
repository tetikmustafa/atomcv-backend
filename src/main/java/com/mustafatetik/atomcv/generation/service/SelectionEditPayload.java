package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.selection.GenerationDirectives;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a queued hand edit carries in {@code jobs.payload} (Bolum 24.4).
 *
 * <p>Its own record next to {@link GenerationPayload}, and its own shape in
 * the column, because an edit and a generation share almost none of their
 * inputs: there is no posting to read, no page limit to choose, no letter to
 * ask for and no allowance to refund — the parent row already decided all of
 * those and a toggle spends nothing. Folding it into the other record would
 * have made five of its eight fields meaningless in half the jobs.
 *
 * <p>The {@link JobType} is still {@code GENERATION}. The work <em>is</em> a
 * generation — same priority, same handler, same progress events, same screen
 * waiting on it — and a seventh job type would have taught the queue, the
 * column comment and the frontend's vocabulary a distinction none of them
 * needs. The parent id in the payload is the whole difference.
 */
public record SelectionEditPayload(UUID parentGenerationId, GenerationDirectives directives) {

    private static final String PARENT = "parentGenerationId";

    public SelectionEditPayload {
        if (parentGenerationId == null) {
            throw new IllegalArgumentException("An edit is an edit of something");
        }
        directives = directives == null ? GenerationDirectives.none() : directives;
    }

    /**
     * Ordered, because the map becomes a JSONB column and the JDK's immutable
     * maps iterate in an order salted per JVM run (CLAUDE.md).
     */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(PARENT, parentGenerationId.toString());
        payload.putAll(directives.asMap());
        return payload;
    }

    /**
     * Whether this job is an edit rather than a generation.
     *
     * <p>Read before the payload is parsed as anything, because
     * {@link GenerationPayload#from} is forgiving about absence: an edit read
     * as a generation would come out as general CV mode and quietly rebuild
     * the document from scratch — the same person, the same profile, and none
     * of the edits they had made.
     */
    public static boolean isEdit(Map<String, Object> payload) {
        return payload != null && payload.get(PARENT) != null;
    }

    public static SelectionEditPayload from(Map<String, Object> payload) {
        return new SelectionEditPayload(
                UUID.fromString(String.valueOf(payload.get(PARENT))),
                GenerationDirectives.fromMap(payload));
    }
}
