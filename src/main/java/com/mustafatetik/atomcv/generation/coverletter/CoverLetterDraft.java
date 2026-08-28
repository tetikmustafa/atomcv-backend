package com.mustafatetik.atomcv.generation.coverletter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What the model answers, in the five parts of Bolum 34.3.
 *
 * <p>Parts rather than one string, and the reason is the greeting. A letter
 * addressed to the wrong company is the one failure a reader notices before
 * they have read a sentence, and the check for it needs to know which words
 * were the greeting — in a single block of prose, "Acme" in the opening line
 * and "Acme" in the third paragraph are the same characters and not the same
 * mistake.
 *
 * @param greeting  the company by name where the posting gave one, generic
 *                  where it did not
 * @param opening   the role, and why this person is writing — one or two
 *                  sentences
 * @param body      two or three pieces of evidence, each drawn from an atom
 *                  and tied to something the posting asked for
 * @param closing   what happens next, in a sentence or two
 * @param signature the person's name
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoverLetterDraft(
        String greeting, String opening, String body, String closing, String signature) {

    public CoverLetterDraft {
        greeting = orEmpty(greeting);
        opening = orEmpty(opening);
        body = orEmpty(body);
        closing = orEmpty(closing);
        signature = orEmpty(signature);
    }

    /** The letter as it is copied out — Bolum 34.7 renders no document. */
    public String plainText() {
        return String.join("\n\n", greeting, opening, body, closing, signature).strip();
    }

    /** Everything but the greeting, which is checked on its own. */
    String prose() {
        return String.join(" ", opening, body, closing);
    }

    /** Length only: every field here is the letter (absolute rule 4). */
    @Override
    public String toString() {
        return "CoverLetterDraft[chars=" + plainText().length() + "]";
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}
