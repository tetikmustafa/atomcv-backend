package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.repository.EntryRepository;
import com.mustafatetik.atomcv.profile.repository.SectionRepository;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.util.EntityTags;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Positions, degrees and projects: the rows that group atoms. */
@Service
public class EntryService {

    private final EntryRepository entries;
    private final SectionRepository sections;

    EntryService(EntryRepository entries, SectionRepository sections) {
        this.entries = entries;
        this.sections = sections;
    }

    @Transactional(readOnly = true)
    public List<Entry> list(ProfileRef profile, UUID sectionId) {
        List<Entry> all = entries.findAll(profile);
        return sectionId == null
                ? all
                : all.stream().filter(entry -> entry.getSectionId().equals(sectionId)).toList();
    }

    @Transactional
    public Entry create(ProfileRef profile, EntryDraft draft) {
        // The section is fetched through the scoped repository, so an entry
        // cannot be hung off another profile's section by sending its id.
        sections.findById(profile, draft.sectionId())
                .orElseThrow(() -> invalid("sectionId"));

        short next = (short) list(profile, draft.sectionId()).stream()
                .mapToInt(Entry::getDisplayOrder)
                .map(order -> order + 1)
                .max()
                .orElse(0);

        var entry = new Entry(profile.id(), draft.sectionId(), draft.title(), next);
        entry.setOrganization(draft.organization());
        entry.setLocation(draft.location());
        entry.setStartDate(draft.startDate());
        entry.setEndDate(draft.endDate());
        entry.setUrl(draft.url());
        entry.setImportance(draft.importance());
        entry.setAlwaysInclude(draft.alwaysInclude());
        entry.setVerbatim(draft.verbatim());
        entry.setMinAtoms(draft.minAtoms());
        return entries.save(profile, entry);
    }

    @Transactional
    public Entry patch(ProfileRef profile, UUID id, String ifMatch, EntryPatch patch) {
        Entry entry = require(profile, id);
        EntityTags.requireMatch(ifMatch, entry.getVersion());

        if (patch.title() != null) {
            entry.setTitle(patch.title());
        }
        if (isDefined(patch.organization())) {
            entry.setOrganization(patch.organization().get());
        }
        if (isDefined(patch.location())) {
            entry.setLocation(patch.location().get());
        }
        if (isDefined(patch.startDate())) {
            entry.setStartDate(patch.startDate().get());
        }
        if (isDefined(patch.endDate())) {
            entry.setEndDate(patch.endDate().get());
        }
        if (isDefined(patch.url())) {
            entry.setUrl(patch.url().get());
        }
        if (patch.importance() != null) {
            entry.setImportance(patch.importance());
        }
        if (patch.active() != null) {
            entry.setActive(patch.active());
        }
        if (patch.alwaysInclude() != null) {
            entry.setAlwaysInclude(patch.alwaysInclude());
        }
        if (patch.verbatim() != null) {
            entry.setVerbatim(patch.verbatim());
        }
        if (patch.minAtoms() != null) {
            entry.setMinAtoms(patch.minAtoms());
        }
        return entries.save(profile, entry);
    }

    /** Deleting an entry takes its atoms and their variants with it. */
    @Transactional
    public void delete(ProfileRef profile, UUID id, String ifMatch) {
        Entry entry = require(profile, id);
        EntityTags.requireMatch(ifMatch, entry.getVersion());
        entries.delete(profile, entry);
    }

    /** Same contract as sections: the complete list of one section's entries. */
    @Transactional
    public List<Entry> reorder(ProfileRef profile, UUID sectionId, List<UUID> ids) {
        List<Entry> current = list(profile, sectionId);
        Set<UUID> requested = new HashSet<>(ids);

        if (requested.size() != ids.size()) {
            throw invalid("ids");
        }
        Set<UUID> existing = current.stream().map(Entry::getId).collect(Collectors.toSet());
        if (current.isEmpty() || !requested.equals(existing)) {
            throw invalid("ids");
        }

        for (Entry entry : current) {
            entry.setDisplayOrder((short) ids.indexOf(entry.getId()));
            entries.save(profile, entry);
        }
        return list(profile, sectionId);
    }

    /** Undefined means "leave it alone"; defined — even holding null — is an edit. */
    private static boolean isDefined(JsonNullable<?> field) {
        return field != null && field.isPresent();
    }

    private Entry require(ProfileRef profile, UUID id) {
        return entries.findById(profile, id)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static ApiException invalid(String field) {
        return new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                .param("fields", List.of(field))
                .build());
    }
}
