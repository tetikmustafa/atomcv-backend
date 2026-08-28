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
 * @param postingFocus  what the job is <em>about</em>, which the About
 *                      paragraph leads with (Bolum 21.7). Separate from the
 *                      skills because it is emphasis rather than vocabulary
 * @param ownWords      {@code profiles.self_description}: the only source the
 *                      summary has for a claim that is not a skill or a number
 * @param language      the language the CV is being written in
 * @param tone          how the profile asked to sound
 * @param bucketKey     who this generation is for, so that a prompt experiment
 *                      keeps showing one person one variant (Bolum 53.3)
 */
public record RewriteContext(
        List<String> postingSkills,
        List<String> postingFocus,
        String ownWords,
        String language,
        String tone,
        String bucketKey) {

    public RewriteContext {
        postingSkills = List.copyOf(postingSkills);
        postingFocus = postingFocus == null ? List.of() : List.copyOf(postingFocus);
        ownWords = ownWords == null ? "" : ownWords;
        language = language == null || language.isBlank() ? "en" : language;
        tone = tone == null || tone.isBlank() ? Tone.FORMAL.wireValue() : tone;
    }

    /**
     * Required and preferred together: a model told only about the required
     * ones writes past the preferred ones, and a validator that only knew the
     * required ones would let a preferred skill be claimed unchecked. The
     * guard has to cover everything the posting put in front of the model.
     */
    public static RewriteContext of(JobAnalysis posting, String ownWords,
            String language, Tone tone, String bucketKey) {

        var skills = new LinkedHashSet<String>();
        posting.requiredSkills().forEach(skill -> skills.add(canonical(skill)));
        posting.preferredSkills().forEach(skill -> skills.add(canonical(skill)));
        skills.remove("");

        // Bolum 18's responsibilities and not its keywords: the keywords are
        // the posting's vocabulary, which is exactly what a stuffed summary
        // would draw from. The validator refuses that either way, but a prompt
        // is better for not having been shown the temptation.
        var focus = new LinkedHashSet<>(posting.responsibilities());
        focus.remove(null);
        focus.remove("");

        return new RewriteContext(List.copyOf(skills), List.copyOf(focus), ownWords,
                language, tone == null ? null : tone.wireValue(), bucketKey);
    }

    private static String canonical(JobAnalysis.Skill skill) {
        String name = skill.canonical().isBlank() ? skill.name() : skill.canonical();
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
