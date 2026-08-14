package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Profile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The profile head as the API publishes it.
 *
 * <p>No identifier: no endpoint accepts a profile id, because ownership comes
 * from the session and never from a path (Bolum 35.1). No version field
 * either — a single resource carries its version in the {@code ETag}, and the
 * two could disagree.
 */
@Schema(name = "Profile", description = "The head of the Master Profile")
public record ProfileResponse(

        @Schema(description = "One line under the name", example = "Backend Engineer")
        String headline,

        Contact contact,

        @Schema(description = "Free text the user writes about themselves")
        String selfDescription,

        Preferences preferences,

        @Schema(description = "The language the profile is authored in", example = "en")
        String sourceLanguage,

        @Schema(description = "Languages this profile can be rendered in")
        List<String> enabledLanguages,

        @Schema(description = "How complete the profile is, 0-100", example = "0")
        int completeness) {

    public static ProfileResponse of(Profile profile) {
        return new ProfileResponse(
                profile.getHeadline(),
                profile.getContact(),
                profile.getSelfDescription(),
                profile.getPreferences(),
                profile.getSourceLanguage(),
                profile.getEnabledLanguages(),
                profile.getCompleteness());
    }
}
