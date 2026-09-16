package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.identity.domain.MagicLinkToken;
import com.mustafatetik.atomcv.identity.domain.UserAccount;
import com.mustafatetik.atomcv.identity.repository.MagicLinkTokens;
import com.mustafatetik.atomcv.identity.repository.SignInAccounts;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two writes a sign-in request makes, and the reason they are a bean of
 * their own.
 *
 * <p><strong>The sender must not be called inside a transaction.</strong>
 * {@code MagicLinkService.request} held one across {@code EmailSender.send}
 * until this existed — an HTTP call to Resend with a ten second ceiling, on
 * the request thread, holding a connection from a pool of ten. Sign-in is the
 * one request every user makes before they can make any other, so it is the
 * worst place in the system to spend a connection waiting on somebody else's
 * network.
 *
 * <p>The two writes stay together: a token pointing at an account row that was
 * never committed is a link that cannot be redeemed, and the account is
 * created precisely so the token can point at it. So the transaction is here,
 * around those two and nothing else — and it has to be a separate bean,
 * because a {@code @Transactional} method a class calls on itself is not
 * proxied and would have kept the behaviour while looking like it had changed.
 *
 * <p>What it does <em>not</em> do is decide anything about the address. Both
 * branches — an account that exists and one that does not — do the same work
 * in the same order, because that indistinguishability is the whole defence
 * against enumeration, and it is the caller that owns it.
 */
@Service
public class MagicLinkIssuer {

    private final SignInAccounts accounts;
    private final MagicLinkTokens tokens;

    MagicLinkIssuer(SignInAccounts accounts, MagicLinkTokens tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    /**
     * Finds or creates the account, and mints the row the link redeems.
     *
     * <p>The verifier itself never arrives here: the caller keeps it, because
     * the only copy that may exist is the one in the email. What is stored is
     * its hash, which is what makes a stolen database a set of useless rows.
     *
     * @param selector     the half that is looked up, stored as it is
     * @param verifierHash the half that is checked, already hashed
     * @return the locale to write the email in, which is the only thing about
     *         the account the caller is allowed to learn
     */
    @Transactional
    public Issued issue(String address, String selector, String verifierHash, Instant expiresAt) {
        UserAccount user = accounts.byEmail(address)
                .orElseGet(() -> accounts.createAwaitingVerification(address));

        tokens.save(MagicLinkToken.issued(selector, verifierHash, user.getId(), expiresAt));
        return new Issued(user.getLocale());
    }

    /** What the sender needs and nothing else. */
    public record Issued(String locale) {
    }
}
