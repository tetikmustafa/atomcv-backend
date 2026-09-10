package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.billing.QuotaSubject;
import com.mustafatetik.atomcv.generation.selection.GenerationDirectives;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What a queued edit carries in {@code jobs.payload} (Bolum 24.4, 24.2).
 *
 * <p>Its own record next to {@link GenerationPayload}, and its own shape in
 * the column, because an edit and a generation share almost none of their
 * inputs: there is no posting to read, no page limit to choose and no letter
 * to ask for — the parent row already decided all of those. Folding it into
 * the other record would have made five of its eight fields meaningless in
 * half the jobs.
 *
 * <p>The {@link com.mustafatetik.atomcv.jobs.queue.JobType} is still
 * {@code GENERATION}. The work <em>is</em> a generation — same priority, same
 * handler, same progress events, same screen waiting on it — and a seventh job
 * type would have taught the queue, the column comment and the frontend's
 * vocabulary a distinction none of them needs. The parent id is the whole
 * difference.
 *
 * <p><strong>One record, two costs.</strong> A hand toggle arrives with
 * directives and no sentence and spends nothing; a natural-language edit
 * arrives with a sentence and pays for the parse. Keeping them apart would
 * have duplicated the parent id, the merge and the whole re-run path to say
 * one thing differently.
 *
 * @param instruction what the person typed, or null for a hand toggle. It is
 *                    their own writing and it is stored, not logged — the same
 *                    footing as the posting on the generation row (absolute
 *                    rule 4 is about diagnostics leaving the system)
 * @param allowance   whose ceiling paid for the parse, so the worker can give
 *                    it back. Absent for a hand toggle, which took nothing
 */
public record SelectionEditPayload(
        UUID parentGenerationId,
        GenerationDirectives directives,
        String instruction,
        QuotaSubject allowance) {

    private static final String PARENT = "parentGenerationId";
    private static final String INSTRUCTION = "instruction";
    private static final String ALLOWANCE_TYPE = "allowanceType";
    private static final String ALLOWANCE_ID = "allowanceId";

    public SelectionEditPayload {
        if (parentGenerationId == null) {
            throw new IllegalArgumentException("An edit is an edit of something");
        }
        directives = directives == null ? GenerationDirectives.none() : directives;
    }

    /** A hand toggle: ids, no sentence, no allowance. */
    public SelectionEditPayload(UUID parentGenerationId, GenerationDirectives directives) {
        this(parentGenerationId, directives, null, null);
    }

    /** Whether this edit has to be read before it can be applied (Bolum 24.2). */
    public boolean isNaturalLanguage() {
        return instruction != null && !instruction.isBlank();
    }

    /**
     * Ordered, because the map becomes a JSONB column and the JDK's immutable
     * maps iterate in an order salted per JVM run (CLAUDE.md).
     */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(PARENT, parentGenerationId.toString());
        payload.putAll(directives.asMap());
        if (instruction != null) {
            payload.put(INSTRUCTION, instruction);
        }
        if (allowance != null) {
            payload.put(ALLOWANCE_TYPE, allowance.type().wireValue());
            payload.put(ALLOWANCE_ID, allowance.id());
        }
        return payload;
    }

    /**
     * Whether this job is an edit rather than a generation.
     *
     * <p>Read before the payload is parsed as anything else, because
     * {@link GenerationPayload#from} is forgiving about absence: an edit read
     * as a generation would come out as general CV mode and quietly rebuild
     * the document from scratch — the same person, the same profile, none of
     * their edits, and no error anywhere to say so.
     */
    public static boolean isEdit(Map<String, Object> payload) {
        return payload != null && payload.get(PARENT) != null;
    }

    public static SelectionEditPayload from(Map<String, Object> payload) {
        Object instruction = payload.get(INSTRUCTION);
        return new SelectionEditPayload(
                UUID.fromString(String.valueOf(payload.get(PARENT))),
                GenerationDirectives.fromMap(payload),
                instruction == null ? null : String.valueOf(instruction),
                allowanceIn(payload));
    }

    /**
     * Null for a hand toggle, which spent nothing and has nothing to refund.
     *
     * <p>A missing type on a payload that does name one is a job queued by an
     * older release; {@code QuotaService.refund} on an empty id credits a
     * subject nothing was taken from, which is the harmless direction.
     */
    private static QuotaSubject allowanceIn(Map<String, Object> payload) {
        Object type = payload.get(ALLOWANCE_TYPE);
        if (type == null) {
            return null;
        }
        return new QuotaSubject(
                QuotaSubject.Type.valueOf(String.valueOf(type).toUpperCase(Locale.ROOT)),
                String.valueOf(payload.getOrDefault(ALLOWANCE_ID, "")));
    }

    /** Shape only: the sentence is the user's own writing (absolute rule 4). */
    @Override
    public String toString() {
        return "SelectionEditPayload[parent=" + parentGenerationId
                + ", naturalLanguage=" + isNaturalLanguage() + "]";
    }
}
