package com.mustafatetik.atomcv.shared.security;

/**
 * Who is acting on this request.
 *
 * <p>One interface, one implementation at a time: a fixed local user until
 * identity lands in Stage 3 (XI-A.6), a session-backed one after. Endpoints
 * depend on this rather than on a static "current user" helper, so the
 * substitution is a bean definition rather than a search through the codebase.
 */
public interface CurrentUser {

    /**
     * @throws IllegalStateException when nobody is acting — an endpoint that
     *                               calls this is one that requires a user
     */
    UserContext require();
}
