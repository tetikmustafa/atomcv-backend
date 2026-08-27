package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.email.EmailSender;
import com.mustafatetik.atomcv.email.EmailSuppressions;
import com.mustafatetik.atomcv.email.MagicLinkEmail;
import com.mustafatetik.atomcv.identity.domain.AuthMethod;
import com.mustafatetik.atomcv.identity.domain.MagicLinkToken;
import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.domain.UserAccount;
import com.mustafatetik.atomcv.identity.ratelimit.SignInRateLimit;
import com.mustafatetik.atomcv.identity.repository.MagicLinkTokens;
import com.mustafatetik.atomcv.identity.repository.SignInAccounts;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bolum 40.2's selector/verifier link, issued and redeemed.
 *
 * <p><strong>Requesting one always looks the same.</strong> Bolum 40.4 is the
 * whole reason: an address that already has an account and one that does not
 * must be indistinguishable from outside, so both walk the same path — find or
 * create the account, mint a token, hand it to the sender — and the caller
 * gets the same answer either way. Nothing about the outcome reaches the
 * response, and nothing about the address reaches a log line.
 *
 * <p>The link is also how an account is created. The product's own document
 * lists it under signing up, and there is nothing to verify at request time
 * anyway: opening the email <em>is</em> the proof, so the row waits unverified
 * until then.
 */
@Service
public class MagicLinkService {

    /** Bolum 40.2. Long enough to walk to another device, short enough to matter. */
    static final int VALID_FOR_MINUTES = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);

    private final SignInAccounts accounts;
    private final MagicLinkTokens tokens;
    private final SessionStore sessions;
    private final SignInRateLimit rateLimit;
    private final EmailSender email;
    private final EmailSuppressions suppressions;
    private final MagicLinkProperties properties;
    private final Clock clock;

    MagicLinkService(SignInAccounts accounts, MagicLinkTokens tokens, SessionStore sessions,
            SignInRateLimit rateLimit, EmailSender email, EmailSuppressions suppressions,
            MagicLinkProperties properties, Clock clock) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.sessions = sessions;
        this.rateLimit = rateLimit;
        this.email = email;
        this.suppressions = suppressions;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Issues a link, whatever the address turns out to be.
     *
     * <p>Returns nothing on purpose. There is no outcome a caller could act on
     * without also being able to report it, and reporting it is exactly what
     * Bolum 40.4 forbids.
     *
     * <p><strong>Bolum 40.5's address layer is checked here and not at the
     * controller</strong>, one line after the address is normalised. Keyed on
     * anything else, {@code A@x.com} and {@code a@x.com} would be two buckets
     * against one account and the limit would count double — and the only way
     * that stays true is for the key and the lookup to be the same string.
     *
     * @throws com.mustafatetik.atomcv.shared.error.ApiException
     *         {@code RATE_LIMITED} when this address has had its share of the
     *         window. Thrown before the account row is touched, so a refused
     *         request creates nothing.
     */
    @Transactional
    public void request(String rawEmail) {
        String address = normalise(rawEmail);
        rateLimit.checkAddress(address);
        UserAccount user = accounts.byEmail(address)
                .orElseGet(() -> accounts.createAwaitingVerification(address));

        String selector = randomToken(16);
        String verifier = randomToken(32);
        Instant now = clock.instant();
        tokens.save(MagicLinkToken.issued(selector, sha256(verifier), user.getId(),
                now.plusSeconds(VALID_FOR_MINUTES * 60L)));

        if (suppressions.isSuppressed(address)) {
            // A hard bounce or a complaint is a standing instruction, and
            // sending anyway costs the domain's reputation — which would break
            // sign-in for everyone, not for this address. The token is still
            // written so the work, and so the timing, stays the same.
            log.info("Skipped a sign-in email to a suppressed address");
            return;
        }
        boolean accepted = email.send(MagicLinkEmail.to(
                address, user.getLocale(), linkFor(selector, verifier), VALID_FOR_MINUTES));
        if (!accepted) {
            // Said, but not to the caller: a failure that reached the response
            // would answer the question Bolum 40.4 exists to leave unanswered.
            log.warn("A sign-in email was not accepted by the sender");
        }
    }

    /**
     * Redeems a link, or refuses without saying why.
     *
     * <p>Every refusal is the same refusal (Bolum 40.2's {@code failGeneric}).
     * Expired, already used, wrong verifier and never existed are one answer,
     * because telling them apart tells an attacker which half of a guess was
     * right.
     */
    @Transactional
    public Optional<Session> verify(String selector, String verifier) {
        if (verifier == null || verifier.isBlank()) {
            return Optional.empty();
        }
        Optional<MagicLinkToken> found = tokens.bySelector(selector);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MagicLinkToken token = found.get();
        Instant now = clock.instant();

        // Constant time, and not String.equals. Comparing hashes byte by byte
        // with an early return leaks how much of a guess was right, one
        // measurable request at a time.
        if (!MessageDigest.isEqual(
                sha256(verifier).getBytes(StandardCharsets.UTF_8),
                token.getVerifierHash().getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        if (token.isUsed() || token.isExpiredAt(now)) {
            return Optional.empty();
        }
        // The database settles it, not this thread: two requests arriving
        // together would both have passed the check above.
        if (!tokens.redeem(token.getId(), now)) {
            return Optional.empty();
        }

        Optional<UserAccount> account = accounts.byId(token.getUserId());
        if (account.isEmpty() || account.get().isDeleted()) {
            return Optional.empty();
        }
        UserAccount user = account.get();
        // Opening the email is the proof, and this is the moment it lands.
        user.markEmailVerified();
        accounts.seen(user, now);
        // Anything else outstanding for this account dies here, so a link
        // somebody else requested cannot still be redeemed afterwards.
        tokens.spendOutstandingFor(user.getId(), now);

        return Optional.of(sessions.create(
                user.getId(), user.getRole(), AuthMethod.MAGIC_LINK));
    }

    /**
     * {@code {app}/verify?s=..&v=..} — a GET the person lands on, which is a
     * page and not a redemption. Bolum 40.3: corporate scanners click links,
     * and a one-shot token spent by a scanner is a login the user never got.
     */
    private String linkFor(String selector, String verifier) {
        return properties.verifyBaseUrl() + properties.verifyPath()
                + "?s=" + URLEncoder.encode(selector, StandardCharsets.UTF_8)
                + "&v=" + URLEncoder.encode(verifier, StandardCharsets.UTF_8);
    }

    /**
     * Trimmed and lowercased so one person is one row.
     *
     * <p>{@code Locale.ROOT} because absolute rule 7 applies to exactly this:
     * a Turkish default locale turns {@code ADA@X.COM} into {@code adı@x.com}
     * and the address stops being the address.
     */
    private static String normalise(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return ENCODER.encodeToString(value);
    }

    private static String sha256(String value) {
        try {
            return ENCODER.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every JRE", impossible);
        }
    }
}
