package com.mustafatetik.atomcv.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Which provider each profile gets, and that it is exactly one.
 *
 * <p>Worth a test rather than a reading of the annotations, because both
 * failure modes are silent. Two beans and the context refuses to start with a
 * message about ambiguity that names neither profile; none, and whatever
 * injects an {@code EmbeddingProvider} fails at startup for a reason that
 * looks like a missing class.
 */
class EmbeddingProviderWiringTest {

    @Configuration
    @ComponentScan("com.mustafatetik.atomcv.embedding")
    @EnableConfigurationProperties(EmbeddingProperties.class)
    static class ScanTheModule {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    /** Daily work: no container, no 2.5 GB download, no cost. */
    @Test
    void localFakeGetsTheHashBasedProvider() {
        run("local,local-fake", provider ->
                assertThat(provider).isInstanceOf(FakeEmbeddingProvider.class));
    }

    /**
     * Bolum 54.2's recording and prompt modes call the real thing. A fake
     * vector recorded as a fixture would be indistinguishable from a real one
     * afterwards — the same reason the LLM fake is off in those profiles.
     */
    @Test
    void everyOtherProfileGetsTheRealService() {
        run("local,local-record", provider ->
                assertThat(provider).isInstanceOf(TeiEmbeddingProvider.class));
        run("local,local-real", provider ->
                assertThat(provider).isInstanceOf(TeiEmbeddingProvider.class));
        run("prod", provider ->
                assertThat(provider).isInstanceOf(TeiEmbeddingProvider.class));
    }

    @Test
    void thereIsNeverMoreThanOne() {
        for (String profiles : new String[] {"local,local-fake", "local", "prod"}) {
            new ApplicationContextRunner()
                    .withPropertyValues("spring.profiles.active=" + profiles)
                    .withUserConfiguration(ScanTheModule.class)
                    .run(context -> assertThat(context)
                            .as("%s", profiles)
                            .hasSingleBean(EmbeddingProvider.class));
        }
    }

    private static void run(String profiles, java.util.function.Consumer<EmbeddingProvider> check) {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + profiles)
                .withUserConfiguration(ScanTheModule.class)
                .run(context -> check.accept(context.getBean(EmbeddingProvider.class)));
    }
}
