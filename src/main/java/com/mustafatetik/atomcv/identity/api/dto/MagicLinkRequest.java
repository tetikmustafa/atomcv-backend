package com.mustafatetik.atomcv.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param email          where to send the link. Validated for shape only —
 *                       whether it has an account is the one thing the answer
 *                       must not depend on (Bolum 40.4)
 * @param challengeToken what the Turnstile widget produced (Bolum 44.4).
 *                       <strong>Optional here and required by the challenge
 *                       itself</strong>, which is not the same thing: a
 *                       deployment with no secret configured has no challenge
 *                       to fail, and a {@code @NotBlank} would make local
 *                       development and the test suite invent a token to
 *                       satisfy a check nobody is performing. Shape is this
 *                       record's business; whether a person is on the other
 *                       end is not.
 */
@Schema(description = "Ask for a sign-in link")
public record MagicLinkRequest(
        @NotBlank @Email @Size(max = 320) String email,

        @Schema(description = "The Turnstile widget's token. Required wherever the "
                + "challenge is configured; a request without one is answered "
                + "`403 CHALLENGE_FAILED`.")
        @Size(max = 2048) String challengeToken) {
}
