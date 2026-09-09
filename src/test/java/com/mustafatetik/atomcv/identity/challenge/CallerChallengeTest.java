package com.mustafatetik.atomcv.identity.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The challenge in front of the two endpoints that spend money for somebody who
 * has answered nothing (Bolum 44.4, Bolum 9).
 *
 * <p><strong>Tested here because the integration lane cannot.</strong>
 * {@code ChallengeConfig} hands out {@code token -> true} where there is no
 * secret, which is every profile but {@code prod} — so an IT of the import or the
 * generation endpoint passes whatever it sends and proves nothing about the
 * refusal. Bolum 51.7's rule about a component the suite switches off applies
 * exactly, and these cases run against a real refusing implementation.
 */
class CallerChallengeTest {

    private static final UUID USER = UUID.randomUUID();

    /** What a deployment with no secret gets: everything through. */
    private static final Challenge WAVES_THROUGH = token -> true;

    /** What production gets on a bad token. */
    private static final Challenge REFUSES = token -> false;

    /** And the shape that matters most: only a real token passes. */
    private static final Challenge CHECKS = "the-widget-said-so"::equals;

    // -- an account has already answered one --------------------------------

    /**
     * Signing in is where the challenge is asked (Bolum 40.4.1), so asking an
     * account again is friction with nothing behind it. Not even the token is
     * looked at.
     */
    @Test
    void anaccountIsNotAsked() {
        var challenge = new CallerChallenge(signedIn(), REFUSES);

        assertThatCode(() -> challenge.requireOfAnonymous(null)).doesNotThrowAnyException();
        assertThatCode(() -> challenge.requireOfAnonymous("rubbish")).doesNotThrowAnyException();
    }

    // -- and a session has not ----------------------------------------------

    @Test
    void ananonymousCallerWithAGoodTokenPasses() {
        var challenge = new CallerChallenge(anonymous(), CHECKS);

        assertThatCode(() -> challenge.requireOfAnonymous("the-widget-said-so"))
                .doesNotThrowAnyException();
    }

    @Test
    void ananonymousCallerWithABadTokenIsRefused() {
        var challenge = new CallerChallenge(anonymous(), CHECKS);

        assertThatThrownBy(() -> challenge.requireOfAnonymous("forged"))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> {
                    var error = ((ApiException) thrown).error();
                    assertThat(error.code()).isEqualTo(ErrorCode.CHALLENGE_FAILED);
                    assertThat(error.httpStatus()).isEqualTo(403);
                });
    }

    /**
     * <strong>Absence is a failure, not an absence.</strong> A client that omits
     * the token is exactly the client this exists to stop, so both shapes of
     * "nothing sent" are refused rather than treated as an old client.
     */
    @Test
    void anananonymousCallerWhoSendsNothingIsRefused() {
        var challenge = new CallerChallenge(anonymous(), CHECKS);

        for (String nothing : List.of("", "   ")) {
            assertThatThrownBy(() -> challenge.requireOfAnonymous(nothing))
                    .isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> challenge.requireOfAnonymous(null))
                .isInstanceOf(ApiException.class);
    }

    /**
     * And the local implementation lets them through, which is the state every
     * test but this file runs in — said out loud so nobody reads a green suite as
     * evidence that the refusal works.
     */
    @Test
    void withNoSecretConfiguredEverybodyPasses() {
        var challenge = new CallerChallenge(anonymous(), WAVES_THROUGH);

        assertThatCode(() -> challenge.requireOfAnonymous(null)).doesNotThrowAnyException();
    }

    private static CurrentUser signedIn() {
        return new CurrentUser() {
            @Override
            public UserContext require() {
                return UserContext.of(USER);
            }

            @Override
            public Optional<UserContext> find() {
                return Optional.of(UserContext.of(USER));
            }
        };
    }

    private static CurrentUser anonymous() {
        return new CurrentUser() {
            @Override
            public UserContext require() {
                throw new IllegalStateException("no account");
            }

            @Override
            public Optional<UserContext> find() {
                return Optional.empty();
            }

            @Override
            public Optional<AnonymousSessionId> anonymousSession() {
                return Optional.of(AnonymousSessionId.of("a-session"));
            }
        };
    }
}
