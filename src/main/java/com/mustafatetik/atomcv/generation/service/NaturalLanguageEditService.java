package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.phases.edit.EditPhase;
import com.mustafatetik.atomcv.generation.phases.edit.EditPlan;
import com.mustafatetik.atomcv.generation.phases.edit.NumberedLines;
import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.shared.error.Result;
import java.util.List;
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
     *
     * <p><strong>The cap is this endpoint's, not the selection's.</strong>
     * {@code GET /generations/{id}/selection} publishes every line for the same
     * generation, because a toggle a person can see is a toggle they meant and
     * a list is not a prompt (F-031).
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
     * <p>{@link WeighedLines} resolves the text, and it is the same resolution
     * the selection endpoint draws its toggles from: one answer to "what does
     * this line say", however it is being asked (F-031).
     */
    private static NumberedLines number(
            ProfileTree tree, StoredSelection snapshot,
            RewrittenContent rewritten, Tone tone) {

        List<WeighedLines.Line> weighed = WeighedLines.of(tree, snapshot, rewritten, tone);

        List<NumberedLines.Line> page = weighed.stream()
                .filter(WeighedLines.Line::onPage)
                .map(line -> new NumberedLines.Line(line.atomId(), line.text()))
                .toList();

        List<NumberedLines.Line> heldBack = weighed.stream()
                .filter(line -> !line.onPage())
                .limit(HELD_BACK_SHOWN)
                .map(line -> new NumberedLines.Line(line.atomId(), line.text()))
                .toList();

        return NumberedLines.of(page, heldBack);
    }
}
