package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.identity.repository.SignInAccounts;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether Bolum 57.7's optional emails may be sent to an account.
 *
 * <p>Two ways in and they are not the same: a signed-in person changes it
 * through their preferences, and somebody holding a link changes it with the
 * token that travelled in the email. This is the second, which has no session
 * to act under.
 */
@Service
public class LifecyclePreference {

    private static final Logger log = LoggerFactory.getLogger(LifecyclePreference.class);

    private final SignInAccounts accounts;

    LifecyclePreference(SignInAccounts accounts) {
        this.accounts = accounts;
    }

    /** What the settings screen reads. */
    @Transactional(readOnly = true)
    public boolean wantedBy(UUID userId) {
        return accounts.byId(userId)
                .map(com.mustafatetik.atomcv.identity.domain.UserAccount::wantsLifecycleEmails)
                .orElse(true);
    }

    /** And what it writes, under a session rather than under a token. */
    @Transactional
    public boolean set(UUID userId, boolean wanted) {
        accounts.byId(userId).ifPresent(account -> {
            account.setLifecycleEmails(wanted);
            accounts.save(account);
        });
        return wanted;
    }

    /**
     * Idempotent on purpose: a second click, or a gateway that fetched the
     * page twice, is the same answer as the first.
     */
    @Transactional
    public void stop(UUID token) {
        if (token == null) {
            return;
        }
        accounts.byUnsubscribeToken(token).ifPresent(account -> {
            account.setLifecycleEmails(false);
            accounts.save(account);
            // No address and no id: Bolum 44.1's rule, and this line would
            // otherwise say who stopped reading us.
            log.info("An account turned its lifecycle emails off");
        });
    }
}
