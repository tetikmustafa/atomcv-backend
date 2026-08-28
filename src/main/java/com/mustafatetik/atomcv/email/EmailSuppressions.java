package com.mustafatetik.atomcv.email;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Addresses this deployment must stop writing to.
 *
 * <p>A hard bounce or a complaint is a standing instruction, and continuing to
 * send after one is how a sending domain loses its reputation — which for
 * Bolum 40.2's magic link means the product stops working for everyone, not
 * just for the address that bounced. Adim 3.2 spends weeks earning that
 * reputation; this is what keeps it.
 *
 * <p><strong>Nothing writes this table yet.</strong> The rows arrive from
 * Resend's webhooks, which are not in this slice. The guard goes in first on
 * the same reasoning the architecture rules did: retrofitting it later means
 * first cleaning up what accumulated while it was missing.
 *
 * <p>{@code JdbcTemplate} and no entity, like {@code UsageCounters}: one
 * read of one keyless table does not need a mapping.
 */
@Repository
public class EmailSuppressions {

    private final JdbcTemplate jdbc;

    EmailSuppressions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The cast is not decoration. {@code email_suppressions.email} is
     * {@code CITEXT}; JDBC binds a {@code varchar}, and Postgres settles
     * {@code citext = varchar} by casting the column down to {@code text} — a
     * case-sensitive comparison. A suppressed address written in one case
     * would then be missed in another, and the send would go out. The same
     * trap as {@code users.email}; see {@code UserAccountJpaRepository}.
     */
    public boolean isSuppressed(String email) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM email_suppressions WHERE email = CAST(? AS citext)",
                Integer.class, email);
        return count != null && count > 0;
    }

    /**
     * Stops writing to an address, for good.
     *
     * <p>The write half, which this class was missing: the guard was put in
     * first on the reasoning the architecture rules were, and the rows were to
     * arrive from Resend's webhooks. Until they did, a hard bounce meant
     * nothing — the next sign-in attempt sent to the same dead address again,
     * and the sending domain paid for it.
     *
     * <p>Idempotent, because a webhook is delivered at least once and a
     * retried delivery must not be an error. The first reason wins: an address
     * that hard-bounced and later produced a complaint is still suppressed for
     * the bounce, and the distinction is a diagnostic rather than a decision.
     */
    public void suppress(String email, Reason reason) {
        jdbc.update("""
                INSERT INTO email_suppressions (email, reason)
                VALUES (CAST(? AS citext), ?)
                ON CONFLICT (email) DO NOTHING
                """, email, reason.wireValue());
    }

    /** The three values {@code email_suppressions.reason} allows, verbatim from V1. */
    public enum Reason {

        /** The address does not exist. Nothing will ever make it deliverable. */
        HARD_BOUNCE,

        /**
         * Somebody pressed "this is spam". Worse than a bounce for the sending
         * domain, and the one signal a provider will not let you argue with.
         */
        COMPLAINT,

        /** An operator decided. Not produced by anything automatic. */
        MANUAL;

        String wireValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
