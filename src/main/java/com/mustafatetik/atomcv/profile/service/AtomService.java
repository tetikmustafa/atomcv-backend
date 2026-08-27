package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.profile.repository.EntryRepository;
import com.mustafatetik.atomcv.profile.repository.SectionRepository;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.util.EntityTags;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atoms and their wordings.
 *
 * <p>An atom and its variants are two resources with two versions, but one
 * thing to a user: creating an atom writes its first wording in the same
 * transaction, because a fact nobody can read is not worth a row.
 */
@Service
public class AtomService {

    /** Primary first, then by language, then by tone — never insertion order. */
    private static final Comparator<AtomVariant> VARIANT_ORDER =
            Comparator.comparing(AtomVariant::isPrimary, Comparator.reverseOrder())
                    .thenComparing(AtomVariant::getLanguage)
                    .thenComparing(variant -> variant.getTone() == null ? "" : variant.getTone().name())
                    .thenComparing(variant -> variant.getId().toString());

    private final AtomRepository atoms;
    private final AtomVariantRepository variants;
    private final SectionRepository sections;
    private final EntryRepository entries;
    private final VariantSynchronization synchronization;

    AtomService(AtomRepository atoms, AtomVariantRepository variants,
            SectionRepository sections, EntryRepository entries,
            VariantSynchronization synchronization) {
        this.atoms = atoms;
        this.variants = variants;
        this.sections = sections;
        this.entries = entries;
        this.synchronization = synchronization;
    }

    @Transactional(readOnly = true)
    public List<Atom> list(ProfileRef profile, UUID sectionId, UUID entryId) {
        return atoms.findAll(profile).stream()
                .filter(atom -> sectionId == null || atom.getSectionId().equals(sectionId))
                .filter(atom -> entryId == null || entryId.equals(atom.getEntryId()))
                .toList();
    }

    /** Every wording in the profile, grouped by atom — one query, not one per atom. */
    @Transactional(readOnly = true)
    public Map<UUID, List<AtomVariant>> variantsByAtom(ProfileRef profile) {
        return variants.findAll(profile).stream()
                .sorted(VARIANT_ORDER)
                .collect(Collectors.groupingBy(AtomVariant::getAtomId));
    }

    @Transactional
    public Atom create(ProfileRef profile, AtomDraft draft) {
        sections.findById(profile, draft.sectionId()).orElseThrow(() -> invalid("sectionId"));
        if (draft.entryId() != null) {
            var entry = entries.findById(profile, draft.entryId())
                    .orElseThrow(() -> invalid("entryId"));
            // The composite foreign key would refuse this anyway; saying so
            // here turns a constraint violation into an answer.
            if (!entry.getSectionId().equals(draft.sectionId())) {
                throw invalid("entryId");
            }
        }

        short next = (short) list(profile, draft.sectionId(), draft.entryId()).stream()
                .mapToInt(Atom::getDisplayOrder)
                .map(order -> order + 1)
                .max()
                .orElse(0);

        var atom = new Atom(profile.id(), draft.sectionId(), draft.entryId(), draft.kind(), next);
        atom.setImportance(draft.importance());
        atom.setAlwaysInclude(draft.alwaysInclude());
        atom.setVerbatim(draft.verbatim());
        atom.setSkills(draft.skills());
        atom.setMetrics(draft.metrics());
        atom.setProperNouns(draft.properNouns());
        Atom saved = atoms.save(profile, atom);

        var variant = new AtomVariant(profile.id(), saved.getId(), draft.language(), draft.content());
        variant.setPrimary(true);
        variants.save(profile, variant);
        return saved;
    }

    @Transactional
    public Atom patch(ProfileRef profile, UUID id, String ifMatch, AtomPatch patch) {
        Atom atom = requireAtom(profile, id);
        EntityTags.requireMatch(ifMatch, atom.getVersion());

        if (patch.kind() != null) {
            atom.setKind(patch.kind());
        }
        if (patch.importance() != null) {
            atom.setImportance(patch.importance());
        }
        if (patch.active() != null) {
            atom.setActive(patch.active());
        }
        if (patch.alwaysInclude() != null) {
            atom.setAlwaysInclude(patch.alwaysInclude());
        }
        if (patch.verbatim() != null) {
            atom.setVerbatim(patch.verbatim());
        }
        if (patch.verified() != null) {
            atom.setVerified(patch.verified());
        }
        if (patch.skills() != null) {
            atom.setSkills(patch.skills());
        }
        if (patch.metrics() != null) {
            atom.setMetrics(patch.metrics());
        }
        if (patch.properNouns() != null) {
            atom.setProperNouns(patch.properNouns());
        }
        return atoms.save(profile, atom);
    }

    @Transactional
    public void delete(ProfileRef profile, UUID id, String ifMatch) {
        Atom atom = requireAtom(profile, id);
        EntityTags.requireMatch(ifMatch, atom.getVersion());
        atoms.delete(profile, atom);
    }

    @Transactional
    public List<Atom> reorder(ProfileRef profile, UUID sectionId, UUID entryId, List<UUID> ids) {
        List<Atom> current = list(profile, sectionId, entryId);
        Set<UUID> requested = new HashSet<>(ids);

        if (requested.size() != ids.size() || current.isEmpty()) {
            throw invalid("ids");
        }
        if (!requested.equals(current.stream().map(Atom::getId).collect(Collectors.toSet()))) {
            throw invalid("ids");
        }

        for (Atom atom : current) {
            atom.setDisplayOrder((short) ids.indexOf(atom.getId()));
            atoms.save(profile, atom);
        }
        return list(profile, sectionId, entryId);
    }

    // ── wordings ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AtomVariant> variantsOf(ProfileRef profile, UUID atomId) {
        requireAtom(profile, atomId);
        return variants.findAll(profile).stream()
                .filter(variant -> variant.getAtomId().equals(atomId))
                .sorted(VARIANT_ORDER)
                .toList();
    }

    @Transactional
    public AtomVariant addVariant(ProfileRef profile, UUID atomId, VariantDraft draft) {
        requireAtom(profile, atomId);
        String language = draft.language() == null ? "en" : draft.language();
        requireFreeSlot(profile, atomId, language, draft.tone(), null);

        var variant = new AtomVariant(profile.id(), atomId, language, draft.content());
        variant.setTone(draft.tone());
        if (Boolean.TRUE.equals(draft.primary())) {
            variants.clearPrimary(profile, atomId);
            variant.setPrimary(true);
        }
        return variants.save(profile, variant);
    }

    /**
     * Changes only what the patch carries. An omitted field is left alone —
     * including {@code content}, so making a wording the default costs one
     * boolean rather than the whole sentence.
     */
    @Transactional
    /**
     * @param user the acting user, and not the same thing as {@code profile}.
     *             The rows are scoped by profile (absolute rule 3); a job is
     *             owned by a user, because that is what the queue claims and
     *             authorises by. They are one person here and two concepts
     *             everywhere, so both travel rather than one being derived
     *             from the other.
     */
    public AtomVariant patchVariant(ProfileRef profile, UserContext user, UUID atomId,
            UUID variantId, String ifMatch, VariantPatch patch) {

        AtomVariant variant = requireVariant(profile, atomId, variantId);
        EntityTags.requireMatch(ifMatch, variant.getVersion());

        String language = patch.language() == null ? variant.getLanguage() : patch.language();
        Tone tone = patch.tone() == null || !patch.tone().isPresent()
                ? variant.getTone()
                : patch.tone().get();
        requireFreeSlot(profile, atomId, language, tone, variantId);

        if (patch.content() != null) {
            // setContent re-derives the plain text and the hash, and drops the
            // measured render costs when the words actually changed.
            variant.setContent(patch.content());
            // Only a write that carries words makes a wording the user's own.
            // Setting this on a promote would tell Stage 2's translation job
            // that a human wrote a sentence nobody touched (P8).
            variant.setUserEdited(true);
        }
        variant.setLanguage(language);
        variant.setTone(tone);
        if (Boolean.TRUE.equals(patch.primary()) && !variant.isPrimary()) {
            variants.clearPrimary(profile, atomId);
            variant.setPrimary(true);
        }
        if (Boolean.FALSE.equals(patch.userEdited())) {
            // Bolum 32.2's "regenerate the English": the person is handing the
            // wording back. Queued here rather than left for the next edit of
            // the source, because the source may not be edited again for
            // months and the wording is stale now.
            variant.setUserEdited(false);
        }
        AtomVariant saved = variants.save(profile, variant);
        if (Boolean.FALSE.equals(patch.userEdited()) && saved.isStale()) {
            synchronization.regenerate(profile, user, saved);
        }
        if (patch.content() != null) {
            // Bolum 32.2, and only when the words moved. A promote or a tone
            // change leaves every translation of this wording still accurate.
            synchronization.afterEdit(profile, user, saved);
        }
        return saved;
    }

    /**
     * An atom must keep a wording, and it must keep a primary one. Both
     * refusals leave the client something to do — promote another wording, or
     * delete the atom — rather than leaving an unreadable atom behind.
     */
    @Transactional
    public void deleteVariant(ProfileRef profile, UUID atomId, UUID variantId, String ifMatch) {
        AtomVariant variant = requireVariant(profile, atomId, variantId);
        EntityTags.requireMatch(ifMatch, variant.getVersion());

        long remaining = variantsOf(profile, atomId).size() - 1;
        if (remaining == 0) {
            throw invalid("variantId");
        }
        if (variant.isPrimary()) {
            throw invalid("primary");
        }
        variants.delete(profile, variant);
    }

    private void requireFreeSlot(ProfileRef profile, UUID atomId, String language,
            Tone tone, UUID selfId) {
        // A unique index allows one wording per (atom, language, tone).
        // Answering here beats letting the constraint surface as a 500.
        boolean taken = variantsOf(profile, atomId).stream()
                .filter(existing -> !existing.getId().equals(selfId))
                .anyMatch(existing -> existing.getLanguage().equals(language)
                        && Objects.equals(existing.getTone(), tone));
        if (taken) {
            throw invalid("language");
        }
    }

    private Atom requireAtom(ProfileRef profile, UUID id) {
        return atoms.findById(profile, id)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private AtomVariant requireVariant(ProfileRef profile, UUID atomId, UUID variantId) {
        AtomVariant variant = variants.findById(profile, variantId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));
        if (!variant.getAtomId().equals(atomId)) {
            throw ApiException.of(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return variant;
    }

    private static ApiException invalid(String field) {
        return new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                .param("fields", List.of(field))
                .build());
    }
}
