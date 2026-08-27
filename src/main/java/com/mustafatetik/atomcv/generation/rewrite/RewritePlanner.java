package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.Tone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Faz D's decisions, all of them made without an LLM (Bolum 21.1-21.3).
 *
 * <p>Pure and static, like {@code RenderPhase}: a tree and a selection go in,
 * a plan comes out. Everything here is a promise the product makes about what
 * it will and will not do to somebody's sentences, and each one is a line that
 * can be asserted against on its own.
 */
public final class RewritePlanner {

    /** Bolum 21.2, verbatim. At or above this, drawing the connection out is honest. */
    static final double FULL_ADAPTATION_SCORE = 0.65;

    /** Bolum 21.2, verbatim. Below this there is no connection to draw. */
    static final double FLOOR_SCORE = 0.40;

    /**
     * Bolum 21.2 asks for "the top 6-8", and this is the eight.
     *
     * <p>The cap is there for two things: cost, and the CV where every
     * sentence has been stuffed with the posting's words. The second is the
     * one that matters, and the floor above already handles it — an atom with
     * no real connection is not a candidate whatever the cap is. Within the
     * range the spec gives, the larger number leaves less of a genuinely
     * matching CV untouched.
     */
    static final int MAX_CANDIDATES = 8;

    /** Bolum 21.3: the rewrite may be five per cent longer than the original. */
    static final double LENGTH_TOLERANCE = 1.05;

    /**
     * <strong>Ekleme.</strong> Bolum 21.2 says a mid-scoring atom is
     * compressed "if it is long" and does not say what long is. Two printed
     * lines is the answer here: below that, compressing buys a few points of
     * page and risks the meaning of a sentence that was not the problem. The
     * number is characters rather than points because the decision is about
     * the sentence, and a font size cannot make a short bullet worth cutting.
     */
    static final int COMPRESSIBLE_CHARS = 160;

    private RewritePlanner() {
    }

    /**
     * @param tone what the profile asked to sound like; {@code null} means the
     *             person expressed no preference, and the wordings they marked
     *             primary are then the answer
     */
    public static RewritePlan plan(
            ProfileTree tree, SelectionState selection, String language, Tone tone) {

        Map<UUID, AtomNode> nodes = index(tree);
        Map<UUID, UUID> wordings = new LinkedHashMap<>();
        List<RewriteCandidate> candidates = new ArrayList<>();

        for (SelectedAtom selected : selection.selected()) {
            AtomNode node = nodes.get(selected.atomId());
            if (node == null) {
                // Selected from a tree that no longer holds it. Nothing to
                // print and nothing to rewrite; the renderer skips it too.
                continue;
            }
            AtomVariant wording = AlternativeWording.pick(node, language, tone)
                    .orElse(null);
            if (wording == null) {
                continue;
            }
            wordings.put(selected.atomId(), wording.getId());

            candidateFor(node.atom(), wording, selected.score())
                    .ifPresent(candidates::add);
        }

        candidates.sort(Comparator.comparingDouble(RewriteCandidate::score).reversed()
                // Score ties are common at the top and a run must not depend
                // on which order the tree happened to be walked in.
                .thenComparing(candidate -> candidate.atomId().toString()));
        return new RewritePlan(wordings,
                candidates.subList(0, Math.min(MAX_CANDIDATES, candidates.size())));
    }

    /**
     * Bolum 21.2's three tiers, plus the two exclusions that come before them.
     *
     * <p>{@code verbatim} is never sent — the person marked that sentence as
     * one that must be printed exactly, and a rewrite would be the product
     * overruling them. An atom with no wording to work from is not a candidate
     * either: there is nothing to be five per cent longer than.
     */
    private static Optional<RewriteCandidate> candidateFor(
            Atom atom, AtomVariant wording, double score) {

        if (atom.isVerbatim() || wording.getContent().isEmpty()) {
            return Optional.empty();
        }
        String text = wording.getContent().plainText();
        RewriteIntent intent;
        if (score >= FULL_ADAPTATION_SCORE) {
            intent = RewriteIntent.ADAPT;
        } else if (score >= FLOOR_SCORE && text.length() > COMPRESSIBLE_CHARS) {
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
     * Bolum 21.3. Faz C chose these atoms by their <em>measured</em> cost, so
     * a Faz D that made them longer would spend a page the selection had
     * already promised away.
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
