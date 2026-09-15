package com.mustafatetik.atomcv.generation.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mustafatetik.atomcv.generation.domain.EngineVersion;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.RenderedContent;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import java.time.Instant;
import java.util.UUID;

/**
 * One generation, in the shape {@code gradlew replay} can read.
 *
 * <p><strong>the premise is "without touching production data", and that
 * premise needed a file.</strong> The section describes a task that re-runs a
 * pure phase from a {@code selection_state} on a developer's own machine;
 * nothing could produce that file. {@code GET /generations/{id}} does not
 * publish the selection state, and {@code /selection} publishes the weighed
 * rows with their text, which is a different thing. This is the format, and it
 * is deliberately the two columns that already exist rather than a new view of
 * them.
 *
 * <p><strong>What is here, and what is not.</strong> {@link
 * #contentSnapshot()} is the {@code RenderRequest} — it is <em>exactly</em>
 * Faz E's input, which is why Faz E replays and the others do not. Faz B needs
 * a scored tree and Faz C needs the {@code SelectionRequest} built from it
 * with every atom's measured height; neither is stored anywhere, and storing
 * them would be a retention decision about a copy of somebody's whole profile
 * rather than a debugging convenience. {@link #selectionState()} is Faz C's
 * <em>output</em>, and travels because it carries the geometry and the
 * language a re-render needs, and because it is what one reads to see what Faz
 * C decided.
 *
 * <p><strong>This is user content and the grant is what permits it.</strong>
 * The snapshot is the document that was sent to an employer. A file outlives
 * the terminal that {@code SupportRead} prints to, which is the one thing that
 * makes it more dangerous than the printout — so it is written only under an
 * open grant, the same access is stamped, and the export names the moment it
 * was taken so that a file found later can be read against the permission that
 * allowed it.
 *
 * @param generation     which one, so a file on disk can be tied back to a row
 * @param exportedAt     when it was taken. Not decoration: a grant lasts
 *                       forty-eight hours and a file does not
 * @param engineVersion  what produced it, so a replay that differs can say
 *                       whether the code moved
 * @param selectionState Faz C's answer, with the geometry and the language
 * @param contentSnapshot Faz E's input, verbatim
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerationExport(
        UUID generation,
        Instant exportedAt,
        EngineVersion engineVersion,
        StoredSelection selectionState,
        RenderedContent contentSnapshot) {

    public static GenerationExport of(Generation generation, Instant now) {
        return new GenerationExport(
                generation.getId(),
                now,
                generation.getEngineVersion(),
                generation.getSelectionState(),
                generation.getContentSnapshot());
    }

    /**
     * Whether a replay can do anything with this.
     *
     * <p>A generation that failed before Faz E has no snapshot, and a replay
     * that answered with an empty document would look like a rendering bug
     * rather than an export with nothing in it.
     */
    public boolean isReplayable() {
        return contentSnapshot != null && selectionState != null
                && !contentSnapshot.sections().isEmpty();
    }

    /** Shape only: everything under the snapshot is the person's own writing. */
    @Override
    public String toString() {
        return "GenerationExport[" + generation + " at " + exportedAt
                + ", replayable=" + isReplayable() + "]";
    }
}
