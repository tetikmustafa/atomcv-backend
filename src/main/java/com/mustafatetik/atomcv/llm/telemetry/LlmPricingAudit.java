package com.mustafatetik.atomcv.llm.telemetry;

import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Says at startup which configured models nobody priced (F-015).
 *
 * <p>{@link LlmPricing} answers zero for a model it does not know, on purpose:
 * a made-up figure would put invented money into a number an operator acts on.
 * But zero and "nobody priced this" print the same, and the price table went
 * on naming a model that had been retired while every chain ran a different
 * one — so every cost in the system was zero and nothing said why.
 * {@code llm.unpriced_calls} counted it, which only helps somebody already
 * looking at a dashboard.
 *
 * <p><strong>At startup, because that is when it is cheap to fix.</strong> The
 * table is configuration and the chain is configuration; whether they agree is
 * knowable before the first call, and after the first call it is a number that
 * is quietly wrong.
 *
 * <p>A free model is priced at zero <em>explicitly</em> rather than left out.
 * The figure is the same and the claim is not: one says the vendor charges
 * nothing, the other says we do not know.
 */
@Component
public class LlmPricingAudit {

    private static final Logger log = LoggerFactory.getLogger(LlmPricingAudit.class);

    private final LlmProperties properties;
    private final LlmPricing pricing;

    LlmPricingAudit(LlmProperties properties, LlmPricing pricing) {
        this.properties = properties;
        this.pricing = pricing;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reportUnpricedModels() {
        List<String> unpriced = unpricedModels();
        if (unpriced.isEmpty()) {
            return;
        }
        log.warn("No price is configured for {}; every call to {} will be costed at zero, "
                + "which is not the same as free. Bolum 27.4's table is atomcv.llm.pricing.",
                unpriced, unpriced.size() == 1 ? "it" : "them");
    }

    /** Package-private so the check can be asserted without a context. */
    List<String> unpricedModels() {
        List<String> unpriced = new ArrayList<>();
        for (Map.Entry<String, String> configured : properties.models().entrySet()) {
            String model = configured.getValue();
            // An empty model is a provider that is switched off, not a gap in
            // the table: Bolum 27.3 makes it unavailable and it is never called.
            if (model != null && !model.isBlank() && !pricing.knows(model)) {
                unpriced.add(configured.getKey() + "=" + model);
            }
        }
        return unpriced;
    }
}
