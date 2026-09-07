package com.mustafatetik.atomcv.generation.selection;

import com.mustafatetik.atomcv.profile.domain.SectionKind;

/**
 * What a section is worth printing at, and the order it is printed in.
 *
 * <p><strong>Sapma — Bolum 20 selects by budget alone, and that is not a
 * CV.</strong> The greedy pass ranks every atom against every other, so a
 * profile whose experience scores well spends the whole page on it: a measured
 * run put twenty atoms on the page and all twenty were projects, with no
 * experience, no skills and no summary anywhere. Each of those was a correct
 * answer to "which atoms are worth the most points", and the document was not a
 * CV. A reader expects a shape before they expect relevance.
 *
 * <p>So each kind reserves a floor first, and only what is left competes. The
 * floor is what the section is worth printing <em>at</em> — below it the
 * section says less than its heading costs.
 *
 * <p><strong>A floor is a ceiling on what may be reserved, never a demand.</strong>
 * A profile with one job does not grow a second to satisfy {@code EXPERIENCE};
 * it reserves the one it has. A profile with no projects at all prints no
 * Projects heading. The rule is "as much of this as exists, up to the floor" —
 * an empty heading is worse than an absent section, and inventing content to
 * fill one is what this product exists not to do.
 *
 * <p>The order is fixed here rather than read from {@code sections.display_order}.
 * That column is what an import happened to write, and the reader's expectation
 * is not per-profile. Making it the user's choice is a feature, and a later one.
 *
 * <p><strong>A floor says how much, never where.</strong> The same kind hangs
 * its atoms differently in two profiles — one import writes the summary as a
 * paragraph straight under the heading, another puts it in a "Professional
 * Summary" entry; one writes two languages as two lines, another as two
 * entries. A floor written in terms of one shape reserves nothing in the other,
 * and that is not hypothetical: About and Languages were given loose-atom
 * floors, a real profile kept both in entries, and the two sections the floors
 * were added for were the two that still did not reach the page.
 *
 * @param entries        how many entries to open, where the section has them
 * @param atomsPerEntry  how many atoms from each of those entries
 * @param atoms          the total the section is worth printing at, counted
 *                       across both shapes — the section's own atoms first, and
 *                       then whatever its entries carry
 * @param maxAtoms       the most it may print, or {@code 0} for no ceiling.
 *                       Only {@code ABOUT} has one, and it is not a budget
 *                       rule: a summary is one paragraph, and a page carrying
 *                       two of them is not a fuller CV but a confused one. A
 *                       real profile keeps four — one written towards backend
 *                       work, one towards data, one towards AI — and every one
 *                       of them scores the same against a posting, so without a
 *                       ceiling the greedy pass prints whichever two fit
 */
public record SectionFloor(int entries, int atomsPerEntry, int atoms, int maxAtoms) {

    /** No ceiling: as many as the page and the score allow. */
    public SectionFloor(int entries, int atomsPerEntry, int atoms) {
        this(entries, atomsPerEntry, atoms, 0);
    }

    /** No floor: it competes for the page on score alone, like every atom. */
    public static final SectionFloor NONE = new SectionFloor(0, 0, 0);

    public SectionFloor {
        if (entries < 0 || atomsPerEntry < 0 || atoms < 0 || maxAtoms < 0) {
            throw new IllegalArgumentException("A floor is never negative");
        }
        if (maxAtoms > 0 && atoms > maxAtoms) {
            throw new IllegalArgumentException(
                    "A floor of " + atoms + " cannot sit above a ceiling of " + maxAtoms);
        }
    }

    /**
     * The order sections are placed and printed in.
     *
     * <p>A kind with no place here — {@link SectionKind#SOFT_SKILLS},
     * {@link SectionKind#CUSTOM}, and anything added later — sorts after all of
     * them and competes among its peers on score. A person who wrote a
     * "Certifications" section did not tell us where it goes, and guessing from
     * a name would be worse than letting the posting decide.
     */
    public static int priorityOf(SectionKind kind) {
        return switch (kind) {
            case ABOUT -> 0;
            case EDUCATION -> 1;
            case EXPERIENCE -> 2;
            case PROJECTS -> 3;
            case SKILLS -> 4;
            case LANGUAGES -> 5;
            default -> UNRANKED;
        };
    }

    /** Where a kind the order does not name sits: after all of them. */
    public static final int UNRANKED = 100;

    /**
     * The floor for a kind, in the shape that kind is printed in.
     *
     * <ul>
     *   <li>{@code ABOUT} — one paragraph, and never more than one. It is the
     *       only kind with a ceiling as well as a floor: a profile carrying
     *       four summaries written towards four different jobs must print the
     *       one, not whichever two fit.</li>
     *   <li>{@code EDUCATION} — one entry, and no bullets: a degree line is a
     *       heading, and asking for an achievement under it is asking to pad
     *       (Bolum 20.2).</li>
     *   <li>{@code EXPERIENCE} — two roles at two bullets. One role reads as a
     *       first job whatever the person has done, and one bullet under a role
     *       reads as a role that went nowhere.</li>
     *   <li>{@code PROJECTS} — two projects at three bullets. Three because a
     *       project is judged on what was built, and two lines cannot carry
     *       what a role's two lines carry: the role has an employer and a date
     *       range doing that work.</li>
     *   <li>{@code SKILLS} — three lines. Fewer reads as a fragment of a list
     *       rather than as a stack.</li>
     *   <li>{@code LANGUAGES} — two. One language is a fact about a person who
     *       filled in half a form.</li>
     * </ul>
     */
    public static SectionFloor forKind(SectionKind kind) {
        return switch (kind) {
            case ABOUT -> new SectionFloor(1, 1, 1, 1);
            case EDUCATION -> new SectionFloor(1, 0, 0);
            case EXPERIENCE -> new SectionFloor(2, 2, 4);
            case PROJECTS -> new SectionFloor(2, 3, 6);
            case SKILLS -> new SectionFloor(3, 1, 3);
            case LANGUAGES -> new SectionFloor(2, 1, 2);
            default -> NONE;
        };
    }

    public boolean isNone() {
        return equals(NONE);
    }

    /** Whether a section already holding {@code taken} atoms may take another. */
    public boolean allowsMoreThan(int taken) {
        return maxAtoms == 0 || taken < maxAtoms;
    }

    /**
     * The least this section may be reduced to and still be on the page: one
     * entry and one atom, whichever way its atoms hang.
     *
     * <p>Reached only where the floors together do not fit, which the measured
     * numbers say does not happen on a one-page CV with all six kinds. It
     * exists because the alternative is dropping a section, and a CV missing
     * its experience is not a smaller CV, it is a different document.
     */
    public SectionFloor hardFloor() {
        if (isNone()) {
            return NONE;
        }
        return new SectionFloor(1, Math.min(atomsPerEntry, 1), Math.min(atoms, 1), maxAtoms);
    }

}
