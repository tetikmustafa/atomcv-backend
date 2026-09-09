package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.repository.AnonymousProfiles;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The profile of whoever is calling, account or anonymous session (Bolum 9).
 *
 * <p><strong>One line per endpoint became one line per module.</strong>
 * {@link ProfileResolver} answers this for an account and is unchanged — it is
 * still the only place a {@code UserContext} becomes a {@code ProfileRef}. This
 * sits in front of it and answers the same question for a caller who has not
 * signed up, so the four profile controllers ask one thing and neither of them
 * has to know which kind of caller it is serving.
 *
 * <p><strong>The scope travels with the answer, and that is what enforces
 * § 35.7.</strong> A {@code ProfileRef} says whether it is {@code EPHEMERAL},
 * so a service holding one already knows whether the person on the other end
 * has an account — no second lookup, and no way for an endpoint to forget to
 * ask. That is where the capability limits bite: {@code AtomService} refuses
 * atom controls and alternatives on an ephemeral scope, and the ceiling of
 * sixty atoms applies to one.
 *
 * <p><strong>An anonymous profile is created on first use, exactly as an
 * account's is.</strong> That is the point of the whole slice — the same code
 * path, the same behaviour — and it has a cost worth naming: a caller who has
 * done nothing but ask for a session can cause a row to be written. It is
 * bounded rather than ignored. The session itself is already a write (Redis),
 * the row is empty, it carries an expiry and the sweep removes it within
 * minutes, and the endpoints in front of this are rate limited like every other.
 */
@Service
public class CallerProfiles {

    private final CurrentUser caller;
    private final ProfileResolver accounts;
    private final AnonymousProfiles anonymous;

    CallerProfiles(CurrentUser caller, ProfileResolver accounts, AnonymousProfiles anonymous) {
        this.caller = caller;
        this.accounts = accounts;
        this.anonymous = anonymous;
    }

    /**
     * The scope everything below the profile is read and written within.
     *
     * @throws com.mustafatetik.atomcv.shared.error.ApiException
     *         {@code AUTHENTICATION_REQUIRED} when the request carries neither
     *         an account nor an anonymous session — which is the same refusal
     *         {@code CurrentUser.require()} gave before, for the same reason:
     *         a caller with no session at all has no profile of their own.
     */
    @Transactional
    public ProfileRef ref() {
        return owned().ref();
    }

    /**
     * The account behind this request, or empty for an anonymous session.
     *
     * <p>Needed where a <em>user</em> rather than a profile is the subject:
     * Bolum 32.2's translation jobs are claimed by user id, and an anonymous
     * session has no id to claim by and one language to want (§ 35.7). Callers
     * treat empty as "nothing to queue" rather than as a failure.
     */
    public java.util.Optional<com.mustafatetik.atomcv.shared.security.UserContext> user() {
        return caller.find();
    }

    /** Both halves, for a caller that needs the head row's fields as well. */
    @Transactional
    public ProfileResolver.OwnedProfile owned() {
        return caller.find()
                .map(accounts::owned)
                .orElseGet(this::anonymousProfile);
    }

    /**
     * The anonymous session's profile, created if this is the first thing it
     * has done.
     *
     * <p>The expiry is the session's own, asked of the session rather than
     * recomputed: it slides with activity (EK D.6.6), so a caller reading their
     * profile has just moved it, and a writer deriving "two hours from now" for
     * itself would be a second place the window is decided.
     */
    private ProfileResolver.OwnedProfile anonymousProfile() {
        ProfileRef ref = ProfileRef.ephemeral(caller.anonymousSession()
                // The same refusal JobOwner.of gives, and it has to be the
                // same: "sign up" is the only thing a caller with no session
                // at all can do about it.
                .orElseThrow(() -> ApiException.of(ErrorCode.AUTHENTICATION_REQUIRED,
                        Resolution.of(ResolutionAction.SIGN_UP))));
        Profile profile = anonymous.findOrCreate(ref, caller.anonymousSessionEndsAt()
                // Unreachable together: both are the same session, and the id
                // above was already found. Named rather than assumed, because a
                // future implementation of the port could answer one and not
                // the other and the failure would be an immortal profile row.
                .orElseThrow(() -> new IllegalStateException(
                        "An anonymous session with no end; its profile would never expire")));
        return new ProfileResolver.OwnedProfile(profile, ref);
    }
}
