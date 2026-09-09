package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.profile.service.ProfileUpgrade;
import com.mustafatetik.atomcv.profile.service.ProfileUpgradeService;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.stereotype.Component;

/**
 * What the person was carrying when they signed in (Adim 3.6).
 *
 * <p>Both ways in — a provider and a link in an email — end at the same two
 * lines: a new session, and a cookie replacing whatever the browser held. The
 * anonymous session id is readable up to that swap and never afterwards, so
 * the handover happens here, between the two, rather than at an endpoint the
 * client could call a moment later with an identifier it no longer has.
 *
 * <p>A class of its own so that the two controllers cannot drift apart on it.
 * Signing in through Google and signing in through an email are the same act
 * as far as the work in front of the person is concerned.
 */
@Component
public class SignInHandover {

    private static final Logger log = LoggerFactory.getLogger(SignInHandover.class);

    private final CurrentUser caller;
    private final ProfileUpgradeService upgrades;

    SignInHandover(CurrentUser caller, ProfileUpgradeService upgrades) {
        this.caller = caller;
        this.upgrades = upgrades;
    }

    /**
     * @param signedIn the session just created for the account
     * @return what became of the anonymous profile; {@link ProfileUpgrade#NONE}
     *         when the caller had no anonymous session, which is most sign-ins
     */
    public ProfileUpgrade follow(Session signedIn) {
        return caller.anonymousSession()
                .map(session -> attempt(signedIn, session))
                .orElse(ProfileUpgrade.NONE);
    }

    /**
     * <strong>Signing in does not fail because the hand-over did.</strong> The
     * person is being let into their account; losing two hours of anonymous
     * work is bad and being unable to sign in at all is worse, so the failure is
     * reported as {@link ProfileUpgrade#UNAVAILABLE} and the sign-in continues.
     *
     * <p>The catch is here and not inside the upgrade because the upgrade is one
     * transaction. Catching within it would leave this method holding a
     * rollback-only transaction and the commit would throw anyway — later, and
     * somewhere with less to say about it.
     */
    private ProfileUpgrade attempt(Session signedIn, AnonymousSessionId session) {
        try {
            return upgrades.upgrade(signedIn.asUserContext(), session);
        } catch (InvalidDataAccessApiUsageException misuse) {
            // Not a transient failure and not to be reported as one. Spring
            // translates exceptions thrown inside an @Repository, so the guard
            // in AnonymousProfiles -- "this ref is not ephemeral" -- arrives
            // here wearing a DataAccessException's clothes. Caught by the
            // clause below it would become UNAVAILABLE: a programming error
            // reported to the user as bad luck, and to us as nothing at all.
            throw misuse;
        } catch (DataAccessException failed) {
            // The class, never the message: a constraint violation names the
            // row it was about (absolute rule 4).
            log.warn("Could not carry an anonymous profile into an account: {}",
                    failed.getClass().getSimpleName());
            return ProfileUpgrade.UNAVAILABLE;
        }
    }
}
