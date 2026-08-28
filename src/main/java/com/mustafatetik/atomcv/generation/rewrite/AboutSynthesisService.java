package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.ProviderChain;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.mustafatetik.atomcv.llm.prompts.FencedPrompt;
import com.mustafatetik.atomcv.llm.prompts.Prompt;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.RunMarking;
import com.mustafatetik.atomcv.shared.error.Result;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The opening paragraph, written from the rest of the page (Bolum 21.7).
 *
 * <p>Next to {@link BulletRewriteService} and deliberately not inside it. The
 * two share a shape — two attempts, then what the person wrote — and share
 * nothing else: this one is given the whole page's skills instead of one
 * atom's, has nothing it must preserve, and is checked by a validator with
 * different rules. Folding them together would put four branches through the
 * part that is identical.
 *
 * <p><strong>This never fails.</strong> A summary that could not be written is
 * the summary the person already had.
 */
@Service
public class AboutSynthesisService {

    public static final String PROMPT_ID = "about_synthesis";

    /** Bolum 43.1's fence. The lists and the person's own words are all data. */
    private static final String FENCE_TAG = "about";

    /** Ours, not the CV's, so they are substituted into the instructions. */
    private static final String MAX_CHARS = "{{max_chars}}";
    private static final String LANGUAGE = "{{language}}";
    private static final String TONE = "{{tone}}";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Bolum 21.6's rule, and it governs here too: one retry, then the original. */
    static final int ATTEMPTS = 2;

    private static final Logger log = LoggerFactory.getLogger(AboutSynthesisService.class);

    private final PromptRegistry prompts;
    private final ProviderChain providers;

    AboutSynthesisService(PromptRegistry prompts, ProviderChain providers) {
        this.prompts = prompts;
        this.providers = providers;
    }

    /** Which version of the prompt this bucket is on (Bolum 53.3). */
    public String promptVersionFor(String bucketKey) {
        return prompts.selectVersion(PROMPT_ID, bucketKey);
    }

    /**
     * @return the paragraph to print: synthesised, or the person's own
     */
    public RichContent synthesise(AboutCandidate candidate, RewriteContext context) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            RichContent accepted = attempt(candidate, context);
            if (accepted != null) {
                return accepted;
            }
        }
        log.info("Kept the person's own About for atom {} after {} attempts",
                candidate.atomId(), ATTEMPTS);
        return candidate.original();
    }

    /** @return the accepted paragraph, or null when this attempt did not pass */
    private RichContent attempt(AboutCandidate candidate, RewriteContext context) {
        Prompt prompt = prompts.load(PROMPT_ID,
                prompts.selectVersion(PROMPT_ID, context.bucketKey()));
        FencedPrompt fenced = FencedPrompt.of(prompt, FENCE_TAG);

        String system = fenced.system()
                .replace(MAX_CHARS, Integer.toString(candidate.maxChars()))
                .replace(LANGUAGE, context.language())
                .replace(TONE, context.tone());

        var answer = providers.call(new StructuredRequest<>(
                PROMPT_ID, prompt.version(), system,
                fenced.userPromptFor(fencedData(candidate)),
                prompt.schema(), SynthesisedAbout.class, ModelTier.MID, TIMEOUT, context.userId()));

        if (answer instanceof Result.Err<LlmResponse<SynthesisedAbout>>) {
            // An outage is not a failed synthesis, but the answer is the same:
            // the person's paragraph stands and the generation carries on.
            return null;
        }
        String text = answer.orElseThrow().data().text();

        List<RewriteIssue> issues =
                AboutValidator.validate(candidate, text, context.postingSkills());
        if (!issues.isEmpty()) {
            log.info("An About for atom {} was refused: {}", candidate.atomId(), issues);
            return null;
        }
        // No emphasis: a summary that bolds its way through four technologies
        // emphasises none of them, and the marks the page needs are the ones
        // RunMarking derives from the skills themselves.
        return RunMarking.mark(text, List.of(), candidate.skills(), candidate.metrics());
    }

    /**
     * Everything the model is given, inside the fence — all of it either the
     * person's content or the posting's (Bolum 43.1).
     */
    private static String fencedData(AboutCandidate candidate) {
        return "current: " + candidate.originalText()
                + "\nskills: " + String.join(", ", candidate.skills())
                + "\nmetrics: " + String.join(", ", candidate.metrics())
                + "\nownWords: " + candidate.ownWords()
                + "\npostingFocus: " + String.join("; ", candidate.focus());
    }
}
