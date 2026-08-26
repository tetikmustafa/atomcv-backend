package com.mustafatetik.atomcv.identity.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the {@code sid} cookie is written and how long a session lives
 * (Bolum 40.1, EK D.6.6).
 *
 * @param ttl           Bolum 40.1's thirty days. Redis holds the authority; the
 *                      cookie's {@code Max-Age} only mirrors it, so a session
 *                      revoked server-side is dead whatever the browser kept.
 * @param touchInterval how much activity has to pass before the sliding TTL is
 *                      rewritten. Refreshing on every request would put a Redis
 *                      write in front of every read for a value that moves by
 *                      milliseconds; refreshing never would make the TTL
 *                      absolute, which EK D.6.6 explicitly rejects. Anything
 *                      well below the shortest TTL keeps an active session
 *                      alive, and this is two orders below it.
 * @param cookieName    Bolum 40.1's {@code sid}.
 * @param domain        left empty everywhere but production. Adim 3.3 warns
 *                      against a leading dot: {@code .mustafatetik.com} would
 *                      send the session cookie to the portfolio site as well.
 * @param secure        true everywhere it can be. Configurable only because
 *                      Safari refuses a {@code Secure} cookie over
 *                      {@code http://localhost}, where Chrome and Firefox
 *                      accept one — see {@code application-local.yml}.
 */
@ConfigurationProperties(prefix = "atomcv.session")
public record SessionProperties(
        Duration ttl,
        Duration touchInterval,
        String cookieName,
        String domain,
        Boolean secure) {

    public SessionProperties {
        ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofDays(30) : ttl;
        touchInterval = touchInterval == null || touchInterval.isNegative()
                ? Duration.ofMinutes(5)
                : touchInterval;
        cookieName = cookieName == null || cookieName.isBlank() ? "sid" : cookieName;
        domain = domain == null || domain.isBlank() ? null : domain;
        secure = secure == null || secure;
        if (domain != null && domain.startsWith(".")) {
            throw new IllegalArgumentException(
                    "atomcv.session.domain must not start with a dot: a leading dot widens "
                            + "the cookie to every sibling subdomain (Adim 3.3)");
        }
    }
}
