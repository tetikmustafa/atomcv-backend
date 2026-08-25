package com.mustafatetik.atomcv.generation.pipeline;

import com.mustafatetik.atomcv.shared.error.CompilationFailureKind;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Every pipeline failure, as something a user can act on (Bolum 25.3).
 *
 * <p>The switch is exhaustive on purpose: a new kind of failure does not
 * compile until someone has decided what the user is told and what they can do
 * about it. Design principle 4, enforced by the language rather than by
 * discipline.
 */
@Component
public class ErrorPresenter {

    /**
     * @param pageHeightPt the page the limit was measured against, needed to
     *                     say "2.3 pages" rather than "1627 points"
     */
    public UserFacingError present(PipelineError error, double pageHeightPt) {
        return switch (error) {
            case PipelineError.InsufficientProfile thin -> UserFacingError
                    .with(ErrorCode.INSUFFICIENT_PROFILE)
                    .param("completeness", thin.completeness())
                    .param("missing", thin.missing())
                    .resolution(ResolutionAction.COMPLETE_PROFILE)
                    .build();

            /*
             * No resolution, deliberately. The closed vocabulary of EK D.6.1
             * has no "wait until tomorrow", and `retry` means the opposite —
             * a transient failure worth repeating unchanged, now. Adding an
             * eleventh value would buy a button that does nothing the params
             * do not already say: `resetsAt` is an absolute instant and the
             * client writes the sentence in the user's own locale (F-007).
             */
            /*
             * `retry` and nothing else: the brake is lifted by an operator,
             * not by anything the user can do, and asking again later is
             * exactly the right advice.
             */
            case PipelineError.GenerationPaused ignored -> UserFacingError.of(
                    ErrorCode.GENERATION_PAUSED, new Resolution(ResolutionAction.RETRY, null));

            case PipelineError.QuotaExceeded spent -> UserFacingError
                    .with(ErrorCode.QUOTA_EXCEEDED)
                    .param("metric", spent.metric())
                    .param("resetsAt", spent.resetsAt())
                    .build();

            case PipelineError.UnparseableJobDescription unreadable -> {
                var presented = UserFacingError.with(ErrorCode.UNPARSEABLE_JOB_DESCRIPTION)
                        // The reason, never the posting (absolute rule 4): a
                        // closed vocabulary naming which check refused.
                        .param("reason", unreadable.reason().wireValue())
                        .param("confidence", unreadable.confidence())
                        .param("skillsFound", unreadable.skillsFound());
                // Bolum 18.1: a posting that does not look like one is a
                // question, not a refusal. All three ways out are offered,
                // in the order Bolum 18.1 lists them.
                presented.resolution(ResolutionAction.CONTINUE_ANYWAY)
                        .resolution(ResolutionAction.PASTE_FULL_POSTING)
                        .resolution(ResolutionAction.CONTINUE_AS_GENERAL_CV);
                yield presented.build();
            }

            case PipelineError.ConflictingPreferences conflict -> UserFacingError
                    .with(ErrorCode.CONFLICTING_PREFERENCES)
                    .param("pinnedPages", round(conflict.pinnedPages(pageHeightPt)))
                    .param("maxPages", (int) Math.round(conflict.budgetPt() / pageHeightPt))
                    .resolutions(conflict.options())
                    .build();

            case PipelineError.PageLimitExceeded overflow -> UserFacingError
                    .with(ErrorCode.PAGE_LIMIT_EXCEEDED)
                    .param("actual", overflow.actualPages())
                    .param("limit", overflow.maxPages())
                    // The page count the compiler actually produced: the one
                    // number that is known to be enough.
                    .resolution(Resolution.of(ResolutionAction.INCREASE_PAGE_LIMIT,
                            "maxPages", overflow.actualPages()))
                    .build();

            case PipelineError.AllProvidersUnavailable outage -> UserFacingError
                    .with(ErrorCode.ALL_PROVIDERS_UNAVAILABLE)
                    // Vendor ids, not content: EK D.6 publishes the list, and
                    // it is what makes the message say "we could not reach the
                    // model" rather than "something went wrong".
                    .param("tried", outage.tried())
                    // Every reason a chain runs out is transient by
                    // construction — the failures that are not transient stop
                    // the walk before it reaches here (Bolum 27.3).
                    .resolution(ResolutionAction.RETRY)
                    .build();

            case PipelineError.CompilationFailed failed -> {
                var presented = UserFacingError.with(ErrorCode.COMPILATION_FAILED)
                        // The kind, never the log: the log is built from the
                        // user's own content and this string is interpolated
                        // into a message.
                        .param("detail", failed.kind().name().toLowerCase(Locale.ROOT))
                        // Stage 1 keeps no artifacts, so there is no source to
                        // offer alongside the failure yet (EK D.8.8).
                        .param("rawSourceAvailable", false);
                if (retryable(failed)) {
                    presented.resolution(ResolutionAction.RETRY);
                }
                yield presented.build();
            }
        };
    }

    /**
     * A document TeX refused will be refused again. The other three are the
     * service being busy, slow or absent, and those pass.
     */
    private static boolean retryable(PipelineError.CompilationFailed failed) {
        return failed.kind() != CompilationFailureKind.INVALID_DOCUMENT;
    }

    /** One decimal is as much precision as a page count deserves. */
    private static double round(double pages) {
        return Math.round(pages * 10) / 10.0;
    }
}
