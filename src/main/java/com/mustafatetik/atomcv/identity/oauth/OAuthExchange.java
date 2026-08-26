package com.mustafatetik.atomcv.identity.oauth;

import com.mustafatetik.atomcv.identity.domain.OAuthAccount;

/**
 * What came back from a provider: an account, or a reason there is none.
 *
 * <p>Not {@code Result<T>}: that one carries a {@link
 * com.mustafatetik.atomcv.shared.error.PipelineError}, which is the generation
 * pipeline's closed vocabulary (Bolum 25.1). A failed sign-in is not a failed
 * generation, and widening that hierarchy to hold one would make every
 * pipeline {@code switch} answer for a case it can never see.
 */
public sealed interface OAuthExchange {

    record Account(OAuthAccount value) implements OAuthExchange {
    }

    record Failed(OAuthFailure reason) implements OAuthExchange {
    }

    static OAuthExchange of(OAuthAccount account) {
        return new Account(account);
    }

    static OAuthExchange failed(OAuthFailure reason) {
        return new Failed(reason);
    }
}
