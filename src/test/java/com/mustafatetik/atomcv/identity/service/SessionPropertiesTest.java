package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Bolum 40.1's numbers, and the one value the cookie must never take. */
class SessionPropertiesTest {

    @Test
    void anUnconfiguredDeploymentGetsTheDocumentedDefaults() {
        var properties = new SessionProperties(null, null, null, null, null, null);

        assertThat(properties.ttl()).isEqualTo(Duration.ofDays(30));
        assertThat(properties.touchInterval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.cookieName()).isEqualTo("sid");
        assertThat(properties.domain()).isNull();
        // The one that must not default to off. A deployment that forgot to
        // set it sends the session in the clear.
        assertThat(properties.secure()).isTrue();
    }

    @Test
    void aZeroOrNegativeTtlIsNotAcceptedAsAConfiguredValue() {
        assertThat(new SessionProperties(Duration.ZERO, null, null, null, null, null).ttl())
                .isEqualTo(Duration.ofDays(30));
        assertThat(new SessionProperties(Duration.ofDays(-1), null, null, null, null, null).ttl())
                .isEqualTo(Duration.ofDays(30));
    }

    @Test
    void anEmptyDomainIsNoDomainRatherThanAnEmptyOne() {
        assertThat(new SessionProperties(null, null, null, null, "   ", null).domain()).isNull();
        assertThat(new SessionProperties(null, null, null, null, "atomcv.example.com", null).domain())
                .isEqualTo("atomcv.example.com");
    }

    /**
     * Adim 3.3's warning, enforced rather than written down. A leading dot
     * widens the cookie to every sibling of the apex — the portfolio site
     * included — and the mistake is invisible in a browser that keeps working.
     */
    @Test
    void aDottedDomainIsRefusedAtStartUpAndNotInProduction() {
        assertThatThrownBy(() -> new SessionProperties(null, null, null, null, ".example.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not start with a dot");
    }
}
