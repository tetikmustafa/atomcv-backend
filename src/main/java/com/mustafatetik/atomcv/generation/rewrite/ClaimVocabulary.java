package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.shared.text.SkillNames;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The names a keyword-stuffed answer would reach for (Bolum 21.6, Bolum 21.7).
 *
 * <p>A guard can only refuse a claim it can recognise, and this is the list of
 * things it recognises. One copy, because the bullet rewrite and the summary
 * are exposed to the same temptation and a term missing from one of two lists
 * is a hole in exactly one of them.
 *
 * <p><strong>Both halves of the alias dictionary.</strong> The file maps
 * {@code k8s = kubernetes}, so reading only its keys leaves the canonical name
 * — the one the model actually writes — out of the vocabulary entirely. That
 * was the shape of a real gap: "Kubernetes" in an answer matched nothing,
 * because nobody had ever needed an alias line whose left side was already the
 * name people write.
 */
final class ClaimVocabulary {

    private ClaimVocabulary() {
    }

    /**
     * @param postingSkills what this posting asked for, which is where the
     *                      temptation comes from — the prompt has just been
     *                      shown them. May be empty
     */
    static Set<String> of(Collection<String> postingSkills) {
        Set<String> terms = new LinkedHashSet<>();
        for (String skill : postingSkills) {
            if (skill != null && !skill.isBlank()) {
                terms.add(skill.toLowerCase(Locale.ROOT));
            }
        }
        SkillNames.aliases().forEach((alias, canonical) -> {
            terms.add(alias);
            terms.add(canonical);
        });
        return terms;
    }
}
