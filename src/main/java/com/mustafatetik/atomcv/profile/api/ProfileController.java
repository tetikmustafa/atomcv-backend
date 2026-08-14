package com.mustafatetik.atomcv.profile.api;

import com.mustafatetik.atomcv.profile.api.dto.ProfileResponse;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Master Profile head (Bolum 35.2). */
@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile", description = "The user's structured professional data")
public class ProfileController {

    private final CurrentUser currentUser;
    private final ProfileResolver profiles;

    ProfileController(CurrentUser currentUser, ProfileResolver profiles) {
        this.currentUser = currentUser;
        this.profiles = profiles;
    }

    @Operation(
            summary = "Read the profile head",
            description = """
                    Never answers 404. A user has exactly one profile, so an account \
                    that has none yet gets an empty one created on the spot — the client \
                    has no "not created yet" state to carry.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The profile head",
                    headers = @Header(name = "ETag",
                            description = "Current version, for If-Match on writes",
                            schema = @Schema(type = "string", example = "\"7\""))),
            @ApiResponse(responseCode = "500", description = "Unexpected failure",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ProfileResponse> own() {
        Profile profile = profiles.own(currentUser.require());
        return ResponseEntity.ok()
                .eTag("\"" + profile.getVersion() + "\"")
                .body(ProfileResponse.of(profile));
    }
}
