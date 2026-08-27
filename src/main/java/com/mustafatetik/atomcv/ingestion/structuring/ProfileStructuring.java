package com.mustafatetik.atomcv.ingestion.structuring;

import com.mustafatetik.atomcv.ingestion.extraction.ExtractedText;
import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.ProviderChain;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.mustafatetik.atomcv.llm.prompts.FencedPrompt;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A CV, read as structure (Bolum 31.4).
 *
 * <p><strong>One call, and Bolum 31.4 insists on it.</strong> The English
 * rendering of every bullet is produced in the same request as the parse:
 * a second translation pass would work on a sentence already stripped of the
 * document that gave it meaning, and would double the cost of the most
 * expensive call the product makes.
 *
 * <p>The tier is {@link ModelTier#MID}, which is what Bolum 5.4 assigns to
 * reading a whole CV. It is the one place the product pays for a long input,
 * and Bolum 44.1 gives profile creation its own daily counter for exactly that
 * reason.
 *
 * <p>Three refusals, and none of them says which one it was to anybody but the
 * operator. A language that could not be settled is asked about rather than
 * guessed at; a document that yielded no atoms and one whose answer failed the
 * field-length audit are the same answer, because Bolum 43.2 will not let a
 * message tell an attacker their injection was noticed.
 */
@Component
public class ProfileStructuring {

    /** Public so a profile can record which prompt produced it. */
    public static final String PROMPT_ID = "profile_extraction";

    /** Bolum 43.1's fence: everything inside it is data, not instructions. */
    private static final String FENCE_TAG = "cv_text";

    /**
     * A whole CV in and a whole structured profile out, at MID tier. Bolum
     * 31.6 budgets around eight seconds for this; the timeout is what stops a
     * stalled provider holding a worker rather than what the call is expected
     * to take.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    /**
     * Below this the language is asked about instead of assumed (Bolum 31.10).
     *
     * <p>Half. The number is not fine-tuning: the model is told to score a
     * document it cannot place below 0.5, so the floor is where the prompt
     * already draws the line, and moving one without the other would make the
     * instruction a lie.
     */
    private static final double MIN_LANGUAGE_CONFIDENCE = 0.5;

    private static final Logger log = LoggerFactory.getLogger(ProfileStructuring.class);

    private final PromptRegistry prompts;
    private final ProviderChain providers;

    ProfileStructuring(PromptRegistry prompts, ProviderChain providers) {
        this.prompts = prompts;
        this.providers = providers;
    }

    /** Which prompt version this bucket runs on (Bolum 53.3). */
    public String promptVersionFor(String bucketKey) {
        return prompts.selectVersion(PROMPT_ID, bucketKey);
    }

    /**
     * @param document  what came out of the file. Its
     *                  {@link ExtractedText#looksScrambled()} flag becomes a
     *                  sentence in front of the CV rather than a refusal
     *                  (Bolum 31.3)
     * @param bucketKey the user id, so an A/B experiment keeps one person on
     *                  one prompt version (Bolum 53.3)
     */
    public Result<ExtractedProfile> structure(ExtractedText document, String bucketKey) {
        String version = prompts.selectVersion(PROMPT_ID, bucketKey);
        var prompt = prompts.load(PROMPT_ID, version);
        var fenced = FencedPrompt.of(prompt, FENCE_TAG);

        var answer = providers.call(new StructuredRequest<>(
                PROMPT_ID, version,
                fenced.system(),
                fenced.userPromptFor(withScrambleNote(document)),
                prompt.schema(), ExtractedProfile.class, ModelTier.MID, TIMEOUT));

        return switch (answer) {
            // An outage is an outage. Restating it as an unreadable CV would
            // send the user to the manual form over a provider being down.
            case Result.Err<LlmResponse<ExtractedProfile>> failed -> Result.err(failed.error());
            case Result.Ok<LlmResponse<ExtractedProfile>> ok -> gate(ok.value().data());
        };
    }

    /**
     * Bolum 31.3's note, and it goes <em>inside</em> the fence.
     *
     * <p>Outside it, in the system half, the sentence would be a standing
     * instruction on every call — and it would break the constant prefix that
     * Bolum 27.4's prompt caching discounts. Inside, it is what it actually
     * is: a remark about this one document.
     */
    private static String withScrambleNote(ExtractedText document) {
        if (!document.looksScrambled()) {
            return document.text();
        }
        return "NOTE: this text may have come out of the file in the wrong order. "
                + "Reconstruct the reading order where you can.\n\n" + document.text();
    }

    private Result<ExtractedProfile> gate(ExtractedProfile profile) {
        var abnormal = StructuringAudit.abnormalField(profile);
        if (abnormal.isPresent()) {
            // Bolum 43.1's third layer. The field's name, never its value:
            // the suspect string is the thing whoever wrote it wants echoed.
            log.warn("Extraction refused by the field-length audit: {}", abnormal.get());
            return Result.err(new PipelineError.NothingExtracted());
        }
        if (profile.detectedLanguage().isBlank()
                || profile.languageConfidence() < MIN_LANGUAGE_CONFIDENCE) {
            log.info("Extraction could not settle a language: {}", profile.shape());
            // The low-confidence guess is offered as the one candidate, which
            // is all there is: the model returns a language, not a ranking.
            // Empty when it returned nothing, so the question becomes an open
            // one rather than a list of nothing.
            return Result.err(new PipelineError.LanguageUndetected(
                    profile.detectedLanguage().isBlank()
                            ? List.of()
                            : List.of(profile.detectedLanguage())));
        }
        if (profile.atoms().isEmpty()) {
            log.info("Extraction produced no atoms: {}", profile.shape());
            return Result.err(new PipelineError.NothingExtracted());
        }
        log.info("Extracted a profile: {}", profile.shape());
        return Result.ok(profile);
    }
}
