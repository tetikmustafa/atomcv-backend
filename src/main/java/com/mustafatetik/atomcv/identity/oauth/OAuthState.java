package com.mustafatetik.atomcv.identity.oauth;

import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import java.time.Instant;

/**
 * What was true when the round trip began, held server-side until the browser
 * comes back.
 *
 * <p>The provider is part of it so that a state minted for one provider cannot
 * be redeemed at another's callback, and {@code returnTo} is here rather than
 * in the URL so a caller cannot rewrite where they land after signing in.
 */
record OAuthState(OAuthProvider provider, String returnTo, Instant startedAt) {
}
