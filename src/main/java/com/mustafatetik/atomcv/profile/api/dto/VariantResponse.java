package com.mustafatetik.atomcv.profile.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.VariantAuthor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** One wording of an atom. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Variant")
public record VariantResponse(
        UUID id,
        @Schema(description = "The wording used when nothing more specific is asked for")
        boolean primary,
        @Schema(example = "en") String language,
        @Schema(description = "Absent means the neutral register") Tone tone,
        ContentDto content,
        @Schema(description = "The same text with every mark removed") String plainText,
        @Schema(description = "sha256 of the plain text. Re-marking a sentence does not "
                + "change it, so it is what a 'needs re-measuring' hint should watch.")
        String contentHash,
        @Schema(description = "Who wrote it") VariantAuthor createdBy,
        @Schema(description = "The source has moved on; this wording needs regenerating")
        boolean stale,
        @Schema(description = "The person wrote this wording themselves. With `stale`, "
                + "it is the pair Bolum 32.2's warning is built from: the two have "
                + "diverged and nothing will regenerate this one behind their back.")
        boolean userEdited,
        @Schema(description = "Send back as If-Match", example = "0") long version) {

    public static VariantResponse of(AtomVariant variant) {
        return new VariantResponse(
                variant.getId(),
                variant.isPrimary(),
                variant.getLanguage(),
                variant.getTone(),
                ContentDto.of(variant.getContent()),
                variant.getPlainText(),
                variant.getContentHash(),
                variant.getCreatedBy(),
                variant.isStale(),
                variant.isUserEdited(),
                variant.getVersion() == null ? 0L : variant.getVersion());
    }
}
