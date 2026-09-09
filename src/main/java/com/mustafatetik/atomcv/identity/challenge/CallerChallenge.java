package com.mustafatetik.atomcv.identity.challenge;

import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

/**
 * The challenge, asked of the callers who have not answered one yet
 * (Bolum 44.4, Bolum 9).
 *
 * <p><strong>Why the anonymous flow needs this and an account does not.</strong>
 * Signing in already passed a challenge — that is what {@code POST /auth/magic-link}
 * asks one for — so asking an account again is friction with nothing behind it.
 * An anonymous caller has answered nothing, and since Bolum 9's flow landed there
 * are two endpoints they can reach that spend real money on a model: importing a
 * CV, which is the most expensive single call this product makes, and generating,
 * which is several.
 *
 * <p><strong>A quota is not a substitute, and that is the whole argument.</strong>
 * Bolum 44.1's counters say <em>how much</em> and never <em>who</em>: five
 * generations per address is five per address somebody can rotate, and § 44.3's
 * tightening is a detector that runs after the spending. The challenge is the only
 * thing in front of it that asks whether there is a person there at all.
 *
 * <p><strong>Absent locally and in the test suite</strong>, where
 * {@link ChallengeConfig} warns and waves the request through — so nothing here
 * proves itself in the integration lane, and {@code CallerChallengeTest} exercises
 * it against both implementations directly (Bolum 51.7).
 */
@Component
public class CallerChallenge {

    private final CurrentUser caller;
    private final Challenge challenge;

    CallerChallenge(CurrentUser caller, Challenge challenge) {
        this.caller = caller;
        this.challenge = challenge;
    }

    /**
     * Lets an account through and asks everybody else.
     *
     * @param token what the widget produced, as the client sent it. Null or blank
     *              from an anonymous caller is a failure rather than an absence:
     *              a client that omits it is exactly the client this exists to
     *              stop.
     * @throws ApiException {@code CHALLENGE_FAILED} (403)
     */
    public void requireOfAnonymous(String token) {
        if (caller.find().isPresent()) {
            return;
        }
        if (!challenge.passed(token)) {
            throw ApiException.of(ErrorCode.CHALLENGE_FAILED);
        }
    }
}
