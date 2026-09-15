package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.VariantAuthor;
import com.mustafatetik.atomcv.profile.domain.content.RunMarking;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write half of a translation, and the reason it is a bean of its own.
 *
 * <p><strong>A provider call must not happen inside a transaction.</strong>
 * {@code VariantTranslationService} held one across the call until this
 * existed, which was survivable while translations were a queue of one job at
 * a time and stopped being survivable the moment Bolum 21.8's second step
 * started asking for sixty wordings at once: the pool is ten connections, a
 * translation takes up to thirty seconds, and fifty virtual threads would have
 * been waiting on a connection for a call that had not started. Every one of
 * them would then have failed, and the all-or-nothing rule would have sent
 * every multilingual generation back to the profile's own language.
 *
 * <p>So the transaction is here, around the write and nothing else — and it
 * has to be a separate bean, because a {@code @Transactional} method a class
 * calls on itself is not proxied and would have kept the behaviour while
 * looking like it had changed.
 */
@Service
public class TranslationWriter {

    private static final Logger log = LoggerFactory.getLogger(TranslationWriter.class);

    private final AtomVariantRepository variants;

    TranslationWriter(AtomVariantRepository variants) {
        this.variants = variants;
    }

    /**
     * Stores a translated wording, or refuses it.
     *
     * <p>Bolum 21.8's fourth step is the refusal: a translation that dropped a
     * number or a proper noun is not saved, and the caller is told. A missing
     * wording is better than a wrong one — the whole point of a CV built from
     * a structured profile is that the numbers in it are the user's own.
     *
     * @param target what to write into: the stale wording for a retranslation,
     *               a wording that does not exist yet for a new language
     * @param source what it was made from, which becomes its {@code derivedFrom}
     *               and is how Bolum 32.2 knows when it goes stale
     */
    @Transactional
    public Result<AtomVariant> store(ProfileRef profile, AtomVariant target,
            AtomVariant source, Atom atom, AtomTranslation translated) {

        List<String> lost = TranslationAudit.missingFrom(
                atom.getMetrics(), atom.getProperNouns(), translated.text());
        if (!lost.isEmpty()) {
            // The count and not the values: what was dropped is the user's own
            // content (absolute rule 4).
            log.warn("A translation dropped {} thing(s) it had to keep", lost.size());
            return Result.err(new PipelineError.TranslationRejected());
        }

        target.setContent(RunMarking.mark(translated.text(), translated.emphasis(),
                atom.getSkills(), atom.getMetrics()));
        target.setCreatedBy(VariantAuthor.LLM_TRANSLATE);
        // Derived-from records what it was made from, and clears the flag. For
        // a pivot translation that is the English wording rather than the
        // original, which is exactly right: the German went stale when the
        // English it was made from did.
        target.markDerivedFrom(source);
        return Result.ok(variants.save(profile, target));
    }
}
