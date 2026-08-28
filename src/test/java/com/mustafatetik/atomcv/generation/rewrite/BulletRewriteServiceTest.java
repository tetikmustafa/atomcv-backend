package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.embedding.EmbeddingProvider;
import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ProviderChain;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.mustafatetik.atomcv.llm.prompts.Prompt;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Bolum 21.6's failure behaviour, which is the whole reason this class returns
 * content rather than a {@code Result}.
 *
 * <p>A generation must not fall over because a model wrote a sentence that did
 * not pass a check. The person asked for a CV; the CV they already had is
 * right there, and printing it is a correct answer rather than a degraded one.
 */
class BulletRewriteServiceTest {

    private static final String ORIGINAL = "Moved 300K rows with Microsoft Fabric";

    private final PromptRegistry prompts = mock(PromptRegistry.class);

    private final ProviderChain providers = mock(ProviderChain.class);

    private final EmbeddingProvider embeddings = mock(EmbeddingProvider.class);

    private final BulletRewriteService service =
            new BulletRewriteService(prompts, providers, embeddings);

    private final RewriteContext context = new RewriteContext(
            List.of("kubernetes", "microsoft-fabric"), List.of(), "", "en", "formal", "a-user");

    @BeforeEach
    void aLoadedPrompt() {
        when(prompts.selectVersion(any(), any())).thenReturn("v1");
        when(prompts.load(any(), any())).thenReturn(TestPrompts.bulletRewrite());
        when(embeddings.embed(any())).thenReturn(new float[] {1, 0, 0});
    }

    @Test
    void agoodRewriteIsWhatGetsPrinted() {
        answering("Moved 300K rows through the Microsoft Fabric batch pipeline");

        RichContent printed = service.rewrite(candidate(), context);

        assertThat(printed.plainText())
                .isEqualTo("Moved 300K rows through the Microsoft Fabric batch pipeline");
    }

    /**
     * <strong>The claim with zero tolerance, refused twice, and the CV is
     * still produced.</strong> This is the shape of the whole phase: the model
     * may fail, the generation may not.
     */
    @Test
    void arewriteThatClaimsSomethingUnsupportedIsThrownAwayTwiceAndTheOriginalStands() {
        answering("Moved 300K rows with Microsoft Fabric on Kubernetes");

        RichContent printed = service.rewrite(candidate(), context);

        assertThat(printed.plainText()).isEqualTo(ORIGINAL);
        verify(providers, times(BulletRewriteService.ATTEMPTS)).call(any());
    }

    /** Bolum 21.6: the second attempt is worth making, and only the second. */
    @Test
    void asecondAttemptThatPassesIsUsed() {
        when(providers.call(request()))
                .thenReturn(answer("Moved rows with Microsoft Fabric"))
                .thenReturn(answer("Moved 300K rows on the Microsoft Fabric pipeline"));

        RichContent printed = service.rewrite(candidate(), context);

        assertThat(printed.plainText())
                .isEqualTo("Moved 300K rows on the Microsoft Fabric pipeline");
        verify(providers, times(2)).call(any());
    }

    /**
     * An outage is not a rewrite failure, but it has the same answer. A CV
     * that failed to generate because a provider was down would be the product
     * refusing to do the part it could do on its own.
     */
    @Test
    void aproviderOutageLeavesTheOriginalStandingRatherThanFailing() {
        when(providers.call(request())).thenReturn(Result.err(
                new PipelineError.AllProvidersUnavailable(List.of("openrouter"))));

        assertThat(service.rewrite(candidate(), context).plainText()).isEqualTo(ORIGINAL);
    }

    /** And so does an embedding service that cannot be reached (check five). */
    @Test
    void anembeddingOutageDoesNotStopAGoodRewriteFromBeingPrinted() {
        when(embeddings.embed(any())).thenThrow(new IllegalStateException("down"));
        answering("Moved 300K rows on the Microsoft Fabric pipeline");

        assertThat(service.rewrite(candidate(), context).plainText())
                .isEqualTo("Moved 300K rows on the Microsoft Fabric pipeline");
    }

    // -- Bolum 43.1 --------------------------------------------------------

    /**
     * <strong>Everything the person wrote goes inside the fence.</strong> The
     * skills, the numbers and the names are the CV's content as much as the
     * sentence is, and a list of them sitting in the instruction half would be
     * exactly the injection surface Bolum 43.1 draws the boundary to remove.
     */
    @Test
    void thecvsOwnWordsTravelInsideTheFenceAndNotInTheInstructions() {
        answering("Moved 300K rows on the Microsoft Fabric pipeline");

        service.rewrite(candidate(), context);

        var request = ArgumentCaptor.forClass(StructuredRequest.class);
        verify(providers).call(request.capture());
        assertThat(request.getValue().systemPrompt())
                .doesNotContain(ORIGINAL)
                .doesNotContain("Microsoft Fabric")
                .doesNotContain("300K");
        assertThat(request.getValue().userPrompt())
                .contains(ORIGINAL)
                .contains("allowedSkills: microsoft-fabric");
    }

    /** What is ours rather than theirs does belong in the instructions. */
    @Test
    void thelimitAndTheIntentAreInstructionsAndAreSubstituted() {
        answering("Moved 300K rows on the Microsoft Fabric pipeline");

        service.rewrite(candidate(), context);

        var request = ArgumentCaptor.forClass(StructuredRequest.class);
        verify(providers).call(request.capture());
        assertThat(request.getValue().systemPrompt())
                .contains("MAXIMUM: 100 characters")
                .contains("Intent: adapt")
                .doesNotContain("{{max_chars}}")
                .doesNotContain("{{intent}}");
    }

    // -- fixtures ----------------------------------------------------------

    private void answering(String text) {
        when(providers.call(request())).thenReturn(answer(text));
    }

    /** The chain is generic in the result type, so the matcher has to be too. */
    private static StructuredRequest<RewrittenBullet> request() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static Result<LlmResponse<RewrittenBullet>> answer(String text) {
        return Result.ok(new LlmResponse<>(
                new RewrittenBullet(text, List.of()), "fake", "fake-model", 0, 0, 0, 0L));
    }

    private static RewriteCandidate candidate() {
        return new RewriteCandidate(UUID.randomUUID(), UUID.randomUUID(),
                RichContent.plain(ORIGINAL), List.of("microsoft-fabric"),
                List.of("300K"), List.of("Microsoft Fabric"),
                0.8, 100, RewriteIntent.ADAPT, new float[] {1, 0, 0});
    }

    /** Loads the real prompt file, because the fence is what is under test. */
    private static final class TestPrompts {

        static Prompt bulletRewrite() {
            return new PromptRegistry(
                    new com.mustafatetik.atomcv.llm.prompts.PromptProperties(
                            java.util.Map.of(BulletRewriteService.PROMPT_ID, "v1"), null),
                    new com.fasterxml.jackson.databind.ObjectMapper())
                    .load(BulletRewriteService.PROMPT_ID, "v1");
        }
    }
}
