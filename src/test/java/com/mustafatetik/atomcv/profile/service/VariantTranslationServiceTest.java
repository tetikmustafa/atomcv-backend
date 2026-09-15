package com.mustafatetik.atomcv.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ProviderChain;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.mustafatetik.atomcv.llm.prompts.PromptProperties;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The pivot: which language a translation is made <em>from</em>.
 *
 * <p>The section's claim is about quality, not about correctness — a model's
 * alignment is strongest in English, so {@code TR → EN → DE} reads better than
 * {@code TR → DE}. Nothing in a test can measure that. What a test can hold is
 * the route: that the extra leg is walked when it is worth walking, that it is
 * not walked when there is nothing to gain, and that the second time costs
 * nothing because the first one was saved.
 */
class VariantTranslationServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private ProviderChain providers;
    private AtomVariantRepository variants;
    private TranslationWriter writer;
    private VariantTranslationService translations;
    private ProfileRef profile;
    private Atom atom;

    @BeforeEach
    void wireTheMocks() {
        providers = mock(ProviderChain.class);
        variants = mock(AtomVariantRepository.class);
        writer = mock(TranslationWriter.class);
        profile = ProfileRef.persistent(UserContext.of(USER), UUID.randomUUID(), USER);
        atom = new Atom(profile.id(), UUID.randomUUID(), null, AtomKind.BULLET, (short) 0);

        // The shipped prompt rather than a stub: what this test reads out of
        // the call is the target language, and the whole point of Bolum 32.5
        // is which language that is.
        var prompts = new PromptRegistry(
                new PromptProperties(Map.of("translation", "v1"), Map.of()),
                new com.fasterxml.jackson.databind.ObjectMapper());

        translations = new VariantTranslationService(prompts, providers,
                mock(AtomRepository.class), variants, writer);

        when(providers.call(any())).thenAnswer(call -> Result.ok(
                new LlmResponse<>(new AtomTranslation("translated", List.of()),
                        "provider", "model", 0, 0, 0, 0L)));
        when(writer.store(any(), any(), any(), any(), any()))
                .thenAnswer(call -> Result.ok(call.getArgument(1, AtomVariant.class)));
        when(variants.wordingOf(any(), any(), any())).thenReturn(Optional.empty());
    }

    /**
     * <strong>The route the section names.</strong> Two calls, English first,
     * and the German is made from the English rather than from the Turkish.
     */
    @Test
    void athirdLanguageGoesByWayOfEnglish() {
        translations.translateInto(profile, atom, wording("tr"), "de", "bucket", USER);

        assertThat(languagesAskedFor()).containsExactly("en", "de");
        var stored = ArgumentCaptor.forClass(AtomVariant.class);
        verify(writer, org.mockito.Mockito.times(2))
                .store(any(), any(), stored.capture(), any(), any());
        assertThat(stored.getAllValues())
                .extracting(AtomVariant::getLanguage)
                .containsExactly("tr", "en");
    }

    /** One leg, because one end of it is already the pivot. */
    @Test
    void englishAsTheTargetIsOneCall() {
        translations.translateInto(profile, atom, wording("tr"), "en", "bucket", USER);

        assertThat(languagesAskedFor()).containsExactly("en");
    }

    /** And the same the other way: an English profile needs no detour. */
    @Test
    void englishAsTheSourceIsOneCall() {
        translations.translateInto(profile, atom, wording("en"), "de", "bucket", USER);

        assertThat(languagesAskedFor()).containsExactly("de");
        verify(variants, never()).wordingOf(any(), any(), any());
    }

    /**
     * <strong>The pivot is paid for once.</strong> Anybody who has generated
     * an English CV already has these wordings, and this is what makes a third
     * language cheap for them: the leg is a lookup, not a call.
     */
    @Test
    void anexistingEnglishWordingIsThePivotAndCostsNothing() {
        AtomVariant english = wording("en");
        when(variants.wordingOf(profile, atom.getId(), "en")).thenReturn(Optional.of(english));

        translations.translateInto(profile, atom, wording("tr"), "de", "bucket", USER);

        assertThat(languagesAskedFor()).containsExactly("de");
        verify(writer).store(any(), any(), org.mockito.ArgumentMatchers.same(english),
                any(), any());
    }

    /**
     * A leg that could not be walked is a translation that did not happen. The
     * alternative would be quietly delivering the straight-across route this
     * section exists to avoid.
     */
    @Test
    void afailedPivotLegFailsTheTranslation() {
        when(writer.store(any(), any(), any(), any(), any()))
                .thenReturn(Result.err(new PipelineError.TranslationRejected()));

        var result = translations.translateInto(profile, atom, wording("tr"), "de",
                "bucket", USER);

        assertThat(result).isInstanceOf(Result.Err.class);
        assertThat(languagesAskedFor()).containsExactly("en");
    }

    /** A regional tag is still English: {@code en-GB} is not a third language. */
    @Test
    void aregionalEnglishIsStillThePivot() {
        translations.translateInto(profile, atom, wording("en-GB"), "de", "bucket", USER);

        assertThat(languagesAskedFor()).containsExactly("de");
    }

    private List<String> languagesAskedFor() {
        var request = ArgumentCaptor.forClass(StructuredRequest.class);
        verify(providers, org.mockito.Mockito.atLeastOnce()).call(request.capture());
        return request.getAllValues().stream()
                .map(asked -> asked.systemPrompt().substring(
                        asked.systemPrompt().indexOf("Target language: ")
                                + "Target language: ".length()).split("\\R", 2)[0].strip())
                .toList();
    }

    private AtomVariant wording(String language) {
        var variant = new AtomVariant(profile.id(), atom.getId(), language,
                RichContent.plain(language + " wording"));
        variant.setPrimary(true);
        return variant;
    }
}
