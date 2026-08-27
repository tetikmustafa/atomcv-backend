package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.embedding.EmbeddingProvider;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every atom's vector, brought up to date (Bolum 28.2).
 *
 * <p><strong>From the English wording and nothing else.</strong> A similarity
 * between a Turkish sentence and an English one measures the languages rather
 * than the match, so Bolum 28 fixes one language for the whole comparison and
 * Bolum 31.4 makes the model produce it in the same call that reads the CV.
 * An atom with no English wording is skipped rather than embedded from its
 * source: a vector in the wrong space is worse than no vector, because scoring
 * would use it.
 *
 * <p><strong>Compared by content hash, never by timestamp.</strong> An edit
 * that put a sentence back the way it was leaves the hash unchanged, and
 * re-embedding that is work bought for nothing.
 *
 * <p>One round trip for the batch. Bolum 28's own note says why: a profile is
 * embedded atom by atom after an import, and one HTTP call per atom is the
 * difference between a second and a minute.
 */
@Service
public class AtomEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(AtomEmbeddingService.class);

    /** Bolum 28's fixed comparison language. */
    private static final String ENGLISH = "en";

    private final AtomRepository atoms;
    private final AtomVariantRepository variants;
    private final EmbeddingProvider embeddings;

    AtomEmbeddingService(AtomRepository atoms, AtomVariantRepository variants,
            EmbeddingProvider embeddings) {
        this.atoms = atoms;
        this.variants = variants;
        this.embeddings = embeddings;
    }

    /**
     * @return how many atoms were given a vector
     */
    @Transactional
    public int embedMissing(ProfileRef profile) {
        Map<UUID, AtomVariant> englishByAtom = new HashMap<>();
        for (AtomVariant variant : variants.findAll(profile)) {
            if (ENGLISH.equals(variant.getLanguage())) {
                englishByAtom.put(variant.getAtomId(), variant);
            }
        }

        List<Atom> pending = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (Atom atom : atoms.findAll(profile)) {
            AtomVariant english = englishByAtom.get(atom.getId());
            if (english == null || english.getContent().isEmpty()) {
                continue;
            }
            if (atom.needsEmbedding(english.getContentHash())) {
                pending.add(atom);
                texts.add(english.getPlainText());
            }
        }
        if (pending.isEmpty()) {
            return 0;
        }

        List<float[]> vectors = embeddings.embedBatch(texts);
        if (vectors.size() != pending.size()) {
            // A provider that answered a different number of vectors has not
            // answered this question at all, and pairing them by position
            // would give some atom somebody else's meaning.
            throw new IllegalStateException("The embedding provider returned "
                    + vectors.size() + " vectors for " + pending.size() + " atoms");
        }

        for (int i = 0; i < pending.size(); i++) {
            Atom atom = pending.get(i);
            atom.setEmbedding(vectors.get(i),
                    englishByAtom.get(atom.getId()).getContentHash());
            atoms.save(profile, atom);
        }
        // A count, never a sentence (absolute rule 4).
        log.info("Embedded {} atoms", pending.size());
        return pending.size();
    }
}
