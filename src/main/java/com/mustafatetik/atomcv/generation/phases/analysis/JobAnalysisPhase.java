package com.mustafatetik.atomcv.generation.phases.analysis;

import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.ProviderChain;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Faz A: a posting, read as structure (Bolum 18).
 *
 * <p>Three gates around one call. The preflight refuses what is not worth
 * sending (Bolum 18.1), the schema constrains what can come back
 * (Bolum 18.2), and the plausibility gate refuses what came back anyway
 * (Bolum 18.4). Only the middle one costs money, which is the whole shape of
 * design principle 5.
 *
 * <p>The tier is {@link ModelTier#CHEAP}: Bolum 5.4 classes structured
 * extraction from a posting as an easy task, and it is the call the product
 * makes most often.
 */
@Component
public class JobAnalysisPhase {

    static final String PROMPT_ID = "job_analysis";

    private static final Logger log = LoggerFactory.getLogger(JobAnalysisPhase.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final PromptRegistry prompts;
    private final ProviderChain providers;

    public JobAnalysisPhase(PromptRegistry prompts, ProviderChain providers) {
        this.prompts = prompts;
        this.providers = providers;
    }

    /**
     * @param jobDescription        the pasted posting. General CV mode never
     *                              reaches here: there is nothing to analyse,
     *                              and the caller takes that branch before
     *                              paying for a call (Bolum 18.1).
     * @param preflightAcknowledged the user chose {@code continue_anyway} after
     *                              a refusal (EK D.6.1). They may know better
     *                              than the heuristics, which are cheap on
     *                              purpose — the plausibility gate still runs.
     * @param bucketKey             the user id, so an A/B experiment keeps one
     *                              person on one prompt version (Bolum 53.3)
     */
    public Result<JobAnalysis> analyse(
            String jobDescription, boolean preflightAcknowledged, String bucketKey) {

        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException(
                    "Faz A is not reached in general CV mode (Bolum 18.1)");
        }

        if (!preflightAcknowledged) {
            var verdict = JobDescriptionPreflight.check(jobDescription);
            if (!verdict.isAccepted()) {
                // The verdict, never the posting (absolute rule 4). Which
                // check refused is what a metric is worth; the text is not.
                log.info("Preflight refused a posting: {}", verdict);
                // Nothing was analysed, and zero says so rather than inventing
                // a number the message would read out.
                return Result.err(new PipelineError.UnparseableJobDescription(0, 0));
            }
        }

        var version = prompts.selectVersion(PROMPT_ID, bucketKey);
        var prompt = prompts.load(PROMPT_ID, version);
        var fenced = FencedPrompt.of(prompt);

        var answer = providers.call(new StructuredRequest<>(
                PROMPT_ID, version,
                fenced.system(),
                fenced.userPromptFor(jobDescription),
                prompt.schema(), JobAnalysis.class, ModelTier.CHEAP, TIMEOUT));

        return switch (answer) {
            // AllProvidersUnavailable travels as it is: the chain already said
            // what happened, and restating it as an unreadable posting would
            // blame the user for an outage.
            case Result.Err<LlmResponse<JobAnalysis>> failed -> Result.err(failed.error());
            case Result.Ok<LlmResponse<JobAnalysis>> ok -> gate(ok.value().data());
        };
    }

    /** Bolum 18.4. A refusal here means Faz B is never entered, so no more is spent. */
    private Result<JobAnalysis> gate(JobAnalysis analysis) {
        var verdict = PlausibilityGate.check(analysis);
        if (verdict.isAccepted()) {
            return Result.ok(analysis);
        }
        log.info("Plausibility gate refused an analysis: {}", verdict);
        return Result.err(new PipelineError.UnparseableJobDescription(
                analysis.confidence(), analysis.requiredSkills().size()));
    }
}
