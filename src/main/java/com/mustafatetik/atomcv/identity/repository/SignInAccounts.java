package com.mustafatetik.atomcv.identity.repository;

import com.mustafatetik.atomcv.identity.domain.OAuthIdentity;
import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import com.mustafatetik.atomcv.identity.domain.UserAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * The only data access that happens without an acting user, and the reason it
 * is allowed to.
 *
 * <p>Absolute rule 3 sends every read through a scoped repository, and
 * {@code oauth_identities} carries a {@code user_id} that would normally make
 * it {@code UserScopedRepository}'s business. <strong>Sign-in cannot do
 * that</strong>: it is the act of working out who the user is, so there is no
 * {@code UserContext} to scope by. Requiring one would be circular.
 *
 * <p>What stands in for the ownership check is this surface being as small as
 * the flow needs and no larger. Every method is either a lookup keyed by a
 * credential the caller has just proved, or a write for an account it has just
 * established. <strong>Nothing here returns another user's data</strong> —
 * there is no finder that takes an id and no finder that lists. That is the
 * property to preserve if a method is ever added: if it could answer a
 * question an attacker wants answered, it belongs behind a scoped repository
 * instead, and behind a user.
 *
 * <p>A facade over package-private Spring Data interfaces, so ArchUnit's rule
 * holds — no raw repository escapes this package, and {@code ..service..} can
 * depend on this class without depending on a {@code Repository}.
 */
@Repository
public class SignInAccounts {

    private final UserAccountJpaRepository users;
    private final OAuthIdentityJpaRepository identities;

    SignInAccounts(UserAccountJpaRepository users, OAuthIdentityJpaRepository identities) {
        this.users = users;
        this.identities = identities;
    }

    /**
     * The account this provider subject is already bound to.
     *
     * <p>Keyed on the provider's own identifier and never on the email: an
     * address can be changed and reassigned, and keying identity on it would
     * hand the account to whoever inherits it.
     */
    public Optional<UserAccount> byProviderIdentity(OAuthProvider provider, String providerUid) {
        return identities.findByProviderAndProviderUid(provider.wireValue(), providerUid)
                .map(OAuthIdentity::getUserId)
                .flatMap(users::findById);
    }

    /**
     * Used only to decide whether a <em>verified</em> address belongs to an
     * account that already exists. The caller checks verification first; this
     * cannot.
     */
    public Optional<UserAccount> byVerifiedEmail(String email) {
        return users.findByEmailIgnoringCase(email);
    }

    public UserAccount create(String email, String displayName) {
        return users.save(UserAccount.signingUp(email, displayName));
    }

    public void link(UUID userId, OAuthProvider provider, String providerUid) {
        identities.save(OAuthIdentity.binding(userId, provider, providerUid));
    }

    public void seen(UserAccount account, Instant now) {
        account.seenAt(now);
        users.save(account);
    }
}
