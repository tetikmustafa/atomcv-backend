package com.mustafatetik.atomcv.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What counts as unusual (Bolum 44.3).
 *
 * @param enabled        false in the integration suite, where a scheduler
 *                       firing over shared tables would report on other tests'
 *                       rows
 * @param baselineFactor Bolum 44.3's five. A multiple of the user's own
 *                       history, not a fixed count: a fixed one is useless for
 *                       a heavy user and an alarm every day for a light one.
 * @param signupsPerHour above this, something is registering accounts rather
 *                       than someone
 * @param dailyBudgetUsd the ceiling that pulls the brake. Deliberately low by
 *                       default: a free product's ordinary day costs cents,
 *                       and an operator raising a threshold that fired is a
 *                       better failure than a bill nobody saw coming.
 */
@ConfigurationProperties(prefix = "atomcv.anomaly")
public record AnomalyProperties(
        boolean enabled, double baselineFactor, int signupsPerHour,
        java.math.BigDecimal dailyBudgetUsd) {

    public AnomalyProperties {
        baselineFactor = baselineFactor <= 0 ? 5.0 : baselineFactor;
        signupsPerHour = signupsPerHour < 1 ? 50 : signupsPerHour;
        dailyBudgetUsd = dailyBudgetUsd == null || dailyBudgetUsd.signum() <= 0
                ? java.math.BigDecimal.valueOf(10)
                : dailyBudgetUsd;
    }
}
