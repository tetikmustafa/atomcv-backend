package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write half of an embedding run, and the reason it is a bean of its own.
 *
 * <p><strong>A provider call must not happen inside a transaction.</strong>
 * {@code AtomEmbeddingService} held one across {@code embedBatch} until this
 * existed: one call carrying every atom of a freshly imported profile, over
 * the network, with a connection from a pool of ten held open for the whole
 * round trip. It ran on a worker rather than a request thread, which is the
 * only reason it was survivable — the fault is the same one
 * {@link TranslationWriter} was split out for, and it is written down twice
 * because it was fixed once and left standing here.
 *
 * <p>So the transaction is here, around the writes and nothing else — and it
 * has to be a separate bean, because a {@code @Transactional} method a class
 * calls on itself is not proxied and would have kept the behaviour while
 * looking like it had changed.
 */
@Service
public class AtomEmbeddingWriter {

    private final AtomRepository atoms;

    AtomEmbeddingWriter(AtomRepository atoms) {
        this.atoms = atoms;
    }

    /**
     * Gives each atom the vector that was measured for it.
     *
     * <p>One transaction for the batch rather than one per atom: a profile
     * half embedded is a profile scored on a mixture of fresh and stale
     * meanings, and the hash written beside each vector is what would make
     * that state look settled.
     *
     * @param pending  the atoms, in the order their texts were sent
     * @param vectors  the answers, in that same order — the caller has already
     *                 refused a reply of a different length, because pairing
     *                 these by position is only meaningful once it has
     * @param hashes   the content hash each atom was embedded from, which is
     *                 what {@code needsEmbedding} reads on the next run
     * @return how many atoms were written
     */
    @Transactional
    public int store(ProfileRef profile, List<Atom> pending, List<float[]> vectors,
            Map<UUID, String> hashes) {

        for (int i = 0; i < pending.size(); i++) {
            Atom atom = pending.get(i);
            atom.setEmbedding(vectors.get(i), hashes.get(atom.getId()));
            atoms.save(profile, atom);
        }
        return pending.size();
    }
}
