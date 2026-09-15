package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.billing.QuotaSubject;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a queued generation carries in {@code jobs.payload}.
 *
 * <p>A typed pair of conversions rather than map reads scattered through the
 * handler. The column is JSONB and its shape is decided here and nowhere else:
 * a handler that read {@code payload.get("jd")} while the enqueuer wrote
 * {@code jobDescription} would fail every job with a null posting, and the
 * failure would look like the LLM's.
 *
 * <p>Reading is deliberately forgiving about absence and strict about type. A
 * payload written by an older release may be missing a field this one added;
 * one carrying a string where a number belongs is a defect worth failing on.
 */
public record GenerationPayload(
        String jobDescription,
        boolean preflightAcknowledged,
        Integer maxPages,
        String language,
        boolean coverLetter,
        java.util.List<String> emphasize,
        java.util.UUID customizationId,
        String freeformNote,
        QuotaSubject allowance) {

    private static final String JOB_DESCRIPTION = "jobDescription";
    private static final String PREFLIGHT_ACKNOWLEDGED = "preflightAcknowledged";
    private static final String MAX_PAGES = "maxPages";
    private static final String LANGUAGE = "language";
    private static final String COVER_LETTER = "coverLetter";
    private static final String EMPHASIZE = "emphasize";
    private static final String CUSTOMIZATION_ID = "customizationId";
    private static final String FREEFORM_NOTE = "freeformNote";
    private static final String ALLOWANCE_TYPE = "allowanceType";
    private static final String ALLOWANCE_ID = "allowanceId";

    /**
     * Ordered, because the map becomes a JSONB column and the JDK's immutable
     * maps iterate in an order salted per JVM run (CLAUDE.md).
     */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(JOB_DESCRIPTION, jobDescription);
        payload.put(PREFLIGHT_ACKNOWLEDGED, preflightAcknowledged);
        payload.put(MAX_PAGES, maxPages);
        payload.put(LANGUAGE, language);
        payload.put(COVER_LETTER, coverLetter);
        payload.put(EMPHASIZE, emphasize);
        payload.put(CUSTOMIZATION_ID, customizationId == null ? null : customizationId.toString());
        payload.put(FREEFORM_NOTE, freeformNote);
        // Whose ceiling this took, so the worker can give it back -- the shape
        // ProfileExtractionPayload already uses, for the same reason. An
        // account pays by user id and an anonymous caller by address, and the
        // worker has no request to read an address from. Refunding a different
        // subject than the one that paid is worse than not refunding: it
        // credits somebody who never spent.
        payload.put(ALLOWANCE_TYPE, allowance.type().wireValue());
        payload.put(ALLOWANCE_ID, allowance.id());
        return payload;
    }

    public static GenerationPayload from(Map<String, Object> payload) {
        return new GenerationPayload(
                string(payload, JOB_DESCRIPTION),
                Boolean.TRUE.equals(payload.get(PREFLIGHT_ACKNOWLEDGED)),
                integer(payload, MAX_PAGES),
                string(payload, LANGUAGE),
                // Absent on a job queued by the release before this one, and
                // absent reads as "no letter" — which is what those jobs were
                // asked for.
                Boolean.TRUE.equals(payload.get(COVER_LETTER)),
                // Absent on a job queued by the release before this one, and
                // absent is the ordinary case anyway: nearly nobody steers.
                termsIn(payload),
                // Absent on a job queued before this field, and absent is the
                // ordinary case: a request that names no set gets the
                // profile's own working settings.
                uuidIn(payload, CUSTOMIZATION_ID),
                string(payload, FREEFORM_NOTE),
                allowanceIn(payload));
    }

    /**
     * The subject that paid, or a user-typed one with an empty id for a job
     * queued by the release before this field existed.
     *
     * <p>Absent reads as "nobody named", and {@code QuotaService.refund} on an
     * empty id credits a subject nothing was ever taken from — which is the
     * harmless direction. Failing the job instead would throw away a generation
     * that was paid for over bookkeeping.
     */
    private static QuotaSubject allowanceIn(Map<String, Object> payload) {
        return new QuotaSubject(
                QuotaSubject.Type.valueOf(String.valueOf(
                        payload.getOrDefault(ALLOWANCE_TYPE, "user")).toUpperCase(Locale.ROOT)),
                String.valueOf(payload.getOrDefault(ALLOWANCE_ID, "")));
    }

    /**
     * The emphasis, as the queue carries it.
     *
     * <p>Canonicalising is {@code GenerationDirectives}' job and not repeated
     * here: two places deciding what a term looks like is two answers on the
     * day one of them learns something.
     */
    private static java.util.List<String> termsIn(Map<String, Object> payload) {
        Object value = payload.get(EMPHASIZE);
        if (value == null) {
            return java.util.List.of();
        }
        if (!(value instanceof java.util.List<?> items)) {
            throw new IllegalArgumentException(
                    EMPHASIZE + " is a list, got " + value.getClass());
        }
        return items.stream().map(String::valueOf).toList();
    }

    private static java.util.UUID uuidIn(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        try {
            return java.util.UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException notAnId) {
            throw new IllegalArgumentException(key + " is a uuid, got " + value);
        }
    }

    private static String string(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(key + " is a string, got " + value.getClass());
        }
        return text;
    }

    private static Integer integer(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " is a number, got " + value.getClass());
        }
        return number.intValue();
    }
}
