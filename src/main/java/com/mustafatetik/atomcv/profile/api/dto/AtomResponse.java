package com.mustafatetik.atomcv.profile.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * An atom with its wordings.
 *
 * <p>The controls and the text are separate resources — an atom carries the
 * facts about a fact, its variants carry the sentences — but they are read
 * together, because an atom with no text on screen is nothing to look at.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Atom")
public record AtomResponse(
        UUID id,
        UUID sectionId,
        @Schema(description = "Absent when the atom hangs straight off the section")
        UUID entryId,
        AtomKind kind,
        short displayOrder,
        @Schema(description = "Weight in scoring, 0 to 1", example = "0.5") float importance,
        boolean active,
        @Schema(description = "Selection may not drop it") boolean alwaysInclude,
        @Schema(description = "Never sent for rewriting") boolean verbatim,
        @Schema(description = "Canonical skill names, used for matching") List<String> skills,
        @Schema(description = "Numbers a rewrite may not lose") List<String> metrics,
        @Schema(description = "Names a rewrite may not invent or alter") List<String> properNouns,
        AtomSource source,
        @Schema(description = "The user has confirmed the fact") boolean verified,
        @Schema(description = "Every wording, primary first") List<VariantResponse> variants,
        @Schema(description = "Send back as If-Match", example = "0") long version) {

    public static AtomResponse of(Atom atom, List<VariantResponse> variants) {
        return new AtomResponse(
                atom.getId(),
                atom.getSectionId(),
                atom.getEntryId(),
                atom.getKind(),
                atom.getDisplayOrder(),
                atom.getImportance(),
                atom.isActive(),
                atom.isAlwaysInclude(),
                atom.isVerbatim(),
                atom.getSkills(),
                atom.getMetrics(),
                atom.getProperNouns(),
                atom.getSource(),
                atom.isVerified(),
                variants,
                atom.getVersion() == null ? 0L : atom.getVersion());
    }
}
