package com.mustafatetik.atomcv.generation.validation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScorer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Faz F's honest answer to "how well does this CV fit that posting?"
 * (Bolum 23.3).
 *
 * <p><strong>Countable facts, never a percentage.</strong> Bolum 23.3 forbids
 * one by name, and the reason is that the underlying measurement cannot carry
 * it: this compares the skill names the posting listed against the skill names
 * on the atoms that reached the page. "Four of four required" is a claim a
 * user can check and act on; "87% match" is a number that reads as a hiring
 * probability and is not one.
 *
 * <p><strong>Measured on what was printed, not on what was ranked.</strong>
 * Faz B scores every atom in the profile; Faz C then drops most of them for
 * budget. A report built from the ranking would credit the user for a skill
 * that lost its place on the page — which is the same defect as a CV claiming
 * something it does not say.
 *
 * <p>Matching is on {@link JobAnalysis.Skill#canonical}, through
 * {@link RelevanceScorer#canonicalSkill} so the report agrees with the scorer.
 * The names that come <em>back</em> are the posting's own
 * ({@link JobAnalysis.Skill#name}), in the language it was written in, because
 * that is the word the user will look for when they go to check.
 *
 * <p><strong>General mode has no report and gets none.</strong> There is no
 * posting to be relevant to, so every count would be zero and
 * {@link MatchLevel#GOOD} would be a verdict about nothing.
 *
 * <p>Stored on {@code generations.fit_report}. No getter-shaped methods live
 * here on purpose: on a record Jackson writes to a JSONB column, one of those
 * is a stored field nobody declared — Stage 2 lost a download to it.
 *
 * @param coveredSkills   every posting skill the page does say, required and
 *                        preferred together, in the posting's own words
 * @param missingRequired what the posting demanded and the page does not say
 * @param level           the heading over the counts, derived once here rather
 *                        than by each reader
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "How much of the posting's vocabulary the CV actually says. "
        + "Counts, never a percentage — Bolum 23.3.")
public record FitReport(
        int requiredCovered,
        int requiredTotal,
        int preferredCovered,
        int preferredTotal,

        @Schema(description = "Posting skills the page says, in the posting's own words")
        List<String> coveredSkills,

        List<String> missingRequired,
        List<String> missingPreferred,
        MatchLevel level) {

    /** Bolum 23.3: above this share of the nice-to-haves, a clean match is STRONG. */
    private static final double STRONG_PREFERRED_RATIO = 0.6;

    public FitReport {
        coveredSkills = copyOf(coveredSkills);
        missingRequired = copyOf(missingRequired);
        missingPreferred = copyOf(missingPreferred);
        if (level == null) {
            throw new IllegalArgumentException("A report without a level is half a report");
        }
    }

    /**
     * The report for one posting against the skills that reached the page.
     *
     * @param skillsOnThePage canonical skill keys of the atoms Faz C selected.
     *                        Canonical because that is what the atoms carry and
     *                        what the scorer compared.
     */
    public static FitReport of(JobAnalysis posting, Set<String> skillsOnThePage) {
        Set<String> onThePage = skillsOnThePage == null ? Set.of() : skillsOnThePage;

        var covered = new LinkedHashSet<String>();
        var missingRequired = new ArrayList<String>();
        var missingPreferred = new ArrayList<String>();

        int requiredCovered =
                split(posting.requiredSkills(), onThePage, covered, missingRequired);
        int preferredCovered =
                split(posting.preferredSkills(), onThePage, covered, missingPreferred);

        int requiredTotal = posting.requiredSkills().size();
        int preferredTotal = posting.preferredSkills().size();

        return new FitReport(
                requiredCovered, requiredTotal,
                preferredCovered, preferredTotal,
                List.copyOf(covered),
                List.copyOf(missingRequired),
                List.copyOf(missingPreferred),
                levelOf(requiredCovered, requiredTotal, preferredCovered, preferredTotal));
    }

    /**
     * Bolum 23.3's ladder, and the order of the rungs is the point: a missing
     * requirement is never offset by nice-to-haves, however many of them the
     * page covers.
     *
     * <p>A posting that listed no requirements at all cannot be short of one,
     * so it falls through to the preferred ratio — and with nothing preferred
     * either, to {@link MatchLevel#GOOD}. That is the honest reading: nothing
     * was asked for and nothing is missing.
     */
    private static MatchLevel levelOf(
            int requiredCovered, int requiredTotal, int preferredCovered, int preferredTotal) {

        int shortBy = requiredTotal - requiredCovered;
        if (shortBy >= 2) {
            return MatchLevel.WEAK;
        }
        if (shortBy == 1) {
            return MatchLevel.MODERATE;
        }
        if (preferredTotal > 0
                && (double) preferredCovered / preferredTotal > STRONG_PREFERRED_RATIO) {
            return MatchLevel.STRONG;
        }
        return MatchLevel.GOOD;
    }

    /**
     * Walks one of the posting's lists, counting hits and collecting names.
     *
     * <p>A skill with no canonical key is counted as missing rather than
     * skipped. It was still something the posting asked for, and dropping it
     * would quietly shrink the denominator — turning an extraction gap into a
     * better-looking match.
     */
    private static int split(
            List<JobAnalysis.Skill> skills, Set<String> onThePage,
            Set<String> covered, List<String> missing) {

        int hits = 0;
        for (JobAnalysis.Skill skill : skills) {
            String key = RelevanceScorer.canonicalSkill(skill.canonical());
            String shown = skill.name() == null || skill.name().isBlank()
                    ? skill.canonical() : skill.name();
            if (!key.isBlank() && onThePage.contains(key)) {
                covered.add(shown);
                hits++;
            } else {
                missing.add(shown);
            }
        }
        return hits;
    }

    private static List<String> copyOf(List<String> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
