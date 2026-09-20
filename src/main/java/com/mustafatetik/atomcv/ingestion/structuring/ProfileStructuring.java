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
 * A CV, read as structure.
 *
 * <p><strong>One call, and that is not negotiable.</strong> The English
 * rendering of every bullet is produced in the same request as the parse: a
 * second translation pass would work on a sentence already stripped of the
 * document that gave it meaning, and would double the cost of the most
 * expensive call the product makes.
 *
 * <p>The tier is {@link ModelTier#MID}, which is what reading a whole CV is
 * worth. It is the one place the product pays for a long input, and profile
 * creation has its own daily counter for exactly that reason.
 *
 * <p>Three refusals, and none of them says which one it was to anybody but the
 * operator. A language that could not be settled is asked about rather than
 * guessed at — and since F-037 the answer comes back on the next upload as
 * {@code declaredLanguage}, which skips the question entirely; a document that yielded no atoms and one whose answer failed the
 * field-length audit are the same answer, because no message may tell an
 * attacker their injection was noticed.
 */
@Component
public class ProfileStructuring {

    /** Public so a profile can record which prompt produced it. */
    public static final String PROMPT_ID = "profile_extraction";

    /** The fence: everything inside it is data, not instructions. */
    private static final String FENCE_TAG = "cv_text";

    /**
     * A whole CV in and a whole structured profile out, at MID tier. About
     * eight seconds is the budget; the timeout is what stops a stalled
     * provider holding a worker rather than what the call is expected to take.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    /**
     * Below this the language is asked about instead of assumed.
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

    /** Which prompt version this bucket runs on. */
    public String promptVersionFor(String bucketKey) {
        return prompts.selectVersion(PROMPT_ID, bucketKey);
    }

    /**
     * @param document  what came out of the file. Its
     *                  {@link ExtractedText#looksScrambled()} flag becomes a
     *                  sentence in front of the CV rather than a refusal
     *
     * @param bucketKey the user id, so an A/B experiment keeps one person on
     *  one prompt version
     */
    /**
     * @param userId whose upload this is, or {@code null} — and null is the
     *               ordinary case here rather than an omission. An anonymous
     *  import has no account to bill, which is exactly
     *               why {@code bucketKey} is a session id for it and why the
     *               two are separate arguments
     */
    /**
     * @param declaredLanguage what the caller said the CV is written in, ISO
     *                         639-1, or null — and null is the ordinary case.
     *                         When one is given the language gate is not run
     *                         and this is the language the profile gets
     *                         (F-037): the field exists so somebody who was
     *                         asked {@code choose_language} has somewhere to
     *                         put the answer, and a second upload that could
     *                         land on the same low confidence would be the
     *                         loop the question was meant to end
     */
    public Result<ExtractedProfile> structure(
            ExtractedText document, String declaredLanguage, String bucketKey,
            java.util.UUID userId, java.util.UUID jobId) {
        String version = prompts.selectVersion(PROMPT_ID, bucketKey);
        var prompt = prompts.load(PROMPT_ID, version);
        var fenced = FencedPrompt.of(prompt, FENCE_TAG);

        var answer = providers.call(new StructuredRequest<>(
                PROMPT_ID, version,
                fenced.system(),
                fenced.userPromptFor(withScrambleNote(document)),
                prompt.schema(), ExtractedProfile.class, ModelTier.MID, TIMEOUT,
                userId, jobId));

        return switch (answer) {
            // An outage is an outage. Restating it as an unreadable CV would
            // send the user to the manual form over a provider being down.
            case Result.Err<LlmResponse<ExtractedProfile>> failed -> Result.err(failed.error());
            case Result.Ok<LlmResponse<ExtractedProfile>> ok ->
                    gate(ok.value().data(), declaredLanguage);
        };
    }

    /**
     * The note, and it goes <em>inside</em> the fence.
     *
     * <p>Outside it, in the system half, the sentence would be a standing
     * instruction on every call — and it would break the constant prefix that
     * the prompt caching discounts. Inside, it is what it actually is: a
     * remark about this one document.
     */
    private static String withScrambleNote(ExtractedText document) {
        if (!document.looksScrambled()) {
            return document.text();
        }
        return "NOTE: this text may have come out of the file in the wrong order. "
                + "Reconstruct the reading order where you can.\n\n" + document.text();
    }

    private Result<ExtractedProfile> gate(ExtractedProfile profile, String declaredLanguage) {
        var abnormal = StructuringAudit.abnormalField(profile);
        if (abnormal.isPresent()) {
            // The third layer. The field's name, never its value: the suspect
            // string is the thing whoever wrote it wants echoed.
            log.warn("Extraction refused by the field-length audit: {}", abnormal.get());
            return Result.err(new PipelineError.NothingExtracted());
        }
        if (declaredLanguage != null && !declaredLanguage.isBlank()) {
            // The person answered the question, so there is nothing left to
            // detect. Their answer replaces the model's guess rather than
            // sitting beside it: the profile stores one language, and keeping
            // a low-confidence guess in it would mean the answer changed
            // nothing the CV is written from.
            //
            // Confidence goes to 1.0 for the same reason. It is not a claim
            // about the model -- it is the record that this language was not
            // guessed at.
            profile = profile.inLanguage(declaredLanguage);
            log.info("Extraction took the caller's language: {}", profile.shape());
            return profile.atoms().isEmpty()
                    ? Result.err(new PipelineError.NothingExtracted())
                    : Result.ok(profile);
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
