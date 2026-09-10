package com.mustafatetik.atomcv.rendering.measurement;

import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.rendering.latex.LatexDocumentRenderer;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * What a page of this shape can hold, measured (Bolum 26.4, 33.1).
 *
 * <p>The arithmetic here was written twice in the test lane and nowhere in the
 * product, which was correct while there were two templates at two fixed
 * settings: somebody measured them once and wrote the numbers down. Layer B
 * ends that. A person moving a slider asks for a geometry nobody has ever
 * compiled, and the answer has to be produced while they wait rather than
 * committed to a source file.
 *
 * <p><strong>Every number is a difference between two probes.</strong> The
 * calibration document prints the page's position before and after each piece
 * of furniture; what a section heading costs is where the page was after it
 * minus where it was before. Nothing here is derived from another template's
 * numbers by scaling, because the negative spacing a preamble writes does not
 * scale — {@code TemplateRegistry}'s own constants were measured this way and
 * {@code CalibrationServiceIT} checks that this reproduces them exactly.
 *
 * <p>One compilation, and it is the expensive part: this is why Bolum 33.3
 * puts the measurement behind a debounce and lets a generation run on an
 * estimate until it lands.
 */
@Service
public class CalibrationService {

    private static final Logger log = LoggerFactory.getLogger(CalibrationService.class);

    private final LatexDocumentRenderer renderer;
    private final LatexCompilerClient compiler;

    CalibrationService(LatexDocumentRenderer renderer, LatexCompilerClient compiler) {
        this.renderer = renderer;
        this.compiler = compiler;
    }

    /**
     * @return what the page holds at these settings, or empty when the
     *         calibration document did not compile. Empty is not a capacity of
     *         zero and must never be treated as one: a page guarantee made
     *         against numbers nobody produced is not a guarantee.
     */
    public Optional<CapacityModel> measure(TemplateCustomization customization) {
        String log;
        try {
            log = compiler.measure(renderer.renderCalibration(customization).value());
        } catch (CompilationException failed) {
            // Counts and the kind, never the source (absolute rule 4) -- though
            // there is no user content in a calibration document at all, which
            // is what makes it safe to compile on demand.
            CalibrationService.log.warn("Calibration did not compile for {}: {}",
                    customization.costKey(), failed.kind());
            return Optional.empty();
        }

        Map<String, Double> probes = TexLogParser.parseCalibration(log);
        if (probes.isEmpty()) {
            CalibrationService.log.warn("Calibration for {} produced no probes",
                    customization.costKey());
            return Optional.empty();
        }
        if (ranPastThePage(probes)) {
            return Optional.empty();
        }
        return Optional.of(derive(probes));
    }

    /**
     * The seventeen numbers, in the order the calibration document lays its
     * probes out.
     *
     * <p>Read this against {@code LatexCalibrationIT}: every line below is one
     * of its assertions with the assertion taken off. That duplication is
     * deliberate and worth keeping — the test derives the numbers its own way
     * and compares them to what is stored, so if this method is ever wrong,
     * the two disagree instead of agreeing about the same mistake.
     */
    private static CapacityModel derive(Map<String, Double> probes) {
        double sectionHeader = delta(probes, "afterHeaderBlock", "afterSection");
        double sectionOne = delta(probes, "afterSection", "afterListUnderSection");
        double sectionThree =
                delta(probes, "beforeThreeUnderSection", "afterThreeUnderSection");
        double sectionItemLine = (sectionThree - sectionOne) / 2;

        double entryHeader = delta(probes, "beforeBareEntry", "afterBareEntry");
        double oneEntry = delta(probes, "beforeOneEntry", "afterOneEntry");
        double itemLine =
                (delta(probes, "beforeEntryThreeItems", "afterEntryThreeItems") - oneEntry) / 2;
        double itemizeOverhead = oneEntry - entryHeader - itemLine;
        // What every entry and project probe carries besides its heading: one
        // bullet, in a list.
        double bulletAndItsList = itemizeOverhead + itemLine;

        double oneProject = delta(probes, "beforeOneProject", "afterOneProject");
        double inlineOne = delta(probes, "beforeInlineOne", "afterInlineOne");
        double inlineRow =
                (delta(probes, "beforeInlineThree", "afterInlineThree") - inlineOne) / 2;

        var fixed = new LinkedHashMap<String, Double>();
        fixed.put(CapacityModel.HEADER_BLOCK, delta(probes, "start", "afterHeaderBlock"));
        fixed.put(CapacityModel.SECTION_HEADER, sectionHeader);
        // The probe for this was already in the document and the derivation
        // simply never read it: between afterOneEntry and beforeTwoEntries
        // stands a section heading with an entry list closed above it, which is
        // the position every heading but the first is actually in.
        fixed.put(CapacityModel.SECTION_HEADER_AFTER_LIST,
                delta(probes, "afterOneEntry", "beforeTwoEntries"));
        fixed.put(CapacityModel.ENTRY_HEADER, entryHeader);
        fixed.put(CapacityModel.ENTRY_HEADER_AFTER_LIST,
                delta(probes, "beforeTwoEntries", "afterTwoEntries") - oneEntry - bulletAndItsList);
        fixed.put(CapacityModel.PROJECT_HEADING, oneProject - bulletAndItsList);
        fixed.put(CapacityModel.PROJECT_HEADING_AFTER_LIST,
                delta(probes, "beforeTwoProjects", "afterTwoProjects")
                        - oneProject - bulletAndItsList);
        fixed.put(CapacityModel.ITEMIZE_OVERHEAD, itemizeOverhead);
        fixed.put(CapacityModel.ITEM_LINE, itemLine);
        fixed.put(CapacityModel.SECTION_ITEM_LINE, sectionItemLine);
        fixed.put(CapacityModel.SECTION_LIST_OVERHEAD, sectionOne - sectionItemLine);
        // Charged to the list rather than to the heading below it, which is
        // where CapacityModel.SECTION_LIST_CLOSE says why.
        fixed.put(CapacityModel.SECTION_LIST_CLOSE,
                delta(probes, "afterListUnderSection", "beforeThreeUnderSection") - sectionHeader);
        fixed.put(CapacityModel.PARAGRAPH_LIST_OVERHEAD,
                delta(probes, "beforeParagraphOne", "afterParagraphOne") - sectionItemLine);
        fixed.put(CapacityModel.INLINE_ROW, inlineRow);
        fixed.put(CapacityModel.INLINE_LIST_OVERHEAD, inlineOne - inlineRow);

        return new CapacityModel(
                probes.get("textheight"),
                probes.get("textwidth"),
                probes.get("baselineskip"),
                probes.get("itembaselineskip"),
                fixed);
    }

    /**
     * Whether the calibration document overflowed onto a second page.
     *
     * <p><strong>Every number here is a difference between two readings of
     * {@code \pagetotal}, and that counts the page it is on.</strong> When the
     * document breaks, the reading after the break is small and the difference
     * comes out hugely negative — a project heading measured at −646.7pt, which
     * is what a roomy geometry produced the first time one was tried.
     *
     * <p>Refused rather than repaired. A capacity is what the page guarantee is
     * made against; storing one with a negative six hundred in it would charge
     * a piece of furniture as a credit and over-fill every page at those
     * settings, and nothing downstream would ever say so. Empty means "nobody
     * has measured this", the caller falls back to
     * {@link CapacityEstimator}'s scaled guess with its margin, and the person
     * still gets a CV.
     *
     * <p>The threshold is zero rather than a tolerance: the probes are laid out
     * in the order the page is built, so no honest pair of them runs backwards.
     * A piece of furniture can cost negative points — several do, and they are
     * single digits — but the page position between two probes only ever moves
     * forward.
     */
    private static boolean ranPastThePage(Map<String, Double> probes) {
        for (String[] pair : ORDERED_PROBES) {
            Double before = probes.get(pair[0]);
            Double after = probes.get(pair[1]);
            if (before != null && after != null && after < before) {
                log.warn("The calibration document ran past its page between {} and {}"
                        + " ({} to {}); this geometry cannot be measured",
                        pair[0], pair[1], before, after);
                return true;
            }
        }
        return false;
    }

    /** The probes in the order the page lays them down, pairwise. */
    private static final String[][] ORDERED_PROBES = {
            {"start", "afterHeaderBlock"},
            {"afterHeaderBlock", "afterSection"},
            {"afterSection", "afterListUnderSection"},
            {"afterListUnderSection", "beforeThreeUnderSection"},
            {"beforeThreeUnderSection", "afterThreeUnderSection"},
            {"afterThreeUnderSection", "beforeBareEntry"},
            {"beforeBareEntry", "afterBareEntry"},
            {"beforeOneEntry", "afterOneEntry"},
            {"afterOneEntry", "beforeTwoEntries"},
            {"beforeEntryThreeItems", "afterEntryThreeItems"},
            {"beforeTwoEntries", "afterTwoEntries"},
            {"beforeOneProject", "afterOneProject"},
            {"beforeTwoProjects", "afterTwoProjects"},
            {"beforeParagraphOne", "afterParagraphOne"},
            {"beforeInlineOne", "afterInlineOne"},
            {"beforeInlineThree", "afterInlineThree"},
    };

    private static double delta(Map<String, Double> probes, String from, String to) {
        Double before = probes.get(from);
        Double after = probes.get(to);
        if (before == null || after == null) {
            // A probe the calibration document stopped printing. Better to fail
            // here than to write a capacity with a hole in it: the hole would
            // be charged as zero and the page would be over-filled by exactly
            // the piece of furniture nobody measured.
            throw new IllegalStateException(
                    "The calibration document printed no probe for "
                            + (before == null ? from : to));
        }
        return after - before;
    }
}
