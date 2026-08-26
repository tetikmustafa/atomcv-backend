package com.mustafatetik.atomcv.identity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What an account may do, as far as the client needs to know (§ 35.7).
 *
 * <p><strong>§ 35.7 only ever wrote down the anonymous set.</strong> Its
 * example is an {@code "authenticated": false} body, and no section states the
 * other half — so the account side is decided here and published to the
 * frontend as {@code B-044} rather than guessed silently in a controller.
 *
 * <p>Configuration and not constants for the same reason as
 * {@code QuotaProperties}: these are business decisions that will move, and a
 * deployment should be able to open or close one without a release.
 *
 * @param accountLanguages the output languages an account may ask for. Bolum
 *                         38.1 keeps three language axes apart and this is the
 *                         third — the document's language, not the interface's.
 */
@ConfigurationProperties(prefix = "atomcv.capabilities")
public record CapabilityProperties(List<String> accountLanguages) {

    public CapabilityProperties {
        accountLanguages = accountLanguages == null || accountLanguages.isEmpty()
                ? List.of("en", "tr")
                // LinkedHashSet: configured order is kept and reaches the wire
                // unchanged, and the JDK's immutable copies iterate in an order
                // salted per JVM run.
                : List.copyOf(Collections.unmodifiableSet(
                        new LinkedHashSet<>(accountLanguages)));
    }
}
