package com.mustafatetik.atomcv.rendering.template;

import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import java.util.Map;
import java.util.Objects;

/**
 * What a page of this template holds, in points (Bolum 26.3, 26.4).
 *
 * <p>Points, never lines. Rounding each atom up to a whole line accumulates
 * error — sixteen atoms can drift by sixteen lines — so everything is summed
 * in points and compared with the capacity once, at the end.
 *
 * <p>Every number here was measured against the real compiler, not estimated.
 * A calibration test re-derives them and fails when the template moves, which
 * is the moment its version has to be raised (Bolum 16.3).
 *
 * @param pageTextHeightPt the height of the text block, {@code \textheight}
 * @param textWidthPt      the width a line is set at, {@code \textwidth}
 * @param baselineSkipPt   the distance between consecutive baselines in the
 *                         document's own size
 * @param itemBaselineSkipPt the same distance inside a bullet, which the
 *                         reference template sets {@code \small}. Two numbers
 *                         because the document has two sizes, and reading a
 *                         bullet back against the page's baseline is not a
 *                         rounding error that stays small: a box of nine small
 *                         lines divided by the large baseline comes back as
 *                         eight, and the ninth line is not paid for
 * @param fixedCosts       what the furniture costs: a section heading, an
 *                         entry heading, the overhead of a bullet list
 */
public record CapacityModel(
        double pageTextHeightPt,
        double textWidthPt,
        double baselineSkipPt,
        double itemBaselineSkipPt,
        Map<String, Double> fixedCosts) {

    /**
     * The name, the headline and the contact line at the top of the page.
     * Calibrated for the shape the renderer emits: one name and two centred
     * lines under it.
     */
    public static final String HEADER_BLOCK = "headerBlock";

    /**
     * A section heading with its rule and the space around it.
     *
     * <p>One number, and it took a corrected probe to know that. The reference
     * opens its section format with a negative space, so a heading is pulled up
     * against whatever closed above it — which looked like it had to be two
     * numbers, one for the top of the page and one for further down. Measured
     * with the paragraph flushed first, they are the same to five decimals: the
     * negative space is spent against the heading's own spacing rather than
     * against the block above.
     */
    public static final String SECTION_HEADER = "sectionHeader";


    /**
     * The two lines of an entry heading — title, organization, dates — where
     * it follows a section heading.
     */
    public static final String ENTRY_HEADER = "entryHeader";

    /**
     * The same heading where a bullet list came before it.
     *
     * <p>Nine points more, because the paragraph skip between two blocks
     * applies and the section heading's own spacing does not. A CV of four
     * jobs pays it three times; charging every entry the cheaper number is how
     * a page overflows by half a bullet for no visible reason (EK D.8.10).
     */
    public static final String ENTRY_HEADER_AFTER_LIST = "entryHeaderAfterList";

    /**
     * A project's heading, which is one line rather than two.
     *
     * <p>The renderer chooses it from the data: a project is the entry carrying
     * no employer, no location and no dates, so there is no second line to set.
     * It had no constant while every entry was charged the two-line number, and
     * a page with two projects gave away most of a bullet to a heading that is
     * never printed that tall.
     */
    public static final String PROJECT_HEADING = "projectHeading";

    /** The same, where the sub-heading list it sits in is already open. */
    public static final String PROJECT_HEADING_AFTER_LIST = "projectHeadingAfterList";

    /** What a bullet list costs before its first bullet, under an entry heading. */
    public static final String ITEMIZE_OVERHEAD = "itemizeOverhead";

    /**
     * The same list where it opens directly under a section heading, with no
     * entry between them — a skills matrix, a list of languages.
     *
     * <p>Five points less, and for the reason {@link #ENTRY_HEADER_AFTER_LIST}
     * exists: TeX adds the space above a list with {@code \addvspace}, which
     * takes the larger of what is asked and what is already there rather than
     * the sum. A section heading has just left its own space behind, so the
     * list adds almost nothing; an entry heading has ended a paragraph, and the
     * paragraph skip is still to be paid.
     *
     * <p>Measured, not reasoned: with one number for both, a real profile's
     * skills section was charged 66.6pt for something the compiler put on the
     * page in 57.4, and the page came out short by most of a bullet.
     */
    public static final String SECTION_LIST_OVERHEAD = "sectionListOverhead";

    /**
     * What a first-level bullet list leaves behind it, once anything follows.
     *
     * <p>Exactly one small baseline, and it is spent on whatever comes next
     * rather than on the list: {@code \resumeItemListEnd} closes with
     * {@code \vspace{-5pt}}, the next section heading opens with
     * {@code \addvspace}, and {@code \addvspace} takes the larger of the two —
     * so the negative pull is discarded and the {@code \topsep} above it is
     * not. A heading measured after the header block costs 20.86; the same
     * heading after a list of loose bullets costs 32.86, at one bullet and at
     * three alike.
     *
     * <p>An inline list and a paragraph list leave nothing: neither closes with
     * the negative space, so the heading after them costs what a heading costs.
     *
     * <p><strong>Charged to the list, not to the heading below it.</strong>
     * Which is a deliberate over-charge in one case — a bullet list that is the
     * last thing on the page has nothing after it to spend the space on, and is
     * charged twelve points it does not use. Pricing it on the heading instead
     * would mean knowing, at the moment a heading is priced, what will end up
     * above it; selection opens sections in score order and the page prints
     * them in reading order, so it does not know. Twelve points held back is a
     * bullet not printed. Twelve points not charged is a second page.
     */
    public static final String SECTION_LIST_CLOSE = "sectionListClose";

    /**
     * One bullet under an entry heading, at one line. Longer bullets are
     * measured, not assumed.
     *
     * <p>This is the common one: experience and projects hang their bullets off
     * entries, and the list holding them is nested inside the section's
     * sub-heading list. LaTeX sets a second-level list tighter than a
     * first-level one — {@code \\labelitemii} exists for the same reason — so it
     * is not the same number as {@link #SECTION_ITEM_LINE}.
     */
    public static final String ITEM_LINE = "itemLine";

    /**
     * One bullet in a list opened directly under a section heading, where
     * nothing is nested — a summary, a section of loose rows.
     *
     * <p>Five points more than {@link #ITEM_LINE}, because a first-level list
     * sets an item separation that a second-level one has already tightened.
     * They were one constant while the template zeroed every list length; the
     * reference does not, and a page of sixty bullets charged the wrong one of
     * the two came back two pages long.
     */
    public static final String SECTION_ITEM_LINE = "sectionItemLine";

    /**
     * One row of an inline list, at one line — and it is not the same number
     * as {@link #ITEM_LINE}.
     *
     * <p>An inline list is a single {@code \item} whose rows are separated by
     * {@code \\}, so a row costs a baseline and nothing else. A bullet is an
     * {@code \item} of its own and pays the list's separation between two
     * items as well. Under the reference template that is five points a row,
     * and a skills matrix of four rows would be charged twenty points for
     * separation that is never set.
     *
     * <p>They were one constant while the template zeroed every list length
     * and set nothing {@code \small}; both of those went when the reference's
     * own spacing came in, and the calibration said so on the first run.
     */
    public static final String INLINE_ROW = "inlineRow";

    /**
     * What an inline list costs before its first row, under a section heading.
     *
     * <p>Larger than {@link #SECTION_LIST_OVERHEAD}, and by more than a
     * rounding: a bullet list under a heading is pulled up by the negative
     * space {@code \resumeItemListEnd} leaves behind, and an inline list —
     * which the renderer opens with its own {@code itemize} — is not.
     */
    public static final String INLINE_LIST_OVERHEAD = "inlineListOverhead";

    /**
     * What a summary's list costs before its paragraph.
     *
     * <p>Five points more than {@link #SECTION_LIST_OVERHEAD} and nine less
     * than {@link #INLINE_LIST_OVERHEAD}, and both differences are template
     * text rather than accident: a bullet list closes by pulling five points
     * back and a paragraph list does not, and an inline row is not wrapped in
     * the four points a bullet pulls back after itself.
     */
    public static final String PARAGRAPH_LIST_OVERHEAD = "paragraphListOverhead";

    public CapacityModel {
        fixedCosts = Map.copyOf(Objects.requireNonNull(fixedCosts, "fixedCosts"));
        if (pageTextHeightPt <= 0 || textWidthPt <= 0 || baselineSkipPt <= 0
                || itemBaselineSkipPt <= 0) {
            throw new IllegalArgumentException("A page has a height, a width and a baseline");
        }
    }

    public double fixedCost(String name) {
        Double cost = fixedCosts.get(name);
        if (cost == null) {
            throw new IllegalArgumentException("Nothing measured for " + name);
        }
        return cost;
    }

    /**
     * What a bullet list adds between two items.
     *
     * <p>Derived rather than measured separately: a one-line item was measured
     * in place, and one line of it is a bullet's baseline, so whatever is left
     * is the separation. Keeping it derived means the two can never disagree.
     *
     * <p><strong>It is negative under this template, and that is not a
     * mistake.</strong> The reference writes {@code \resumeItem} as
     * {@code \item\small{{#1 \vspace{-4pt}}}}, so an item ends by pulling the
     * next one four points closer. A list of twenty bullets is twenty
     * baselines less eighty points, and the arithmetic says so in one place
     * rather than twenty.
     */
    public double itemSpacingPt() {
        return rowSpacingPt(RowShape.ENTRY_BULLET);
    }

    /**
     * Where one line of content sits, which is what decides its cost.
     *
     * <p>Three shapes and three numbers, because the reference template leaves
     * LaTeX's own list lengths alone. A bullet under an entry is in a
     * second-level list; a bullet under a section heading is in a first-level
     * one and pays five points more for it; a row of an inline list shares a
     * single item with its neighbours and pays nothing at all.
     */
    public enum RowShape {

        /** A bullet under an entry heading: experience, projects. */
        ENTRY_BULLET,

        /** A bullet in a list opened straight under a section heading. */
        SECTION_BULLET,

        /** A row of a skills matrix or a list of languages. */
        INLINE_ROW_SHAPE
    }

    /** What one line costs in the shape it is set in. */
    public double lineCostPt(RowShape shape) {
        return switch (shape) {
            case ENTRY_BULLET -> fixedCost(ITEM_LINE);
            case SECTION_BULLET -> fixedCost(SECTION_ITEM_LINE);
            case INLINE_ROW_SHAPE -> fixedCost(INLINE_ROW);
        };
    }

    /**
     * What the list sets between two of them: a line's cost, less the line.
     *
     * <p>Derived rather than measured separately, so the two can never
     * disagree, and it is what {@code RenderCost.totalPt} adds once to a
     * measured box of however many lines.
     */
    public double rowSpacingPt(RowShape shape) {
        return lineCostPt(shape) - itemBaselineSkipPt;
    }

    /**
     * What an entry's heading costs: two lines for a role, one for a project.
     *
     * @param bare    whether the entry carries no employer, place or dates,
     *                which is how the renderer decides to set a one-line
     *                heading
     * @param afterList whether the sub-heading list it goes into is already
     *                open, which is true of every entry after a section's first
     */
    public double entryHeadingPt(boolean bare, boolean afterList) {
        if (bare) {
            return fixedCost(afterList ? PROJECT_HEADING_AFTER_LIST : PROJECT_HEADING);
        }
        return fixedCost(afterList ? ENTRY_HEADER_AFTER_LIST : ENTRY_HEADER);
    }

    /**
     * What a list costs before its first line, under a section heading.
     *
     * <p>Three numbers, one per label-less shape the template sets. They differ
     * by what the template writes at each end of the list rather than by
     * anything about the content, which is why the layout is the right thing to
     * ask.
     */
    public double sectionListOverheadPt(SectionLayout layout) {
        return switch (layout) {
            case INLINE_LIST -> fixedCost(INLINE_LIST_OVERHEAD);
            case PARAGRAPH -> fixedCost(PARAGRAPH_LIST_OVERHEAD);
            default -> fixedCost(SECTION_LIST_OVERHEAD);
        };
    }

    /** What the same list leaves behind it — see {@link #SECTION_LIST_CLOSE}. */
    public double sectionListClosePt(SectionLayout layout) {
        return switch (layout) {
            case INLINE_LIST, PARAGRAPH -> 0.0;
            default -> fixedCost(SECTION_LIST_CLOSE);
        };
    }

    /** What is left for content once the page's furniture is paid for. */
    public double freeBudgetPt(int pages, double structuralCostPt) {
        if (pages < 1) {
            throw new IllegalArgumentException("A CV has at least one page");
        }
        return pages * pageTextHeightPt - structuralCostPt;
    }
}
