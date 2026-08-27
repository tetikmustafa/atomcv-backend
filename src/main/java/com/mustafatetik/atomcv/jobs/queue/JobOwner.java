package com.mustafatetik.atomcv.jobs.queue;

import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.Objects;
import java.util.UUID;

/**
 * Whose job it is (Bolum 30, Adim 3.6).
 *
 * <p>Two kinds, and the queue has always had columns for both: {@code user_id}
 * for an account and {@code anon_session_id} for somebody who has not signed
 * up. What it did not have was a way to say "this caller" without meaning a
 * user — so every read was {@code currentUser.require()}, and an anonymous
 * caller could not so much as poll the job they had just started.
 *
 * <p><strong>This is where absolute rule 3 lives for {@code jobs}.</strong> A
 * job is read back by whoever owns it and by nobody else, and the two ways to
 * build an owner are the two things that establish who is asking: a
 * {@link UserContext}, or an {@link AnonymousSessionId} that only the module
 * able to check a session can produce. Neither can be made from a path
 * variable.
 *
 * <p>An anonymous owner is exactly as strong as the cookie, which is the same
 * strength the anonymous profile has (§ 41.3). It is weaker than an account
 * and it is meant to be: what it protects is two hours of work, and the person
 * chose not to sign up.
 */
public record JobOwner(UUID userId, String anonSessionId) {

    public JobOwner {
        boolean signedIn = userId != null;
        boolean anonymous = anonSessionId != null && !anonSessionId.isBlank();
        if (signedIn == anonymous) {
            throw new IllegalArgumentException(
                    "A job belongs to an account or to an anonymous session, never both "
                            + "and never neither");
        }
    }

    public static JobOwner of(UserContext user) {
        Objects.requireNonNull(user, "user");
        return new JobOwner(user.userId(), null);
    }

    public static JobOwner anonymous(AnonymousSessionId session) {
        Objects.requireNonNull(session, "session");
        return new JobOwner(null, session.value());
    }

    /**
     * Whoever is on this request, whichever of the two they are.
     *
     * <p>Ends the request when they are neither — which is the same refusal
     * {@code CurrentUser.require()} gives, and the right one: a caller with no
     * session at all has no work of their own to look at.
     *
     * @throws com.mustafatetik.atomcv.shared.error.ApiException
     *         {@code AUTHENTICATION_REQUIRED}
     */
    public static JobOwner of(CurrentUser caller) {
        Objects.requireNonNull(caller, "caller");
        return caller.find().map(JobOwner::of)
                .or(() -> caller.anonymousSession().map(JobOwner::anonymous))
                .orElseThrow(() -> ApiException.of(ErrorCode.AUTHENTICATION_REQUIRED,
                        Resolution.of(ResolutionAction.SIGN_UP)));
    }

    public boolean isAnonymous() {
        return userId == null;
    }

    /**
     * Shape only. The anonymous half is a session id, which is the cookie —
     * a value that printed itself would put a credential in the first log line
     * somebody added while debugging.
     */
    @Override
    public String toString() {
        return isAnonymous() ? "JobOwner[anonymous]" : "JobOwner[user=" + userId + "]";
    }
}
