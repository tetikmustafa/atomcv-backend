package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.selection.AlternativeWording;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.Tone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What a generation weighed, in words a person can read (Bolum 24.2, 24.4).
 *
 * <p>A selection state is ids and scores: it says which atoms competed and
 * which reached the page, and it says nothing anybody could look at. Both
 * halves of Faz G need the other thing — the sentence each line printed — and
 * they need it resolved the same way, because one of them numbers the lines
 * for a model and the other draws a toggle beside them. Two resolutions would
 * be two answers to "what does line four say".
 *
 * <p><strong>The text is what this generation printed, as closely as the data
 * allows.</strong> Faz D's wording where there was one, otherwise the variant
 * the snapshot named, otherwise the wording the alternative picker would have
 * chosen. Today's profile is the wrong source on its own: the person may have
 * edited a bullet since, and a list showing them a sentence their CV does not
 * contain would have them acting on the wrong line (EK D.6.3).
 *
 * <p><strong>Held-back lines carry the profile's wording, not Faz D's.</strong>
 * They were not printed by this generation, so there is nothing it printed to
 * show; an edit that puts one back re-runs Faz D over it and may word it
 * differently.
 *
 * <p>An atom deleted from the profile since simply does not appear. It cannot
 * be put back and asking to drop it is already true, so a line for it would be
 * one nobody can act on.
 */
public final class WeighedLines {

    private WeighedLines() {
    }

    /**
     * One candidate, with the text it competed as and why it stands where it
     * stands (P7).
     *
     * @param onPage          whether it reached the page of this generation
     * @param matchedKeywords which of the posting's terms this line carries.
     *                        Empty in general mode, and empty on a generation
     *                        made before Faz B recorded them
     * @param heldBackReason  why it did not reach the page, or null when it
     *                        did. The four values are opposite instructions to
     *                        the reader -- "there was no room" invites a longer
     *                        page, "you switched it off" invites the profile
     *                        editor, and one word of explanation is the
     *                        difference
     */
    public record Line(UUID atomId, String text, boolean onPage,
            List<String> matchedKeywords,
            SelectionState.RejectionReason heldBackReason) {
    }

    /**
     * The page first and what did not fit after it, because that is the order
     * the person is looking at.
     *
     * <p>Held-back lines are ranked by the score they competed on, ties broken
     * by id, so that two reads of one generation produce one order — an
     * unstable list would renumber a model's prompt between two runs (Bolum
     * 19.6, 53.3) and move a toggle under somebody's cursor.
     *
     * @param rewritten Faz D's wording, or null when nothing was rewritten
     */
    public static List<Line> of(
            ProfileTree tree, StoredSelection snapshot,
            RewrittenContent rewritten, Tone tone) {

        Map<UUID, AtomNode> byId = atomsById(tree);
        RewrittenContent wording = rewritten == null ? RewrittenContent.none() : rewritten;

        var lines = new ArrayList<Line>();
        for (SelectionState.SelectedAtom atom : snapshot.selected()) {
            textOf(byId.get(atom.atomId()), atom.variantId(), snapshot.language(), tone)
                    .map(text -> new Line(atom.atomId(),
                            wording.covers(atom.atomId())
                                    ? wording.byAtom().get(atom.atomId()).plainText()
                                    : text,
                            true, atom.matchedKeywords(), null))
                    .ifPresent(lines::add);
        }

        snapshot.rejected().stream()
                .sorted(Comparator.comparingDouble(SelectionState.RejectedAtom::score).reversed()
                        .thenComparing(atom -> atom.atomId().toString()))
                .forEach(atom -> textOf(byId.get(atom.atomId()), null, snapshot.language(), tone)
                        // A held-back line has no matched terms recorded: only
                        // the chosen ones are written to the snapshot, and
                        // inventing them here would mean re-running Faz B --
                        // the one thing Bolum 24.1 says not to do.
                        .map(text -> new Line(atom.atomId(), text, false,
                                List.of(), atom.reason()))
                        .ifPresent(lines::add));

        return List.copyOf(lines);
    }

    /**
     * The wording this line was costed and printed with, or nothing when the
     * atom is gone from the profile.
     */
    private static Optional<String> textOf(
            AtomNode node, UUID variantId, String language, Tone tone) {

        if (node == null) {
            return Optional.empty();
        }
        if (variantId != null) {
            Optional<String> named = node.variants().stream()
                    .filter(variant -> variant.getId().equals(variantId))
                    .findFirst()
                    .map(variant -> variant.getContent().plainText());
            if (named.isPresent()) {
                return named;
            }
        }
        return AlternativeWording.pick(node, language, tone)
                .map(AtomVariant::getContent)
                .map(content -> content.plainText());
    }

    private static Map<UUID, AtomNode> atomsById(ProfileTree tree) {
        var byId = new HashMap<UUID, AtomNode>();
        for (ProfileTree.SectionNode section : tree.sections()) {
            index(section.atoms(), byId);
            for (ProfileTree.EntryNode entry : section.entries()) {
                index(entry.atoms(), byId);
            }
        }
        return byId;
    }

    private static void index(List<AtomNode> atoms, Map<UUID, AtomNode> byId) {
        for (AtomNode node : atoms) {
            byId.put(node.atom().getId(), node);
        }
    }
}
