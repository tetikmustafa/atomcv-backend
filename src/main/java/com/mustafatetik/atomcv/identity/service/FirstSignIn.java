package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.identity.domain.UserAccount;

/**
 * Somebody signed in for the first time, and the row saying so has committed.
 *
 * <p><strong>Published inside the transaction, acted on after it.</strong>
 * Both sign-in routes decide this — it is answerable only in the moment
 * before {@code seen(...)} fills in {@code last_seen_at}, which is why the
 * decision belongs to them and not to the listener.
 *
 * <p><strong>The contrast with {@code AnonymousProfileAdopted} is the
 * point.</strong> That one is a plain {@code @EventListener} on purpose: it
 * writes rows, and those rows have to land or not land with the adoption that
 * caused them. This one sends an email, which is a network call — it must not
 * hold the connection the transaction is using, and it must not go out at all
 * for a sign-in that then rolled back. Two events, two listener kinds, and the
 * difference is whether the work is a write or a call.
 *
 * @param account as it stood before the sign-in was recorded. Detached by the
 *                time the listener reads it, which is harmless: every field
 *                the welcome needs was loaded before the commit
 */
public record FirstSignIn(UserAccount account) {
}
