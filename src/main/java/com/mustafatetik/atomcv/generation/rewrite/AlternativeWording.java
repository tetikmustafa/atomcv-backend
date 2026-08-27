package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.Tone;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Faz D, step one: the wording that is already there (Bolum 21.1).
 *
 * <p><strong>The cheapest rewrite is the one somebody already wrote.</strong>
 * A person who kept two versions of a bullet — one formal, one technical — has
 * made an investment, and Bolum 21.1 is where it pays: if one of them fits
 * this posting, the LLM is never called for that atom at all.
 *
 * <p><strong>Düzeltme — Bolum 21.1 ranks the alternatives by
 * {@code similarity(v.embedding(), jdVector)}, and a variant has no
 * embedding.</strong> The vector lives on {@code atoms}, computed from the
 * English wording (Bolum 31.6.2), because that is what a cross-language
 * comparison needs: two wordings of one sentence embed to nearly the same
 * point, so ranking them against the posting would be measuring noise. What
 * actually separates them is what the person set — the language and the tone —
 * so that is what this filters on, and the rest is a deterministic tie-break.
 * The same generation asked for twice must not come out differently.
 */
public final class AlternativeWording {

    private AlternativeWording() {
    }

    /**
     * The best wording of one atom for this posting, or empty when the atom
     * has none at all.
     *
     * <p>Language first, because a CV in the wrong language is not a style
     * question. Tone second, and only as a preference: an atom with no
     * wording in the requested tone keeps the one it has rather than being
     * dropped, which is the same fallback selection already makes for a
     * missing translation (Bolum 21.8).
     */
    public static Optional<AtomVariant> pick(AtomNode atom, String language, Tone tone) {
        List<AtomVariant> variants = atom.variants();
        if (variants.isEmpty()) {
            return Optional.empty();
        }
        String target = language == null || language.isBlank()
                ? "en" : language.toLowerCase(Locale.ROOT);

        List<AtomVariant> inLanguage = variants.stream()
                .filter(variant -> target.equalsIgnoreCase(variant.getLanguage()))
                .toList();
        List<AtomVariant> pool = inLanguage.isEmpty() ? variants : inLanguage;

        return pool.stream().max(Comparator
                // What the posting was asked for, when the person wrote one.
                .comparing((AtomVariant variant) -> tone != null && tone == variant.getTone())
                // Then the wording the person calls the real one.
                .thenComparing(AtomVariant::isPrimary)
                // And last, something total, so two runs cannot disagree.
                .thenComparing(variant -> variant.getId().toString()));
    }
}
