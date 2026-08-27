package com.mustafatetik.atomcv.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The daily ceilings of Bolum 44.1.
 *
 * <p>Configuration rather than constants because the numbers are a business
 * decision that will move — and because a deployment under attack should be
 * able to tighten them without a release.
 *
 * @param generationsPerUser Bolum 44.1's twenty
 * @param profileExtractsPerUser five, and lower than the other on purpose:
 *                               extraction is the most expensive call the
 *                               product makes
 * @param generationsPerIp       Bolum 44.1's anonymous five. Lower than an
 *                               account's twenty because an address is a
 *                               weaker identity than an account: it is shared
 *                               by everybody behind one office router, and it
 *                               is also what somebody spending our money
 *                               rotates.
 * @param profileExtractsPerIp   three, and the same reasoning again against
 *                               the most expensive call in the product
 */
@ConfigurationProperties(prefix = "atomcv.quota")
public record QuotaProperties(
        int generationsPerUser,
        int profileExtractsPerUser,
        int generationsPerIp,
        int profileExtractsPerIp) {

    public QuotaProperties {
        generationsPerUser = generationsPerUser < 1 ? 20 : generationsPerUser;
        profileExtractsPerUser = profileExtractsPerUser < 1 ? 5 : profileExtractsPerUser;
        generationsPerIp = generationsPerIp < 1 ? 5 : generationsPerIp;
        profileExtractsPerIp = profileExtractsPerIp < 1 ? 3 : profileExtractsPerIp;
    }

    /**
     * The ceiling for one metric and one kind of subject (Bolum 44.1).
     *
     * <p>Two tables of numbers rather than one, because an account and an
     * address are not the same claim about who is asking. A single ceiling
     * would have to be the lower of the two, which punishes the people who
     * signed up.
     */
    public int dailyLimit(QuotaMetric metric, QuotaSubject.Type subject) {
        return subject == QuotaSubject.Type.USER
                ? dailyLimit(metric)
                : anonymousLimit(metric);
    }

    private int anonymousLimit(QuotaMetric metric) {
        return switch (metric) {
            case GENERATION -> generationsPerIp;
            case PROFILE_EXTRACT -> profileExtractsPerIp;
            case LLM_COST -> Integer.MAX_VALUE;
        };
    }

    public int dailyLimit(QuotaMetric metric) {
        return switch (metric) {
            case GENERATION -> generationsPerUser;
            case PROFILE_EXTRACT -> profileExtractsPerUser;
            // Not a count, and nothing consumes it through the quota gate: the
            // anomaly detector reads the sum instead (Bolum 44.3).
            case LLM_COST -> Integer.MAX_VALUE;
        };
    }
}
