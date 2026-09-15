package com.mustafatetik.atomcv.shared.config;

import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The parser limits, seen refusing.
 *
 * <p>Jackson's own defaults are twenty times looser, so a test that only fed
 * the mapper a reasonable document would pass with this configuration deleted.
 * Each case here is one step past the limit and one step inside it.
 */
class JacksonConfigTest {

    private final ObjectMapper mapper = configuredMapper();

    private static ObjectMapper configuredMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().streamReadConstraints().customize(builder);
        return builder.build();
    }

    @Test
    void adocumentNestedPastTheCeilingIsRefused() {
        assertThatThrownBy(() -> mapper.readTree(nested(JacksonConfig.MAX_NESTING_DEPTH + 1)))
                .isInstanceOf(StreamConstraintsException.class)
                .hasMessageContaining("nesting depth");
    }

    @Test
    void adocumentNestedInsideTheCeilingIsRead() {
        assertThatCode(() -> mapper.readTree(nested(JacksonConfig.MAX_NESTING_DEPTH - 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void astringPastTheCeilingIsRefused() {
        assertThatThrownBy(() -> mapper.readTree(stringOf(JacksonConfig.MAX_STRING_LENGTH + 1)))
                .isInstanceOf(StreamConstraintsException.class);
    }

    @Test
    void thelongestJobDescriptionTheApiAcceptsIsWellInsideTheCeiling() {
        // A posting is capped at 20,000 characters; the limit exists for
        // documents this product would never produce, not for the ones it
        // does.
        assertThatCode(() -> mapper.readTree(stringOf(20_000)))
                .doesNotThrowAnyException();
    }

    @Test
    void anunconfiguredMapperAcceptsBothOfThem() throws Exception {
        // The first rule, kept in the suite rather than done once by hand:
        // Jackson's defaults are 1,000 and 20,000,000, so deleting the
        // customizer would leave every other case in this class passing. This
        // one says what the configuration is actually buying.
        ObjectMapper plain = new ObjectMapper();
        assertThat(plain.readTree(nested(JacksonConfig.MAX_NESTING_DEPTH + 1))).isNotNull();
        assertThat(plain.readTree(stringOf(JacksonConfig.MAX_STRING_LENGTH + 1))).isNotNull();
    }

    @Test
    void defaultTypingIsOff() {
        assertThat(mapper.getPolymorphicTypeValidator()).isNotNull();
        assertThat(mapper.getDeserializationConfig().getDefaultTyper(null)).isNull();
    }

    private static String nested(int depth) {
        return "[".repeat(depth) + "]".repeat(depth);
    }

    private static String stringOf(int length) {
        return "\"" + "a".repeat(length) + "\"";
    }
}
