package com.mustafatetik.atomcv.generation.validation;

import com.mustafatetik.atomcv.generation.scoring.ScorableAtomFactory;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.EntryNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * What the finished page actually claims, in canonical skill keys
 * (Bolum 23.3).
 *
 * <p><strong>Selected, not scored.</strong> Faz B ranks every atom in the
 * profile and Faz C then drops most of them for budget, so the ranking is not
 * a description of the document. A report built from it would credit the user
 * for a skill that lost its place — the CV would be saying one thing and the
 * report beside it another.
 *
 * <p>Pure: a tree and a selection in, a set out. Faz F's honesty is a property
 * worth being able to test without a database.
 */
public final class SelectedSkills {

    private SelectedSkills() {
    }

    /**
     * @return the union of the canonical skills of every selected atom, in the
     *         tree's own order. {@code LinkedHashSet} rather than
     *         {@code Set.copyOf}: this feeds a report that reaches a JSON
     *         column and a response, and copyOf iterates in an order salted
     *         per JVM run.
     */
    public static Set<String> onThePage(ProfileTree tree, SelectionState selection) {
        Set<UUID> selected = new LinkedHashSet<>();
        for (SelectionState.SelectedAtom atom : selection.selected()) {
            selected.add(atom.atomId());
        }

        Set<String> skills = new LinkedHashSet<>();
        for (SectionNode section : tree.sections()) {
            for (EntryNode entry : section.entries()) {
                collect(entry.atoms(), selected, skills);
            }
            collect(section.atoms(), selected, skills);
        }
        return skills;
    }

    private static void collect(
            Iterable<AtomNode> nodes, Set<UUID> selected, Set<String> into) {

        for (AtomNode node : nodes) {
            if (selected.contains(node.atom().getId())) {
                // Through the factory's rule, which is the scorer's rule: the
                // report has to match on the keys Faz B compared.
                into.addAll(ScorableAtomFactory.canonicalSkills(node.atom()));
            }
        }
    }
}
