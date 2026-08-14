package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import java.util.Set;

/**
 * How complete a profile is, 0 to 100 (Bolum 31.9).
 *
 * <p>The number is not decoration: the preflight gate refuses to start a
 * generation below a threshold (Bolum 25.5), and the figure the user sees has
 * to be the figure that gate uses.
 *
 * <p>Bolum 31.9 gives the weights but leaves the predicates to be read off
 * method names. What each one counts is settled here and recorded in EK D.6.2.
 */
public final class CompletenessCalculator {

    private static final Set<SectionKind> EXPERIENCE = Set.of(SectionKind.EXPERIENCE);
    private static final Set<SectionKind> PROJECTS = Set.of(SectionKind.PROJECTS);
    private static final Set<SectionKind> EDUCATION_OR_EXPERIENCE =
            Set.of(SectionKind.EDUCATION, SectionKind.EXPERIENCE);

    private CompletenessCalculator() {
    }

    public static short of(Profile profile, ProfileTree tree) {
        int score = 0;
        score += hasContact(profile) ? 15 : 0;
        score += entriesIn(tree, EDUCATION_OR_EXPERIENCE) > 0 ? 20 : 0;
        score += Math.min(entriesIn(tree, EXPERIENCE) * 10, 20);
        score += Math.min(entriesIn(tree, PROJECTS) * 5, 15);
        score += Math.min(skills(tree), 10);
        score += hasText(profile.getSelfDescription()) ? 10 : 0;
        score += atomsWithMetrics(tree) >= 3 ? 10 : 0;
        return (short) Math.min(score, 100);
    }

    /**
     * A name and an email. Those two are what a CV header cannot be rendered
     * without; a phone number or a website is worth having but never blocks a
     * generation.
     */
    private static boolean hasContact(Profile profile) {
        return hasText(profile.getContact().name()) && hasText(profile.getContact().email());
    }

    private static long entriesIn(ProfileTree tree, Set<SectionKind> kinds) {
        return tree.sections().stream()
                .filter(section -> kinds.contains(section.section().getKind()))
                .mapToLong(section -> section.entries().size())
                .sum();
    }

    /** Skill atoms, wherever they hang — the kind is what makes one a skill. */
    private static long skills(ProfileTree tree) {
        return atoms(tree).filter(atom -> atom.getKind() == AtomKind.SKILL).count();
    }

    /** The quality signal of Bolum 31.9: atoms that claim a number. */
    private static long atomsWithMetrics(ProfileTree tree) {
        return atoms(tree).filter(atom -> !atom.getMetrics().isEmpty()).count();
    }

    private static java.util.stream.Stream<com.mustafatetik.atomcv.profile.domain.Atom> atoms(
            ProfileTree tree) {
        return tree.sections().stream().flatMap(section -> java.util.stream.Stream.concat(
                section.atoms().stream(),
                section.entries().stream().flatMap(entry -> entry.atoms().stream())))
                .map(ProfileTree.AtomNode::atom);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
