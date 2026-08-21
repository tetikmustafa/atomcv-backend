package com.mustafatetik.atomcv.llm.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Bolum 53.1-53.3, against the prompt under {@code test/resources/prompts}. */
class PromptRegistryTest {

    private static final String PROMPT = "probe_prompt";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void aPromptIsItsTextAndTheSchemaBesideIt() {
        var prompt = registry(Map.of(PROMPT, "v1"), Map.of()).load(PROMPT);

        assertThat(prompt.ref()).isEqualTo("probe_prompt:v1");
        assertThat(prompt.text()).contains("Answer with JSON only");
        assertThat(prompt.schema().name()).isEqualTo(PROMPT);
        assertThat(prompt.schema().node().get("required")).isNotNull();
    }

    /** Bolum 53.2: rolling back a prompt is a config change, not a release. */
    @Test
    void theActiveVersionComesFromConfiguration() {
        assertThat(registry(Map.of(PROMPT, "v2"), Map.of()).load(PROMPT).text())
                .contains("Be terse");
    }

    @Test
    void aPromptWithNoConfiguredVersionNamesTheKeyThatIsMissing() {
        assertThatThrownBy(() -> registry(Map.of(), Map.of()).load(PROMPT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("atomcv.prompts.active." + PROMPT);
    }

    @Test
    void aConfiguredVersionWithNoFileFailsAtStartupRatherThanOnAGeneration() {
        var registry = registry(Map.of(PROMPT, "v9"), Map.of());

        assertThatThrownBy(registry::validateConfiguredPrompts)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("probe_prompt/v9.md");
    }

    @Test
    void anExperimentVariantWithNoFileIsCaughtToo() {
        var registry = registry(Map.of(PROMPT, "v1"),
                Map.of(PROMPT, new PromptProperties.Experiment(true, "v7", 10)));

        assertThatThrownBy(registry::validateConfiguredPrompts)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("probe_prompt/v7.md");
    }

    // ── Bolum 53.3: the split ─────────────────────────────────────────────

    @Test
    void withNoExperimentEveryoneGetsTheActiveVersion() {
        var registry = registry(Map.of(PROMPT, "v1"), Map.of());

        assertThat(registry.selectVersion(PROMPT, "any-user")).isEqualTo("v1");
    }

    @Test
    void anExperimentThatIsOffDoesNotRunEvenWhenItIsConfigured() {
        var registry = registry(Map.of(PROMPT, "v1"),
                Map.of(PROMPT, new PromptProperties.Experiment(false, "v2", 100)));

        assertThat(registry.selectVersion(PROMPT, "any-user")).isEqualTo("v1");
    }

    @Test
    void aFullyRolledOutExperimentSendsEveryone() {
        var registry = registry(Map.of(PROMPT, "v1"),
                Map.of(PROMPT, new PromptProperties.Experiment(true, "v2", 100)));

        assertThat(registry.selectVersion(PROMPT, "any-user")).isEqualTo("v2");
    }

    /**
     * The property that makes an experiment mean anything: a user who saw one
     * variant keeps seeing it. Bucketing on the request id instead would
     * measure the variance within one person's session.
     */
    @Test
    void theSameUserIsAlwaysBucketedTheSameWay() {
        var registry = registry(Map.of(PROMPT, "v1"),
                Map.of(PROMPT, new PromptProperties.Experiment(true, "v2", 50)));
        var user = UUID.randomUUID().toString();

        var first = registry.selectVersion(PROMPT, user);

        assertThat(IntStream.range(0, 200)
                .mapToObj(i -> registry.selectVersion(PROMPT, user))
                .distinct()).containsExactly(first);
    }

    @Test
    void aTenPercentSplitLandsNearTenPercent() {
        var registry = registry(Map.of(PROMPT, "v1"),
                Map.of(PROMPT, new PromptProperties.Experiment(true, "v2", 10)));

        long sampled = IntStream.range(0, 4000)
                .filter(i -> registry.selectVersion(PROMPT, "user-" + i).equals("v2"))
                .count();

        // Deterministic hash over deterministic keys: this is a fixed number,
        // not a draw. The band is wide enough to survive a hash change and
        // narrow enough to catch a bucket that is not spread at all.
        assertThat(sampled).isBetween(300L, 500L);
    }

    /**
     * An anonymous caller has no stable key. Bucketing it on a random value
     * would move a user between variants mid-session, so it gets the active
     * version instead.
     */
    @Test
    void aCallerWithNoBucketKeyGetsTheActiveVersion() {
        var registry = registry(Map.of(PROMPT, "v1"),
                Map.of(PROMPT, new PromptProperties.Experiment(true, "v2", 100)));

        assertThat(registry.selectVersion(PROMPT, null)).isEqualTo("v1");
        assertThat(registry.selectVersion(PROMPT, "  ")).isEqualTo("v1");
    }

    @Test
    void anEnabledExperimentWithoutAVariantIsRefusedAtBindTime() {
        assertThatThrownBy(() -> new PromptProperties.Experiment(true, null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PromptProperties.Experiment(true, "v2", 140))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PromptRegistry registry(
            Map<String, String> active, Map<String, PromptProperties.Experiment> experiments) {
        return new PromptRegistry(new PromptProperties(active, experiments), JSON);
    }
}
