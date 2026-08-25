package com.mustafatetik.atomcv.generation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.shared.error.CompilationFailureKind;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.shared.error.UnreadablePostingReason;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every pipeline failure, as a body a user can act on (Bolum 25.3).
 *
 * <p>{@code UserFacingError} validates its parameters against the catalogue as
 * it is built, so each of these also proves the error publishes exactly what
 * the frontend's message interpolates — no more and no less.
 */
class ErrorPresenterTest {

    private static final double PAGE_HEIGHT_PT = 708.245;

    private final ErrorPresenter presenter = new ErrorPresenter();

    @Test
    void anEmptyProfileIsToldWhatIsMissingAndWhereToGo() {
        var presented = presenter.present(
                new PipelineError.InsufficientProfile(12, List.of("atoms")), PAGE_HEIGHT_PT);

        assertThat(presented.code()).isEqualTo(ErrorCode.INSUFFICIENT_PROFILE);
        assertThat(presented.params()).containsEntry("completeness", 12);
        assertThat(presented.params().get("missing")).isEqualTo(List.of("atoms"));
        assertThat(presented.resolutions()).extracting(Resolution::action)
                .containsExactly(ResolutionAction.COMPLETE_PROFILE);
        assertThat(presented.httpStatus()).isEqualTo(422);
    }

    @Test
    void aPageConflictIsMeasuredInPagesNotPoints() {
        var presented = presenter.present(new PipelineError.ConflictingPreferences(
                PAGE_HEIGHT_PT * 2.34, PAGE_HEIGHT_PT,
                List.of(Resolution.of(ResolutionAction.REVIEW_PINS))), PAGE_HEIGHT_PT);

        assertThat(presented.params()).containsEntry("pinnedPages", 2.3);
        assertThat(presented.params()).containsEntry("maxPages", 1);
        assertThat(presented.httpStatus()).isEqualTo(409);
    }

    @Test
    void aConflictKeepsTheWaysOutSelectionComputed() {
        var options = List.of(
                Resolution.of(ResolutionAction.INCREASE_PAGE_LIMIT, "maxPages", 2),
                Resolution.of(ResolutionAction.REVIEW_PINS),
                Resolution.of(ResolutionAction.KEEP_TOP_PINNED, "keep", 3));

        var presented = presenter.present(new PipelineError.ConflictingPreferences(
                PAGE_HEIGHT_PT * 2, PAGE_HEIGHT_PT, options), PAGE_HEIGHT_PT);

        assertThat(presented.resolutions()).isEqualTo(options);
    }

    /** The offered limit is the page count the compiler actually produced. */
    @Test
    void anOverlongDocumentOffersTheLimitThatWouldHaveWorked() {
        var presented = presenter.present(
                new PipelineError.PageLimitExceeded(3, 1), PAGE_HEIGHT_PT);

        assertThat(presented.code()).isEqualTo(ErrorCode.PAGE_LIMIT_EXCEEDED);
        assertThat(presented.params()).containsEntry("actual", 3).containsEntry("limit", 1);
        assertThat(presented.resolutions()).containsExactly(
                Resolution.of(ResolutionAction.INCREASE_PAGE_LIMIT, "maxPages", 3));
    }

    @Test
    void aBusyCompilerIsWorthRetryingAndABadDocumentIsNot() {
        var busy = presenter.present(new PipelineError.CompilationFailed(
                CompilationFailureKind.BUSY, ""), PAGE_HEIGHT_PT);
        var broken = presenter.present(new PipelineError.CompilationFailed(
                CompilationFailureKind.INVALID_DOCUMENT, "! Undefined control sequence."),
                PAGE_HEIGHT_PT);

        assertThat(busy.resolutions()).extracting(Resolution::action)
                .containsExactly(ResolutionAction.RETRY);
        assertThat(broken.resolutions()).isEmpty();
    }

    /**
     * Bolum 18.1 is explicit that a posting which does not look like one is a
     * question, not a refusal — so all three ways out are offered, and the
     * first of them is proceeding with the same text.
     */
    @Test
    void aPreflightRefusalIsAQuestionWithThreeWaysOut() {
        var presented = presenter.present(new PipelineError.UnparseableJobDescription(
                0, 0, UnreadablePostingReason.NOT_JOB_LIKE), PAGE_HEIGHT_PT);

        assertThat(presented.httpStatus()).isEqualTo(422);
        assertThat(presented.resolutions()).extracting(Resolution::action).containsExactly(
                ResolutionAction.CONTINUE_ANYWAY,
                ResolutionAction.PASTE_FULL_POSTING,
                ResolutionAction.CONTINUE_AS_GENERAL_CV);
    }

    /**
     * A preflight refusal analysed nothing, so both numbers are zero rather
     * than invented — and {@code reason} is what the sentence is written from.
     */
    @Test
    void aPreflightRefusalReportsThatNothingWasAnalysed() {
        var presented = presenter.present(new PipelineError.UnparseableJobDescription(
                0, 0, UnreadablePostingReason.TOO_SHORT), PAGE_HEIGHT_PT);

        assertThat(presented.params())
                .containsEntry("reason", "too_short")
                .containsEntry("confidence", 0.0)
                .containsEntry("skillsFound", 0);
    }

    /**
     * F-016: the defect itself. A gate refusal carried {@code confidence 0.95}
     * and nothing to say the confidence was not what was wrong, so the screen
     * read "we could not read the posting — confidence 95%, 8 skills found".
     * The numbers still travel; {@code reason} is what makes them readable.
     */
    @Test
    void aSuspiciousAnswerSaysSoRatherThanBlamingThePosting() {
        var presented = presenter.present(new PipelineError.UnparseableJobDescription(
                0.95, 8, UnreadablePostingReason.SUSPICIOUS_OUTPUT), PAGE_HEIGHT_PT);

        assertThat(presented.params())
                .containsEntry("reason", "suspicious_output")
                .containsEntry("confidence", 0.95)
                .containsEntry("skillsFound", 8);
        // Nothing about the text is wrong, so there is nothing to paste; the
        // gate does not cache a refusal, so asking again is the way out.
        assertThat(presented.resolutions()).extracting(Resolution::action).containsExactly(
                ResolutionAction.RETRY,
                ResolutionAction.CONTINUE_AS_GENERAL_CV);
    }

    /**
     * The three thin-posting verdicts. {@code continue_anyway} is not offered:
     * the preflight had already passed, so acknowledging it skips nothing and
     * the resubmission meets the same gate — the button was {@code retry}
     * under a name that told the user to expect something else.
     */
    @Test
    void aThinPostingIsNotOfferedAWayPastAPreflightItAlreadyPassed() {
        for (var reason : List.of(UnreadablePostingReason.LOW_CONFIDENCE,
                UnreadablePostingReason.TOO_FEW_SKILLS,
                UnreadablePostingReason.NO_RESPONSIBILITIES)) {
            var presented = presenter.present(
                    new PipelineError.UnparseableJobDescription(0.7, 1, reason), PAGE_HEIGHT_PT);

            assertThat(presented.resolutions()).extracting(Resolution::action)
                    .as("%s", reason)
                    .containsExactly(ResolutionAction.PASTE_FULL_POSTING,
                            ResolutionAction.CONTINUE_AS_GENERAL_CV);
        }
    }

    /** Every reason presents, and every one of them publishes its own value. */
    @Test
    void everyReasonReachesTheWireUnderItsOwnName() {
        var published = Arrays.stream(UnreadablePostingReason.values())
                .map(reason -> presenter.present(new PipelineError.UnparseableJobDescription(
                        0.5, 2, reason), PAGE_HEIGHT_PT).params().get("reason"))
                .toList();

        assertThat(published).doesNotHaveDuplicates()
                .hasSize(UnreadablePostingReason.values().length)
                .allSatisfy(value -> assertThat((String) value).matches("[a-z][a-z_]*"));
    }

    /**
     * Bolum 27.3: a chain runs out only for reasons that were transient at
     * every stop, so the answer is always "ask again".
     */
    @Test
    void anExhaustedProviderChainNamesWhoWasAskedAndOffersARetry() {
        var presented = presenter.present(new PipelineError.AllProvidersUnavailable(
                List.of("gemini", "deepseek")), PAGE_HEIGHT_PT);

        assertThat(presented.httpStatus()).isEqualTo(503);
        assertThat(presented.params()).containsEntry("tried", List.of("gemini", "deepseek"));
        assertThat(presented.resolutions()).extracting(Resolution::action)
                .containsExactly(ResolutionAction.RETRY);
    }

    /**
     * Bolum 27.3 skips a provider with no key silently. Reporting it as tried
     * would tell the user a vendor this deployment never configured is down.
     */
    @Test
    void aChainThatFoundNothingConfiguredReportsAnEmptyList() {
        var presented = presenter.present(
                new PipelineError.AllProvidersUnavailable(List.of()), PAGE_HEIGHT_PT);

        assertThat(presented.params()).containsEntry("tried", List.of());
    }

    /** Absolute rule 4: the log is the user's own content and never a parameter. */
    @Test
    void theTexLogNeverReachesTheBody() {
        var presented = presenter.present(new PipelineError.CompilationFailed(
                CompilationFailureKind.INVALID_DOCUMENT,
                "! Undefined control sequence. \\thisWasTheUsersOwnBullet"), PAGE_HEIGHT_PT);

        assertThat(presented.params()).containsEntry("detail", "invalid_document");
        assertThat(presented.params().toString()).doesNotContain("UsersOwnBullet");
        assertThat(presented.params()).containsEntry("rawSourceAvailable", false);
    }
}
