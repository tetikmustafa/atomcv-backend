package com.mustafatetik.atomcv.generation.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A page of history, and how much of it there is (EK D.8.7 · Sayfalama).
 *
 * <p><strong>{@code items} and {@code nextCursor}</strong>, which is the shape
 * EK D.8.7 named for this list and {@code /applications}. Offset pagination
 * skips rows in a list that grows from the top, and this one grows from the
 * top every time somebody makes a CV.
 *
 * <p><strong>{@code total} is here for the one screen that cannot page.</strong>
 * F-020 asked for a listing or, failing that, a count, because the
 * account-deletion confirmation has to say what goes and would otherwise name
 * generations without numbering them. A count derived from walking the pages
 * would be a different number by the time the walk finished; this one is a
 * count.
 *
 * @param nextCursor absent on the last page, which is how a client knows to
 *                   stop — an empty {@code items} would be one page too late
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A page of a user's generations, newest first")
public record GenerationPage(

        List<GenerationSummary> items,

        @Schema(description = "Pass back as `cursor` for the next page. Opaque: "
                + "it is this server's to read and the client's to echo.")
        String nextCursor,

        @Schema(description = "How many generations this account has in total, "
                + "not how many are on this page")
        long total) {

    public static GenerationPage of(GenerationRepository.Page page, long total) {
        return new GenerationPage(
                page.items().stream().map(GenerationSummary::of).toList(),
                page.nextCursor() == null ? null : page.nextCursor().encode(),
                total);
    }
}
