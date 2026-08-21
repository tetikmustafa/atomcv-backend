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

    /** The base configuration, for contrast: the adapter that exists. */
    @Test
    void withoutTheFakeProfileTheChainNamesTheRealAdapter() {
        run("local", context ->
                assertThat(context.getBean(LlmProperties.class).chainFor(ModelTier.CHEAP))
                        .containsExactly("openrouter"));
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
