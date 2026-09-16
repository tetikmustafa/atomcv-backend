package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.RenderedContent;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.rendering.DocumentWriter;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Handing back a document that was already made.
 *
 * <p>Stage 2 stores no bytes — {@code pdf_key} is for R2 and R2 arrives in
 * Stage 3 — so a download is a re-render. That is not a workaround: the PDF is
 * always reproducible by design, and here it is the whole mechanism rather
 * than the fallback for an expired one.
 *
 * <p><strong>It re-renders the snapshot, never the profile.</strong> The
 * selection names atoms by id and the text under those ids goes on being
 * edited; reading the profile again would hand back a different document from
 * the one that was sent to an employer, and nothing would say so. The content
 * snapshot is what the renderer was given the first time, so the second run has
 * the same input.
 *
 * <p>No LLM, no scoring, no selection. One compilation, and it is deterministic
 * — the same row produces the same bytes on any day.
 *
 * <p><strong>It no longer knows which formats exist.</strong> There were four
 * methods here, one per format, and two of them held a concrete writer from
 * the rendering module — so this class, and the controller above it, were the
 * two places that enumerated the product's output formats. Both are the places
 * least entitled to: module rule 3 says the generation module takes a capacity
 * from rendering and nothing else, and the fourth product claim says formats
 * are independent plug-ins. They were not; adding one meant editing this file.
 * Now a caller resolves a {@link DocumentWriter} and this hands it the same
 * request every format gets.
 */
@Service
public class GenerationDownloadService {

    private final GenerationRepository generations;

    GenerationDownloadService(GenerationRepository generations) {
        this.generations = generations;
    }

    /** The user's own generation, or nothing. Absolute rule 3. */
    public Optional<Generation> find(UserContext user, UUID generationId) {
        return generations.findById(user, generationId);
    }

    /**
     * The document, in whichever format the caller resolved.
     *
     * @return the bytes, or the failure the writer reported. Only the PDF has
     *         one to report — it is the only format that goes through a
     *         compiler. Whether the row exists and whether it can be
     *         re-rendered at all are the caller's questions: both end the
     *         request with a status code and neither is a pipeline failure.
     */
    public Result<byte[]> write(Generation generation, DocumentWriter writer) {
        return writer.bytesFor(requestFor(generation));
    }

    /**
     * What was printed, read off the snapshot rather than off today's profile.
     *
     * <p>Shared by every format on purpose: two readings of one row would be
     * two documents, and the whole reason the snapshot exists is that the text
     * under an atom keeps changing.
     */
    private static RenderRequest requestFor(Generation generation) {
        RenderedContent content = generation.getContentSnapshot();
        StoredSelection selection = generation.getSelectionState();
        return content.toRenderRequest(
                selection.customization(), java.util.Locale.forLanguageTag(selection.language()));
    }
}
