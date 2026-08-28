package com.mustafatetik.atomcv.generation.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A CV asked for (Bolum 35.3).
 *
 * <p>{@code jobDescription} is optional, and leaving it out is not a mistake:
 * it is general CV mode (Bolum 19.4), where there is nothing to be relevant to
 * and the profile is ranked on its own terms. The column says the same thing —
 * {@code generations.job_description} is NULL for exactly this case.
 *
 * @param acknowledgePreflight the user was told the text does not look like a
 *                             posting and asked to go ahead anyway
 *                             ({@code continue_anyway}, EK D.6.1). The
 *                             heuristics are cheap on purpose and a person may
 *                             know better; the plausibility gate still runs.
 */
@Schema(description = "A generation against a job posting")
public record GenerationRequest(

        @Schema(description = "The posting, pasted as it was found. "
                + "Omitted or blank means a general CV: no posting, no LLM.")
        // Well above what a posting ever is, and low enough that a pasted book
        // is refused by the framework rather than by the LLM bill.
        @Size(max = 60_000)
        String jobDescription,

        @Schema(description = "Proceed even though the preflight refused the text",
                defaultValue = "false")
        Boolean acknowledgePreflight,

        @Schema(description = "How many pages the CV may take", example = "1",
                minimum = "1", maximum = "10")
        @Min(1) @Max(10)
        Integer maxPages,

        @Schema(description = "Which wording to render, as an ISO 639-1 code. "
                + "Omitted, the profile decides — and its `auto` follows the posting.",
                example = "en", pattern = "^[a-z]{2}$")
        @Pattern(regexp = "^[a-z]{2}$")
        String language,

        @Schema(description = "Write a covering letter alongside the CV (Bolum 34). "
                + "Off by default: it is a second LLM call, and most generations "
                + "do not want one. It can be asked for afterwards instead, at "
                + "POST /generations/{id}/cover-letter/regenerate.",
                defaultValue = "false")
        Boolean coverLetter) {

    public boolean acknowledged() {
        return Boolean.TRUE.equals(acknowledgePreflight);
    }

    /**
     * <strong>Opt-in, and design principle 5 is the reason.</strong> A letter
     * is a call this product would otherwise make for everybody who only
     * wanted a CV. General mode ignores it: Bolum 34.2 writes the letter
     * against a posting, and there is none.
     */
    public boolean wantsCoverLetter() {
        return Boolean.TRUE.equals(coverLetter);
    }

    /**
     * Bolum 19.4: no posting to be relevant to.
     *
     * <p><strong>Not a field, and the annotations are what say so (F-009).</strong>
     * An {@code isX()} on a record is a getter as far as Jackson and springdoc
     * are concerned, so this published a {@code generalMode} boolean into the
     * request schema — a second way to ask for general mode, next to the one
     * that decides it. The frontend found it and asked what it was for.
     *
     * <p>Same shape as the bug Stage 2 hit on {@code RichContent}, on the other
     * side of the wire: <em>every getter-shaped method on a record that
     * Jackson touches is a field somebody will find</em> — a stored one on a
     * JSONB column, a documented one on a DTO.
     */
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isGeneralMode() {
        return jobDescription == null || jobDescription.isBlank();
    }
}
