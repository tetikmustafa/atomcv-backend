package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.ProviderChain;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.mustafatetik.atomcv.llm.prompts.FencedPrompt;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One stale wording, brought back into step with the one it came from
 * (Bolum 21.8, Bolum 32.2) — and one wording that never existed at all
 * (Bolum 21.8's second step), by way of English when that is the better route
 * (Bolum 32.5).
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
 *
 * <p><strong>Nothing here is transactional, on purpose.</strong> The writes
 * are {@link TranslationWriter}'s, and that class carries the reason.
 */
@Service
public class VariantTranslationService {

    public static final String PROMPT_ID = "translation";

    /**
     * Bolum 32.5's pivot. A model's alignment is strongest in English, so a
     * Turkish profile applying to a German posting is translated
     * {@code TR -> EN -> DE} rather than straight across. The extra leg is
     * paid once: the English wording is saved like any other (Bolum 21.8's
     * third step), and it is the language a profile is most likely to want
     * next anyway.
     */
    public static final String PIVOT_LANGUAGE = "en";

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
    private final TranslationWriter writer;

    VariantTranslationService(PromptRegistry prompts, ProviderChain providers,
            AtomRepository atoms, AtomVariantRepository variants, TranslationWriter writer) {
        this.prompts = prompts;
        this.providers = providers;
        this.atoms = atoms;
        this.variants = variants;
        this.writer = writer;
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
     * <p><strong>No pivot here.</strong> This wording exists and already
     * records what it was made from; reaching for a different source to
     * refresh it would rewrite its provenance.
     *
     * @param variantId the stale wording
     * @param source    what it is derived from
     * @param atom      the fact it belongs to, whose numbers and names the
     *                  translation has to keep
     */
    public Result<AtomVariant> retranslate(ProfileRef profile, UUID variantId,
            AtomVariant source, Atom atom, String bucketKey, UUID userId) {

        AtomVariant target = variants.findById(profile, variantId).orElseThrow(
                () -> new IllegalStateException("The wording was checked and then vanished"));

        return ask(source, target.getLanguage(), bucketKey, userId)
                .flatMap(translated -> writer.store(profile, target, source, atom, translated));
    }

    /**
     * Bolum 21.8's second step: a wording in a language this atom has none in,
     * made from the one it has.
     *
     * <p><strong>The difference from {@link #retranslate} is that there is
     * nothing to overwrite.</strong> That one refreshes a wording that went
     * stale; this one creates the wording a generation needs and the profile
     * never had, which is what F-013 was waiting for — until it existed,
     * {@code auto} could only follow the posting when every atom already had a
     * wording in its language, and a Turkish profile applying to an English
     * posting got a Turkish CV.
     *
     * <p>Saved rather than used and thrown away (Bolum 21.8's third step), and
     * the payoff is in the section's own sentence: the second generation in
     * that language costs nothing. {@code created_by = llm_translate} is what
     * lets Bolum 32.2's staleness know a person did not write it, and what the
     * screen reads to say a wording was generated and is worth reviewing
     * (Bolum 32.5's warning).
     *
     * <p>The same audit as {@link #retranslate}. A translation that dropped a
     * number is refused here too, which is what keeps the fallback honest: a
     * missing wording is better than a wrong one.
     */
    public Result<AtomVariant> translateInto(ProfileRef profile, Atom atom, AtomVariant source,
            String language, String bucketKey, UUID userId) {

        Result<AtomVariant> pivot = pivotFor(profile, atom, source, language, bucketKey, userId);
        if (pivot instanceof Result.Err<AtomVariant> failed) {
            return Result.err(failed.error());
        }
        AtomVariant from = pivot.orElseThrow();

        return ask(from, language, bucketKey, userId).flatMap(translated -> writer.store(
                profile,
                new AtomVariant(profile.id(), atom.getId(), language, from.getContent()),
                from, atom, translated));
    }

    /**
     * What to translate from, which is Bolum 32.5 and is only ever one of two
     * things: the wording the caller named, or the English one.
     *
     * <p>The pivot is skipped when either end is already English, because then
     * there is only one leg to walk. It is also skipped — without a call —
     * when the atom already has an English wording, which is the common case
     * for anybody who has generated an English CV before, and the reason a
     * third language is expensive once rather than every time.
     *
     * <p><strong>A pivot leg that fails takes the translation down with
     * it.</strong> Translating straight across instead would quietly deliver
     * the lower-quality route this section exists to avoid; and in practice
     * the leg fails because the provider is unavailable, which the direct call
     * was about to find out for itself.
     */
    private Result<AtomVariant> pivotFor(ProfileRef profile, Atom atom, AtomVariant source,
            String language, String bucketKey, UUID userId) {

        if (isPivot(language) || isPivot(source.getLanguage())) {
            return Result.ok(source);
        }
        Optional<AtomVariant> existing =
                variants.wordingOf(profile, atom.getId(), PIVOT_LANGUAGE);
        if (existing.isPresent()) {
            return Result.ok(existing.get());
        }

        log.info("Translating by way of {} rather than straight into {}",
                PIVOT_LANGUAGE, language);
        return ask(source, PIVOT_LANGUAGE, bucketKey, userId).flatMap(translated -> writer.store(
                profile,
                new AtomVariant(profile.id(), atom.getId(), PIVOT_LANGUAGE, source.getContent()),
                source, atom, translated));
    }

    /**
     * One call, outside any transaction.
     *
     * <p>That last part is the whole reason {@link TranslationWriter} is a
     * separate bean: a provider call inside a transaction holds a connection
     * from the pool for as long as the model takes to answer, and Bolum 21.8's
     * second step makes sixty of these at once.
     */
    private Result<AtomTranslation> ask(AtomVariant source, String language,
            String bucketKey, UUID userId) {

        var prompt = prompts.load(PROMPT_ID, prompts.selectVersion(PROMPT_ID, bucketKey));
        var fenced = FencedPrompt.of(prompt, FENCE_TAG);
        return providers.call(new StructuredRequest<>(
                        PROMPT_ID, prompt.version(),
                        fenced.system().replace(TARGET_LANGUAGE, language),
                        fenced.userPromptFor(source.getPlainText()),
                        prompt.schema(), AtomTranslation.class, ModelTier.CHEAP, TIMEOUT, userId))
                .map(LlmResponse::data);
    }

    private static boolean isPivot(String language) {
        return language != null && !language.isBlank() && PIVOT_LANGUAGE.equals(
                Locale.forLanguageTag(language.strip()).getLanguage());
    }
}
