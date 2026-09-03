package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What the About paragraph would be written from (Bolum 21.7), decided without
 * an LLM.
 *
 * <p><strong>Ekleme — the About is synthesised only where one already
 * exists.</strong> Bolum 21.7 says how to write the paragraph and is silent on
 * where it goes. It cannot be invented here: selection costed the atoms it
 * chose and promised a page limit on the strength of that, and a paragraph
 * that no section held is a block the budget never accounted for. A person who
 * switched their About off has also already answered the question.
 *
 * <p>So this looks for an About paragraph that selection kept, and everything
 * it may say is read off the rest of that same selection — the skills and
 * numbers that are going on the page, not the ones in the profile. A summary
 * naming a technology that got dropped for budget describes a CV the employer
 * is not holding.
 */
public final class AboutSynthesis {

    /**
     * Bolum 21.7's "~65 words", as characters. Six characters a word plus the
     * space is the usual English average and Turkish runs longer, so this is
     * generous rather than exact — the number that actually binds is the
     * measured one below.
     */
    static final int ABOUT_BUDGET_CHARS = 65 * 7;

    private AboutSynthesis() {
    }

    /**
     * @return the About worth writing, or empty when this profile has none on
     *         the page, when nothing was selected to write it from, or when
     *         the person marked it verbatim
     */
    public static Optional<AboutCandidate> plan(
            ProfileTree tree, SelectionState selection, RewriteContext context) {

        String ownWords = context.ownWords();

        Set<UUID> selected = new LinkedHashSet<>();
        for (SelectedAtom atom : selection.selected()) {
            selected.add(atom.atomId());
        }

        AtomNode about = null;
        Set<String> skills = new LinkedHashSet<>();
        List<String> metrics = new ArrayList<>();

        for (SectionNode section : tree.sections()) {
            boolean isAbout = section.section().getKind() == SectionKind.ABOUT;
            for (AtomNode node : atomsOf(section)) {
                if (!selected.contains(node.atom().getId())) {
                    continue;
                }
                if (isAbout) {
                    // The first one is the paragraph that gets rewritten:
                    // synthesising each of them from the same input would
                    // print the same paragraph twice.
                    about = about == null ? node : about;
                }
                // Every selected atom feeds the union, About included.
                // Bolum 21.7 says the input is "seçilmiş atomların skills +
                // metrics birleşimi" and this skipped the About section's own,
                // so a paragraph reworded from itself could be refused for
                // keeping a technology it already named. A profile with four
                // About paragraphs — which is what real ones have, whatever
                // the comment here used to claim — lost the other three from
                // the union entirely.
                skills.addAll(node.atom().getSkills());
                metrics.addAll(node.atom().getMetrics());
            }
        }

        if (about == null || about.atom().isVerbatim()) {
            return Optional.empty();
        }
        if (skills.isEmpty() && (ownWords == null || ownWords.isBlank())) {
            // Nothing on the page and nothing the person said about
            // themselves. There is no synthesis to make, only an invention.
            return Optional.empty();
        }

        UUID variantId = variantIdOf(selection, about.atom().getId());
        Optional<AtomVariant> wording = about.variants().stream()
                .filter(variant -> variant.getId().equals(variantId))
                .findFirst()
                .or(about::primaryVariant);
        if (wording.isEmpty() || wording.get().getContent().isEmpty()) {
            return Optional.empty();
        }

        String original = wording.get().getContent().plainText();
        return Optional.of(new AboutCandidate(
                about.atom().getId(), wording.get().getContent(),
                List.copyOf(skills), List.copyOf(metrics), ownWords,
                context.postingFocus(),
                Math.min(ABOUT_BUDGET_CHARS, RewritePlanner.maxCharsFor(original))));
    }

    private static List<AtomNode> atomsOf(SectionNode section) {
        List<AtomNode> atoms = new ArrayList<>(section.atoms());
        section.entries().forEach(entry -> atoms.addAll(entry.atoms()));
        return atoms;
    }

    private static UUID variantIdOf(SelectionState selection, UUID atomId) {
        return selection.selected().stream()
                .filter(atom -> atom.atomId().equals(atomId))
                .map(SelectedAtom::variantId)
                .findFirst()
                .orElse(null);
    }
}
