package com.mustafatetik.atomcv.generation.coverletter;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.shared.error.Result;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Bolum 34 as one call: read the page, write the letter.
 *
 * <p>Two callers with two different answers to the same failure. A generation
 * that was <em>also</em> asked for a letter must not lose its CV because the
 * letter could not be written — the document is what the person came for — so
 * {@link #writeQuietly} swallows the refusal and answers with nothing. The
 * endpoint of Bolum 34.6 asked for the letter and nothing else, so
 * {@link #write} hands the refusal back.
 *
 * <p>The planning is the same either way, which is the point of it being here
 * rather than duplicated on both sides.
 */
@Service
public class CoverLetterWriter {

    private static final Logger log = LoggerFactory.getLogger(CoverLetterWriter.class);

    private final CoverLetterService letters;
    private final Clock clock;

    CoverLetterWriter(CoverLetterService letters, Clock clock) {
        this.letters = letters;
        this.clock = clock;
    }

    public String promptVersionFor(String bucketKey) {
        return letters.promptVersionFor(bucketKey);
    }

    /**
     * @param companyNote what the person knows about this employer
     *                    (Bolum 34.5), or blank
     * @return the letter, or the reason there is none
     */
    public Result<CoverLetterDraft> write(
            Profile profile,
            ProfileTree tree,
            SelectionState selection,
            JobAnalysis posting,
            String companyNote,
            CoverLetterStyle style,
            String bucketKey,
            java.util.UUID userId) {

        CoverLetterInput input = CoverLetterPlanner.plan(
                profile, tree, selection, posting, companyNote,
                languageFor(profile, posting), toneOf(profile), LocalDate.now(clock));
        return letters.write(input, style, bucketKey, userId);
    }

    /**
     * The same, for a caller whose real answer is a CV.
     *
     * @return the letter, or null — and null is not an error here. The
     *         generation carries on and the person has the document they
     *         asked for; the letter can be asked for again on its own.
     */
    public String writeQuietly(
            Profile profile,
            ProfileTree tree,
            SelectionState selection,
            JobAnalysis posting,
            String companyNote,
            CoverLetterStyle style,
            String bucketKey,
            java.util.UUID userId) {

        Result<CoverLetterDraft> written = write(
                profile, tree, selection, posting, companyNote, style, bucketKey, userId);
        return switch (written) {
            case Result.Ok<CoverLetterDraft> ok -> ok.value().plainText();
            case Result.Err<CoverLetterDraft> refused -> {
                // The kind, never the letter. The CV is unaffected and the
                // person is not told mid-generation: the result screen shows
                // no letter, and the button writes one.
                log.info("A generation kept its CV and no cover letter: {}",
                        refused.error().getClass().getSimpleName());
                yield null;
            }
        };
    }

    /**
     * <strong>Ekleme — {@code auto} follows the posting here, and not the
     * CV.</strong> Bolum 5's note says {@code jdLanguage} is kept for exactly
     * this. F-013 made the CV's language conditional on the profile carrying a
     * wording for every atom, because a document is assembled from wordings
     * that may not exist; a letter is written from scratch, so that constraint
     * does not apply to it. Somebody whose Turkish profile is applying to an
     * English posting gets a Turkish CV and an English letter, and both are
     * right.
     */
    private static String languageFor(Profile profile, JobAnalysis posting) {
        String preferred = profile.getPreferences().defaults().coverLetterLanguage();
        if (preferred != null && !preferred.isBlank() && !"auto".equals(preferred)) {
            return preferred;
        }
        if (posting != null && posting.jdLanguage() != null && !posting.jdLanguage().isBlank()) {
            return posting.jdLanguage().strip();
        }
        return profile.getSourceLanguage();
    }

    private static String toneOf(Profile profile) {
        Tone tone = profile.getPreferences().writingStyle().tone();
        return tone == null ? Tone.FORMAL.wireValue() : tone.wireValue();
    }
}
