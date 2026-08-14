package com.mustafatetik.atomcv.rendering.measurement;

import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a profile's measured render costs current (Bolum 26.5).
 *
 * <p>Lives in {@code rendering} rather than in {@code profile} on purpose:
 * rendering already knows about content, and having profile reach back into
 * rendering would make the two modules depend on each other. The cost belongs
 * to the variant, so the write happens through the profile's own scoped
 * repository.
 */
@Service
public class RenderCostService {

    private static final Logger log = LoggerFactory.getLogger(RenderCostService.class);

    private final AtomVariantRepository variants;
    private final MeasurementService measurements;

    RenderCostService(AtomVariantRepository variants, MeasurementService measurements) {
        this.variants = variants;
        this.measurements = measurements;
    }

    /**
     * Measures every wording that has no cost for this customization yet.
     *
     * <p>Nothing is re-measured: a cost is cleared when its content changes
     * (see {@code AtomVariant.setContent}), so anything still holding one is
     * still describing itself accurately.
     *
     * @return how many wordings were measured
     */
    @Transactional
    public int measureMissing(ProfileRef profile, TemplateCustomization customization) {
        CapacityModel capacity = TemplateRegistry.capacityOf(customization).orElseThrow(
                () -> new IllegalStateException(
                        "This customization has never been calibrated; measure it first"));

        String costKey = customization.costKey();
        List<AtomVariant> pending = variants.findAll(profile).stream()
                .filter(variant -> !variant.getRenderCosts().containsKey(costKey))
                .toList();

        if (pending.isEmpty()) {
            return 0;
        }

        var request = new MeasurementRequest(pending.stream()
                .map(variant -> new MeasurementRequest.MeasurableItem(
                        variant.getId().toString(), variant.getContent()))
                .toList(),
                customization);

        Map<String, RenderCost> measured = measurements.measure(request);
        Instant measuredAt = Instant.now();
        int stored = 0;

        for (AtomVariant variant : pending) {
            RenderCost cost = measured.get(variant.getId().toString());
            if (cost == null) {
                // One missing measurement is not a reason to throw away the
                // rest: selection falls back to an estimate for this one and
                // says so, rather than the whole profile going unmeasured.
                continue;
            }
            variant.recordRenderCost(costKey, cost.totalPt(capacity.baselineSkipPt()), measuredAt);
            variants.save(profile, variant);
            stored++;
        }

        // Counts, never content.
        log.info("Measured {} of {} wordings for {}", stored, pending.size(), costKey);
        return stored;
    }
}
