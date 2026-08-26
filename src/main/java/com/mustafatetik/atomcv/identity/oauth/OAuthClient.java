package com.mustafatetik.atomcv.identity.oauth;

import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import java.net.URI;

/**
 * One provider's half of the round trip (Bolum 40.6).
 *
 * <p>Two calls and no state: the {@code state} parameter belongs to
 * {@link OAuthStateStore}, and the session to {@code identity.service}. An
 * adapter's whole job is to turn a code into an identity.
 */
public interface OAuthClient {

    OAuthProvider provider();

    /** Where to send the browser. The caller has already minted {@code state}. */
    URI authorizationUri(String state);

    /**
     * Redeems the code the provider sent back.
     *
     * <p>Never throws for an expected failure — a provider that is down, a
     * code already used, an account with no verified address are all ordinary
     * and all end in a page the user has to be shown.
     */
    OAuthExchange exchange(String code);
}
