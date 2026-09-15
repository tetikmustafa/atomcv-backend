package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The keep-mark on a generation ({@code generations.archived}).
 *
 * <p><strong>What the mark is for, and what it is not yet.</strong> Bolum 13
 * pairs the column with {@code pdf_expires_at}: an artifact is kept fourteen
 * days, and an archived one is kept for good. No artifact is stored today — EK
 * D.6.3 settled that a download re-renders from {@code content_snapshot} and
 * that the {@code 410} path arrives with R2 — so nothing expires in either
 * direction and the mark changes no retention. It is the owner's mark on their
 * own history until storage lands, and the row the retention rule will read on
 * the day it does (the open paragraph is the reminder).
 *
 * <p><strong>Idempotent, and that is not an accident of the write.</strong>
 * Pressing archive on something already archived is not a conflict: the caller
 * asked for a state and the row is in it. The same reasoning as the second
 * press of account deletion.
 *
 * <p>Scoped, which is the IDOR defense here as everywhere: someone else's
 * generation reads as absent, and the endpoint answers 404 rather than telling
 * a stranger the id exists (absolute rule 3).
 */
@Service
public class GenerationArchiveService {

    private final GenerationRepository generations;

    GenerationArchiveService(GenerationRepository generations) {
        this.generations = generations;
    }

    /**
     * @return the row as it now stands, archived or not
     * @throws ApiException {@code RESOURCE_NOT_FOUND} when this user has no
     *                      such generation
     */
    @Transactional
    public Generation setArchived(UserContext user, UUID generationId, boolean archived) {
        Generation generation = generations.findById(user, generationId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));

        if (generation.isArchived() == archived) {
            return generation;
        }
        generation.setArchived(archived);
        return generations.save(user, generation);
    }
}
