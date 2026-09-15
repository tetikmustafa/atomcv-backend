package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Tag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private; reached through {@link TagRepository}. */
interface TagJpaRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByProfileIdOrderByLabelAsc(UUID profileId);

    /**
     * The profile's own vocabulary is keyed on the label, so writing a tag is
     * find-or-create rather than insert (the {@code UNIQUE (profile_id,
     * label)}).
     */
    Optional<Tag> findByProfileIdAndLabel(UUID profileId, String label);

    /**
     * Every tag on the atoms of one profile, with the link's own source.
     *
     * <p>{@link #findLabelsByProfileId} answers Faz B, which wants labels and
     * nothing else; this answers the editor, which has to name a tag to remove
     * it and has to show whether a person put it there or the extraction did.
     *
     * <p>Ordered for the same reason: an unordered result set is a different
     * response on a different day, and the editor draws the list in the order
     * it arrives.
     */
    @Query("""
            select new com.mustafatetik.atomcv.profile.repository.AtomTagRow(
                    link.atomId, tag.id, tag.label, link.source)
            from AtomTag link
            join Tag tag on tag.id = link.tagId
            where tag.profileId = :profileId
            order by link.atomId, tag.label
            """)
    List<AtomTagRow> findRowsByProfileId(@Param("profileId") UUID profileId);

    /**
     * Every (atom, label) pair in one profile, in one query.
     *
     * <p>The join is the scope. {@code atom_tags} carries no {@code
     * profile_id}, so {@code tags.profile_id} is the only thing that says
     * whose row this is — a query over {@code atom_tags} alone would return
     * another profile's tags for an atom id that happened to be guessed
     * (absolute rule 3).
     *
     * <p>Ordered, because Faz B's determinism is a property of its inputs and
     * an unordered result set is a different input on a different day.
     */
    @Query("""
            select new com.mustafatetik.atomcv.profile.repository.AtomTagLabel(
                    link.atomId, tag.label)
            from AtomTag link
            join Tag tag on tag.id = link.tagId
            where tag.profileId = :profileId
            order by link.atomId, tag.label
            """)
    List<AtomTagLabel> findLabelsByProfileId(@Param("profileId") UUID profileId);
}
