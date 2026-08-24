package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.service.CompletenessCalculator;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.util.ArrayList;
import java.util.List;

/**
 * Is there a profile here at all (Bolum 25.2)?
 *
 * <p>The first and cheapest of the gates design principle 5 asks for, and the
 * only one both generation modes share. In job-specific mode it runs
 * <em>before</em> Faz A, so a profile with nothing in it never costs an LLM
 * call.
 *
 * <p>Deliberately structural rather than a percentage. Completeness is a nudge
 * on a progress bar; what stops a generation is having nothing to print, and a
 * percentage threshold would refuse profiles that would have rendered
 * perfectly well.
 */
final class ProfilePreflight {

    private ProfilePreflight() {
    }

    static Result<Void> check(Profile head, ProfileTree tree) {
        List<String> missing = new ArrayList<>();
        if (tree.atomCount() == 0) {
            missing.add("atoms");
        }
        if (tree.sections().isEmpty()) {
            missing.add("sections");
        }
        if (missing.isEmpty()) {
            return Result.ok(null);
        }
        return Result.err(new PipelineError.InsufficientProfile(
                CompletenessCalculator.of(head, tree), missing));
    }
}
