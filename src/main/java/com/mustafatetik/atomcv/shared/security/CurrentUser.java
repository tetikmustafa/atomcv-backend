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

    /**
     * When the anonymous session behind this request ends (Bolum 9, EK D.6.6).
     *
     * <p><strong>Part of "who is calling", which is why it is here.</strong>
     * Anything an anonymous session owns has to stop existing when the session
     * does — its profile above all (§ 51.6.1) — so whatever writes that has to
     * know the moment. Deriving it instead, from a TTL plus a clock, would make
     * every writer a second place the window is decided: two hours from *when*
     * is the session's own answer, it slides with activity, and a copy of the
     * rule would eventually slide differently.
     *
     * <p>It also keeps the module graph honest. The profile module needs this
     * and identity already depends on profile, so reading identity's session
     * properties from there would have been a cycle — ArchUnit says so. The port
     * both sides already share is the place a shared fact belongs.
     *
     * <p>Empty exactly when {@link #anonymousSession()} is.
     */
    default Optional<java.time.Instant> anonymousSessionEndsAt() {
        return Optional.empty();
    }
}
