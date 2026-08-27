package com.mustafatetik.atomcv.shared.security;

import java.util.Optional;

/**
 * Who is acting on this request.
 *
 * <p>One interface, one implementation: the session-backed one that landed with
 * identity in Adim 3.3. Endpoints depend on this rather than on a static
 * "current user" helper, so how a request is authenticated stays a bean
 * definition rather than a search through the codebase.
 *
 * <p>The two methods answer two different questions and both are needed.
 * {@link #require()} is what a user-scoped endpoint calls: there is no useful
 * answer without a user, so it ends the request. {@link #find()} is for the
 * handful of endpoints — {@code GET /auth/session} above all — whose whole
 * purpose is to report whether anyone is signed in; asking those to catch an
 * exception to learn "nobody" would make the ordinary case an error path.
 */
public interface CurrentUser {

    /**
     * @throws com.mustafatetik.atomcv.shared.error.ApiException
     *         {@code AUTHENTICATION_REQUIRED} when nobody is acting. An
     *         endpoint that calls this is one that requires a user.
     */
    UserContext require();

    /** Empty when the request carries no session. */
    Optional<UserContext> find();

    /**
     * The anonymous session behind this request, when there is one (Adim 3.6).
     *
     * <p>A third question, and it is not the negation of {@link #find()}:
     * "nobody is signed in" and "somebody is here without an account" are
     * different states, and the work an anonymous person has started belongs
     * to the second. Anything scoped by caller rather than by user — a job
     * they queued, the profile they uploaded — needs to tell them apart.
     *
     * <p>Empty for a signed-in caller and for a request with no session at
     * all, so the two together are exhaustive: a caller is a user, an
     * anonymous session, or nobody.
     */
    default Optional<AnonymousSessionId> anonymousSession() {
        return Optional.empty();
    }
}
