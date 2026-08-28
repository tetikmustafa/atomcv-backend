package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.ProviderChain;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.mustafatetik.atomcv.llm.prompts.FencedPrompt;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.VariantAuthor;
import com.mustafatetik.atomcv.profile.domain.content.RunMarking;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One stale wording, brought back into step with the one it came from
 * (Bolum 21.8, Bolum 32.2).
 *
 * <p><strong>It never touches a wording the user wrote.</strong> That is the
 * whole of Bolum 32.2's protection: an edit to the Turkish marks the English
 * stale either way, so the person is told the two have diverged — but if they
 * wrote that English themselves, nothing regenerates it behind their back.
 * The choice is theirs to make on the screen.
 *
 * <p>The tier is {@link ModelTier#CHEAP}: one sentence in, one sentence out,
 * and it is the call this product will make most often once a profile has two
 * languages.
 */
@Service
public class VariantTranslationService {

    public static final String PROMPT_ID = "translation";

    /** Bolum 43.1's fence: everything inside it is data, not instructions. */
    private static final String FENCE_TAG = "atom_text";

    /**
     * The target language is an instruction, so it lives in the instructions.
     *
     * <p>Substituted into the system half rather than sent inside the fence:
     * a CV that wrote "Target language: en" in one of its bullets would
     * otherwise be giving the order. The prefix then varies by language, and
     * that costs nothing real — Bolum 27.4's caching keys on the prefix, and
     * there is one prefix per language rather than one per call.
     */
    private static final String TARGET_LANGUAGE = "{{target_language}}";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(VariantTranslationService.class);

    private final PromptRegistry prompts;
    private final ProviderChain providers;
    private final AtomRepository atoms;
    private final AtomVariantRepository variants;

    VariantTranslationService(PromptRegistry prompts, ProviderChain providers,
            AtomRepository atoms, AtomVariantRepository variants) {
        this.prompts = prompts;
        this.providers = providers;
        this.atoms = atoms;
        this.variants = variants;
    }

    /**
     * Regenerates one wording from the one it was derived from.
     *
     * <p>The caller has already decided there is work: that the wording still
     * exists, that the user did not write it, and that its source has words in
     * it. Those are three different reasons to do nothing and none of them is
     * a failure, so they are answered where they are known rather than
     * squeezed into an error type here.
     *
     * @param variantId the stale wording
     * @param source    what it is derived from
     * @param atom      the fact it belongs to, whose numbers and names the
     *                  translation has to keep
     */
    @Transactional
    public Result<AtomVariant> retranslate(ProfileRef profile, UUID variantId,
            AtomVariant source, Atom atom, String bucketKey, UUID userId) {

        AtomVariant target = variants.findById(profile, variantId).orElseThrow(
                () -> new IllegalStateException("The wording was checked and then vanished"));

        var prompt = prompts.load(PROMPT_ID, prompts.selectVersion(PROMPT_ID, bucketKey));
        var fenced = FencedPrompt.of(prompt, FENCE_TAG);
        var answer = providers.call(new StructuredRequest<>(
                PROMPT_ID, prompt.version(),
                fenced.system().replace(TARGET_LANGUAGE, target.getLanguage()),
                fenced.userPromptFor(source.getPlainText()),
                prompt.schema(), AtomTranslation.class, ModelTier.CHEAP, TIMEOUT, userId));

        return switch (answer) {
            case Result.Err<LlmResponse<AtomTranslation>> failed -> Result.err(failed.error());
            case Result.Ok<LlmResponse<AtomTranslation>> ok ->
                    store(profile, target, source, atom, ok.value().data());
        };
    }

    private Result<AtomVariant> store(ProfileRef profile, AtomVariant target,
            AtomVariant source, Atom atom, AtomTranslation translated) {

        List<String> lost = TranslationAudit.missingFrom(
                atom.getMetrics(), atom.getProperNouns(), translated.text());
        if (!lost.isEmpty()) {
            // Bolum 21.8's fourth step. The count and not the values: what was
            // dropped is the user's own content (absolute rule 4).
            log.warn("A translation dropped {} thing(s) it had to keep", lost.size());
            return Result.err(new PipelineError.TranslationRejected());
        }

        target.setContent(RunMarking.mark(translated.text(), translated.emphasis(),
                atom.getSkills(), atom.getMetrics()));
        target.setCreatedBy(VariantAuthor.LLM_TRANSLATE);
        // Derived-from records what it was made from, and clears the flag.
        target.markDerivedFrom(source);
        return Result.ok(variants.save(profile, target));
    }
}
