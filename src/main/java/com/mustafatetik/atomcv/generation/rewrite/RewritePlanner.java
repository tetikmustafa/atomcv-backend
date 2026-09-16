package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Faz D's decisions, all of them made without an LLM.
 *
 * <p>Pure and static, like {@code RenderPhase}: a tree and a selection go in,
 * a plan comes out. Everything here is a promise the product makes about what
 * it will and will not do to somebody's sentences, and each one is a line that
 * can be asserted against on its own.
 *
 * <p>The step is not here: the wording was chosen before the budget was spent
 * on it ({@code AlternativeWording}), and this reads the variant id selection
 * recorded. Choosing again would be a second opinion about which sentence is
 * on the page, and only one of the two would have been costed.
 */
public final class RewritePlanner {

    /**
     * How many of the posting's own asks one sentence has to name before the
     * connection may be drawn out rather than merely shortened.
     *
     * <p><strong>A count, where this used to be a score of 0.65.</strong> That
     * number was unreachable and not by a little: the best raw score the golden
     * set produces against the posting it was written for is 0.413, and the
     * importance multiplier tops out at 1.5, so 0.62 is the ceiling of the
     * scale it was set on. Every rewrite would have been a COMPRESS forever.
     *
     * <p>Moving it to the same axis as the floor's gate is the point rather
     * than a convenience. ADAPT is the tier that integrates the posting's
     * terminology into somebody's own sentence — the operation closest to the
     * thing this product exists to prevent — so it must ask for <em>more
     * evidence</em>, not for more resemblance. A score could never carry that:
     * two thirds of it is cosine, which is high for everything, times the
     * person's own importance flag.
     *
     * <p><strong>Four, and it is deliberately demanding.</strong> Across the
     * nineteen atoms selected for the best-matched pair the evidence counts run
     * 0x11, 1x2, 2x1, 3x4, 5x1: a mass at zero, a cluster at three, and one
     * sentence naming five. Three is "related", which is what COMPRESS is for;
     * four is the first value above the cluster, so the tier fires on the
     * sentence that is genuinely about the job and not on the ones that merely
     * touch it.
     *
     * <p><strong>And on that fixture it fires on nothing</strong>, which is
     * worth knowing rather than discovering later: the one atom clearing four
     * is the About paragraph, and that has its own prompt, its own ceiling and
     * its own validator — it never reaches here. So the tier is reachable in
     * principle now, where 0.65 was not reachable at all, and no bullet in the
     * golden set names four of this posting's asks. One fixture is one
     * fixture: this is the number to revisit first when real generations
     * accumulate.
     *
     * <p>A count rather than a fraction of the posting's list, because the
     * quantity that matters is what <em>this sentence</em> demonstrably names.
     * A posting asking for forty things does not make a bullet naming four of
     * them less of a match than the same bullet against a posting asking for
     * ten.
     */
    static final int FULL_ADAPTATION_TERMS = 4;

    /**
     * Below this a connection is not worth an LLM call.
     *
     * <p><strong>0.35, measured, and it no longer carries the safety
     * argument.</strong> It was 0.40 and it was the only thing between a
     * rewrite and a sentence with nothing to do with the posting — a job the
     * score cannot do, because the same measurement that set this number found
     * an unrelated CV's bullet at 0.387. The evidence gate in {@link
     * #candidateFor} took that job over, so what is left here is a cost
     * question: is this connection strong enough to spend a call on.
     *
     * <p>At 0.40 with the gate, the best-matched pair in the golden set
     * produced <em>one</em> candidate — a feature that fires once on a
     * perfectly matched CV is still off. At 0.35 it produces four, and every
     * unmatched profile still produces none. **0.30 produces the same four**:
     * nothing with evidence scores between the two, so the number sits in a gap
     * rather than on a data point, and the more conservative of two equal
     * choices is the one taken.
     */
    static final double FLOOR_SCORE = 0.35;

    /**
     * What is asked for is "the top 6-8", and this is the eight.
     *
     * <p>The cap is there for two things: cost, and the CV where every
     * sentence has been stuffed with the posting's words. The second is the
     * one that matters, and the floor above already handles it — an atom with
     * no real connection is not a candidate whatever the cap is. Within the
     * designed range of six to eight, the larger number leaves less of a
     * genuinely matching CV untouched.
     */
    static final int MAX_CANDIDATES = 8;

    /** The rewrite may be five per cent longer than the original. */
    static final double LENGTH_TOLERANCE = 1.05;

    /**
     * <strong>An addition.</strong> A mid-scoring atom is compressed "if it is
     * long" and nothing says what long is. Two printed lines is the answer
     * here: below that, compressing buys a few points of page and risks the
     * meaning of a sentence that was not the problem. The number is characters
     * rather than points because the decision is about the sentence, and a
     * font size cannot make a short bullet worth cutting.
     */
    static final int COMPRESSIBLE_CHARS = 160;

    private RewritePlanner() {
    }

    /**
     * @param selection what Faz C chose, and with it the wording it costed for
     *                  each atom — the sentence that is going on the page and
     *                  therefore the only one worth rewriting
     */
    public static RewritePlan plan(ProfileTree tree, SelectionState selection) {

        Map<UUID, AtomNode> nodes = index(tree);
        List<RewriteCandidate> candidates = new ArrayList<>();

        for (SelectedAtom selected : selection.selected()) {
            AtomNode node = nodes.get(selected.atomId());
            if (node == null) {
                // Selected from a tree that no longer holds it. Nothing to
                // print and nothing to rewrite; the renderer skips it too.
                continue;
            }
            wordingOf(node, selected.variantId())
                    .flatMap(wording -> candidateFor(
                            node.atom(), wording, selected.score(),
                            selected.matchedKeywords()))
                    .ifPresent(candidates::add);
        }

        candidates.sort(Comparator.comparingDouble(RewriteCandidate::score).reversed()
                // Score ties are common at the top and a run must not depend
                // on which order the tree happened to be walked in.
                .thenComparing(candidate -> candidate.atomId().toString()));
        return new RewritePlan(
                candidates.subList(0, Math.min(MAX_CANDIDATES, candidates.size())));
    }

    /**
     * The wording selection recorded, and the same fallback the renderer makes
     * for an id that no longer resolves: the primary one, or nothing at all.
     * Rewriting a sentence the renderer would not print is worse than not
     * rewriting — it is paid for and thrown away.
     */
    private static Optional<AtomVariant> wordingOf(AtomNode node, UUID variantId) {
        return node.variants().stream()
                .filter(variant -> variant.getId().equals(variantId))
                .findFirst()
                .or(node::primaryVariant);
    }

    /**
     * The three tiers, plus the exclusions that come before them.
     *
     * <p>{@code verbatim} is never sent — the person marked that sentence as
     * one that must be printed exactly, and a rewrite would be the product
     * overruling them. An atom with no wording to work from is not a candidate
     * either: there is nothing to be five per cent longer than.
     *
     * <p><strong>And the sentence has to name something the posting asked
     * for.</strong> {@link RewriteIntent} says where there is no real
     * connection, adapting is not adaptation but invention — and until this
     * gate existed the only thing standing for "a real connection" was the
     * score, which cannot carry it. Measured against a real BGE-M3
     * ({@code ScoreReachIT}): the top atom of an academic CV scored 0.387
     * against a senior Java posting with <em>zero</em> matched terms, against
     * 0.413 for the bullet of the CV written for that posting. Two things it
     * conflates are why. Cosine sits in a narrow high band — 0.63 to 0.84
     * across every fixture, related or not — so resemblance is nearly a
     * constant; and the result is multiplied by importance, which is the
     * person's judgement about their own CV and says nothing about this
     * posting. A sentence can therefore score well by being marked important
     * and vaguely technical.
     *
     * <p>{@code matchedTerms} is the half that does discriminate: the
     * posting's own required and preferred skills that this atom carries, and
     * the posting's phrases that it says. Not tags — those are the profile's
     * vocabulary rather than the posting's ask. With the gate, the same
     * measurement selects four candidates from the matched profile and
     * <strong>none</strong> from the six that were not written for this
     * posting.
     */
    private static Optional<RewriteCandidate> candidateFor(
            Atom atom, AtomVariant wording, double score, List<String> matchedTerms) {

        if (atom.isVerbatim() || wording.getContent().isEmpty() || !isABullet(atom)) {
            return Optional.empty();
        }
        if (matchedTerms.isEmpty()) {
            return Optional.empty();
        }
        if (score < FLOOR_SCORE) {
            return Optional.empty();
        }
        String text = wording.getContent().plainText();
        RewriteIntent intent;
        if (matchedTerms.size() >= FULL_ADAPTATION_TERMS) {
            // No length condition here, and that is the asymmetry: adapting a
            // short sentence is ordinary, compressing one is what risks the
            // meaning of something that was not the problem.
            intent = RewriteIntent.ADAPT;
        } else if (text.length() > COMPRESSIBLE_CHARS) {
            intent = RewriteIntent.COMPRESS;
        } else {
            return Optional.empty();
        }
        return Optional.of(new RewriteCandidate(
                atom.getId(), wording.getId(), wording.getContent(),
                atom.getSkills(), atom.getMetrics(), atom.getProperNouns(),
                score, maxCharsFor(text), intent, atom.getEmbedding()));
    }

    /**
     * Whether this atom is the kind of thing the prompt is written about: one
     * sentence, saying what somebody did.
     *
     * <p><strong>Ekleme — three kinds are not, and each was
     * reachable.</strong> The loop above offers every selected atom, and the
     * tiers are about scores alone, so a Tech Stack row scoring well against a
     * Java posting was a rewrite candidate like any bullet.
     *
     * <ul>
     * <li>{@code SKILL} — a Tech Stack row is a category and the items in it,
     * and the rule for it is <em>filtering</em>: items may be dropped from a
     * category and a category dropped when it empties, and nothing may be
     * added. A prompt that asks a model to bring a line closer to a posting is
     * an invitation to do the opposite — rename the category, or write in the
     * item the posting asked for. The validator would catch a technology the
     * posting named; it cannot catch a category heading nobody wrote, because
     * a heading is not a claim about a technology.</li> <li>{@code LANGUAGE} —
     * "Turkish: Native" is a fact with no phrasing to improve, and the
     * posting's vocabulary has nothing to offer it.</li> <li>{@code
     * ABOUT_PARAGRAPH} — the summary has its own prompt, its own ceiling and
     * its own validator, and {@code RewritePhase} plans it in the same
     * fan-out. Leaving it here too meant one paragraph asked for twice, two
     * invoices, and the second answer overwriting the first by arriving
     * later.</li>
     * </ul>
     */
    private static boolean isABullet(Atom atom) {
        return switch (atom.getKind()) {
            case BULLET, CERTIFICATION -> true;
            case SKILL, LANGUAGE, ABOUT_PARAGRAPH -> false;
        };
    }

    /**
     * Faz C chose these atoms by their <em>measured</em> cost, so a Faz D that
     * made them longer would spend a page the selection had already promised
     * away.
     */
    static int maxCharsFor(String original) {
        return (int) (original.length() * LENGTH_TOLERANCE);
    }

    private static Map<UUID, AtomNode> index(ProfileTree tree) {
        Map<UUID, AtomNode> nodes = new HashMap<>();
        for (var section : tree.sections()) {
            section.atoms().forEach(node -> nodes.put(node.atom().getId(), node));
            for (var entry : section.entries()) {
                entry.atoms().forEach(node -> nodes.put(node.atom().getId(), node));
            }
        }
        return nodes;
    }
}
