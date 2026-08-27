package com.mustafatetik.atomcv.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What redeeming a sign-in link answers (Adim 3.6).
 *
 * <p>It used to be {@code 204}, and the one thing that made a body worth
 * having is here: what became of the work the person was carrying. It is a
 * one-time fact, which is why it is answered here rather than added to
 * {@code /session} — a field on the session would be repeated to every call
 * for a fortnight and the client would have to remember whether it had already
 * acted on it.
 *
 * <p>Nothing about the account is in it. Who signed in is what {@code /session}
 * answers, and repeating it here would put identity in a second place.
 *
 * @param profileUpgrade {@code upgraded} when the anonymous profile is now the
 *                       account's, {@code none} when there was nothing to move,
 *                       {@code kept_existing} when the account already had a
 *                       profile and the anonymous one was left alone, and
 *                       {@code unavailable} when it could not be read
 */
@Schema(description = "The outcome of signing in, beyond the cookie")
public record SignInResponse(
        @Schema(allowableValues = {"upgraded", "none", "kept_existing", "unavailable"})
        String profileUpgrade) {
}
