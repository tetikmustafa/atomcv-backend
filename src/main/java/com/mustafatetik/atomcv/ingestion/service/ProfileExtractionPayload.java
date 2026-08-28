package com.mustafatetik.atomcv.ingestion.service;

import com.mustafatetik.atomcv.billing.QuotaSubject;
import com.mustafatetik.atomcv.ingestion.extraction.DocumentFormat;
import com.mustafatetik.atomcv.ingestion.extraction.ExtractedText;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a queued extraction carries: the text, and the two facts about how it
 * was read.
 *
 * <p><strong>The text and not the file.</strong> Adim 3.4's first slice
 * decided the bytes are never stored, and this is where that decision has its
 * cost: the extracted text lives in {@code jobs.payload} until the job reaches
 * a terminal state. It is the user's own CV in the user's own row, but nothing
 * prunes completed jobs yet — recorded as an open item rather than solved
 * here, because retention is every job type's question and not this one's.
 *
 * <p>{@code looksScrambled} travels because Bolum 31.3's note is about the
 * <em>file</em>, and by the time the handler runs the file is gone. Recomputing
 * the heuristic from the text would give the same answer today and would be a
 * second place for it to be defined.
 */
record ProfileExtractionPayload(
        String text, DocumentFormat format, boolean looksScrambled, QuotaSubject allowance,
        boolean replace) {

    private static final String TEXT = "text";
    private static final String FORMAT = "format";
    private static final String SCRAMBLED = "looksScrambled";
    private static final String ALLOWANCE_TYPE = "allowanceType";
    private static final String ALLOWANCE_ID = "allowanceId";
    private static final String REPLACE = "replace";

    static ProfileExtractionPayload of(
            ExtractedText extracted, QuotaSubject allowance, boolean replace) {
        return new ProfileExtractionPayload(extracted.text(), extracted.format(),
                extracted.looksScrambled(), allowance, replace);
    }

    /**
     * A {@code LinkedHashMap}, not {@code Map.of}: this is serialised into a
     * JSONB column, and the JDK's immutable maps iterate in an order salted per
     * JVM run — the same payload would be written differently between runs.
     */
    Map<String, Object> asMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(TEXT, text);
        payload.put(FORMAT, format.name().toLowerCase(Locale.ROOT));
        payload.put(SCRAMBLED, looksScrambled);
        // Whose ceiling this took, so the worker can give it back.
        //
        // Carried rather than recomputed: the allowance was taken at the door
        // from the caller's address, and the worker has no request to read one
        // from. Refunding a different subject than the one that paid is worse
        // than not refunding at all — it credits somebody who never spent.
        payload.put(ALLOWANCE_TYPE, allowance.type().wireValue());
        payload.put(ALLOWANCE_ID, allowance.id());
        // The answer the caller already gave to the 409, carried to the worker
        // that acts on it. Asking again at write time would mean asking
        // somebody who is no longer on the other end of a request.
        payload.put(REPLACE, replace);
        return payload;
    }

    static ProfileExtractionPayload from(Map<String, Object> payload) {
        return new ProfileExtractionPayload(
                String.valueOf(payload.getOrDefault(TEXT, "")),
                DocumentFormat.valueOf(
                        String.valueOf(payload.getOrDefault(FORMAT, "txt"))
                                .toUpperCase(Locale.ROOT)),
                Boolean.TRUE.equals(payload.get(SCRAMBLED)),
                new QuotaSubject(
                        QuotaSubject.Type.valueOf(String.valueOf(
                                payload.getOrDefault(ALLOWANCE_TYPE, "user"))
                                .toUpperCase(Locale.ROOT)),
                        String.valueOf(payload.getOrDefault(ALLOWANCE_ID, ""))),
                Boolean.TRUE.equals(payload.get(REPLACE)));
    }

    ExtractedText asExtractedText() {
        return new ExtractedText(text, format, looksScrambled);
    }
}
