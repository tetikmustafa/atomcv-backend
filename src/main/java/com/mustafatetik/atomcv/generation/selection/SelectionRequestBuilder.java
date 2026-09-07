package com.mustafatetik.atomcv.generation.selection;

import com.mustafatetik.atomcv.generation.scoring.AtomScoreSource;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.AtomCandidate;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.EntryPlan;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.SectionPlan;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.EntryNode;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.measurement.RenderCostEstimator;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A profile, as numbers selection can work on (Bolum 19.4, 20.2).
 *
 * <p>{@link SelectionPhase} takes scores and costs and knows nothing about
 * profiles; this is the piece that knows both. It reads what the user marked —
 * inactive rows, locks, the minimum an entry is worth printing at — and turns
 * each atom into a score and a number of points.
 *
 * <p>Pure and deterministic. The date is a parameter and the wordings come
 * from the tree, so the same profile on the same day produces the same request
 * every time (Bolum 19.6).
 */
public final class SelectionRequestBuilder {

    private SelectionRequestBuilder() {
    }

    /**
     * @param estimatedAtoms how many atoms were charged an estimate rather
     *                       than a measurement (Bolum 26.5). Worth watching:
     *                       a generation full of estimates is one the
     *                       measurement job did not reach in time.
     * @param withoutWording atoms that have no wording in any language and so
     *                       cannot be rendered at all
     */
    public record BuiltRequest(
            SelectionRequest request, int estimatedAtoms, int withoutWording) {
    }

    /**
     * General CV mode: rank on what the profile says about itself
     * (Bolum 19.4).
     */
    public static BuiltRequest build(
            ProfileTree tree,
            TemplateCustomization customization,
            CapacityModel capacity,
            int maxPages,
            String language,
            Tone tone,
            LocalDate today) {

        return build(tree, customization, capacity, maxPages, language, tone,
                AtomScoreSource.generalMode(today));
    }

    /**
     * @param tone   how the profile asked to sound (Bolum 21.1). It belongs
     *               here rather than in Faz D because the wording it chooses
     *               is the one this charges the budget for: a tone applied
     *               after selection would print a line nothing had costed
     * @param scores where each atom's score comes from — Faz B against a
     *               posting, or the general-mode scorer when there is none.
     *               It is the only difference between the two modes: the
     *               algorithm below reads numbers and does not know which
     *               produced them (Bolum 19.4).
     */
    public static BuiltRequest build(
            ProfileTree tree,
            TemplateCustomization customization,
            CapacityModel capacity,
            int maxPages,
            String language,
            Tone tone,
            AtomScoreSource scores) {

        var run = new Run(customization, capacity, language, tone, scores);
        List<SectionPlan> sections = new ArrayList<>();

        for (SectionNode section : tree.sections()) {
            // A section the user switched off contributes nothing, not even a
            // rejection: it is not part of this CV at all (Bolum 20.2,
            // constraint 3).
            if (!section.section().isActive()) {
                continue;
            }

            // The renderer prints an INLINE_LIST section as one block and
            // discards its entry titles, so its rows are filed here in the
            // order it writes them: the section's own atoms, then every
            // entry's. See Run#inlineRows for what that is worth in points.
            boolean inline = section.section().getLayout() == SectionLayout.INLINE_LIST;
            List<AtomCandidate> loose = new ArrayList<>(run.candidates(section.atoms(), null));

            List<EntryPlan> entries = new ArrayList<>();
            for (EntryNode entry : section.entries()) {
                if (!entry.entry().isActive()) {
                    continue;
                }
                if (inline) {
                    loose.addAll(run.inlineRows(entry.atoms(), entry.entry()));
                    continue;
                }
                List<AtomCandidate> candidates = run.candidates(entry.atoms(), entry.entry());
                if (candidates.isEmpty()) {
                    // No bullets to offer, so the entry itself is the offer.
                    // Until this existed the loop skipped it and a degree line
                    // could not reach the page at all (Bolum 20.2).
                    candidates = List.of(run.heading(entry.entry()));
                }
                short minAtoms = entry.entry().getMinAtoms();
                if (entry.entry().isAlwaysInclude()) {
                    // "This job stays on the CV" is a statement about the
                    // entry, and SelectionPhase only understands atom locks.
                    // The budget meaning of the two is the same: the heading
                    // plus the minimum it is worth printing at (EK D.8.7).
                    candidates = pinBest(candidates, Math.max(1, minAtoms));
                }
                // A project is the entry with no employer, no place and no
                // dates, and the renderer gives it a one-line heading for
                // exactly that reason. Asking the data here rather than the
                // section's name is what keeps the two in step.
                boolean bare = isBlank(entry.entry().getOrganization())
                        && isBlank(entry.entry().getLocation())
                        && entry.entry().getStartDate() == null;
                entries.add(new EntryPlan(
                        entry.entry().getId(), minAtoms, bare, candidates));
            }

            if (section.section().isAlwaysInclude()) {
                if (!loose.isEmpty()) {
                    loose = pinBest(loose, 1);
                } else if (!entries.isEmpty()) {
                    EntryPlan first = entries.get(0);
                    entries.set(0, new EntryPlan(first.entryId(), first.minAtoms(),
                            first.bare(), pinBest(first.atoms(), 1)));
                }
            }

            if (!loose.isEmpty() || !entries.isEmpty()) {
                SectionKind kind = section.section().getKind();
                sections.add(new SectionPlan(section.section().getId(),
                        section.section().isAlwaysInclude(),
                        SectionFloor.priorityOf(kind), SectionFloor.forKind(kind),
                        section.section().getLayout(), entries, loose));
            }
        }

        // The order the page is built and printed in (SectionFloor). A stable
        // sort, so two sections the order does not name keep the profile's own
        // arrangement between them rather than swapping run to run.
        sections.sort(Comparator.comparingInt(SectionPlan::priority));

        return new BuiltRequest(
                new SelectionRequest(sections, maxPages, capacity),
                run.estimated, run.withoutWording);
    }

    /**
     * Pins the highest-scoring candidates, ties broken by id so that two runs
     * of the same profile pin the same ones (Bolum 19.6).
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<AtomCandidate> pinBest(List<AtomCandidate> candidates, int count) {
        List<AtomCandidate> ranked = candidates.stream()
                .filter(AtomCandidate::active)
                .sorted(Comparator.comparingDouble(AtomCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.atomId().toString()))
                .limit(count)
                .toList();

        return candidates.stream()
                .map(candidate -> ranked.contains(candidate) && !candidate.alwaysInclude()
                        ? pin(candidate)
                        : candidate)
                .toList();
    }

    private static AtomCandidate pin(AtomCandidate candidate) {
        return new AtomCandidate(candidate.atomId(), candidate.variantId(), candidate.entryId(),
                candidate.score(), candidate.renderCostPt(), true, candidate.active(),
                candidate.contentKey(), candidate.headerOnly());
    }

    /** One build, and the counters it fills in as it goes. */
    private static final class Run {

        private final TemplateCustomization customization;
        private final CapacityModel capacity;
        private final String costKey;
        private final String language;
        private final Tone tone;
        private final AtomScoreSource scores;

        private int estimated;
        private int withoutWording;

        Run(TemplateCustomization customization, CapacityModel capacity,
                String language, Tone tone, AtomScoreSource scores) {

            this.customization = customization;
            this.capacity = capacity;
            this.costKey = customization.costKey();
            this.language = language;
            this.tone = tone;
            this.scores = scores;
        }

        List<AtomCandidate> candidates(List<AtomNode> atoms, Entry entry) {
            List<AtomCandidate> candidates = new ArrayList<>();
            for (AtomNode node : atoms) {
                // Bolum 21.1's choice, made here so that the variant costed is
                // the variant printed and the variant Faz D rewrites.
                Optional<AtomVariant> wording = AlternativeWording.pick(node, language, tone);
                if (wording.isEmpty()) {
                    // Nothing to render and nothing to measure. Counted rather
                    // than silently dropped: it means an atom was written
                    // without content, which is a defect upstream.
                    withoutWording++;
                    continue;
                }
                AtomVariant variant = wording.get();
                candidates.add(new AtomCandidate(
                        node.atom().getId(),
                        variant.getId(),
                        entry == null ? null : entry.getId(),
                        scores.scoreOf(node.atom(), entry),
                        costOf(variant),
                        node.atom().isAlwaysInclude(),
                        node.atom().isActive(),
                        // Bolum 20.3's tie-break, derived from the wording
                        // rather than from an id that is minted fresh on every
                        // import. The column is already there and already a
                        // digest of exactly this content -- nothing new is
                        // computed and nothing readable travels.
                        variant.getContentHash()));
            }
            return candidates;
        }

        /**
         * The rows of an {@code INLINE_LIST} entry, filed where the renderer
         * prints them (Bolum 33.4).
         *
         * <p>Scored <em>with</em> the entry, because that is what the row
         * belongs to and where a date would be found; filed <em>without</em>
         * it, because {@code LatexDocumentRenderer.inlineList} flattens every
         * entry of such a section into a single block and prints no heading and
         * opens no list of its own for any of them.
         *
         * <p>The gap was worth a quarter of a page. Selection charged each row
         * {@code ENTRY_HEADER_AFTER_LIST} plus {@code ITEMIZE_OVERHEAD} —
         * 35.43 pt measured against the compiler — so a five-row Tech Stack
         * reserved 169.55 pt of a 708 pt page for furniture that is never set.
         * The page then came out short and the sections that could not be
         * opened were reported as {@code BUDGET} rejections, which was true of
         * the arithmetic and false of the document.
         *
         * <p>The entry's own lock travels down to its rows. It has nowhere else
         * to go — the entry does not reach {@code SelectionPhase} at all — and
         * a lock that quietly stops meaning anything is worse than one that was
         * never set.
         */
        List<AtomCandidate> inlineRows(List<AtomNode> atoms, Entry entry) {
            List<AtomCandidate> rows = new ArrayList<>();
            for (AtomCandidate row : candidates(atoms, entry)) {
                rows.add(new AtomCandidate(
                        row.atomId(), row.variantId(), null, row.score(), row.renderCostPt(),
                        row.alwaysInclude() || entry.isAlwaysInclude(), row.active(),
                        row.contentKey()));
            }
            return rows;
        }

        /**
         * The entry as its own candidate, for one that has no bullets at all
         * (Bolum 20.2).
         *
         * <p>Its score comes from the entry rather than from any atom, because
         * there is no atom — see {@link AtomScoreSource#scoreOfEntry}. Its
         * tie-break is a digest of the heading, for the same reason every other
         * tie-break here is derived from content: entry ids are minted fresh on
         * every import, and two degree lines that score and cost the same would
         * otherwise swap places between two reads of one CV (Bolum 20.3).
         */
        AtomCandidate heading(Entry entry) {
            return AtomCandidate.forEntryHeader(entry.getId(), scores.scoreOfEntry(entry),
                    RichContent.plain(headingText(entry)).contentHash());
        }

        /** Everything the heading prints, which is exactly what identifies it. */
        private static String headingText(Entry entry) {
            return String.join("|",
                    orEmpty(entry.getTitle()),
                    orEmpty(entry.getOrganization()),
                    orEmpty(entry.getLocation()),
                    String.valueOf(entry.getStartDate()),
                    String.valueOf(entry.getEndDate()));
        }

        private static String orEmpty(String value) {
            return value == null ? "" : value;
        }

        private double costOf(AtomVariant variant) {
            Double measured = variant.getRenderCosts().get(costKey);
            if (measured != null) {
                return measured;
            }
            estimated++;
            return RenderCostEstimator.estimateBulletPt(
                    variant.getContent(), customization, capacity);
        }
    }
}
