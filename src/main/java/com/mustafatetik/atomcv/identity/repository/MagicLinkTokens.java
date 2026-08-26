package com.mustafatetik.atomcv.identity.repository;

import com.mustafatetik.atomcv.identity.domain.MagicLinkToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Sign-in links, and the same argument as {@link SignInAccounts}: this is
 * identity establishment, so there is no acting user to scope by.
 *
 * <p>The surface is as narrow as the flow needs. The only finder takes a
 * selector — a value the caller can hold only because it arrived in an email
 * we sent — and it returns a token that proves nothing on its own, because the
 * verifier still has to match. There is no finder by user, and none that
 * lists.
 */
@Repository
public class MagicLinkTokens {

    private final MagicLinkTokenJpaRepository tokens;

    MagicLinkTokens(MagicLinkTokenJpaRepository tokens) {
        this.tokens = tokens;
    }

    public MagicLinkToken save(MagicLinkToken token) {
        return tokens.save(token);
    }

    /**
     * The indexed half of Bolum 40.2's split. Finding the row must not depend
     * on the secret, or how long it takes becomes a measurement of it.
     */
    public Optional<MagicLinkToken> bySelector(String selector) {
        return selector == null || selector.isBlank()
                ? Optional.empty()
                : tokens.findBySelector(selector);
    }

    /** @return true for the one request that redeemed it, false for the rest */
    public boolean redeem(UUID tokenId, Instant now) {
        return tokens.markUsed(tokenId, now) == 1;
    }

    public void spendOutstandingFor(UUID userId, Instant now) {
        tokens.spendOutstandingFor(userId, now);
    }
}
