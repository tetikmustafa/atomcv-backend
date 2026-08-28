package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
 * Bolum 21.7's failure behaviour, which is Bolum 21.6's: two attempts, then
 * the paragraph the person already had.
 *
 * <p>The summary is the line an employer reads first and quotes back in an
 * interview, so the phase's rule matters more here than anywhere else in Faz
 * D — a paragraph that could not be written honestly is not written at all.
 */
class AboutSynthesisServiceTest {

    private static final String ORIGINAL = "Backend engineer, mostly on data platforms.";

    private final PromptRegistry prompts = mock(PromptRegistry.class);
    private final ProviderChain providers = mock(ProviderChain.class);

    private final AboutSynthesisService service = new AboutSynthesisService(prompts, providers);

    private final RewriteContext context = new RewriteContext(
            List.of("kubernetes"), List.of("run the payments platform"),
            "Likes small teams.", "en", "formal", "a-user");

    @BeforeEach
    void aLoadedPrompt() {
        when(prompts.selectVersion(any(), any())).thenReturn("v1");
        when(prompts.load(any(), any())).thenReturn(aboutPrompt());
    }

    @Test
    void agoodSummaryIsWhatGetsPrinted() {
        answering("Backend engineer who has run Postgres in production for a payments team.");

        RichContent printed = service.synthesise(candidate(), context);

        assertThat(printed.plainText())
                .isEqualTo("Backend engineer who has run Postgres in production "
                        + "for a payments team.");
    }

    /**
     * <strong>The posting asked for Kubernetes and the page does not have
     * it.</strong> Refused twice, and the CV is still produced — with the
     * person's own paragraph, which was never wrong.
     */
    @Test
    void asummaryThatReachesForThePostingsWordsIsThrownAwayAndTheOriginalStands() {
        answering("Backend engineer running Postgres and Kubernetes for payments.");

        RichContent printed = service.synthesise(candidate(), context);

        assertThat(printed.plainText()).isEqualTo(ORIGINAL);
        verify(providers, times(AboutSynthesisService.ATTEMPTS)).call(any());
    }

    /** The second attempt is worth making, and only the second. */
    @Test
    void asecondAttemptThatPassesIsUsed() {
        when(providers.call(request()))
                .thenReturn(answer("Ran Postgres and Kubernetes."))
                .thenReturn(answer("Ran Postgres for a payments team."));

        assertThat(service.synthesise(candidate(), context).plainText())
                .isEqualTo("Ran Postgres for a payments team.");
        verify(providers, times(2)).call(any());
    }

    /** An outage has the same answer as a refusal, and it is not a failure. */
    @Test
    void aproviderOutageLeavesTheOriginalStandingRatherThanFailing() {
        when(providers.call(request())).thenReturn(Result.err(
                new PipelineError.AllProvidersUnavailable(List.of("openrouter"))));

        assertThat(service.synthesise(candidate(), context).plainText()).isEqualTo(ORIGINAL);
    }

    /**
     * <strong>Bolum 43.1.</strong> The skills, the numbers and what the person
     * wrote about themselves are the CV's content; a copy of any of them in
     * the instruction half is the injection surface the fence exists to
     * remove. The summary carries more of the profile than any other prompt in
     * the product, which is why this is asserted here as well.
     */
    @Test
    void thewholeOfTheProfileTravelsInsideTheFence() {
        answering("Ran Postgres for a payments team.");

        service.synthesise(candidate(), context);

        var request = ArgumentCaptor.forClass(StructuredRequest.class);
        verify(providers).call(request.capture());
        assertThat(request.getValue().systemPrompt())
                .doesNotContain(ORIGINAL)
                .doesNotContain("Likes small teams.")
                .doesNotContain("postgres")
                .doesNotContain("40");
        assertThat(request.getValue().userPrompt())
                .contains(ORIGINAL)
                .contains("skills: postgres")
                .contains("metrics: 40")
                .contains("ownWords: Likes small teams.")
                .contains("postingFocus: run the payments platform");
    }

    /** What is ours rather than theirs does belong in the instructions. */
    @Test
    void thelimitAndTheLanguageAreInstructionsAndAreSubstituted() {
        answering("Ran Postgres for a payments team.");

        service.synthesise(candidate(), context);

        var request = ArgumentCaptor.forClass(StructuredRequest.class);
        verify(providers).call(request.capture());
        assertThat(request.getValue().systemPrompt())
                .contains("MAXIMUM: 200 characters")
                .doesNotContain("{{max_chars}}")
                .doesNotContain("{{language}}")
                .doesNotContain("{{tone}}");
    }

    // -- fixtures ----------------------------------------------------------

    private void answering(String text) {
        when(providers.call(request())).thenReturn(answer(text));
    }

    private static StructuredRequest<SynthesisedAbout> request() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static Result<LlmResponse<SynthesisedAbout>> answer(String text) {
        return Result.ok(new LlmResponse<>(
                new SynthesisedAbout(text), "fake", "fake-model", 0, 0, 0, 0L));
    }

    private static AboutCandidate candidate() {
        return new AboutCandidate(UUID.randomUUID(), RichContent.plain(ORIGINAL),
                List.of("postgres"), List.of("40"), "Likes small teams.",
                List.of("run the payments platform"), 200);
    }

    /** Loads the real prompt file, because the fence is what is under test. */
    private static Prompt aboutPrompt() {
        return new PromptRegistry(
                new com.mustafatetik.atomcv.llm.prompts.PromptProperties(
                        java.util.Map.of(AboutSynthesisService.PROMPT_ID, "v1"), null),
                new com.fasterxml.jackson.databind.ObjectMapper())
                .load(AboutSynthesisService.PROMPT_ID, "v1");
    }
}
