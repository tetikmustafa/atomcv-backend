package com.mustafatetik.atomcv.ingestion.structuring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.ingestion.extraction.DocumentFormat;
import com.mustafatetik.atomcv.ingestion.extraction.ExtractedText;
import com.mustafatetik.atomcv.llm.gateway.LlmFailure;
import com.mustafatetik.atomcv.llm.gateway.LlmOutcome;
import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import com.mustafatetik.atomcv.llm.gateway.LlmProvider;
import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import com.mustafatetik.atomcv.llm.gateway.ProviderChain;
import com.mustafatetik.atomcv.llm.gateway.StructuredRequest;
import com.mustafatetik.atomcv.llm.prompts.PromptProperties;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Bolum 31.4's one call, with the provider stubbed.
 *
 * <p>Two groups of cases. What is <em>sent</em> — the fence, the tier, where
 * the scramble note goes — because those are decided once and never observed
 * again; and what comes back, where three refusals have to stay three
 * different decisions while looking like two answers from outside.
 */
class ProfileStructuringTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-27T09:00:00Z"), ZoneOffset.UTC);

    private static final String CV = "Ada Lovelace, Analytical Engine programmer, London.";

    private final AtomicReference<StructuredRequest<?>> sent = new AtomicReference<>();

    // -- what is sent ------------------------------------------------------

    /**
     * Bolum 43.1's fence, and the CV on the correct side of it.
     *
     * <p>The system half has to stay constant for Bolum 27.4's prompt caching
     * to discount it, and it has to be the half that says the fenced region is
     * data. A document that leaked into it would break both at once.
     */
    @Test
    void theDocumentGoesInsideTheFenceAndNeverIntoTheSystemHalf() {
        structuring(answering(profileJson("tr", 0.95, 1)))
                .structure(document(CV, false), "user-1");

        assertThat(sent.get().systemPrompt()).containsIgnoringCase("DATA to be parsed");
        assertThat(sent.get().systemPrompt()).doesNotContain(CV);
        assertThat(sent.get().userPrompt()).contains("<cv_text>", CV, "</cv_text>");
    }

    /**
     * Bolum 5.4 puts reading a whole CV at MID, and this is the one call in the
     * product where the tier is a cost decision rather than a quality one — the
     * input is the whole document.
     */
    @Test
    void theCallAsksForTheMidTier() {
        structuring(answering(profileJson("en", 0.99, 1)))
                .structure(document(CV, false), "user-1");

        assertThat(sent.get().preferredTier()).isEqualTo(ModelTier.MID);
        assertThat(sent.get().promptRef()).isEqualTo("profile_extraction:v1");
    }

    /**
     * Bolum 31.3's note travels, and it travels inside the fence.
     *
     * <p>In the system half it would be a standing instruction on every call
     * and would break the constant prefix; it is a remark about one document.
     */
    @Test
    void aScrambledDocumentCarriesItsNoteInsideTheFence() {
        structuring(answering(profileJson("en", 0.99, 1)))
                .structure(document(CV, true), "user-1");

        assertThat(sent.get().userPrompt()).containsIgnoringCase("wrong order");
        assertThat(sent.get().systemPrompt()).doesNotContainIgnoringCase("wrong order");
    }

    @Test
    void anOrdinaryDocumentCarriesNoNote() {
        structuring(answering(profileJson("en", 0.99, 1)))
                .structure(document(CV, false), "user-1");

        assertThat(sent.get().userPrompt()).doesNotContainIgnoringCase("wrong order");
    }

    // -- what comes back ---------------------------------------------------

    @Test
    void aReadableCvComesBackAsAProfile() {
        var result = structuring(answering(profileJson("tr", 0.96, 2)))
                .structure(document(CV, false), "user-1");

        assertThat(result).isInstanceOf(Result.Ok.class);
        var profile = ((Result.Ok<ExtractedProfile>) result).value();
        assertThat(profile.detectedLanguage()).isEqualTo("tr");
        assertThat(profile.atoms()).hasSize(2);
    }

    /**
     * Bolum 31.10: a language that cannot be settled is asked about, not
     * guessed at. The guess decides which variant of every atom is written, so
     * a wrong one produces a whole profile in the wrong language with no screen
     * that says so.
     */
    @Test
    void aLanguageTheModelIsUnsureOfBecomesAQuestionCarryingItsGuess() {
        var result = structuring(answering(profileJson("tr", 0.31, 2)))
                .structure(document(CV, false), "user-1");

        assertThat(errorOf(result)).isInstanceOf(PipelineError.LanguageUndetected.class);
        assertThat(((PipelineError.LanguageUndetected) errorOf(result)).candidates())
                .containsExactly("tr");
    }

    @Test
    void aLanguageTheModelDidNotNameBecomesAnOpenQuestion() {
        var result = structuring(answering(profileJson("", 0.9, 2)))
                .structure(document(CV, false), "user-1");

        assertThat(((PipelineError.LanguageUndetected) errorOf(result)).candidates()).isEmpty();
    }

    @Test
    void aDocumentWithNoAtomsInItIsNothingExtracted() {
        var result = structuring(answering(profileJson("en", 0.99, 0)))
                .structure(document(CV, false), "user-1");

        assertThat(errorOf(result)).isInstanceOf(PipelineError.NothingExtracted.class);
    }

    /**
     * <strong>Bolum 43.2 in one assertion.</strong> An answer refused by the
     * field-length audit and a document that yielded nothing are the same
     * answer, because a message that told them apart would tell whoever wrote
     * the injected text that it was noticed.
     */
    @Test
    void anAnswerRefusedByTheAuditIsIndistinguishableFromAnEmptyOne() {
        var injected = structuring(answering(profileJsonWithAtomText("x".repeat(2000))))
                .structure(document(CV, false), "user-1");
        var empty = structuring(answering(profileJson("en", 0.99, 0)))
                .structure(document(CV, false), "user-1");

        assertThat(errorOf(injected)).isInstanceOf(PipelineError.NothingExtracted.class);
        assertThat(errorOf(injected)).isEqualTo(errorOf(empty));
    }

    /**
     * An outage is an outage. Restating it as an unreadable CV would send the
     * user to the manual form because a provider was down, and Bolum 30.5
     * would then refuse to retry a job that should be retried.
     */
    @Test
    void aProviderOutageTravelsAsItselfAndNotAsAnUnreadableCv() {
        var result = structuring(answering(null)).structure(document(CV, false), "user-1");

        assertThat(errorOf(result))
                .isInstanceOf(PipelineError.AllProvidersUnavailable.class);
    }

    // -- fixtures ----------------------------------------------------------

    private static ExtractedText document(String text, boolean scrambled) {
        return new ExtractedText(text, DocumentFormat.PDF, scrambled);
    }

    private ProfileStructuring structuring(LlmProvider provider) {
        var chain = new ProviderChain(List.of(provider),
                new LlmProperties(Map.of(ModelTier.MID, List.of(provider.id())),
                        Map.of(), Duration.ofSeconds(30), 0),
                event -> { }, CLOCK);
        return new ProfileStructuring(
                new PromptRegistry(
                        new PromptProperties(Map.of("profile_extraction", "v1"), Map.of()), JSON),
                chain);
    }

    private StubProvider answering(String json) {
        return new StubProvider(json, sent);
    }

    private static PipelineError errorOf(Result<ExtractedProfile> result) {
        return ((Result.Err<ExtractedProfile>) result).error();
    }

    private static String profileJson(String language, double confidence, int atoms) {
        return profile(language, confidence, atomsJson(atoms));
    }

    private static String profileJsonWithAtomText(String text) {
        return profile("en", 0.99, """
                {"textSource":"%s","textEn":null,"emphasisSource":[],"emphasisEn":[],
                 "skills":[],"metrics":[],"properNouns":[],"tags":[]}""".formatted(text));
    }

    private static String atomsJson(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> """
                        {"textSource":"Engineered ETL pipelines number %d","textEn":null,
                         "emphasisSource":["ETL pipelines"],"emphasisEn":[],
                         "skills":["etl"],"metrics":[],"properNouns":[],"tags":[]}"""
                        .formatted(i))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String profile(String language, double confidence, String atoms) {
        return """
                {"detectedLanguage":"%s","languageConfidence":%s,
                 "contact":{"name":"Ada Lovelace","email":null,"phone":null,"linkedin":null,
                            "github":null,"website":null,"location":null},
                 "sections":[{"kind":"experience","title":"Experience","entries":[
                   {"title":"Data Engineer","organization":"Brisa","location":"Istanbul",
                    "startDate":"2025-09","endDate":null,"atoms":[%s]}]}],
                 "warnings":[]}
                """.formatted(language, confidence, atoms);
    }

    /** Answers whatever it was given; a null answer is a provider that is down. */
    private record StubProvider(String answer, AtomicReference<StructuredRequest<?>> seen)
            implements LlmProvider {

        @Override
        public String id() {
            return "stub";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public ModelTier tier() {
            return ModelTier.MID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> LlmOutcome<T> callStructured(StructuredRequest<T> request) {
            seen.set(request);
            if (answer == null) {
                return LlmOutcome.failed(
                        new LlmFailure(LlmFailure.Kind.SERVER_ERROR, "stub", "down"));
            }
            try {
                var value = JSON.readValue(answer, request.resultType());
                return (LlmOutcome<T>) LlmOutcome.answered(
                        new LlmResponse<>(value, "stub", "stub-model", 4000, 900, 0, 3200));
            } catch (Exception unparseable) {
                throw new IllegalStateException("the stub's own answer did not parse",
                        unparseable);
            }
        }
    }
}
