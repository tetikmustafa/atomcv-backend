package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.repository.EntryRepository;
import com.mustafatetik.atomcv.profile.repository.SectionRepository;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.util.EntityTags;
import java.time.LocalDate;
import java.util.ArrayList;
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
        // A create carries both ends, so both are named when the pair is
        // reversed: either one is the field the user can correct.
        requireOrderedDates(draft.startDate(), draft.endDate(),
                List.of("startDate", "endDate"));

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

        // Checked against what the entry will hold, not against what the body
        // carries: a patch that moves only one of the two dates is still able
        // to reverse the range, and it is the pair that has to stay ordered.
        //
        // The pair is what is checked, but only the half this request sent is
        // reported (F-005). A patch that moves startDate used to be answered
        // with `fields: ["endDate"]` — a field it never mentioned — and a
        // single-field form has nowhere to put that but the wrong input.
        //
        // A patch touching neither date is not checked at all. It cannot make
        // the range worse, and a row stored reversed before F-002 existed
        // would otherwise refuse an unrelated title edit while naming no field
        // the user could fix.
        List<String> patchedDates = patchedDateFields(patch);
        if (!patchedDates.isEmpty()) {
            requireOrderedDates(
                    isDefined(patch.startDate()) ? patch.startDate().get() : entry.getStartDate(),
                    isDefined(patch.endDate()) ? patch.endDate().get() : entry.getEndDate(),
                    patchedDates);
        }

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

    /**
     * An entry that ends before it starts is accepted by everything
     * downstream: it renders as "Jan 2022 - Jan 2019" and reaches generation
     * that way. Nobody re-reads a date line that looks plausible, so the range
     * has to be refused at the door (F-002). Equal dates are a same-day entry
     * and stay legal; an absent end date means ongoing and has nothing to
     * compare against.
     *
     * <p>{@code fields} is what the caller sent, not what the rule compared:
     * the check needs both ends, the client can only correct the ones it put
     * on screen.
     */
    private static void requireOrderedDates(LocalDate start, LocalDate end, List<String> fields) {
        if (start != null && end != null && end.isBefore(start)) {
            throw invalid(fields);
        }
    }

    /** The date fields this patch carries, in the order the form shows them. */
    private static List<String> patchedDateFields(EntryPatch patch) {
        List<String> fields = new ArrayList<>(2);
        if (isDefined(patch.startDate())) {
            fields.add("startDate");
        }
        if (isDefined(patch.endDate())) {
            fields.add("endDate");
        }
        return List.copyOf(fields);
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
        return invalid(List.of(field));
    }

    private static ApiException invalid(List<String> fields) {
        return new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                .param("fields", fields)
                .build());
    }
}
