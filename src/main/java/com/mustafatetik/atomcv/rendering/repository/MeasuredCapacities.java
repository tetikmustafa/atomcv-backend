package com.mustafatetik.atomcv.rendering.repository;

import com.mustafatetik.atomcv.rendering.domain.TemplateCapacity;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The capacities somebody has already paid a compilation for (Bolum 33.1).
 *
 * <p><strong>Unscoped, and it is not an exception to absolute rule 3.</strong>
 * That rule is about user data, and there is none here: a row says what a page
 * of a given font size and margin holds, which is the same answer whoever
 * asks. Nothing about a person can be read out of this table, and nothing in
 * it can be reached by guessing somebody's id -- the key is a geometry.
 *
 * <p>A facade rather than the Spring Data interface, because the interface is
 * a finder that takes any key and a save that writes any row, and this is two
 * methods with names that say what they are for.
 */
@Repository
public class MeasuredCapacities {

    private final TemplateCapacityJpaRepository jpa;

    MeasuredCapacities(TemplateCapacityJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Transactional(readOnly = true)
    public Optional<CapacityModel> find(String costKey) {
        return jpa.findById(costKey).map(TemplateCapacity::toCapacityModel);
    }

    /**
     * Writes what a calibration produced, replacing anything under the same
     * key.
     *
     * <p>Replacing rather than refusing: two workers can measure one geometry
     * at once -- the debounce is in a browser and does not bind a second tab --
     * and the two answers are the same seventeen numbers. Losing a race here
     * costs nothing, while a unique-key failure would fail a job for arriving
     * second with the right answer.
     */
    @Transactional
    public void store(String costKey, CapacityModel capacity) {
        jpa.save(new TemplateCapacity(costKey, capacity));
    }
}
