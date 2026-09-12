package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.service.CallerProfiles;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reading half of Faz G's hand toggle (Bolum 24.4, F-031).
 *
 * <p><strong>A screen cannot draw a toggle it cannot name.</strong> The edit
 * endpoint refuses an atom this generation never weighed, and refuses it on
 * purpose — so a client drawing its buttons from today's profile would print
 * buttons that answer 400. What it needs is this generation's own list, which
 * is the same list {@link NaturalLanguageEditService} numbers for the model,
 * uncapped and with the ids left in.
 *
 * <p>Nothing here is scoped, and it must not be: the generation arrives already
 * read through a scoped repository, exactly as {@link SelectionEditService}
 * takes it, so absolute rule 3 stays at the one door the controller opens.
 */
@Service
public class SelectionViewService {

    private final CallerProfiles callers;
    private final ProfileAssembler assembler;

    SelectionViewService(CallerProfiles callers, ProfileAssembler assembler) {
        this.callers = callers;
        this.assembler = assembler;
    }

    /**
     * Every candidate this generation weighed, page first.
     *
     * <p>The tone is today's preference rather than the generation's, which is
     * what the sentence half already does: it decides nothing but which
     * wording is picked when the snapshot named no variant, and having two
     * answers to that would put a different sentence beside the toggle than
     * beside the number.
     *
     * @param generation already read through a scoped repository
     */
    @Transactional(readOnly = true)
    public List<WeighedLines.Line> linesOf(Generation generation) {
        ProfileResolver.OwnedProfile owned = callers.owned();
        ProfileTree tree = assembler.load(owned.ref());
        Tone tone = owned.profile().getPreferences().writingStyle().tone();

        return WeighedLines.of(tree, generation.getSelectionState(),
                generation.getRewrittenContent(), tone);
    }
}
