package com.mustafatetik.atomcv.generation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.api.dto.GenerationResponse;
import com.mustafatetik.atomcv.generation.api.dto.GenerationSummary;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The page limit a generation was made under, as the two DTOs publish it
 * (F-039).
 *
 * <p><strong>Why this is not read off the profile.</strong> "A CV shorter than
 * one page is correct and is never padded — the person is told" was written in
 * the product document and could not be built: the server published {@code
 * pageCount} and no limit to read it against, so a note written without a
 * signal would have appeared on every CV ever made. The profile's own
 * preference cannot stand in for it. {@code POST /generations} takes a {@code
 * maxPages} and {@code increase_page_limit} changes exactly that, so the
 * preference says what is set today; calling a two-year-old CV short against
 * today's setting would be reporting a fact that never existed.
 *
 * <p>Which makes the absent case the one worth a test of its own. A row
 * written before the option was recorded does not know its limit, and a
 * plausible default there would be the server inventing the fact rather than
 * declining to state it.
 */
class GenerationMaxPagesTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID PROFILE = UUID.randomUUID();

    @Test
    void thelimitTheGenerationWasMadeUnderReachesTheResponse() {
        var generation = generationWith(optionsWithLimit(2));
        generation.setPageCount(1);

        var response = GenerationResponse.of(generation);

        // The pair the screen compares: one page produced out of two allowed
        // is a document shorter than it was permitted to be.
        assertThat(response.pageCount()).isEqualTo(1);
        assertThat(response.maxPages()).isEqualTo(2);
    }

    @Test
    void thelimitReachesAhistoryRowToo() {
        var generation = generationWith(optionsWithLimit(2));
        generation.setPageCount(1);

        assertThat(GenerationSummary.of(generation).maxPages()).isEqualTo(2);
    }

    /**
     * And the guard has to be seen refusing. Without this the two above would
     * pass against an accessor that returned a constant.
     */
    @Test
    void arowWrittenBeforeTheLimitWasRecordedSaysNothingRatherThanGuessing() {
        var generation = generationWith(new LinkedHashMap<>(Map.of("templateId", "classic")));
        generation.setPageCount(1);

        assertThat(generation.getMaxPages()).isNull();
        assertThat(GenerationResponse.of(generation).maxPages()).isNull();
        assertThat(GenerationSummary.of(generation).maxPages()).isNull();
    }

    /**
     * Jackson reads a JSONB number back as an Integer here and could hand back
     * a Long or a BigDecimal from a wider column; the accessor takes any
     * Number rather than casting to one of them.
     */
    @Test
    void awidenedNumberIsStillTheLimit() {
        var options = new LinkedHashMap<String, Object>();
        options.put(Generation.MAX_PAGES, 3L);

        assertThat(generationWith(options).getMaxPages()).isEqualTo(3);
    }

    private static Generation generationWith(Map<String, Object> options) {
        return new Generation(USER, PROFILE, options, selection(), engine());
    }

    /**
     * A {@code LinkedHashMap}: the JDK's immutable maps iterate in an order
     * salted per JVM run, and this one reaches a JSONB column.
     */
    private static Map<String, Object> optionsWithLimit(int maxPages) {
        var options = new LinkedHashMap<String, Object>();
        options.put("templateId", "classic");
        options.put(Generation.MAX_PAGES, maxPages);
        options.put("cvLanguage", "en");
        return options;
    }

    private static StoredSelection selection() {
        return StoredSelection.of(new SelectionState(List.of(), List.of(),
                        new SelectionState.BudgetBreakdown(648.0, 142.0, 506.0, 0.0)),
                "en", TemplateCustomization.CLASSIC);
    }

    private static EngineVersion engine() {
        return new EngineVersion(EngineVersion.PIPELINE, "default", "classic:v1", Map.of());
    }
}
