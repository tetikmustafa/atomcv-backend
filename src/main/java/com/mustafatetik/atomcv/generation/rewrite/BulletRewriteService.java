package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.embedding.EmbeddingProvider;
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
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One bullet, rewritten for one posting — or not (Bolum 21.4, Bolum 21.6).
 *
 * <p><strong>This never fails.</strong> It answers with the rewrite when the
 * rewrite is good, and with what the person originally wrote when it is not,
 * and the caller cannot tell the difference from the return type. That is
 * Bolum 21.6's rule: try once more, then use the original. A generation must
 * not fall over because a model produced a sentence that did not pass a check
 * — the person asked for a CV, and the CV they already had is right there.
 *
 * <p>Two attempts and no more. The second is worth making because these
 * failures are largely a model being sloppy once; a third would be paying
 * again for the same answer, and the original is not a bad outcome.
 */
@Service
public class BulletRewriteService {

    public static final String PROMPT_ID = "bullet_rewrite";

    /** Bolum 43.1's fence. Everything inside it is data, the lists included. */
    private static final String FENCE_TAG = "bullet";

    /** Ours, not the CV's, so they are substituted into the instructions. */
    private static final String MAX_CHARS = "{{max_chars}}";
    private static final String INTENT = "{{intent}}";
    private static final String LANGUAGE = "{{language}}";
    private static final String TONE = "{{tone}}";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Bolum 21.6: one retry, then the original. */
    static final int ATTEMPTS = 2;

    private static final Logger log = LoggerFactory.getLogger(BulletRewriteService.class);

    private final PromptRegistry prompts;
    private final ProviderChain providers;
    private final EmbeddingProvider embeddings;

    BulletRewriteService(PromptRegistry prompts, ProviderChain providers,
            EmbeddingProvider embeddings) {
        this.prompts = prompts;
        this.providers = providers;
        this.embeddings = embeddings;
    }

    /** Which version of the prompt this bucket is on (Bolum 53.3). */
    public String promptVersionFor(String bucketKey) {
        return prompts.selectVersion(PROMPT_ID, bucketKey);
    }

    /**
     * @param candidate what the planner admitted, carrying its own constraints
     * @param context   the posting's words and the profile's voice
     * @return the content to print for this atom, rewritten or original
     */
    public RichContent rewrite(RewriteCandidate candidate, RewriteContext context) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            RichContent accepted = attempt(candidate, context);
            if (accepted != null) {
                return accepted;
            }
        }
        // Counts and ids are the log; the sentence is the user's (rule 4).
        log.info("Kept the original wording for atom {} after {} attempts",
                candidate.atomId(), ATTEMPTS);
        return candidate.original();
    }

    /** @return the accepted content, or null when this attempt did not pass */
    private RichContent attempt(RewriteCandidate candidate, RewriteContext context) {
        Prompt prompt = prompts.load(PROMPT_ID,
                prompts.selectVersion(PROMPT_ID, context.bucketKey()));
        FencedPrompt fenced = FencedPrompt.of(prompt, FENCE_TAG);

        String system = fenced.system()
                .replace(MAX_CHARS, Integer.toString(candidate.maxChars()))
                .replace(INTENT, candidate.intent().name().toLowerCase(Locale.ROOT))
                .replace(LANGUAGE, context.language())
                .replace(TONE, context.tone());

        var answer = providers.call(new StructuredRequest<>(
                PROMPT_ID, prompt.version(), system,
                fenced.userPromptFor(fencedData(candidate, context)),
                prompt.schema(), RewrittenBullet.class, ModelTier.MID, TIMEOUT, context.userId()));

        if (answer instanceof Result.Err<LlmResponse<RewrittenBullet>>) {
            // An outage is not a rewrite failure, but the answer is the same
            // one: the original stands and the generation carries on.
            return null;
        }
        RewrittenBullet rewritten = answer.orElseThrow().data();

        List<RewriteIssue> issues = RewriteValidator.validate(
                candidate, rewritten.text(), context.postingSkills(),
                vectorOf(rewritten.text()), candidate.originalVector());
        if (!issues.isEmpty()) {
            log.info("A rewrite of atom {} was refused: {}", candidate.atomId(), issues);
            return null;
        }
        return RunMarking.mark(rewritten.text(), rewritten.emphasis(),
                candidate.skills(), candidate.metrics());
    }

    /**
     * Everything the model is given, inside the fence.
     *
     * <p>The lists go in here rather than into the instructions above because
     * they are the person's own content and the posting's — Bolum 43.1 draws
     * the line at where the data starts, not at which field looks structured.
     */
    private static String fencedData(RewriteCandidate candidate, RewriteContext context) {
        return "line: " + candidate.originalText()
                + "\nallowedSkills: " + String.join(", ", candidate.skills())
                + "\nmustKeep: " + String.join(", ", mustKeep(candidate))
                + "\npostingWants: " + String.join(", ", context.postingSkills());
    }

    private static List<String> mustKeep(RewriteCandidate candidate) {
        return java.util.stream.Stream
                .concat(candidate.metrics().stream(), candidate.properNouns().stream())
                .toList();
    }

    /**
     * The drift check needs the answer's vector, and the embedding service is
     * the one dependency here that is allowed to be missing: a check that
     * cannot run must not be reported as one that passed, so a null travels to
     * the validator and it skips that rule rather than inventing a similarity.
     */
    private float[] vectorOf(String text) {
        try {
            return embeddings.embed(text);
        } catch (RuntimeException unavailable) {
            log.warn("Could not embed a rewrite to check it for drift: {}",
                    unavailable.getClass().getSimpleName());
            return null;
        }
    }
}
