package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.phases.edit.EditPhase;
import com.mustafatetik.atomcv.generation.phases.edit.EditPlan;
import com.mustafatetik.atomcv.generation.phases.edit.NumberedLines;
import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.selection.AlternativeWording;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.shared.error.Result;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Faz G's natural-language half: a sentence, turned into a directive
 * (Bolum 24.2).
 *
 * <p>Everything expensive about it is one cheap call. The lines are read out
 * of the generation being edited rather than out of today's profile, numbered,
 * and shown to the model with the person's sentence; what comes back are
 * numbers. From there it is the same path a hand toggle takes, which is why
 * this class ends where {@link GenerationRerunService} begins.
 */
@Service
public class NaturalLanguageEditService {

    /**
     * How many held-back lines the model is shown.
     *
     * <p>A full profile can have hundreds of rejected atoms and a person
     * asking to put something back means something they remember writing, not
     * the four hundredth-best bullet. Sending all of them buys tokens and
     * noise: the more lines in the list, the more ways there are to pick the
     * wrong one, and Bolum 24.2's parse is supposed to be the cheapest call
     * the product makes.
     *
     * <p>Ranked by the score they competed on, so what is shown is what came
     * closest to the page.
     */
    private static final int HELD_BACK_SHOWN = 30;

    private final ProfileAssembler assembler;
    private final EditPhase edits;

    NaturalLanguageEditService(ProfileAssembler assembler, EditPhase edits) {
        this.assembler = assembler;
        this.edits = edits;
    }

    public String promptVersionFor(String bucketKey) {
        return edits.promptVersionFor(bucketKey);
    }

    /**
     * @param parent      the generation being edited, already read through a
     *                    scoped repository
     * @param instruction what the person typed
     */
    public Result<EditPlan> plan(
            GenerationSubject subject, Generation parent, String instruction, UUID jobId) {

        ProfileTree tree = assembler.load(subject.profile());
        StoredSelection snapshot = parent.getSelectionState();
        Tone tone = subject.head().getPreferences().writingStyle().tone();

        NumberedLines lines = number(tree, snapshot, parent.getRewrittenContent(), tone);

        return edits.parse(instruction, lines, subject.bucketKey(),
                subject.userId(), jobId);
    }

    /**
     * The page, then what did not fit — in that order, because that is the
     * order the person is looking at.
     *
     * <p>The text is what <em>this</em> generation printed as closely as the
     * data allows: Faz D's wording where there was one, and otherwise the
     * variant the snapshot named. Today's profile is the wrong source — the
     * person may have edited a bullet since, and a list that showed them a
     * sentence their CV does not contain would have them numbering the wrong
     * line (EK D.6.3).
     */
    private static NumberedLines number(
            ProfileTree tree, StoredSelection snapshot,
            RewrittenContent rewritten, Tone tone) {

        Map<UUID, AtomNode> byId = atomsById(tree);
        RewrittenContent wording = rewritten == null ? RewrittenContent.none() : rewritten;

        var page = new ArrayList<NumberedLines.Line>();
        for (SelectionState.SelectedAtom atom : snapshot.selected()) {
            textOf(byId.get(atom.atomId()), atom.variantId(), snapshot.language(), tone)
                    .map(text -> new NumberedLines.Line(atom.atomId(),
                            wording.covers(atom.atomId())
                                    ? wording.byAtom().get(atom.atomId()).plainText()
                                    : text))
                    .ifPresent(page::add);
        }

        var heldBack = new ArrayList<NumberedLines.Line>();
        snapshot.rejected().stream()
                // Ranked, then capped. Ties broken by id so that two reads of
                // one generation number the same lines the same way -- an
                // unstable list would make one recorded answer mean two
                // different things (Bolum 19.6, 53.3).
                .sorted(Comparator.comparingDouble(SelectionState.RejectedAtom::score).reversed()
                        .thenComparing(atom -> atom.atomId().toString()))
                .limit(HELD_BACK_SHOWN)
                .forEach(atom -> textOf(byId.get(atom.atomId()), null, snapshot.language(), tone)
                        .map(text -> new NumberedLines.Line(atom.atomId(), text))
                        .ifPresent(heldBack::add));

        return NumberedLines.of(page, heldBack);
    }

    /**
     * The wording this line was costed and printed with, or nothing when the
     * atom is gone from the profile.
     *
     * <p>An atom deleted since the generation simply does not appear in the
     * list. It cannot be put back and asking to drop it is already true, so
     * numbering it would only give the model a line the person cannot act on.
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
