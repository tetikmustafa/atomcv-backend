package com.mustafatetik.atomcv.generation.coverletter;

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
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The one phase in Faz D that can answer with a failure (Bolum 34).
 *
 * <p>Everywhere else there is an original to fall back on. Here there is not,
 * so the two outcomes are an honest letter and a refusal — and the refusal is
 * the correct answer, because the alternative is a letter claiming something
 * the person will have to defend.
 */
class CoverLetterServiceTest {

    private final PromptRegistry prompts = mock(PromptRegistry.class);
    private final ProviderChain providers = mock(ProviderChain.class);

    private final CoverLetterService service = new CoverLetterService(prompts, providers);

    @BeforeEach
    void aLoadedPrompt() {
        when(prompts.selectVersion(any(), any())).thenReturn("v1");
        when(prompts.load(any(), any())).thenReturn(coverLetterPrompt());
    }

    @Test
    void agoodLetterIsWhatComesBack() {
        answering(draft("Dear Acme,", padded("Ran Postgres in production.")));

        var written = service.write(input(), CoverLetterStyle.DEFAULT, "a-user");

        assertThat(written.orElseThrow().greeting()).isEqualTo("Dear Acme,");
    }

    /**
     * <strong>Refused twice, and the caller is told.</strong> There is no
     * original letter to print, and a letter that claims Kubernetes because a
     * posting asked for it is the failure this subsystem exists to prevent.
     */
    @Test
    void aletterThatClaimsSomethingUnsupportedIsRefusedAndReported() {
        answering(draft("Dear Acme,",
                padded("Ran Postgres and Kubernetes in production.")));

        var written = service.write(input(), CoverLetterStyle.DEFAULT, "a-user");

        assertThat(written.isErr()).isTrue();
        var error = (PipelineError.CoverLetterRejected)
                ((Result.Err<CoverLetterDraft>) written).error();
        assertThat(error.issues()).containsExactly("unsupported_claim");
        verify(providers, times(CoverLetterService.ATTEMPTS)).call(any());
    }

    /** The second attempt is worth making, and only the second. */
    @Test
    void asecondAttemptThatPassesIsUsed() {
        when(providers.call(request()))
                .thenReturn(answer(draft("Dear Acme,", padded("Ran Kubernetes."))))
                .thenReturn(answer(draft("Dear Acme,", padded("Ran Postgres."))));

        assertThat(service.write(input(), CoverLetterStyle.DEFAULT, "a-user").isErr())
                .isFalse();
        verify(providers, times(2)).call(any());
    }

    /**
     * An outage is not a refused letter and must not be reported as one: the
     * person did nothing wrong and the way out is to try again, not to change
     * their CV.
     */
    @Test
    void aproviderOutageIsReportedAsAnOutageAndNotAsARefusal() {
        when(providers.call(request())).thenReturn(Result.err(
                new PipelineError.AllProvidersUnavailable(List.of("openrouter"))));

        var written = service.write(input(), CoverLetterStyle.DEFAULT, "a-user");

        assertThat(((Result.Err<CoverLetterDraft>) written).error())
                .isInstanceOf(PipelineError.AllProvidersUnavailable.class);
        verify(providers, times(1)).call(any());
    }

    /** Bolum 34.6's buttons are ours, so they are instructions. */
    @Test
    void thestyleIsAnInstructionAndTheCvIsNot() {
        answering(draft("Dear Acme,", padded("Ran Postgres in production.")));

        service.write(input(), CoverLetterStyle.SHORTER, "a-user");

        var request = ArgumentCaptor.forClass(StructuredRequest.class);
        verify(providers).call(request.capture());
        assertThat(request.getValue().systemPrompt())
                .contains("nearer 250 words")
                .doesNotContain("{{style}}")
                .doesNotContain("{{language}}")
                .doesNotContain("Ran the Postgres fleet")
                .doesNotContain("They open-sourced their scheduler.");
    }

    /**
     * <strong>Bolum 43.1.</strong> The sentences, the lists, the years and
     * what the person knows about the employer are all their content, and a
     * copy of any of it above the fence is the injection surface the boundary
     * exists to remove.
     */
    @Test
    void thewholeOfTheCvTravelsInsideTheFence() {
        answering(draft("Dear Acme,", padded("Ran Postgres in production.")));

        service.write(input(), CoverLetterStyle.DEFAULT, "a-user");

        var request = ArgumentCaptor.forClass(StructuredRequest.class);
        verify(providers).call(request.capture());
        assertThat(request.getValue().userPrompt())
                .contains("applicant: Ada Lovelace")
                .contains("company: Acme")
                .contains("skills: postgres")
                .contains("yearsWorking: 6")
                .contains("companyNote: They open-sourced their scheduler.")
                .contains("- Ran the Postgres fleet");
    }

    // -- fixtures ----------------------------------------------------------

    private void answering(CoverLetterDraft draft) {
        when(providers.call(request())).thenReturn(answer(draft));
    }

    private static StructuredRequest<CoverLetterDraft> request() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static Result<LlmResponse<CoverLetterDraft>> answer(CoverLetterDraft draft) {
        return Result.ok(new LlmResponse<>(draft, "fake", "fake-model", 0, 0, 0, 0L));
    }

    private static CoverLetterDraft draft(String greeting, String body) {
        return new CoverLetterDraft(greeting, "About this role.", body,
                "Happy to talk.", "Ada Lovelace");
    }

    private static String padded(String body) {
        return body + " " + "filler ".repeat(260);
    }

    private static CoverLetterInput input() {
        return new CoverLetterInput("Ada Lovelace", "Backend Engineer", "Acme",
                List.of(new CoverLetterInput.Evidence(
                        "Ran the Postgres fleet", List.of("postgres"), List.of())),
                List.of("postgres"), List.of(), List.of("Initech"), 6,
                "They open-sourced their scheduler.", "en", "formal");
    }

    /** Loads the real prompt file, because the fence is what is under test. */
    private static Prompt coverLetterPrompt() {
        return new PromptRegistry(
                new com.mustafatetik.atomcv.llm.prompts.PromptProperties(
                        java.util.Map.of(CoverLetterService.PROMPT_ID, "v1"), null),
                new com.fasterxml.jackson.databind.ObjectMapper())
                .load(CoverLetterService.PROMPT_ID, "v1");
    }
}
