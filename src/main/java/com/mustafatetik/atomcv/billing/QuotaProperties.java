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
 */
@ConfigurationProperties(prefix = "atomcv.quota")
public record QuotaProperties(int generationsPerUser, int profileExtractsPerUser) {

    public QuotaProperties {
        generationsPerUser = generationsPerUser < 1 ? 20 : generationsPerUser;
        profileExtractsPerUser = profileExtractsPerUser < 1 ? 5 : profileExtractsPerUser;
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
