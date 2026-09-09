package com.mustafatetik.atomcv.profile.api;

import com.mustafatetik.atomcv.profile.api.dto.EntryCreateRequest;
import com.mustafatetik.atomcv.profile.api.dto.EntryPatchRequest;
import com.mustafatetik.atomcv.profile.api.dto.EntryReorderRequest;
import com.mustafatetik.atomcv.profile.api.dto.EntryResponse;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.service.EntryDraft;
import com.mustafatetik.atomcv.profile.service.EntryPatch;
import com.mustafatetik.atomcv.profile.service.EntryService;
import com.mustafatetik.atomcv.profile.service.CallerProfiles;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entries of the Master Profile (Bolum 35.2).
 *
 * <p>The resource map lists no {@code GET} for entries; without one the editor
 * cannot render an experience list at all, so one is added here (EK D.6.2).
 */
@RestController
@RequestMapping("/api/v1/profile/entries")
@Tag(name = "Entries", description = "Positions, degrees and projects")
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
public class EntryController {

    private final CallerProfiles callers;
    private final EntryService entries;

    EntryController(CallerProfiles callers, EntryService entries) {
        this.callers = callers;
        this.entries = entries;
    }

    @Operation(operationId = "listEntries", summary = "List entries, optionally within one section")
    @ApiResponse(responseCode = "200", description = "Every matching entry, in display order",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = EntryResponse.class))))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<EntryResponse> list(@RequestParam(required = false) UUID sectionId) {
        return entries.list(profile(), sectionId).stream().map(EntryResponse::of).toList();
    }

    @Operation(operationId = "createEntry", summary = "Add an entry at the end of its section")
    @ApiResponse(responseCode = "201", description = "Created",
            headers = @Header(name = "ETag", description = EntityTags.HEADER_DESCRIPTION,
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = EntryResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EntryResponse> create(@Valid @RequestBody EntryCreateRequest request) {
        Entry created = entries.create(profile(), new EntryDraft(
                request.sectionId(),
                request.title(),
                request.organization(),
                request.location(),
                request.startDate(),
                request.endDate(),
                request.url(),
                request.importance() == null ? 0.5f : request.importance(),
                Boolean.TRUE.equals(request.alwaysInclude()),
                Boolean.TRUE.equals(request.verbatim()),
                request.minAtoms() == null ? Entry.DEFAULT_MIN_ATOMS : request.minAtoms()));

        return ResponseEntity.status(201)
                .eTag(EntityTags.of(created.getVersion()))
                .body(EntryResponse.of(created));
    }

    @Operation(operationId = "patchEntry", summary = "Change part of an entry", description = """
            A field left out is left alone; a nullable field sent as null is \
            cleared. Requires If-Match.""")
    @ApiResponse(responseCode = "200", description = "The entry as it now stands",
            headers = @Header(name = "ETag", description = EntityTags.HEADER_DESCRIPTION,
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = EntryResponse.class)))
    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EntryResponse> patch(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody EntryPatchRequest request) {

        Entry patched = entries.patch(profile(), id, ifMatch, new EntryPatch(
                request.title(),
                request.organization(),
                request.location(),
                request.startDate(),
                request.endDate(),
                request.url(),
                request.importance(),
                request.active(),
                request.alwaysInclude(),
                request.verbatim(),
                request.minAtoms()));

        return ResponseEntity.ok()
                .eTag(EntityTags.of(patched.getVersion()))
                .body(EntryResponse.of(patched));
    }

    @Operation(operationId = "deleteEntry", summary = "Delete an entry",
            description = "Takes its atoms and their variants with it. Requires If-Match.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {

        entries.delete(profile(), id, ifMatch);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "reorderEntries", summary = "Put one section's entries in this order",
            description = "The list must name every entry of that section.")
    @ApiResponse(responseCode = "200", description = "That section's entries, in the new order",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = EntryResponse.class))))
    @PostMapping(path = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<EntryResponse> reorder(@Valid @RequestBody EntryReorderRequest request) {
        return entries.reorder(profile(), request.sectionId(), request.ids()).stream()
                .map(EntryResponse::of)
                .toList();
    }

    /**
     * <strong>Account or anonymous session, and this endpoint does not know
     * which.</strong> It asks for the profile of whoever is calling and gets a
     * scope back; the scope is what says whether § 35.7's limits apply, which is
     * why nothing here has to check.
     */
    private ProfileRef profile() {
        return callers.ref();
    }
}
