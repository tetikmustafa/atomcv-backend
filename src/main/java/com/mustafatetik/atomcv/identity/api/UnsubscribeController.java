package com.mustafatetik.atomcv.identity.api;

import com.mustafatetik.atomcv.identity.service.LifecyclePreference;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Turning the optional post off from an inbox (Bolum 57.7).
 *
 * <p><strong>A POST, and the link in the email is not this.</strong> The link
 * lands on a page that carries a button, which then calls here. Bolum 40.3 is
 * the reason: corporate gateways fetch every URL in a message before anyone
 * reads it, and an unsubscribe that acted on a fetch would switch off email
 * for people who never clicked.
 *
 * <p><strong>In the identity slice, not the email one</strong>, and the cycle
 * rule is what said so: identity already depends on email to send the magic
 * link, so a controller in email reaching back for an account closed the loop
 * and the architecture test failed. The path still reads {@code /email} —
 * a package is not a URL, and from an inbox this is what the reader expects.
 *
 * <p>No session, because there is none in an inbox. The token is the whole
 * credential, which is why it is random rather than derived from anything
 * visible, and why all it can do is set one boolean on its own row.
 */
@RestController
@RequestMapping("/api/v1/email")
public class UnsubscribeController {

    private final LifecyclePreference preference;

    UnsubscribeController(LifecyclePreference preference) {
        this.preference = preference;
    }

    /** What the page sends: the token out of the link, and nothing else. */
    public record UnsubscribeRequest(@NotNull UUID token) {
    }

    @Operation(operationId = "unsubscribe", summary = "Stop the optional emails for the account this token belongs to")
    @ApiResponses(@ApiResponse(responseCode = "204",
            description = "Done, or there was no such token — the answer is the same"))
    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody UnsubscribeRequest request) {
        // The same answer either way, for Bolum 40.4's reason in a smaller
        // place: a different response for an unknown token would turn this
        // into an oracle for whether a token is live.
        preference.stop(request.token());
        return ResponseEntity.noContent().build();
    }
}
