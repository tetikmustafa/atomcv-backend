package com.mustafatetik.atomcv.generation.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mustafatetik.atomcv.generation.domain.RenderedContent;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.rendering.latex.LatexDocumentRenderer;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The replay, and the one property that makes it worth having.
 *
 * <p><strong>A replay that produces something slightly different answers
 * nothing.</strong> The whole use of the task is "here is what shipped, here is
 * what this code produces now, diff them" — so the test is a round trip: render
 * a request, export what the generation would have stored, replay the file, and
 * require the bytes to be identical. Anything that quietly normalises on the
 * way through JSON — a dropped mark, a locale read back as its default, a
 * customization losing a slider — fails here and nowhere else.
 *
 * <p>Nothing is stubbed and nothing is started. That is the section's claim
 * about the pure phases, and a test that needed a context to check it would
 * have disproved it.
 */
class ReplayRunTest {

    private static final ObjectMapper JSON =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void areplayedExportProducesExactlyTheDocumentThatShipped(@TempDir Path directory)
            throws Exception {
        // Deliberately not the template's defaults. A replay that quietly fell
        // back to them would render a document that fits a different page --
        // and a page count is usually the number this is opened to explain --
        // while a fixture at the defaults would agree with the fallback and
        // prove nothing.
        RenderRequest shipped = aDocumentSetAt(moved());
        String asShipped = new LatexDocumentRenderer().renderFinal(shipped).value();
        Path export = write(directory.resolve("export.json"), exportOf(shipped));
        Path replayed = directory.resolve("replayed.tex");

        ReplayRun.main(new String[] {export.toString(), replayed.toString()});

        assertThat(Files.readString(replayed, StandardCharsets.UTF_8))
                .as("the same snapshot renders to the same bytes")
                .isEqualTo(asShipped);
    }

    /**
     * The geometry travels. A replay that rendered at the template's defaults
     * would produce a document that fits a different page — and the number this
     * is usually opened to explain is a page count.
     */
    @Test
    void thecustomizationSurvivesTheRoundTrip(@TempDir Path directory) throws Exception {
        RenderRequest shipped = new RenderRequest(
                aDocument().header(), aDocument().sections(), moved(),
                Locale.forLanguageTag("tr"));
        Path export = write(directory.resolve("export.json"), exportOf(shipped));

        GenerationExport read = JSON.readValue(export.toFile(), GenerationExport.class);

        assertThat(read.selectionState().customization().costKey()).isEqualTo(moved().costKey());
        assertThat(read.selectionState().language()).isEqualTo("tr");
    }

    /** An export from a generation that never rendered says so. */
    @Test
    void anexportWithNoSectionsIsNotReplayable() {
        var empty = new GenerationExport(UUID.randomUUID(), Instant.now(), null,
                storedSelection(TemplateCustomization.CLASSIC, "en"),
                new RenderedContent(null, List.of()));

        assertThat(empty.isReplayable()).isFalse();
    }

    /** A path that is not a file is answered, not thrown at. */
    @Test
    void amissingExportIsReported(@TempDir Path directory) throws Exception {
        ReplayRun.main(new String[] {directory.resolve("nothing.json").toString()});
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static Path write(Path to, GenerationExport export) throws Exception {
        JSON.writerWithDefaultPrettyPrinter().writeValue(to.toFile(), export);
        return to;
    }

    private static GenerationExport exportOf(RenderRequest request) {
        return new GenerationExport(UUID.randomUUID(), Instant.parse("2026-09-15T10:00:00Z"),
                null,
                storedSelection(request.customization(), request.contentLanguage().toLanguageTag()),
                RenderedContent.of(request));
    }

    private static StoredSelection storedSelection(
            TemplateCustomization customization, String language) {

        return new StoredSelection(language, customization,
                new SelectionState.BudgetBreakdown(648.0, 68.4, 579.6, 0.0),
                List.of(), List.of());
    }

    /** Nine and a half point, a narrower margin, tighter lines. */
    private static TemplateCustomization moved() {
        return new TemplateCustomization(
                TemplateCustomization.CLASSIC.baseTemplateId(),
                TemplateCustomization.CLASSIC.fontFamily(),
                9.5, 0.45, 1.05, TemplateCustomization.CLASSIC.accentColor());
    }

    private static RenderRequest aDocumentSetAt(TemplateCustomization customization) {
        RenderRequest defaults = aDocument();
        return new RenderRequest(defaults.header(), defaults.sections(),
                customization, defaults.contentLanguage());
    }

    /** One section with one bullet: enough that a dropped mark would show. */
    private static RenderRequest aDocument() {
        var bullet = com.mustafatetik.atomcv.profile.domain.content.RichContent.plain(
                "Built payment systems in Go, cutting p99 latency by 40%");
        var entry = new RenderRequest.RenderableEntry(
                "Senior Backend Engineer", "Acme", "Istanbul",
                "2020 - Present", List.of(bullet));
        var section = new RenderRequest.RenderableSection(
                "Experience",
                com.mustafatetik.atomcv.profile.domain.SectionLayout.BULLET_LIST,
                List.of(entry), List.of());

        return new RenderRequest(
                new RenderRequest.ProfileHeader("Ada Lovelace", "Backend Engineer", List.of()),
                List.of(section), TemplateCustomization.CLASSIC, Locale.ENGLISH);
    }
}
