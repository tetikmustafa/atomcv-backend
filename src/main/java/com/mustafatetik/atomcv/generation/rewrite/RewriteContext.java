package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.profile.domain.Tone;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * What every bullet in one generation shares (Bolum 21.4).
 *
 * <p>Built once per generation rather than per atom: the posting's skills, the
 * language and the tone are the same for all eight candidates, and passing
 * them one at a time would be eight chances for them to disagree.
 *
 * @param postingSkills what the job asks for, canonical and lowercase. Both
 *                      what the model is told to reach for and the vocabulary
 *                      the validator checks the answer against — deliberately
 *                      the same list, because the temptation and the guard
 *                      must be looking at the same words
 * @param language      the language the CV is being written in
 * @param tone          how the profile asked to sound
 * @param bucketKey     who this generation is for, so that a prompt experiment
 *                      keeps showing one person one variant (Bolum 53.3)
 */
public record RewriteContext(
        List<String> postingSkills, String language, String tone, String bucketKey) {

    public RewriteContext {
        postingSkills = List.copyOf(postingSkills);
        language = language == null || language.isBlank() ? "en" : language;
        tone = tone == null || tone.isBlank() ? Tone.FORMAL.wireValue() : tone;
    }

    /**
     * Required and preferred together: a model told only about the required
     * ones writes past the preferred ones, and a validator that only knew the
     * required ones would let a preferred skill be claimed unchecked. The
     * guard has to cover everything the posting put in front of the model.
     */
    public static RewriteContext of(
            JobAnalysis posting, String language, Tone tone, String bucketKey) {

        var skills = new LinkedHashSet<String>();
        posting.requiredSkills().forEach(skill -> skills.add(canonical(skill)));
        posting.preferredSkills().forEach(skill -> skills.add(canonical(skill)));
        skills.remove("");
        return new RewriteContext(List.copyOf(skills), language,
                tone == null ? null : tone.wireValue(), bucketKey);
    }

    private static String canonical(JobAnalysis.Skill skill) {
        String name = skill.canonical().isBlank() ? skill.name() : skill.canonical();
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
