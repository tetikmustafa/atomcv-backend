package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Faz D, run (Bolum 21.5).
 *
 * <p>Eight bullets at most, each one a call that spends most of its time
 * waiting on somebody else's server. Sequentially that is eight round trips
 * added to a generation the user is watching a progress bar for; on virtual
 * threads it is one.
 *
 * <p><strong>Sapma — Bolum 21.5's {@code StructuredTaskScope} is not used, and
 * not only because it is a preview API in Java 21.</strong> The scope in the
 * spec is a {@code ShutdownOnFailure}, which cancels the siblings when one
 * task fails. That is the wrong rule for this phase: Bolum 21.6 already says
 * what a failed rewrite means — the original sentence stands — so one bullet
 * going wrong is not a reason to abandon the seven that went right. A
 * virtual-thread-per-task executor gives the same fan-out and the same join,
 * and the failure of one task stays the failure of one task.
 *
 * <p><strong>This never fails.</strong> Everything it can answer with is a
 * CV: the rewrites that passed, and the person's own words everywhere else.
 */
@Service
public class RewritePhase {

    private static final Logger log = LoggerFactory.getLogger(RewritePhase.class);

    private final BulletRewriteService rewriter;

    RewritePhase(BulletRewriteService rewriter) {
        this.rewriter = rewriter;
    }

    /** Which version of the rewrite prompt this bucket is on (Bolum 53.3). */
    public String promptVersionFor(String bucketKey) {
        return rewriter.promptVersionFor(bucketKey);
    }

    /**
     * @param carried what an earlier attempt at this same generation already
     *                paid for. The compile loop shrinks the budget and selects
     *                again, and the atoms that survive are the ones that were
     *                rewritten first — asking again would buy the same
     *                sentences a second time
     * @return {@code carried} plus whatever this pass accepted
     */
    public RewrittenContent rewrite(
            ProfileTree tree,
            SelectionState selection,
            RewriteContext context,
            RewrittenContent carried) {

        if (context.postingSkills().isEmpty()) {
            // Nothing to reach for, and — the half that matters — nothing for
            // Bolum 21.6's unsupported-claim check to be measured against. A
            // rewrite made against an empty vocabulary could name any
            // technology at all and pass. Faz D does not run.
            log.info("Faz D skipped: the posting named no skills");
            return carried;
        }

        RewritePlan plan = RewritePlanner.plan(tree, selection);
        List<RewriteCandidate> todo = plan.candidates().stream()
                .filter(candidate -> !carried.covers(candidate.atomId()))
                .toList();
        if (todo.isEmpty()) {
            return carried;
        }
        log.info("Faz D: {} (already rewritten {})", plan.shape(), carried.byAtom().size());

        return carried.and(runAll(todo, context));
    }

    /**
     * All of them at once, and every answer collected — including the ones
     * that came back as the original.
     */
    private Map<UUID, RichContent> runAll(
            List<RewriteCandidate> candidates, RewriteContext context) {

        var accepted = new LinkedHashMap<UUID, RichContent>();
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<RichContent>> answers = new ArrayList<>(candidates.size());
            for (RewriteCandidate candidate : candidates) {
                answers.add(threads.submit(() -> rewriter.rewrite(candidate, context)));
            }
            for (int i = 0; i < candidates.size(); i++) {
                RewriteCandidate candidate = candidates.get(i);
                RichContent answer = answerOf(answers.get(i), candidate);
                // The service answers with the original when it kept the
                // original, and there is no point recording that: an atom
                // absent from the map is printed from the profile anyway.
                if (answer != null && !answer.equals(candidate.original())) {
                    accepted.put(candidate.atomId(), answer);
                }
            }
        } catch (RuntimeException wentWrong) {
            // Bolum 21.5's catch. A CV made of the person's own sentences is
            // a worse CV than the one Faz D would have produced, and a far
            // better answer than no CV at all.
            log.warn("Faz D failed as a whole; every bullet keeps its original wording: {}",
                    wentWrong.getClass().getSimpleName());
            return Map.of();
        }
        return accepted;
    }

    /** @return the rewrite, or null when this one task did not come back */
    private static RichContent answerOf(Future<RichContent> answer, RewriteCandidate candidate) {
        try {
            return answer.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception failed) {
            // One bullet, and it costs that bullet its rewrite and nothing
            // more. The id is the log; the sentence is the user's (rule 4).
            log.warn("A rewrite of atom {} did not come back: {}",
                    candidate.atomId(), failed.getClass().getSimpleName());
            return null;
        }
    }
}
