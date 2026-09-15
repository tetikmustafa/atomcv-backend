package com.mustafatetik.atomcv.shared.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson modules and parser limits that Spring Boot does not set on its own.
 *
 * <p>Without {@code JsonNullableModule}, a patch field would arrive as an
 * object with a {@code present} flag rather than as the value itself, and the
 * distinction between "not sent" and "sent as null" — the whole point of the
 * type — would never reach the service.
 */
@Configuration
public class JacksonConfig {

    /**
     * Bolum 42.4's ceiling on how deep a document may nest.
     *
     * <p>Fifty is far past anything this API accepts: the deepest body it reads
     * is a profile import, and that bottoms out around six. The number is a
     * tripwire for a hand-written document, not a shape this product produces.
     */
    static final int MAX_NESTING_DEPTH = 50;

    /**
     * Bolum 42.4's ceiling on a single string value, in characters.
     *
     * <p>A megabyte against a job description capped at 20,000 characters and
     * an upload capped by {@code max-file-size} before a parser ever sees it.
     * Jackson's own default is twenty times this, which is a limit in name
     * only.
     */
    static final int MAX_STRING_LENGTH = 1_000_000;

    @Bean
    public JsonNullableModule jsonNullableModule() {
        return new JsonNullableModule();
    }

    /**
     * Narrows the parser to the limits of Bolum 42.4.
     *
     * <p>Jackson ships defaults for both — 1,000 and 20,000,000 — so this is
     * not the difference between a guard and none. It is the difference
     * between a guard sized for this API and one sized for every API: a body
     * that reaches either of Jackson's numbers has already spent the memory
     * and the parse, and nothing this product publishes could have sent it.
     *
     * <p>Default typing needs no switching off: Jackson has shipped it
     * disabled since 2.10, and calling {@code deactivateDefaultTyping()} on a
     * builder that never activated it would read as a defence rather than the
     * no-op it is. What keeps it off is that nothing here turns it on.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer streamReadConstraints() {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxNestingDepth(MAX_NESTING_DEPTH)
                .maxStringLength(MAX_STRING_LENGTH)
                .build();
        return builder -> builder.postConfigurer(
                mapper -> mapper.getFactory().setStreamReadConstraints(constraints));
    }
}
