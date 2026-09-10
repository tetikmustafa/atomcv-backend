package com.mustafatetik.atomcv.rendering.measurement;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.profile.repository.SectionRepository;
import com.mustafatetik.atomcv.rendering.model.MeasurementRequest;
import com.mustafatetik.atomcv.rendering.model.ProfileHeaders;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.CapacityModel.RowShape;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final AtomRepository atoms;
    private final SectionRepository sections;
    private final MeasurementService measurements;
    private final Capacities capacities;

    RenderCostService(AtomVariantRepository variants, AtomRepository atoms,
            SectionRepository sections, MeasurementService measurements,
            Capacities capacities) {

        this.variants = variants;
        this.atoms = atoms;
        this.sections = sections;
        this.measurements = measurements;
        this.capacities = capacities;
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
    /**
     * Wordings only, for a caller with no profile entity to hand.
     *
     * <p>The header is left unmeasured, so selection falls back to the
     * template's constant for it. Every generation path passes the profile.
     */
    public int measureMissing(ProfileRef profile, TemplateCustomization customization) {
        return measureMissing(profile, customization, null, Locale.ENGLISH);
    }

    public int measureMissing(ProfileRef profile, TemplateCustomization customization,
            Profile head, Locale language) {
        // Resolved rather than found: a person who has just moved a slider has
        // no measured capacity yet, and this is where their atoms get costed.
        // Refusing here would have made Bolum 33.3's estimate unreachable --
        // the generation would fail before selection ever saw it, which is
        // exactly what the first end-to-end run of a moved slider did.
        CapacityModel capacity = capacities.resolve(customization)
                .map(Capacities.Resolved::capacity)
                .orElseThrow(() -> new IllegalStateException(
                        "This template has never been calibrated; measure it first"));

        String costKey = customization.costKey();
        List<AtomVariant> pending = variants.findAll(profile).stream()
                .filter(variant -> !variant.getRenderCosts().containsKey(costKey))
                .toList();

        // The header is text and wraps, so it belongs to the profile and the
        // language as much as to the geometry -- the contact labels are
        // translated, and "E-posta" is not as wide as "Email".
        String headerKey = headerKeyOf(costKey, language);
        boolean headerPending = head != null && !head.getHeaderCosts().containsKey(headerKey);

        if (pending.isEmpty() && !headerPending) {
            return 0;
        }

        Map<UUID, RowShape> shapeOfAtom = shapes(profile);
        var request = new MeasurementRequest(pending.stream()
                .map(variant -> new MeasurementRequest.MeasurableItem(
                        variant.getId().toString(), variant.getContent(),
                        shapeOfAtom.getOrDefault(variant.getAtomId(), RowShape.ENTRY_BULLET)))
                .toList(),
                customization,
                headerPending ? ProfileHeaders.of(head, language) : null);

        Map<String, RenderCost> measured = measurements.measure(request);
        Instant measuredAt = Instant.now();
        int stored = 0;

        if (headerPending) {
            RenderCost header = measured.get(MeasurementRequest.HEADER_KEY);
            if (header != null) {
                // The whole block, from the top of the page to the baseline
                // under it -- not a box's own height. See renderMeasurement.
                // Inside measureMissing's own transaction, and head is a
                // managed entity: recording it is the write.
                head.recordHeaderCost(headerKey, header.heightPt());
                stored++;
            }
        }

        for (AtomVariant variant : pending) {
            RenderCost cost = measured.get(variant.getId().toString());
            RowShape shape = shapeOfAtom.getOrDefault(
                    variant.getAtomId(), RowShape.ENTRY_BULLET);
            if (cost == null) {
                // One missing measurement is not a reason to throw away the
                // rest: selection falls back to an estimate for this one and
                // says so, rather than the whole profile going unmeasured.
                continue;
            }
            // A bullet pays the separation an itemize puts between two items;
            // an inline row shares a single item with its neighbours and pays
            // none. Five points a row, and a skills matrix is several rows.
            variant.recordRenderCost(costKey,
                    cost.totalPt(capacity.itemBaselineSkipPt(), capacity.rowSpacingPt(shape)),
                    measuredAt);
            variants.save(profile, variant);
            stored++;
        }

        // Counts, never content.
        log.info("Measured {} of {} wordings for {}", stored, pending.size(), costKey);
        return stored;
    }

    /**
     * Which layout each atom's section is set in, so a wording is measured the
     * way it will be printed (Bolum 22.4, rule 3).
     *
     * <p>Two extra reads per measuring job, and they buy the one thing the page
     * guarantee cannot do without: an {@code INLINE_LIST} row is printed with
     * its label in bold, and a measurement taken on the unbolded text reports a
     * row narrower than the page will hold. Wrong in the safe direction is
     * still wrong, and this one is wrong in the other.
     */
    /**
     * Where a measured header is filed: the geometry it was set at and the
     * language its labels were printed in.
     *
     * <p>Public because selection has to look under the same key, and two
     * spellings of one key is a cost that is written and never read.
     */
    public static String headerKeyOf(String costKey, Locale language) {
        return Profile.headerKey(costKey, language == null ? "en" : language.toLanguageTag());
    }

    private Map<UUID, RowShape> shapes(ProfileRef profile) {
        Map<UUID, SectionLayout> bySection = new HashMap<>();
        for (Section section : sections.findAll(profile)) {
            bySection.put(section.getId(), section.getLayout());
        }
        Map<UUID, RowShape> byAtom = new HashMap<>();
        for (Atom atom : atoms.findAll(profile)) {
            SectionLayout layout = bySection.get(atom.getSectionId());
            if (layout == null) {
                continue;
            }
            byAtom.put(atom.getId(), shapeOf(layout, atom.getEntryId() != null));
        }
        return byAtom;
    }

    /**
     * Which of the three shapes a page sets this atom in.
     *
     * <p>An inline section flattens everything it holds into one block, so
     * where the atom hangs does not matter there. Everywhere else it decides
     * the list's nesting, and the nesting decides the number.
     */
    public static RowShape shapeOf(SectionLayout layout, boolean inEntry) {
        if (layout == SectionLayout.INLINE_LIST) {
            return RowShape.INLINE_ROW_SHAPE;
        }
        return inEntry ? RowShape.ENTRY_BULLET : RowShape.SECTION_BULLET;
    }
}
