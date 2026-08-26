package com.mustafatetik.atomcv.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The answer to "who am I", which EK D.6.6 makes a capabilities question
 * rather than an identity one.
 *
 * <p>No email, no display name, no user id. The client needs none of them to
 * decide what to render, and everything put here is content that a browser
 * cache, a screenshot or a log could pick up.
 *
 * @param authenticated whether a session with an account behind it was found.
 *                      An anonymous session (Adim 3.6) is also a session, and
 *                      also answers {@code false} — the cookie is the same one
 */
@Schema(description = "Whether anyone is signed in, and what they may do")
public record SessionResponse(boolean authenticated, CapabilitiesResponse capabilities) {
}
