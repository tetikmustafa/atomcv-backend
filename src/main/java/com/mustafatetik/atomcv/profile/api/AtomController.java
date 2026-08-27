package com.mustafatetik.atomcv.profile.api;

import com.mustafatetik.atomcv.profile.api.dto.AtomCreateRequest;
import com.mustafatetik.atomcv.profile.api.dto.AtomPatchRequest;
import com.mustafatetik.atomcv.profile.api.dto.AtomReorderRequest;
import com.mustafatetik.atomcv.profile.api.dto.AtomResponse;
import com.mustafatetik.atomcv.profile.api.dto.VariantPatchRequest;
import com.mustafatetik.atomcv.profile.api.dto.VariantRequest;
import com.mustafatetik.atomcv.profile.api.dto.VariantResponse;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.service.AtomDraft;
import com.mustafatetik.atomcv.profile.service.AtomPatch;
import com.mustafatetik.atomcv.profile.service.AtomService;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.profile.service.VariantDraft;
import com.mustafatetik.atomcv.profile.service.VariantPatch;
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
import java.util.Map;
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
 * Atoms and their wordings (Bolum 35.2).
 *
 * <p>The atom endpoints carry the controls; the variant endpoints carry the
 * text. They are separate because they are separate rows with separate
 * versions — editing a sentence and pinning an atom must not share one
 * precondition.
 */
@RestController
@RequestMapping("/api/v1/profile/atoms")
@Tag(name = "Atoms", description = "The smallest independently selectable unit of a profile")
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
public class AtomController {

    private final CurrentUser currentUser;
    private final ProfileResolver profiles;
    private final AtomService atoms;

    AtomController(CurrentUser currentUser, ProfileResolver profiles, AtomService atoms) {
        this.currentUser = currentUser;
        this.profiles = profiles;
        this.atoms = atoms;
    }

    @Operation(operationId = "listAtoms", summary = "List atoms with their wordings",
            description = "Unpaginated: a profile holds tens to a few hundred atoms "
                    + "and the editor loads all of them (EK D.6.2).")
    @ApiResponse(responseCode = "200", description = "Every matching atom, in display order",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = AtomResponse.class))))
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AtomResponse> list(
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) UUID entryId) {

        ProfileRef profile = profile();
        Map<UUID, List<AtomVariant>> byAtom = atoms.variantsByAtom(profile);
        return atoms.list(profile, sectionId, entryId).stream()
                .map(atom -> AtomResponse.of(atom, wordings(byAtom, atom.getId())))
                .toList();
    }

    @Operation(operationId = "createAtom", summary = "Add an atom and its first wording",
            description = "Content is required: an atom with no wording is a fact nobody "
                    + "can read, and nothing downstream can render or measure it.")
    @ApiResponse(responseCode = "201", description = "Created",
            headers = @Header(name = "ETag", description = EntityTags.HEADER_DESCRIPTION,
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AtomResponse.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AtomResponse> create(@Valid @RequestBody AtomCreateRequest request) {
        ProfileRef profile = profile();
        Atom created = atoms.create(profile, new AtomDraft(
                request.sectionId(),
                request.entryId(),
                request.kind(),
                request.content().toRichContent(),
                request.language(),
                request.importance() == null ? 0.5f : request.importance(),
                Boolean.TRUE.equals(request.alwaysInclude()),
                Boolean.TRUE.equals(request.verbatim()),
                request.skills(),
                request.metrics(),
                request.properNouns()));

        return ResponseEntity.status(201)
                .eTag(EntityTags.of(created.getVersion()))
                .body(AtomResponse.of(created, variantsOf(profile, created.getId())));
    }

    @Operation(operationId = "patchAtom", summary = "Change an atom's controls",
            description = "Importance, locks, verification and the scoring inputs. Text lives "
                    + "on a wording. Requires If-Match.")
    @ApiResponse(responseCode = "200", description = "The atom as it now stands",
            headers = @Header(name = "ETag", description = EntityTags.HEADER_DESCRIPTION,
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AtomResponse.class)))
    @PatchMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AtomResponse> patch(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody AtomPatchRequest request) {

        ProfileRef profile = profile();
        Atom patched = atoms.patch(profile, id, ifMatch, new AtomPatch(
                request.kind(),
                request.importance(),
                request.active(),
                request.alwaysInclude(),
                request.verbatim(),
                request.verified(),
                request.skills(),
                request.metrics(),
                request.properNouns()));

        return ResponseEntity.ok()
                .eTag(EntityTags.of(patched.getVersion()))
                .body(AtomResponse.of(patched, variantsOf(profile, patched.getId())));
    }

    @Operation(operationId = "deleteAtom", summary = "Delete an atom",
            description = "Takes its wordings with it.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {

        atoms.delete(profile(), id, ifMatch);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "reorderAtoms", summary = "Put one group of atoms in this order",
            description = "The atoms of an entry, or the ones hanging straight off a section.")
    @ApiResponse(responseCode = "200", description = "That group's atoms, in the new order",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = AtomResponse.class))))
    @PostMapping(path = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AtomResponse> reorder(@Valid @RequestBody AtomReorderRequest request) {
        ProfileRef profile = profile();
        Map<UUID, List<AtomVariant>> byAtom = atoms.variantsByAtom(profile);
        return atoms.reorder(profile, request.sectionId(), request.entryId(), request.ids()).stream()
                .map(atom -> AtomResponse.of(atom, wordings(byAtom, atom.getId())))
                .toList();
    }

    // ── wordings ──────────────────────────────────────────────────────────

    @Operation(operationId = "addVariant", summary = "Add a wording",
            description = "One wording per language and tone; a second one for the same pair "
                    + "is refused rather than left to a database constraint.")
    @ApiResponse(responseCode = "201", description = "Created",
            headers = @Header(name = "ETag", description = EntityTags.HEADER_DESCRIPTION,
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = VariantResponse.class)))
    @PostMapping(path = "/{id}/variants", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VariantResponse> addVariant(
            @PathVariable UUID id, @Valid @RequestBody VariantRequest request) {

        AtomVariant created = atoms.addVariant(profile(), id, draftOf(request));
        return ResponseEntity.status(201)
                .eTag(EntityTags.of(created.getVersion()))
                .body(VariantResponse.of(created));
    }

    @Operation(operationId = "patchVariant", summary = "Change a wording",
            description = "Content is optional: making a wording the default is not a text "
                    + "edit and does not need the sentence back. When content is sent it is "
                    + "the whole of it, not a run at a time, and changing the words clears the "
                    + "measured render costs — the same sentence is what made them true. "
                    + "Requires If-Match.")
    @ApiResponse(responseCode = "200", description = "The wording as it now stands",
            headers = @Header(name = "ETag", description = EntityTags.HEADER_DESCRIPTION,
                    schema = @Schema(type = "string")),
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = VariantResponse.class)))
    @PatchMapping(path = "/{id}/variants/{variantId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VariantResponse> patchVariant(
            @PathVariable UUID id,
            @PathVariable UUID variantId,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody VariantPatchRequest request) {

        AtomVariant patched = atoms.patchVariant(
                profile(), currentUser.require(), id, variantId, ifMatch,
                new VariantPatch(
                        request.content() == null ? null : request.content().toRichContent(),
                        request.language(),
                        request.tone(),
                        request.primary()));

        return ResponseEntity.ok()
                .eTag(EntityTags.of(patched.getVersion()))
                .body(VariantResponse.of(patched));
    }

    @Operation(operationId = "deleteVariant", summary = "Delete a wording",
            description = "Not the last one, and not the primary one while others remain: "
                    + "an atom has to keep a wording, and a default among them.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{id}/variants/{variantId}")
    public ResponseEntity<Void> deleteVariant(
            @PathVariable UUID id,
            @PathVariable UUID variantId,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {

        atoms.deleteVariant(profile(), id, variantId, ifMatch);
        return ResponseEntity.noContent().build();
    }

    private static VariantDraft draftOf(VariantRequest request) {
        return new VariantDraft(
                request.content().toRichContent(),
                request.language(),
                request.tone(),
                request.primary());
    }

    private List<VariantResponse> variantsOf(ProfileRef profile, UUID atomId) {
        return atoms.variantsOf(profile, atomId).stream().map(VariantResponse::of).toList();
    }

    private static List<VariantResponse> wordings(
            Map<UUID, List<AtomVariant>> byAtom, UUID atomId) {
        return byAtom.getOrDefault(atomId, List.of()).stream().map(VariantResponse::of).toList();
    }

    private ProfileRef profile() {
        return profiles.resolve(currentUser.require());
    }
}
