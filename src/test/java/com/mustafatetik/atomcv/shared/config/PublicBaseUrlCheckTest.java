package com.mustafatetik.atomcv.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Production with no public address sends every email to localhost, and
 * works perfectly otherwise — the one fault nobody finds from the behaviour.
 */
class PublicBaseUrlCheckTest {

    @Test
    void productionWithoutABaseUrlDoesNotStart() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new PublicBaseUrlCheck(prod(null)))
                .withMessageContaining("APP_BASE_URL");
    }

    @Test
    void productionWithTheDevelopmentFallbackDoesNotStart() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new PublicBaseUrlCheck(prod("http://localhost:3000")));
    }

    @Test
    void productionWithThePublicHostStarts() {
        assertThatCode(() -> new PublicBaseUrlCheck(prod("https://atomcv.mustafatetik.com")))
                .doesNotThrowAnyException();
    }

    /** Local development and the whole test suite run on the fallback. */
    @Test
    void outsideProductionNothingIsChecked() {
        assertThatCode(() -> new PublicBaseUrlCheck(new MockEnvironment()))
                .doesNotThrowAnyException();
    }

    @Test
    void theReasonsNameWhatIsWrong() {
        assertThat(PublicBaseUrlCheck.problemWith("http://atomcv.example")).contains("https");
        assertThat(PublicBaseUrlCheck.problemWith("https://127.0.0.1")).contains("127.0.0.1");
        assertThat(PublicBaseUrlCheck.problemWith("https://app.localhost")).isNotNull();
        assertThat(PublicBaseUrlCheck.problemWith("  ")).contains("not set");
        assertThat(PublicBaseUrlCheck.problemWith("https://atomcv.mustafatetik.com/")).isNull();
    }

    private static MockEnvironment prod(String baseUrl) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        if (baseUrl != null) {
            environment.setProperty("APP_BASE_URL", baseUrl);
        }
        return environment;
    }
}
