package com.mustafatetik.atomcv.compilation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Which compiler each profile gets, and that it is exactly one.
 *
 * <p>The profile files are read the way the application reads them, rather
 * than the switch being set here: what decides this is
 * {@code application-local-fake.yml}, and a test that set
 * {@code atomcv.latex.fake} itself would pass on the day that line left the
 * file.
 *
 * <p>Three failures, all of them quiet. Two beans and the context refuses to
 * start over an ambiguity naming neither; none and every caller looks like a
 * missing class; and the one this switch exists for — the fake reaching a
 * lane that has a real compiler, where it answers every compilation with a
 * one-page placeholder and the page guarantee goes on being reported as kept.
 */
class LatexCompilerWiringTest {

    @Configuration
    @ComponentScan("com.mustafatetik.atomcv.compilation")
    @EnableConfigurationProperties(CompilationProperties.class)
    static class ScanTheModule {
    }

    /** Daily work: no image to build, no Docker, and a document at the end. */
    @Test
    void localFakeGetsTheFake() {
        run("local,local-fake", compiler ->
                assertThat(compiler).isInstanceOf(FakeLatexCompiler.class));
    }

    /**
     * Everything else, and the recording profiles above all: a fixture is
     * recorded against the real engine or it records nothing worth replaying.
     */
    @Test
    void everyOtherProfileGetsTheContainer() {
        run("local,local-record", compiler ->
                assertThat(compiler).isInstanceOf(LatexCompilerClient.class));
        run("local,local-real", compiler ->
                assertThat(compiler).isInstanceOf(LatexCompilerClient.class));
        run("prod", compiler ->
                assertThat(compiler).isInstanceOf(LatexCompilerClient.class));
    }

    /**
     * What {@code AbstractLatexTest} does, asserted here so the reason
     * survives: that lane runs under {@code local-fake} for the model and
     * needs the compiler the profile would otherwise have replaced.
     */
    @Test
    void theSwitchGivesTheCompilerBackUnderTheSameProfile() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=local,local-fake",
                        "atomcv.latex.fake=false")
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(ScanTheModule.class)
                .run(context -> assertThat(context.getBean(LatexCompiler.class))
                        .isInstanceOf(LatexCompilerClient.class));
    }

    @Test
    void thereIsNeverMoreThanOne() {
        for (String profiles : new String[] {"local,local-fake", "local", "prod"}) {
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withPropertyValues("spring.profiles.active=" + profiles)
                    .withConfiguration(AutoConfigurations.of(
                            ConfigurationPropertiesAutoConfiguration.class))
                    .withUserConfiguration(ScanTheModule.class)
                    .run(context -> assertThat(context)
                            .as("%s", profiles)
                            .hasSingleBean(LatexCompiler.class));
        }
    }

    private static void run(String profiles, Consumer<LatexCompiler> check) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profiles)
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(ScanTheModule.class)
                .run(context -> check.accept(context.getBean(LatexCompiler.class)));
    }
}
