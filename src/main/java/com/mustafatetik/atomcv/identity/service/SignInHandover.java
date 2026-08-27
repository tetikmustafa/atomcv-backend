package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.profile.service.ProfileUpgrade;
import com.mustafatetik.atomcv.profile.service.ProfileUpgradeService;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
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
                .map(session -> upgrades.upgrade(signedIn.asUserContext(), session))
                .orElse(ProfileUpgrade.NONE);
    }
}
