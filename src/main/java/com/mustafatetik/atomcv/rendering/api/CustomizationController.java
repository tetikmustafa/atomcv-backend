package com.mustafatetik.atomcv.rendering.api;

import com.mustafatetik.atomcv.profile.service.CallerProfiles;
import com.mustafatetik.atomcv.rendering.domain.SavedCustomization;
import com.mustafatetik.atomcv.rendering.service.CustomizationService;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.rendering.template.TemplateSummary;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Templates, and the appearance settings somebody named.
 *
 * <p><strong>Five endpoints the resource map has always listed and nothing
 * served.</strong> {@code template_customizations} has existed since V1 with
 * no writer; Layer B's sliders live in {@code profiles.preferences.appearance},
 * which is right for the one set a person is working with and cannot be
 * several. Bolum 33.2 is about several.
 *
 * <p><strong>No ETag on either</strong>. Templates are the registry's own
 * constants and change with a release, not with a request; a customization has
 * no {@code version} column and is a small whole object a person replaces
 * rather than a row two tabs edit a field of at once.
 */
@RestController
@Tag(name = "Templates", description = "How a CV is set, and the settings somebody kept")
@ApiResponses({
        @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND",
                content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class CustomizationController {

    private final CallerProfiles callers;
    private final CustomizationService customizations;

    CustomizationController(CallerProfiles callers, CustomizationService customizations) {
        this.callers = callers;
        this.customizations = customizations;
    }

    @Operation(
            operationId = "listTemplates",
            summary = "The templates a CV can be rendered with",
            description = """
                    The registry's own list, with the measured capacity of                     each: Bolum 33.5 describes the three by how much they hold,                     and a chooser showing three names and no density asks                     somebody to pick blind.

                    No display name and no description — those are sentences,                     and Bolum 35.4's rule is that the server sends a key and                     the client writes the sentence. The id is the key.

                    The same list `capabilities.allowedTemplates` publishes,                     which is what a client reads to know which of these it may                     offer. This one says what each of them is.""")
    @ApiResponse(responseCode = "200", description = "Every template in the registry",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = TemplateSummary.class))))
    @GetMapping(path = "/api/v1/templates", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TemplateSummary> templates() {
        // Sorted, because ids() is the key set of a Map.of and its iteration
        // order is salted per JVM run -- a chooser whose options reorder
        // between two page loads is a chooser nobody trusts (CLAUDE.md).
        return TemplateRegistry.ids().stream()
                .sorted()
                .map(TemplateSummary::of)
                .toList();
    }

    @Operation(operationId = "listCustomizations", summary = "The settings this profile kept",
            description = "Oldest first, which is the order somebody made them in.")
    @ApiResponse(responseCode = "200", description = "Every saved set",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(
                            schema = @Schema(implementation = CustomizationResponse.class))))
    @GetMapping(path = "/api/v1/customizations", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CustomizationResponse> list() {
        return customizations.list(profile()).stream()
                .sorted(Comparator.comparing(SavedCustomization::getCreatedAt))
                .map(CustomizationResponse::of)
                .toList();
    }

    @Operation(
            operationId = "createCustomization",
            summary = "Keep a set of appearance settings under a name",
            description = """
                    Bolum 33.2's Layer B, saved. The profile's own
                    `preferences.appearance` is still the working set and is
                    what a generation uses when it names nothing; this is for
                    the person who keeps a dense set for a long CV and a
                    roomier one for a short one.

                    Every value is bounded by Bolum 33.2's ranges, and the
                    ranges are why a bad page is not reachable from here.

                    At most twenty per profile, and names are unique within
                    one.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CustomizationResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED — an unknown template, a name already "
                            + "used, or one set too many",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(path = "/api/v1/customizations", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CustomizationResponse> create(
            @Valid @RequestBody CustomizationRequest request) {

        return ResponseEntity.status(201).body(CustomizationResponse.of(
                customizations.create(profile(), request.name(), request.toSettings())));
    }

    @Operation(operationId = "patchCustomization", summary = "Rename or re-set one",
            description = "The settings are replaced whole. Bolum 33.2's parameters are read "
                    + "together by the renderer, and a half-applied geometry is a page nobody "
                    + "asked for. Sending only a name renames it and leaves the settings.")
    @ApiResponse(responseCode = "200", description = "The set as it now stands",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CustomizationResponse.class)))
    @PatchMapping(path = "/api/v1/customizations/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public CustomizationResponse patch(
            @PathVariable UUID id, @Valid @RequestBody CustomizationPatch request) {

        return CustomizationResponse.of(customizations.update(
                profile(), id, request.name(), request.toSettingsOrNull()));
    }

    @Operation(operationId = "deleteCustomization", summary = "Forget one",
            description = "A generation already made with it is unaffected: Bolum 14.5 writes "
                    + "the settings themselves into the selection snapshot rather than an id, "
                    + "so a document can always be re-rendered exactly as it was sent.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/api/v1/customizations/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customizations.delete(profile(), id);
        return ResponseEntity.noContent().build();
    }

    /** Account or anonymous session; the scope is what decides what is allowed. */
    private ProfileRef profile() {
        return callers.ref();
    }
}
