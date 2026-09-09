package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.util.Objects;
import java.util.UUID;

/**
 * An anonymous session's profile has just become an account's (Adim 3.6).
 *
 * <p><strong>An event because the alternative is a cycle.</strong> Whatever else
 * the session made hangs off this profile and has to change owner with it — its
 * generations, and whatever a later stage adds. Calling those modules from here
 * would mean {@code profile} depending on {@code generation}, which already
 * depends on {@code profile}; ArchUnit refuses that, and rightly, because the
 * profile module has no business knowing what a generation is.
 *
 * <p><strong>Handled inside the same transaction.</strong> A plain
 * {@code @EventListener} runs synchronously in the publisher's transaction, which
 * is the whole point: a CV made from this profile is the reason somebody signs
 * up, and carrying the profile while leaving those behind would delete the very
 * thing they were keeping. If a listener fails, the sign-up's hand-over fails
 * with it and {@code SignInHandover} reports it rather than half-adopting.
 *
 * @param profile the scope the rows were filed under while the session held them
 * @param owner   the account they now belong to
 */
public record AnonymousProfileAdopted(ProfileRef profile, UUID owner) {

    public AnonymousProfileAdopted {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(owner, "owner");
        if (profile.scope() != ProfileRef.Scope.EPHEMERAL) {
            // The ref is what the rows were filed under, so it is the anonymous
            // one by definition. A persistent one here would mean the publisher
            // had already resolved the new scope and the listeners would find
            // nothing, silently.
            throw new IllegalArgumentException("The ref is the one the session held");
        }
    }
}
