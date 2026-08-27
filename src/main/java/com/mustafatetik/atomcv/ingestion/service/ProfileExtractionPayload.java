package com.mustafatetik.atomcv.ingestion.service;

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
record ProfileExtractionPayload(String text, DocumentFormat format, boolean looksScrambled) {

    private static final String TEXT = "text";
    private static final String FORMAT = "format";
    private static final String SCRAMBLED = "looksScrambled";

    static ProfileExtractionPayload of(ExtractedText extracted) {
        return new ProfileExtractionPayload(
                extracted.text(), extracted.format(), extracted.looksScrambled());
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
        return payload;
    }

    static ProfileExtractionPayload from(Map<String, Object> payload) {
        return new ProfileExtractionPayload(
                String.valueOf(payload.getOrDefault(TEXT, "")),
                DocumentFormat.valueOf(
                        String.valueOf(payload.getOrDefault(FORMAT, "txt"))
                                .toUpperCase(Locale.ROOT)),
                Boolean.TRUE.equals(payload.get(SCRAMBLED)));
    }

    ExtractedText asExtractedText() {
        return new ExtractedText(text, format, looksScrambled);
    }
}
