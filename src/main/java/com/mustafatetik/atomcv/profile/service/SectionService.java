package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Section;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The headings of a profile: their content, their order and their locks. */
@Service
public class SectionService {

    private final SectionRepository sections;

    SectionService(SectionRepository sections) {
        this.sections = sections;
    }

    @Transactional(readOnly = true)
    public List<Section> list(ProfileRef profile) {
        return sections.findAll(profile);
    }

    /**
     * Appends. Where a new section belongs is a decision about the whole list,
     * and the client makes it with a reorder rather than by guessing an index
     * that another tab may already have taken.
     */
    @Transactional
    public Section create(ProfileRef profile, SectionDraft draft) {
        short next = (short) sections.findAll(profile).stream()
                .mapToInt(Section::getDisplayOrder)
                .map(order -> order + 1)
                .max()
                .orElse(0);

        var section = new Section(profile.id(), draft.kind(), draft.title(), next);
        section.setLayout(draft.layout());
        section.setAlwaysInclude(draft.alwaysInclude());
        section.setVerbatim(draft.verbatim());
        return sections.save(profile, section);
    }

    @Transactional
    public Section patch(ProfileRef profile, UUID id, String ifMatch, SectionPatch patch) {
        Section section = require(profile, id);
        EntityTags.requireMatch(ifMatch, section.getVersion());

        if (patch.kind() != null) {
            section.setKind(patch.kind());
        }
        if (patch.title() != null) {
            section.setTitle(patch.title());
        }
        if (patch.layout() != null) {
            section.setLayout(patch.layout());
        }
        if (patch.alwaysInclude() != null) {
            section.setAlwaysInclude(patch.alwaysInclude());
        }
        if (patch.verbatim() != null) {
            section.setVerbatim(patch.verbatim());
        }
        if (patch.active() != null) {
            section.setActive(patch.active());
        }
        return sections.save(profile, section);
    }

    /**
     * Deleting a section takes its entries, atoms and variants with it — the
     * database cascades. Nothing here softens that: an explicit delete is the
     * user's decision, and hiding the consequence would be the surprise.
     */
    @Transactional
    public void delete(ProfileRef profile, UUID id, String ifMatch) {
        Section section = require(profile, id);
        EntityTags.requireMatch(ifMatch, section.getVersion());
        sections.delete(profile, section);
    }

    /**
     * Sets the order from a complete list of ids.
     *
     * <p>Complete, because a partial one leaves the rest of the list to be
     * guessed, and two clients guessing differently is how two rows end up
     * claiming the same position.
     *
     * <p>No precondition: the request already carries the caller's whole view
     * of the order, so applying it wholesale is what "put them in this order"
     * means. A stale ordering loses positions, not content.
     */
    @Transactional
    public List<Section> reorder(ProfileRef profile, List<UUID> ids) {
        List<Section> current = sections.findAll(profile);
        Set<UUID> requested = new HashSet<>(ids);

        if (requested.size() != ids.size()) {
            throw invalid("ids");
        }
        Set<UUID> existing = current.stream().map(Section::getId).collect(Collectors.toSet());
        if (!requested.equals(existing)) {
            throw invalid("ids");
        }

        for (Section section : current) {
            section.setDisplayOrder((short) ids.indexOf(section.getId()));
            sections.save(profile, section);
        }
        return sections.findAll(profile);
    }

    private Section require(ProfileRef profile, UUID id) {
        return sections.findById(profile, id)
                .orElseThrow(() -> ApiException.of(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static ApiException invalid(String field) {
        return new ApiException(UserFacingError.with(ErrorCode.VALIDATION_FAILED)
                .param("fields", List.of(field))
                .build());
    }
}
