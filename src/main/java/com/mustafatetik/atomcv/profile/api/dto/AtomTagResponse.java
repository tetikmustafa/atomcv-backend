package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.TagSource;
import com.mustafatetik.atomcv.profile.repository.AtomTagRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One tag on one atom (Bolum 13, Bolum 35.2).
 *
 * <p>The id is here because removing a tag names it, and the label is
 * canonical because that is the form the column holds and the form Faz B
 * compares against a posting (Bolum 19.2) — an editor that showed the typed
 * spelling would show something the scorer never sees.
 *
 * @param source who put it there. The extraction guesses (`auto`); a person
 *               decides (`user`). The editor draws them differently because
 *               they mean different things about how much to trust the tag
 */
@Schema(description = "A tag on an atom")
public record AtomTagResponse(UUID id, String label, TagSource source) {

    public static AtomTagResponse of(AtomTagRow row) {
        return new AtomTagResponse(row.tagId(), row.label(), row.source());
    }
}
