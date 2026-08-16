package com.mustafatetik.atomcv.profile.api;

import com.mustafatetik.atomcv.profile.api.dto.PreferencesUpdateRequest;
import com.mustafatetik.atomcv.profile.api.dto.ProfileExport;
import com.mustafatetik.atomcv.profile.api.dto.ProfileResponse;
import com.mustafatetik.atomcv.profile.api.dto.ProfileUpdateRequest;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.service.ProfileHeadUpdate;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.profile.service.ProfileExporter;
import com.mustafatetik.atomcv.profile.service.ProfileService;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import com.mustafatetik.atomcv.shared.util.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Master Profile head (Bolum 35.2). */
@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile", description = "The user's structured professional data")
public class ProfileController {

    private final CurrentUser currentUser;
    private final ProfileResolver profiles;
    private final ProfileService service;
    private final ProfileExporter exporter;

    ProfileController(CurrentUser currentUser, ProfileResolver profiles, ProfileService service,
            ProfileExporter exporter) {
        this.currentUser = currentUser;
        this.profiles = profiles;
        this.service = service;
        this.exporter = exporter;
    }

    @Operation(
            summary = "Read the profile head",
            description = """
                    Never answers 404. A user has exactly one profile, so an account \
                    that has none yet gets an empty one created on the spot — the client \
                    has no "not created yet" state to carry.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The profile head",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProfileResponse.class)),
                    headers = @Header(name = "ETag",
                            // Swagger parses `example` as JSON where it can, so a
                            // quoted value there loses its quotes. The header is
                            // an RFC 9110 entity tag: the quotes are part of it.
                            description = "Current version as a quoted number, "
                                    + "for If-Match on writes. Sent as: \"7\"",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "500", description = "Unexpected failure",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfileResponse> own() {
        return respond(service.readOwn(currentUser.require()));
    }

    @Operation(summary = "Export the whole profile",
            description = """
                    `?format=json` gives a nested copy in the shapes this API already \
                    publishes; `?format=markdown` gives the same content to read. \
                    Both are served as a download.""")
    @ApiResponses({
            // Both media types, because the endpoint really answers with both.
            // Declaring only the first makes a generated client parse markdown
            // as JSON and throw on the first character (EK D.6.4).
            @ApiResponse(responseCode = "200", description = "The profile as a file",
                    content = {
                            @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ProfileExport.class)),
                            @Content(mediaType = "text/markdown",
                                    schema = @Schema(type = "string"))}),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED — unknown format",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping(path = "/export", produces = {MediaType.APPLICATION_JSON_VALUE, "text/markdown"})
    public ResponseEntity<?> export(
            @Parameter(description = "json or markdown", example = "json")
            @RequestParam(defaultValue = "json") String format) {

        var user = currentUser.require();
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> attachment("json", MediaType.APPLICATION_JSON)
                    .body(exporter.export(user));
            // The charset is stated, not left to be guessed: without it a
            // client falls back to ISO-8859-1 and "İstanbul" arrives broken.
            case "markdown" -> attachment("md", MediaType.valueOf("text/markdown;charset=UTF-8"))
                    .body(exporter.exportAsMarkdown(user));
            default -> throw new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                    .param("fields", List.of("format"))
                    .build());
        };
    }

    /**
     * The filename carries a date and nothing else. A name in it would put
     * personal data into download folders, proxy logs and screenshots for no
     * gain (absolute rule 4).
     */
    private static ResponseEntity.BodyBuilder attachment(String extension, MediaType type) {
        String filename = "atomcv-profile-" + LocalDate.now() + "." + extension;
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
    }

    @Operation(summary = "Delete the profile and everything under it",
            description = """
                    Sections, entries, atoms and wordings go with it. The account \
                    stays: the next read gives an empty profile back. Requires \
                    If-Match — this is the one call that cannot be undone.""")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping
    public ResponseEntity<Void> delete(
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {

        service.delete(currentUser.require(), ifMatch);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Replace the profile head",
            description = """
                    Requires `If-Match`. A field left out is cleared — this replaces \
                    the head rather than patching it. Preferences are not part of it \
                    and have their own endpoint.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The head as it now stands",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProfileResponse.class)),
                    headers = @Header(name = "ETag", description = "The new version",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "412", description = "VERSION_CONFLICT — someone saved first",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "428", description = "PRECONDITION_REQUIRED — no If-Match",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfileResponse> replace(
            @Parameter(description = "The version last read, quoted", example = "\"7\"")
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ProfileUpdateRequest request) {

        return respond(service.replace(currentUser.require(), ifMatch, new ProfileHeadUpdate(
                request.headline(),
                request.contactOrEmpty(),
                request.selfDescription(),
                request.sourceLanguage(),
                request.enabledLanguages())));
    }

    @Operation(summary = "Replace the generation preferences", description = """
            Requires `If-Match`. Separate from the head so that editing a headline \
            cannot reset someone's writing style by omission.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The head as it now stands",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProfileResponse.class)),
                    headers = @Header(name = "ETag", description = "The new version",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "412", description = "VERSION_CONFLICT",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PutMapping(path = "/preferences", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfileResponse> replacePreferences(
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody PreferencesUpdateRequest request) {

        return respond(service.replacePreferences(
                currentUser.require(), ifMatch, request.toPreferences()));
    }

    private static ResponseEntity<ProfileResponse> respond(Profile profile) {
        return ResponseEntity.ok()
                .eTag(EntityTags.of(profile.getVersion() == null ? 0L : profile.getVersion()))
                .body(ProfileResponse.of(profile));
    }
}
