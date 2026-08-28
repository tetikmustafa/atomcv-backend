package com.mustafatetik.atomcv.identity.api;

import com.mustafatetik.atomcv.identity.service.AccountDeletionService;
import com.mustafatetik.atomcv.identity.service.SessionCookies;
import com.mustafatetik.atomcv.shared.error.ApiErrorResponse;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The account, and ending it (Bolum 57.4).
 *
 * <p><strong>No confirmation field, and that is a decision rather than an
 * omission.</strong> This is a {@code DELETE} behind a session cookie and a
 * CSRF token, so nothing another site can do reaches it, and a client cannot
 * arrive here by following a link. The "are you sure" belongs on the screen
 * that has room to say what is about to be lost; a second copy of it in the
 * request body would be a checkbox the API cannot enforce the meaning of.
 */
@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account", description = "The account itself")
public class AccountController {

    private final CurrentUser currentUser;
    private final AccountDeletionService deletion;
    private final SessionCookies cookies;

    AccountController(CurrentUser currentUser, AccountDeletionService deletion,
            SessionCookies cookies) {

        this.currentUser = currentUser;
        this.deletion = deletion;
        this.cookies = cookies;
    }

    @Operation(
            summary = "Delete this account and everything in it",
            description = """
                    Immediate and irreversible. The profile, its atoms and \
                    their embeddings, every generation and its stored \
                    document, the queued jobs, the counters and the email \
                    preferences all go with the account, and every session \
                    signed into it stops working at once.

                    Two things deliberately survive, and neither identifies \
                    anybody afterwards. Cost history keeps its rows with the \
                    user link cut, because a month's spend is not personal \
                    data once it points at nobody. And an address that hard \
                    bounced or complained stays on the suppression list, \
                    because that record is what stops the product mailing it \
                    again — it belongs to the address, not to the account.

                    LLM providers may hold their own short-term logs on their \
                    side; that is on the privacy policy, and it is not \
                    something this call can reach.

                    Answers 204 whether or not the account was still there: a \
                    second press is the same answer as the first.""")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Gone"),
            @ApiResponse(responseCode = "401",
                    description = "AUTHENTICATION_REQUIRED — no account to delete",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @DeleteMapping
    public ResponseEntity<Void> delete() {
        deletion.delete(currentUser.require());

        // The cookie goes with the row. Leaving it set would point a browser
        // at a session that no longer resolves, and every screen it opened
        // would be a failure rather than a signed-out one.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }
}
