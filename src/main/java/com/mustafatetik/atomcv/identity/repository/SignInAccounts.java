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
     * Whose account an address belongs to.
     *
     * <p><strong>This cannot tell you the address was proved.</strong> OAuth
     * calls it only after a provider has vouched for the address, because
     * linking an identity to an account found this way on an <em>unverified</em>
     * address is an account takeover. The magic link calls it to decide who to
     * send to, which needs no such proof — the proof is the person opening the
     * email.
     */
    public Optional<UserAccount> byEmail(String email) {
        return users.findByEmailIgnoringCase(email);
    }

    /**
     * The account a redeemed link belongs to.
     *
     * <p>An id-taking finder, which the class javadoc says to be careful
     * about. It is safe here because the id does not come from a caller: it
     * comes off a token row that was found by a selector and matched against a
     * verifier. Nothing a client sends reaches this argument.
     */
    public Optional<UserAccount> byId(UUID userId) {
        return users.findById(userId);
    }

    /** A first sign-in through a provider: the address is already proved. */
    public UserAccount create(String email, String displayName) {
        return users.save(UserAccount.signingUp(email, displayName));
    }

    /**
     * A first magic link to an address nobody has claimed.
     *
     * <p>The row exists before the person proves anything, because
     * {@code magic_link_tokens.user_id} is {@code NOT NULL} and V1 meant it
     * that way. It carries {@code email_verified = false} until the link is
     * opened, and until then it is a placeholder: no session has ever pointed
     * at it and no profile hangs off it.
     */
    public UserAccount createAwaitingVerification(String email) {
        return users.save(UserAccount.awaitingVerification(email));
    }

    public void link(UUID userId, OAuthProvider provider, String providerUid) {
        identities.save(OAuthIdentity.binding(userId, provider, providerUid));
    }

    /**
     * Bolum 57.4: the account, and by cascade everything hanging off it.
     *
     * <p>Another id-taking method, and it is safe for a narrower reason than
     * {@link #byId}: the id is the acting user's own, taken from their
     * session and never from a request body. It also answers nothing — a
     * caller who guessed somebody else's id would learn only whether a row
     * existed, and the endpoint in front of this does not let them try.
     *
     * @return false when there was no such row, so a second press is not an
     *         error
     */
    public boolean deleteById(UUID userId) {
        if (!users.existsById(userId)) {
            return false;
        }
        users.deleteById(userId);
        return true;
    }

    public void seen(UserAccount account, Instant now) {
        account.seenAt(now);
        users.save(account);
    }
}
