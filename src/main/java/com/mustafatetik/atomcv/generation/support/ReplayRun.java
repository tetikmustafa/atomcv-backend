package com.mustafatetik.atomcv.generation.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.rendering.latex.LatexDocumentRenderer;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Bolum 48.5's replay, on a developer's own machine and against a file.
 *
 * <pre>
 *   ./scripts/replay.sh export.json            # prints the LaTeX Faz E produces
 *   ./scripts/replay.sh export.json out.tex    # writes it instead
 * </pre>
 *
 * <p><strong>No Spring, no database, no compiler.</strong> That is the whole
 * claim of the section — "Faz B, C, E saf fonksiyon" — and it is only testable
 * by running one of them with nothing else present. {@link LatexDocumentRenderer}
 * has no collaborators at all; this constructs it directly and hands it what
 * {@link GenerationExport} carried.
 *
 * <p><strong>Faz E, and only Faz E, and the reason is the data.</strong>
 * {@code content_snapshot} <em>is</em> Bolum 22.2's {@code RenderRequest}, so
 * replaying the render is exact: the same input produces the same bytes, and
 * comparing them to what shipped is a diff. Faz B needs a scored tree and Faz
 * C needs the selection request built from it with every atom's measured
 * height — and nothing stores either. A task that pretended to replay them
 * would have to rebuild its inputs from today's profile, which is a different
 * profile: the text under an atom keeps changing, which is why the snapshot
 * exists in the first place. Answering a question about last week with this
 * week's data is worse than not answering it.
 *
 * <p>So this closes the half that the stored columns support, and
 * {@code GenerationExport} names exactly what the other half would need.
 */
public final class ReplayRun {

    private ReplayRun() {
    }

    @SuppressWarnings("java:S106")
    public static void main(String[] arguments) throws IOException {
        if (arguments.length == 0 || arguments[0].isBlank()) {
            System.out.println(usage());
            return;
        }
        Path export = Path.of(arguments[0]);
        if (!Files.isRegularFile(export)) {
            System.out.println("No such export: " + export + System.lineSeparator() + usage());
            return;
        }

        GenerationExport read = json().readValue(export.toFile(), GenerationExport.class);
        if (!read.isReplayable()) {
            System.out.println(read + System.lineSeparator()
                    + "There is no content snapshot in this export, so Faz E has no input. "
                    + "The generation it came from did not reach rendering.");
            return;
        }

        String source = new LatexDocumentRenderer().renderFinal(requestOf(read)).value();

        if (arguments.length > 1 && !arguments[1].isBlank()) {
            Path out = Path.of(arguments[1]);
            Files.writeString(out, source, StandardCharsets.UTF_8);
            System.out.println("Faz E replayed from " + read.generation()
                    + " (exported " + read.exportedAt() + ", engine "
                    + read.engineVersion() + ") into " + out);
            return;
        }
        System.out.println(source);
    }

    /**
     * The same reading {@code GenerationDownloadService} does, and deliberately
     * the same: two ways to turn a snapshot into a request would be two
     * documents, and a replay that does not produce what shipped answers
     * nothing.
     */
    private static RenderRequest requestOf(GenerationExport export) {
        StoredSelection selection = export.selectionState();
        return export.contentSnapshot().toRenderRequest(
                selection.customization(), Locale.forLanguageTag(selection.language()));
    }

    /**
     * Its own mapper rather than Spring's: this runs with no context, and the
     * one thing it needs beyond the defaults is {@link java.time.Instant}.
     */
    private static ObjectMapper json() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static String usage() {
        return """
                usage: ./scripts/replay.sh <export.json> [out.tex]

                The export comes from the support reader, under the grant its owner gave:
                  ./scripts/support-read.sh <generation-id> --export=export.json

                Faz E is what replays. Faz B and Faz C are pure too (Bolum 48.5) but
                their inputs -- a scored tree, and a selection request carrying every
                atom's measured height -- are not stored anywhere, so there is nothing
                to replay them from.""";
    }
}
