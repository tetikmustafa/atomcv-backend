package com.mustafatetik.atomcv.llm.fake;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.llm.gateway.LlmProperties;
import com.mustafatetik.atomcv.llm.gateway.ModelTier;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The three development profiles of Bolum 54.2, held to actually existing.
 *
 * <p>This guard is here because of how the gap presented itself: Spring
 * accepts an unknown profile name in silence. Before these files were written,
 * {@code make dev} ran with {@code --spring.profiles.active=local,local-fake}
 * and looked correct while the second half contributed nothing at all. A
 * renamed or deleted file would look exactly the same way, so the assertion is
 * on a value only the file can supply.
 *
 * <p>No database and no web server: {@code ConfigDataApplicationContextInitializer}
 * reads the same {@code application-*.yml} the application does.
 */
class LocalProfileConfigTest {

    @Configuration
    @EnableConfigurationProperties({FakeLlmProperties.class, LlmProperties.class})
    static class Binding {
    }

    @Test
    void localFakeReplaysFixturesAndFallsBackToSynthesis() {
        run("local,local-fake", context -> {
            var properties = context.getBean(FakeLlmProperties.class);
            assertThat(properties.synthesize()).isTrue();
            assertThat(properties.fixtureDir())
                    .isEqualTo(Path.of("src/test/resources/fixtures/llm"));
        });
    }

    /**
     * Without this override the base chain names openrouter, which has no key
     * under this profile — so every generation would end in
     * ALL_PROVIDERS_UNAVAILABLE while looking correctly configured. That is
     * the opposite of what "costs nothing, works offline" has to mean.
     */
    @Test
    void localFakePointsBothChainsAtTheFakeAndNothingElse() {
        run("local,local-fake", context -> {
            var llm = context.getBean(LlmProperties.class);
            assertThat(llm.chainFor(ModelTier.CHEAP)).containsExactly("fake");
            assertThat(llm.chainFor(ModelTier.MID)).containsExactly("fake");
        });
    }

    /**
     * The base configuration, for contrast: the real adapters, in order.
     *
     * <p>Two of them, and the order is the fallback (Bolum 27.3). It said
     * {@code openrouter} alone until the second adapter existed — the chain
     * mechanism was built, tested and had exactly one link, so a single
     * vendor's outage stopped the product.
     */
    @Test
    void withoutTheFakeProfileTheChainNamesTheRealAdapters() {
        run("local", context -> {
            var llm = context.getBean(LlmProperties.class);
            assertThat(llm.chainFor(ModelTier.CHEAP)).containsExactly("openrouter", "gemini");
            assertThat(llm.chainFor(ModelTier.MID)).containsExactly("openrouter", "gemini");
        });
    }

    /**
     * Bolum 54.2: a miss must become a real call. Synthesis left on here would
     * record placeholders as though a model had produced them, and nothing
     * afterwards could tell the two apart.
     */
    @Test
    void localRecordNeverSynthesizes() {
        run("local,local-record", context ->
                assertThat(context.getBean(FakeLlmProperties.class).synthesize()).isFalse());
    }

    @Test
    void localRealNeverSynthesizes() {
        run("local,local-real", context ->
                assertThat(context.getBean(FakeLlmProperties.class).synthesize()).isFalse());
    }

    /**
     * The negative control: with no such profile the property falls back to
     * its default, which is what every one of these assertions looked like
     * before the files existed.
     */
    @Test
    void aProfileWithNoFileContributesNothingAndSaysNothing() {
        run("local,local-nonexistent", context ->
                assertThat(context.getBean(FakeLlmProperties.class).synthesize()).isFalse());
    }

    /**
     * The variable in `.env` cannot turn the fake chain into a real one, and
     * this is here because the opposite was written down as measured fact.
     *
     * <p>`.env` sets `LLM_CHAIN_CHEAP` and `LLM_CHAIN_MID` to `openrouter`, the
     * Makefile exports it, and CLAUDE.md concluded that `make dev` therefore
     * calls a real provider whatever the profile is called. It does not:
     * `LLM_CHAIN_CHEAP` relaxed-binds to `llm.chain.cheap`, the property here is
     * `atomcv.llm.chain.cheap`, and the variable only ever feeds the
     * placeholder in the base document. A profile-specific file outranks that
     * document, so `[fake]` wins.
     *
     * <p>Which makes this a guard on a precedence, not on a value: it fails the
     * day the override leaves `application-local-fake.yml`, which is the change
     * that would make the old claim true.
     */
    @Test
    void anEnvironmentVariableCannotTurnTheFakeChainIntoARealOne() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=local,local-fake",
                        "LLM_CHAIN_CHEAP=openrouter,gemini",
                        "LLM_CHAIN_MID=openrouter,gemini")
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(Binding.class)
                .run(context -> {
                    var llm = context.getBean(LlmProperties.class);
                    assertThat(llm.chainFor(ModelTier.CHEAP)).containsExactly("fake");
                    assertThat(llm.chainFor(ModelTier.MID)).containsExactly("fake");
                });
    }

    private static void run(String profiles, ContextAssertion assertion) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profiles)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(Binding.class)
                .run(context -> assertion.check(context));
    }

    @FunctionalInterface
    interface ContextAssertion {
        void check(AssertableApplicationContext context);
    }
}
