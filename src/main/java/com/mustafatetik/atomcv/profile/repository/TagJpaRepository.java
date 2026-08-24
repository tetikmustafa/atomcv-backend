package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private; reached through {@link TagRepository}. */
interface TagJpaRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByProfileIdOrderByLabelAsc(UUID profileId);

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
     * an unordered result set is a different input on a different day
     * (Bolum 19.6).
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
