package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.identity.domain.OAuthAccount;
import com.mustafatetik.atomcv.identity.domain.UserAccount;
import com.mustafatetik.atomcv.identity.oauth.OAuthFailure;
import com.mustafatetik.atomcv.identity.repository.SignInAccounts;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a provider account into a session: find, or link, or create.
 *
 * <p>The order is the whole of the security argument.
 *
 * <ol>
 *   <li><strong>The provider's subject first.</strong> If this GitHub user has
 *       signed in before, that binding decides, whatever the email says now.
 *       A person who changed their address keeps their account.</li>
 *   <li><strong>Then the verified email.</strong> Someone who signed up with
 *       Google and comes back through GitHub lands in the same account rather
 *       than a second one they cannot tell apart. Verified, and only verified:
 *       linking on an address a provider does not vouch for is an account
 *       takeover — a stranger adds your address to their own provider account
 *       and signs in as you. The adapters refuse before this is reached, and
 *       it is checked again here because a defence that lives in one place
 *       lives in the wrong place.</li>
 *   <li><strong>Otherwise a new account.</strong></li>
 * </ol>
 */
@Service
public class OAuthLoginService {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginService.class);

    private final SignInAccounts accounts;
    private final SessionStore sessions;
    private final Clock clock;

    OAuthLoginService(SignInAccounts accounts, SessionStore sessions, Clock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public SignInOutcome signIn(OAuthAccount account) {
        if (!account.emailVerified()) {
            return new SignInOutcome.Refused(OAuthFailure.EMAIL_UNVERIFIED);
        }
        UserAccount user;
        try {
            user = resolve(account);
        } catch (DataIntegrityViolationException raced) {
            // Two first sign-ins for one address, or one subject bound twice.
            // UNIQUE settled it; the loser reads what the winner wrote rather
            // than answering an error for a race the person cannot see.
            log.info("Concurrent first sign-in settled by the database, re-reading");
            user = resolve(account);
        }
        if (user.isDeleted()) {
            return new SignInOutcome.Refused(OAuthFailure.ACCOUNT_DISABLED);
        }
        accounts.seen(user, clock.instant());
        return new SignInOutcome.SignedIn(sessions.create(
                user.getId(), user.getRole(), account.provider().authMethod()));
    }

    private UserAccount resolve(OAuthAccount account) {
        var bound = accounts.byProviderIdentity(account.provider(), account.providerUid());
        if (bound.isPresent()) {
            return bound.get();
        }
        var existing = accounts.byVerifiedEmail(account.email());
        if (existing.isPresent()) {
            UserAccount user = existing.get();
            if (user.isDeleted()) {
                // Do not bind a new identity to a deleted account; signIn
                // refuses it, and a link written here would outlive the
                // refusal.
                return user;
            }
            accounts.link(user.getId(), account.provider(), account.providerUid());
            // The provider vouched for the address, which is more than this
            // row could say before.
            user.markEmailVerified();
            if (user.getDisplayName() == null && account.displayName() != null) {
                user.setDisplayName(account.displayName());
            }
            return user;
        }
        UserAccount created = accounts.create(account.email(), account.displayName());
        accounts.link(created.getId(), account.provider(), account.providerUid());
        return created;
    }
}
