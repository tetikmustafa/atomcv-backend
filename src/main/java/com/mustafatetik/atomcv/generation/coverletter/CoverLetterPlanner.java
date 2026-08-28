package com.mustafatetik.atomcv.generation.coverletter;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.EntryNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The letter's input, read off the page (Bolum 34.2), without an LLM.
 *
 * <p>Pure and static, like the other planners: a profile, a selection and an
 * analysis go in, the constraints come out. Everything it collects it collects
 * from the atoms selection <em>kept</em>, for the reason Bolum 34.2 gives —
 * the letter is the narrative version of the CV, and a letter drawn from the
 * profile instead would tell a story about a page the employer is not holding.
 */
public final class CoverLetterPlanner {

    /** Bolum 34.3: "the two or three highest-scoring atoms", and this is the three. */
    static final int EVIDENCE_COUNT = 3;

    private CoverLetterPlanner() {
    }

    /**
     * @param posting     Faz A's reading of the job
     * @param companyNote what the person told us about this employer
     *                    (Bolum 34.5), or blank
     */
    public static CoverLetterInput plan(
            Profile profile,
            ProfileTree tree,
            SelectionState selection,
            JobAnalysis posting,
            String companyNote,
            String language,
            String tone,
            LocalDate today) {

        Map<UUID, SelectedAtom> selected = new java.util.HashMap<>();
        for (SelectedAtom atom : selection.selected()) {
            selected.put(atom.atomId(), atom);
        }

        List<Scored> onThePage = new ArrayList<>();
        Set<String> skills = new LinkedHashSet<>();
        Set<String> metrics = new LinkedHashSet<>();
        Set<String> employers = new LinkedHashSet<>();
        List<Entry> entries = new ArrayList<>();

        for (SectionNode section : tree.sections()) {
            collect(section.atoms(), selected, onThePage, skills, metrics);
            for (EntryNode entry : section.entries()) {
                entries.add(entry.entry());
                if (entry.entry().getOrganization() != null
                        && !entry.entry().getOrganization().isBlank()) {
                    employers.add(entry.entry().getOrganization().strip());
                }
                collect(entry.atoms(), selected, onThePage, skills, metrics);
            }
        }

        onThePage.sort(Comparator.comparingDouble(Scored::score).reversed()
                // Two atoms at the same score must not swap between runs
                // (design principle 2).
                .thenComparing(scored -> scored.atomId().toString()));

        List<CoverLetterInput.Evidence> evidence = onThePage.stream()
                .limit(EVIDENCE_COUNT)
                .map(Scored::evidence)
                .toList();

        Contact contact = profile.getContact() == null ? Contact.EMPTY : profile.getContact();
        return new CoverLetterInput(
                contact.name(),
                posting == null ? "" : posting.role().title(),
                posting == null || posting.company() == null ? "" : posting.company().name(),
                evidence, List.copyOf(skills), List.copyOf(metrics), List.copyOf(employers),
                yearsWorking(entries, today), companyNote, language, tone);
    }

    private static void collect(
            List<AtomNode> atoms,
            Map<UUID, SelectedAtom> selected,
            List<Scored> onThePage,
            Set<String> skills,
            Set<String> metrics) {

        for (AtomNode node : atoms) {
            SelectedAtom chosen = selected.get(node.atom().getId());
            if (chosen == null) {
                continue;
            }
            skills.addAll(node.atom().getSkills());
            metrics.addAll(node.atom().getMetrics());
            wordingOf(node, chosen.variantId())
                    .filter(wording -> !wording.getContent().isEmpty())
                    .ifPresent(wording -> onThePage.add(new Scored(
                            node.atom().getId(), chosen.score(),
                            new CoverLetterInput.Evidence(wording.getContent().plainText(),
                                    node.atom().getSkills(), node.atom().getMetrics()))));
        }
    }

    /**
     * The wording selection recorded, with the renderer's own fallback. The
     * letter quotes what is on the page, so it has to read the page the same
     * way Faz E does.
     */
    private static Optional<AtomVariant> wordingOf(AtomNode node, UUID variantId) {
        return node.variants().stream()
                .filter(variant -> variant.getId().equals(variantId))
                .findFirst()
                .or(node::primaryVariant);
    }

    /**
     * How long this person has been working, from the earliest start date on
     * the profile to the latest end date — or to today, for anything still
     * running.
     *
     * <p><strong>The span, not the sum.</strong> Two jobs held at once are two
     * entries and one stretch of a life; adding them would hand the letter a
     * larger number than the truth, which is the fabrication Bolum 34.4 names.
     * Undated entries contribute nothing rather than a guess.
     */
    static int yearsWorking(List<Entry> entries, LocalDate today) {
        LocalDate earliest = null;
        LocalDate latest = null;
        for (Entry entry : entries) {
            LocalDate start = entry.getStartDate();
            if (start == null) {
                continue;
            }
            LocalDate end = entry.getEndDate() == null ? today : entry.getEndDate();
            earliest = earliest == null || start.isBefore(earliest) ? start : earliest;
            latest = latest == null || end.isAfter(latest) ? end : latest;
        }
        if (earliest == null || latest == null || latest.isBefore(earliest)) {
            return 0;
        }
        return (int) ChronoUnit.YEARS.between(earliest, latest);
    }

    private record Scored(UUID atomId, double score, CoverLetterInput.Evidence evidence) {
    }
}
