package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.oauth.OAuthFailure;

/** A session, or the reason there is none. */
public sealed interface SignInOutcome {

    record SignedIn(Session session) implements SignInOutcome {
    }

    record Refused(OAuthFailure reason) implements SignInOutcome {
    }
}
