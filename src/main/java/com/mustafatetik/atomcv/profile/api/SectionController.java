package com.mustafatetik.atomcv.profile.api;

import com.mustafatetik.atomcv.profile.api.dto.ReorderRequest;
import com.mustafatetik.atomcv.profile.api.dto.SectionCreateRequest;
import com.mustafatetik.atomcv.profile.api.dto.SectionPatchRequest;
import com.mustafatetik.atomcv.profile.api.dto.SectionResponse;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.profile.service.SectionDraft;
import com.mustafatetik.atomcv.profile.service.SectionPatch;
import com.mustafatetik.atomcv.profile.service.SectionService;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.util.EntityTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sections of the Master Profile (Bolum 35.2).
 *
 * <p>The collection is not paginated: a profile has a handful of sections, and
 * the editor loads all of them anyway (EK D.6.2).
 */
@RestController
@RequestMapping("/api/v1/profile/sections")
@Tag(name = "Sections", description = "The headings a profile is organised under")
@ApiResponses({
        @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "412", description = "VERSION_CONFLICT",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "428", description = "PRECONDITION_REQUIRED",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class SectionController {

    private final CurrentUser currentUser;
    private final ProfileResolver profiles;
    private final SectionService sections;

    SectionController(CurrentUser currentUser, ProfileResolver profiles, SectionService sections) {
        this.currentUser = currentUser;
        this.profiles = profiles;
        this.sections = sections;
    }

    @Operation(operationId = "listSections", summary = "List the sections in display order",
            description = "Every item carries its own version, so editing one needs no extra read.")
    @ApiResponse(responseCode = "200", description = "Every section, primary order",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = SectionResponse.class))))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SectionResponse> list() {
        return sections.list(profile()).stream().map(SectionResponse::of).toList();
    }

    @Operation(operationId = "createSection", summary = "Add a section at the end")
    @ApiResponse(responseCode = "201", description = "Created",
            headers = @Header(name = "ETag", description = EntityTags.HEADER_DESCRIPTION, schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SectionResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SectionResponse> create(@Valid @RequestBody SectionCreateRequest request) {
        Section created = sections.create(profile(), new SectionDraft(
                request.kind(),
                request.title(),
                request.layout(),
                Boolean.TRUE.equals(request.alwaysInclude()),
                Boolean.TRUE.equals(request.verbatim())));

        return ResponseEntity.status(201)
                .eTag(EntityTags.of(created.getVersion()))
                .body(SectionResponse.of(created));
    }

    @Operation(operationId = "patchSection", summary = "Change part of a section",
            description = "A field left out is left alone. Requires If-Match.")
    @ApiResponse(responseCode = "200", description = "The section as it now stands",
            headers = @Header(name = "ETag", description = EntityTags.HEADER_DESCRIPTION,
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SectionResponse.class)))
    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SectionResponse> patch(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody SectionPatchRequest request) {

        Section patched = sections.patch(profile(), id, ifMatch, new SectionPatch(
                request.kind(),
                request.title(),
                request.layout(),
                request.alwaysInclude(),
                request.verbatim(),
                request.active()));

        return ResponseEntity.ok()
                .eTag(EntityTags.of(patched.getVersion()))
                .body(SectionResponse.of(patched));
    }

    @Operation(operationId = "deleteSection", summary = "Delete a section",
            description = """
                    Takes its entries, atoms and variants with it. Requires If-Match, \
                    so a section someone else has changed since you read it is not \
                    removed on stale information.""")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {

        sections.delete(profile(), id, ifMatch);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "reorderSections", summary = "Put the sections in this order",
            description = """
                    The list must name every section. A partial one would leave the \
                    rest to be guessed; sending all of them also makes the call \
                    idempotent.""")
    @ApiResponse(responseCode = "200", description = "Every section, in the new order",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = SectionResponse.class))))
    @PostMapping(path = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<SectionResponse> reorder(@Valid @RequestBody ReorderRequest request) {
        return sections.reorder(profile(), request.ids()).stream()
                .map(SectionResponse::of)
                .toList();
    }

    private ProfileRef profile() {
        return profiles.resolve(currentUser.require());
    }
}
