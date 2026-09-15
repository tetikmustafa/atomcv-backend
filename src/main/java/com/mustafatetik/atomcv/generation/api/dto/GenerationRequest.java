package com.mustafatetik.atomcv.generation.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
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

        @Schema(description = "What the challenge widget produced. Required for a "
                + "caller with no account and ignored for one with an account "
                + "(Bolum 44.4): signing in already answered a challenge, and "
                + "generating spends real money on a model.")
        String challengeToken,

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
        Boolean coverLetter,

        @Schema(description = """
                Terms to bring forward, as Bolum 18.7's directive. They join
                the posting's own keywords and tags for this one generation --
                the formula of Bolum 19.1 is untouched, it reads one larger
                set. Use it when the posting does not say a word you know the
                work is about.

                Its own field and not part of the posting, because the
                analysis of a posting is cached by its hash and shared between
                everyone who pastes it; a directive belongs to one person and
                one run.

                Stored trimmed and lowercased. At most ten, each at most 60
                characters -- past that the ranking would be the reader's list
                rather than the posting's.""",
                example = "[\"microservices\", \"observability\"]")
        @Size(max = 10)
        List<@Size(max = 60) String> emphasize,

        @Schema(description = """
                A set of appearance settings saved under
                `/api/v1/customizations`, to render this one with
                (Bolum 14.4). Absent uses the profile's own working settings,
                which is what nearly every request means.

                A set belonging to somebody else is not found.""")
        java.util.UUID customizationId,

        @Schema(description = """
                A sentence or two about how this CV should read, in the
                person's own words (Bolum 18.7's `freeformNote`).

                It reaches Faz D and nothing else: Faz B ranks against the
                posting and a sentence is not a term. The prompt tells the
                model the note may steer wording and emphasis and may **not**
                licence a claim, lengthen a line past its maximum, or change
                what a sentence says happened — and Bolum 21.6's validators do
                not care what the note said either way, which is what makes
                that a promise rather than a hope.

                It travels inside the fence, because it is the person's own
                content (Bolum 43.1). At most 500 characters.""",
                example = "Lead with the platform work rather than the ML.")
        @Size(max = 500)
        String note) {

    /** Never null downstream: absent and empty mean the same thing here. */
    @JsonIgnore
    @io.swagger.v3.oas.annotations.media.Schema(hidden = true)
    public List<String> emphasizeOrEmpty() {
        return emphasize == null ? List.of() : emphasize;
    }

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
