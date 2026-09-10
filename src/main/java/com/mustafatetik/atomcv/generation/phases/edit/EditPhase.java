package com.mustafatetik.atomcv.generation.phases.edit;

import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.ProviderChain;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.mustafatetik.atomcv.llm.prompts.FencedPrompt;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Faz G: one sentence, read as two lists of line numbers (Bolum 24).
 *
 * <p><strong>The model never sees an atom id and never writes one.</strong>
 * Bolum 24.2 sketches a change set carrying ids, and this does not: an id is
 * exactly the kind of token a model invents or mistypes, and an invented id is
 * indistinguishable from a real one until it is looked up. The lines are
 * numbered before they are shown and the answer is an index, so the only
 * damage a confused model can do is name a number in range — which is a wrong
 * bullet, caught by the person looking at the result, rather than a silent
 * write against somebody else's row.
 *
 * <p>Out-of-range numbers are dropped rather than refused, and dropping every
 * number is a refusal. That ordering matters: a model that answered
 * {@code [2, 47]} understood half the sentence, and applying the half it got
 * right is worse than saying nothing — the person asked for one thing and
 * would be shown another.
 *
 * <p>{@link ModelTier#CHEAP}. It is one short sentence against a numbered
 * list, which is the easy end of Bolum 5.4, and it is the call this feature
 * makes every time.
 */
@Component
public class EditPhase {

    /** Public so a generation record can name the prompt it ran (Bolum 14.7). */
    public static final String PROMPT_ID = "selection_edit";

    /** Bolum 43.1's fence: everything inside it is data, not instructions. */
    private static final String FENCE_TAG = "edit";

    private static final Logger log = LoggerFactory.getLogger(EditPhase.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final PromptRegistry prompts;
    private final ProviderChain providers;

    public EditPhase(PromptRegistry prompts, ProviderChain providers) {
        this.prompts = prompts;
        this.providers = providers;
    }

    public String promptVersionFor(String bucketKey) {
        return prompts.selectVersion(PROMPT_ID, bucketKey);
    }

    /**
     * @param instruction what the person typed, in their own words
     * @param lines       what they can be talking about, numbered from one:
     *                    the page first, then what the budget held back
     * @return the numbers, resolved back to the lines they name
     */
    public Result<EditPlan> parse(
            String instruction, NumberedLines lines, String bucketKey, UUID userId, UUID jobId) {

        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("An edit with nothing in it is refused at the API");
        }

        var version = prompts.selectVersion(PROMPT_ID, bucketKey);
        var prompt = prompts.load(PROMPT_ID, version);
        var fenced = FencedPrompt.of(prompt, FENCE_TAG);

        var request = new StructuredRequest<>(
                PROMPT_ID, version,
                fenced.system(),
                fenced.userPromptFor(lines.asPromptData(instruction)),
                prompt.schema(), EditAnswer.class, ModelTier.CHEAP, TIMEOUT, userId, jobId);

        return switch (providers.call(request)) {
            case Result.Err<LlmResponse<EditAnswer>> failed -> Result.err(failed.error());
            case Result.Ok<LlmResponse<EditAnswer>> ok -> resolve(ok.value().data(), lines);
        };
    }

    /**
     * The answer, checked against the list it was given.
     *
     * <p>Counts only in the log line: which numbers came back is a fact about
     * the model, what they point at is the user's CV (absolute rule 4).
     */
    private Result<EditPlan> resolve(EditAnswer answer, NumberedLines lines) {
        if (!answer.understood()) {
            // The prompt asks for this rather than a guess, so it is an
            // ordinary answer and not a failure of the call.
            return Result.err(new PipelineError.EditNotUnderstood());
        }

        // Deduplicated on both sides of the comparison below: one number said
        // twice is one line, and counting the repeat as a number we could not
        // resolve would refuse a sentence the model got right.
        Set<Integer> drop = inRange(answer.drop(), lines);
        Set<Integer> keep = inRange(answer.keep(), lines);
        int named = distinct(answer.drop()).size() + distinct(answer.keep()).size();
        int resolved = drop.size() + keep.size();

        if (resolved < named) {
            // Half a sentence applied is worse than none: the person asked for
            // one thing and would be shown another, with nothing saying so.
            log.info("Faz G named {} lines and {} were in range; refusing the edit",
                    named, resolved);
            return Result.err(new PipelineError.EditNotUnderstood());
        }
        if (resolved == 0) {
            return Result.err(new PipelineError.EditNotUnderstood());
        }

        var excluded = new ArrayList<UUID>();
        for (int number : drop) {
            excluded.add(lines.idAt(number));
        }
        var included = new ArrayList<UUID>();
        for (int number : keep) {
            UUID atomId = lines.idAt(number);
            if (excluded.contains(atomId)) {
                // One line in both answers. The model was told it cannot be,
                // and a directive holding both is refused downstream anyway --
                // better to say "not understood" than to pick one.
                return Result.err(new PipelineError.EditNotUnderstood());
            }
            included.add(atomId);
        }

        return Result.ok(new EditPlan(List.copyOf(included), List.copyOf(excluded)));
    }

    /** Ordered and without repeats, so one number named twice is one line. */
    private static Set<Integer> inRange(List<Integer> numbers, NumberedLines lines) {
        var kept = new LinkedHashSet<Integer>();
        for (Integer number : distinct(numbers)) {
            if (lines.has(number)) {
                kept.add(number);
            }
        }
        return kept;
    }

    private static Set<Integer> distinct(List<Integer> numbers) {
        var seen = new LinkedHashSet<Integer>();
        for (Integer number : numbers) {
            if (number != null) {
                seen.add(number);
            }
        }
        return seen;
    }
}
