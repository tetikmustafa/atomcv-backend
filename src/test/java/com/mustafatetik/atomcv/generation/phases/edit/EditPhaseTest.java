package com.mustafatetik.atomcv.generation.phases.edit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.llm.gateway.LlmResponse;
import com.mustafatetik.atomcv.llm.gateway.ProviderChain;
import com.mustafatetik.atomcv.llm.prompts.Prompt;
import com.mustafatetik.atomcv.llm.prompts.PromptProperties;
import com.mustafatetik.atomcv.llm.prompts.PromptRegistry;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What the phase does with what comes back (Bolum 24.2).
 *
 * <p>The model is not the thing under test here — the answer-handling is.
 * Every case below is an answer a model can and will produce, and what matters
 * is which of them are allowed to change somebody's CV.
 */
class EditPhaseTest {

    private static final UUID ON_PAGE = UUID.randomUUID();
    private static final UUID ALSO_ON_PAGE = UUID.randomUUID();
    private static final UUID HELD_BACK = UUID.randomUUID();

    private final PromptRegistry prompts = mock(PromptRegistry.class);
    private final ProviderChain providers = mock(ProviderChain.class);
    private final EditPhase phase = new EditPhase(prompts, providers);

    @BeforeEach
    void aLoadedPrompt() {
        when(prompts.selectVersion(any(), any())).thenReturn("v1");
        when(prompts.load(any(), any())).thenReturn(loadedPrompt());
    }

    @Test
    void anumberOnThePageBecomesAnExclusion() {
        answering(new EditAnswer(List.of(), List.of(1), true));

        EditPlan plan = parse().orElseThrow();

        assertThat(plan.excludeAtoms()).containsExactly(ON_PAGE);
        assertThat(plan.includeAtoms()).isEmpty();
    }

    @Test
    void anumberFromTheHeldBackListBecomesAnInclusion() {
        answering(new EditAnswer(List.of(3), List.of(), true));

        EditPlan plan = parse().orElseThrow();

        assertThat(plan.includeAtoms()).containsExactly(HELD_BACK);
    }

    @Test
    void asentenceCanDoBothAtOnce() {
        answering(new EditAnswer(List.of(3), List.of(1), true));

        EditPlan plan = parse().orElseThrow();

        assertThat(plan.excludeAtoms()).containsExactly(ON_PAGE);
        assertThat(plan.includeAtoms()).containsExactly(HELD_BACK);
    }

    /**
     * The prompt asks for this in preference to a guess: "make it shorter"
     * names no line, and removing the wrong bullet is worse than saying
     * nothing because the person may not notice.
     */
    @Test
    void asentenceThatNamedNothingIsRefusedRatherThanGuessedAt() {
        answering(new EditAnswer(List.of(), List.of(), false));

        assertThat(parse()).isInstanceOf(Result.Err.class);
        assertThat(((Result.Err<EditPlan>) parse()).error())
                .isInstanceOf(PipelineError.EditNotUnderstood.class);
    }

    /** Understood, and yet nothing came with it. Same answer. */
    @Test
    void understoodWithEmptyListsIsStillNothing() {
        answering(new EditAnswer(List.of(), List.of(), true));

        assertThat(parse()).isInstanceOf(Result.Err.class);
    }

    /**
     * <strong>The guard that matters most.</strong> A model that named one
     * line in range and one out of it understood half the sentence, and
     * applying the half it got right shows the person something other than
     * what they asked for.
     */
    @Test
    void halfAsentenceIsNotAppliedAtAll() {
        answering(new EditAnswer(List.of(), List.of(1, 47), true));

        assertThat(parse()).isInstanceOf(Result.Err.class);
    }

    @Test
    void anumberSaidTwiceIsOneLine() {
        answering(new EditAnswer(List.of(), List.of(1, 1), true));

        assertThat(parse().orElseThrow().excludeAtoms()).containsExactly(ON_PAGE);
    }

    /** It cannot be both, the prompt says so, and picking one would be a guess. */
    @Test
    void alineInBothAnswersIsRefused() {
        answering(new EditAnswer(List.of(1), List.of(1), true));

        assertThat(parse()).isInstanceOf(Result.Err.class);
    }

    @Test
    void aproviderOutageTravelsAsItself() {
        when(providers.<EditAnswer>call(any()))
                .thenReturn(Result.err(new PipelineError.AllProvidersUnavailable(List.of("a"))));

        assertThat(((Result.Err<EditPlan>) parse()).error())
                .isInstanceOf(PipelineError.AllProvidersUnavailable.class);
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private Result<EditPlan> parse() {
        return phase.parse("take out the android one", lines(), "a-user",
                UUID.randomUUID(), UUID.randomUUID());
    }

    private void answering(EditAnswer answer) {
        when(providers.<EditAnswer>call(any())).thenReturn(Result.ok(
                new LlmResponse<>(answer, "fake", "fake-model", 0, 0, 0, 0L)));
    }

    private static NumberedLines lines() {
        return NumberedLines.of(
                List.of(new NumberedLines.Line(ON_PAGE, "Built the Android client"),
                        new NumberedLines.Line(ALSO_ON_PAGE, "Cut checkout latency by 40%")),
                List.of(new NumberedLines.Line(HELD_BACK, "Ran the Kubernetes migration")));
    }

    private static Prompt loadedPrompt() {
        return new PromptRegistry(
                new PromptProperties(Map.of(EditPhase.PROMPT_ID, "v1"), null),
                new ObjectMapper())
                .load(EditPhase.PROMPT_ID, "v1");
    }
}
