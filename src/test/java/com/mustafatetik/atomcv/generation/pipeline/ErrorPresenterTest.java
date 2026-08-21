package com.mustafatetik.atomcv.generation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.shared.error.CompilationFailureKind;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
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
