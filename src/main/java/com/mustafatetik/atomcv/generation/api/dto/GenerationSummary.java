package com.mustafatetik.atomcv.generation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.GenerationStatus;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.validation.MatchLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the history (F-020, Bolum 41.2).
 *
 * <p><strong>Deliberately not {@link GenerationResponse}.</strong> A list of
 * twenty full generations would carry twenty fit reports and twenty covering
 * letters to draw twenty lines of a menu — and the letter is the largest text
 * the row holds. What is here is what a row of a list is read for: when it was
 * made, whether it worked, how long it came out, and how well it matched.
 *
 * <p><strong>Nothing the user pasted, and the two labels are not an
 * exception to that.</strong> The posting stays on the row (absolute rule 4),
 * which is the rule {@link GenerationResponse} keeps too. What travels is the
 * role and the company Faz A read out of it — enough to tell one row from
 * another, and not the posting. Bolum 57 draws that line explicitly, because
 * an exception with no stated end invites the next field in on the same
 * argument.
 *
 * <p>The row needed them. Without a label it says "one page · a date · strong"
 * and nothing else, which answers no question somebody with ten generations
 * opens a history to ask (F-022). A date does not distinguish two applications
 * made the same afternoon, and asking the person to name each generation
 * themselves is work nobody with ten of them does.
 *
 * @param roleTitle      absent in general mode, and absent when Faz A found no
 *                       title in the posting. An empty string would make a row
 *                       look as though it said something
 * @param companyName    absent for the same two reasons, and independently of
 *                       {@code roleTitle} — a posting can name the work without
 *                       naming who is offering it
 * @param matchLevel     absent in general mode, where there was no posting to
 *                       be relevant to. Four characters, and the heading the
 *                       full report sits under
 * @param hasCoverLetter whether there is one to open, never the letter itself
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "One generation, as a row of the history")
public record GenerationSummary(
        UUID generationId,
        GenerationStatus status,
        Instant createdAt,

        @Schema(description = "How many pages the compiled document came to; "
                + "absent while it is unfinished or failed")
        Integer pageCount,

        @Schema(description = "The role the posting was for, as Faz A read it; "
                + "absent in general mode and when the posting named none",
                example = "Backend Engineer")
        String roleTitle,

        @Schema(description = "The company the posting was for, as Faz A read it; "
                + "absent in general mode and when the posting named none")
        String companyName,

        @Schema(description = "The heading over Faz F's counts, absent in general mode")
        MatchLevel matchLevel,

        @Schema(description = "The language the document was written in, as a BCP 47 tag",
                example = "tr")
        String contentLanguage,

        @Schema(description = "Whether a covering letter was written for it")
        boolean hasCoverLetter) {

    public static GenerationSummary of(Generation generation) {
        // Null in general mode; its two members are never null when it is not,
        // because JobAnalysis fills an absent one in rather than leaving the
        // question to every caller.
        JobAnalysis analysis = generation.getJdAnalysis();
        return new GenerationSummary(
                generation.getId(),
                generation.getStatus(),
                generation.getCreatedAt(),
                generation.getPageCount() == null ? null : generation.getPageCount().intValue(),
                blankToNull(analysis == null ? null : analysis.role().title()),
                blankToNull(analysis == null ? null : analysis.company().name()),
                generation.getFitReport() == null ? null : generation.getFitReport().level(),
                blankToNull(generation.getSelectionState() == null
                        ? null : generation.getSelectionState().language()),
                generation.getCoverLetter() != null && !generation.getCoverLetter().isBlank());
    }

    /**
     * Stored as "" rather than null; F-010 settled that "" is worse on the
     * wire. {@code JobAnalysis} normalises an absent title or company to "",
     * so this is the only thing standing between an empty label and a row
     * that appears to name something.
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
