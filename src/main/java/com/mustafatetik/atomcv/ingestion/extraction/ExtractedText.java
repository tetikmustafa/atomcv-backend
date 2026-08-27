package com.mustafatetik.atomcv.ingestion.extraction;

/**
 * What came out of the file, and what the next stage needs to know about it.
 *
 * <p><strong>{@code looksScrambled} is a note, not a refusal.</strong> Bolum
 * 31.3 has it reach the structuring prompt as "this text may be out of order,
 * try to put it right" — a multi-column layout that PDFBox interleaved is
 * still a readable CV to a model that has been warned. Refusing it here would
 * turn a hint into a wall.
 *
 * @param text          the extracted text, never logged (absolute rule 4)
 * @param format        which reader produced it
 * @param looksScrambled Bolum 31.3's heuristic, for the prompt to carry
 */
public record ExtractedText(String text, DocumentFormat format, boolean looksScrambled) {

    /**
     * What may be said about the text in a log line.
     *
     * <p>Bolum 48.2's {@code ContentShape} is the same idea one stage later,
     * and deliberately not reused: its fields are an atom's — runs, emphasis,
     * render cost — and a record whose useful half is always zero describes
     * nothing. Absolute rule 4 asks for statistics instead of content, not for
     * one particular record.
     */
    public String shape() {
        return "format=" + format
                + " chars=" + text.length()
                + " lines=" + (text.isEmpty() ? 0 : text.split("\n", -1).length)
                + " scrambled=" + looksScrambled;
    }
}
