package com.mustafatetik.atomcv.tracking.api;

import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.util.EntityTags;
import com.mustafatetik.atomcv.tracking.api.dto.ApplicationCreateRequest;
import com.mustafatetik.atomcv.tracking.api.dto.ApplicationResponse;
import com.mustafatetik.atomcv.tracking.api.dto.ApplicationUpdateRequest;
import com.mustafatetik.atomcv.tracking.domain.Application;
import com.mustafatetik.atomcv.tracking.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
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
 * Where somebody applied, and what happened (Bolum 55, Bolum 35.3).
 *
 * <p>Four verbs over one table, and the least clever endpoint in this
 * application: it records what a person tells it. Nothing here forbids a
 * transition — a company that reopens a closed process is not a data error,
 * and a tracker that argued with its user about what happened to them would be
 * worse than one that believed them.
 *
 * <p><strong>Account only.</strong> An anonymous session has no history to
 * keep and nothing to keep it against; the rows are keyed to a user and the
 * scoped repository is the whole of the IDOR defence.
 *
 * <p>Not paginated. A person applies to tens of jobs and not thousands, and a
 * cursor would be machinery for a page that does not exist.
 */
@RestController
@RequestMapping("/api/v1/applications")
@Tag(name = "Applications", description = "Keeping track of where a CV went")
public class ApplicationController {

    private final CurrentUser currentUser;
    private final ApplicationService applications;

    ApplicationController(CurrentUser currentUser, ApplicationService applications) {
        this.currentUser = currentUser;
        this.applications = applications;
    }

    @Operation(
            operationId = "listApplications",
            summary = "Every application, newest first",
            description = """
                    Not paginated: this is a table somebody scans, not a feed \
                    they scroll.

                    A row whose `generationId` is null is one whose CV has \
                    been deleted — the record of applying survives the \
                    document. Do not offer a download for those.""")
    @ApiResponse(responseCode = "200", description = "The whole list")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ApplicationResponse>> list() {
        UserContext user = currentUser.require();

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(applications.list(user).stream().map(ApplicationResponse::of).toList());
    }

    @Operation(
            operationId = "createApplication",
            summary = "Record an application",
            description = """
                    `status` omitted means `applied` and `appliedAt` omitted \
                    means today, which is what somebody who has just pressed \
                    the button means.

                    `generationId` must be one of your own generations. \
                    Somebody else's is a 400 rather than a 404: the field is \
                    wrong rather than the row missing.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Recorded",
                    headers = @Header(name = HttpHeaders.ETAG,
                            description = EntityTags.HEADER_DESCRIPTION,
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED — a missing field, or a "
                            + "`generationId` that is not yours",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody ApplicationCreateRequest request) {

        UserContext user = currentUser.require();
        Application created = applications.create(user, request.company(), request.position(),
                request.generationId(), request.status(), request.appliedAt(), request.notes());

        return ResponseEntity.created(URI.create("/api/v1/applications/" + created.getId()))
                .eTag(EntityTags.of(created.getVersion() == null ? 0L : created.getVersion()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApplicationResponse.of(created));
    }

    @Operation(
            operationId = "updateApplication",
            summary = "Change an application",
            description = """
                    A partial edit: omitting a field leaves it alone, so a \
                    screen moving one row from applied to interview does not \
                    have to send the notes back and risk overwriting an edit \
                    made in another tab.

                    Emptying the notes needs `clearNotes: true` — null cannot \
                    mean both "leave them" and "empty them".

                    `If-Match` is required (Bolum 35.6).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Changed",
                    headers = @Header(name = HttpHeaders.ETAG,
                            description = EntityTags.HEADER_DESCRIPTION,
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "404", description = "No such application, or not yours",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "412",
                    description = "VERSION_CONFLICT — somebody changed it first",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "428",
                    description = "PRECONDITION_REQUIRED — no `If-Match` was sent",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping(path = "/{applicationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApplicationResponse> update(
            @PathVariable UUID applicationId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ApplicationUpdateRequest request) {

        UserContext user = currentUser.require();
        Application changed = applications.update(user, applicationId, ifMatch,
                request.company(), request.position(), request.generationId(),
                request.status(), request.appliedAt(), request.notes(), request.clearNotes());

        return ResponseEntity.ok()
                .eTag(EntityTags.of(changed.getVersion() == null ? 0L : changed.getVersion()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApplicationResponse.of(changed));
    }

    @Operation(
            operationId = "deleteApplication",
            summary = "Forget an application",
            description = """
                    Guarded by `If-Match` like an edit, and for the same \
                    reason: a row deleted from a stale screen is a row \
                    another tab had just changed.

                    The CV is untouched. This forgets the record of applying, \
                    not the document.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Gone"),
            @ApiResponse(responseCode = "404", description = "No such application, or not yours",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "412",
                    description = "VERSION_CONFLICT — somebody changed it first",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping(path = "/{applicationId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID applicationId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {

        applications.delete(currentUser.require(), applicationId, ifMatch);
        return ResponseEntity.noContent().build();
    }
}
