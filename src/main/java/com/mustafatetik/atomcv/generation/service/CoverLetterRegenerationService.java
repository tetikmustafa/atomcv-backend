package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.coverletter.CoverLetterDraft;
import com.mustafatetik.atomcv.generation.coverletter.CoverLetterStyle;
import com.mustafatetik.atomcv.generation.coverletter.CoverLetterWriter;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.repository.GenerationRepository;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bolum 34.6's three buttons, and the first press of any of them.
 *
 * <p><strong>Written against the generation, not against today's
 * profile.</strong> The selection snapshot on the row is what was actually on
 * the page, and it is the whole reason the letter is honest: somebody who
 * edited a bullet after generating still gets a letter about the CV they sent.
 *
 * <p>The letter is stored on the row it belongs to, which means the last one
 * written is the one a reload shows. Three presses of the button leave one
 * letter, and that is what a person means by trying another draft.
 */
@Service
public class CoverLetterRegenerationService {

    private final ProfileResolver profiles;
    private final ProfileAssembler assembler;
    private final GenerationRepository generations;
    private final CoverLetterWriter letters;

    CoverLetterRegenerationService(ProfileResolver profiles, ProfileAssembler assembler,
            GenerationRepository generations, CoverLetterWriter letters) {

        this.profiles = profiles;
        this.assembler = assembler;
        this.generations = generations;
        this.letters = letters;
    }

    /** @return empty when there is no such generation for this user */
    public Optional<Generation> find(UserContext user, java.util.UUID generationId) {
        return generations.findById(user, generationId);
    }

    /**
     * @param generation the row, already read through the scoped repository —
     *                   which is where ownership was decided (absolute rule 3)
     */
    @Transactional
    public Result<CoverLetterDraft> rewrite(
            UserContext user, Generation generation, CoverLetterStyle style, String companyNote) {

        var owned = profiles.owned(user);
        Profile head = owned.profile();
        ProfileTree tree = assembler.load(owned.ref());

        Result<CoverLetterDraft> written = letters.write(
                head, tree, generation.getSelectionState().toSelectionState(),
                generation.getJdAnalysis(), companyNote, style, user.userId().toString(),
                user.userId());

        if (written instanceof Result.Ok<CoverLetterDraft> ok) {
            generation.setCoverLetter(ok.value().plainText());
            generations.save(user, generation);
        }
        return written;
    }
}
