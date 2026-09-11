package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.billing.QuotaSubject;
import com.mustafatetik.atomcv.billing.UsageCounters;
import com.mustafatetik.atomcv.identity.repository.SignInAccounts;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The right to be forgotten (Bolum 57.4).
 *
 * <p><strong>The database does most of this, and that is deliberate.</strong>
 * Every table that holds a person's content references {@code users(id)} with
 * {@code ON DELETE CASCADE}, so one statement removes the profile, its atoms
 * and their embeddings, the generations and their snapshots, the jobs and the
 * email preferences. A service that deleted them one by one
 * would be a second copy of the schema's own answer, and the copy is what goes
 * stale when a table is added.
 *
 * <p>Three things the cascade does not reach, and they are the reason this
 * class exists at all. Redis holds the sessions, so a deleted account whose
 * cookie still worked would be an account that was not deleted.
 * {@code usage_counters} is keyed by a subject rather than by a foreign key
 * — <strong>Düzeltme against Bolum 57.4</strong>, which says Postgres handles
 * everything — so its rows outlive the account unless something removes them.
 * And the deletion itself has to be recorded, because "we deleted it" is a
 * claim somebody may have to answer for later.
 *
 * <p><strong>What survives, on purpose.</strong> {@code llm_invocations} keeps
 * its rows with a null user — the schema says so in its own comment, and the
 * reason is that aggregate cost history is not personal data once the link is
 * cut. {@code email_suppressions} survives too: it is keyed by address rather
 * than by account and it is a deliverability record, so deleting it would let
 * the product mail an address that had already bounced or complained.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final SignInAccounts accounts;
    private final SessionStore sessions;
    private final UsageCounters counters;

    AccountDeletionService(SignInAccounts accounts, SessionStore sessions,
            UsageCounters counters) {

        this.accounts = accounts;
        this.sessions = sessions;
        this.counters = counters;
    }

    /**
     * @return the address to tell, or empty when there was no such account —
     *         which is not an error: a second press of the button is the same
     *         answer as the first.
     *
     *         <p><strong>The caller sends, and that is the point</strong>
     *         (Bolum 57.7). This method is the transaction; returning from it
     *         is what makes the confirmation land after a commit rather than
     *         before one, and a rolled-back deletion therefore tells nobody
     *         anything. The address is read here because after the row is gone
     *         there is nowhere left to read it from.
     */
    @Transactional
    public Optional<Deleted> delete(UserContext user) {
        UUID userId = user.userId();

        // Sessions first, and this throws rather than reporting zero when it
        // cannot: if the row went and this then failed quietly, a live cookie
        // would be pointing at an account that is not there any more. That
        // request is answered by SessionCurrentUser now instead of breaking a
        // foreign key, but an account reported deleted while its sessions are
        // still live is the wrong state to be in either way.
        int revoked = sessions.revokeAllFor(userId);

        int forgotten = counters.forget(QuotaSubject.of(user));

        Optional<Deleted> told = accounts.byId(userId)
                .map(account -> new Deleted(account.getEmail(), account.getLocale()));

        boolean existed = accounts.deleteById(userId);
        if (!existed) {
            return Optional.empty();
        }

        // Bolum 57.4's record: that it happened and when, never what was in
        // it. The id outlives nothing — it references a row that is gone —
        // which is exactly what makes it safe to keep in a log.
        log.info("Deleted account {}: {} sessions revoked, {} usage rows removed",
                userId, revoked, forgotten);
        return told;
    }

    /**
     * An address and the language to write it in, carried out of a transaction
     * that has just removed the row both came from.
     */
    public record Deleted(String email, String locale) {
    }
}
