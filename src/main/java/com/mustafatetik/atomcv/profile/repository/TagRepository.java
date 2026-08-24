package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Tag;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.ProfileScopedRepository;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** The tag vocabulary of one profile, and which atoms wear which of it. */
@Repository
public class TagRepository extends ProfileScopedRepository<Tag> {

    private final TagJpaRepository jpa;

    TagRepository(TagJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<Tag, UUID> delegate() {
        return jpa;
    }

    public List<Tag> findAll(ProfileRef profile) {
        return jpa.findByProfileIdOrderByLabelAsc(profile.id());
    }

    /**
     * What Faz B reads: every tagged atom in the profile, with its labels.
     *
     * <p>One query for the whole profile rather than one per atom, and the
     * fifth query a generation makes — {@link
     * com.mustafatetik.atomcv.profile.service.ProfileAssembler} still loads the
     * tree in four (Bolum 52.2). Tags are a scoring input, not part of what is
     * rendered, so general mode never pays for this one.
     *
     * <p>The query's order is kept all the way out. {@code Set.copyOf} and
     * {@code Map.copyOf} would <em>not</em> keep it: the JDK's immutable
     * collections iterate in an order salted per JVM run, so the same profile
     * came back in one order here and another on the CI runner — which is how
     * this was found. {@code UserFacingError} avoids the same trap for the
     * same reason.
     *
     * @return atoms that have at least one tag; an untagged atom is absent
     *         rather than present with an empty set, and the caller reads a
     *         missing key as "no tags"
     */
    public Map<UUID, Set<String>> labelsByAtom(ProfileRef profile) {
        Map<UUID, Set<String>> labels = new LinkedHashMap<>();
        for (AtomTagLabel row : jpa.findLabelsByProfileId(profile.id())) {
            labels.computeIfAbsent(row.atomId(), atom -> new LinkedHashSet<>())
                    .add(row.label());
        }
        labels.replaceAll((atom, set) -> Collections.unmodifiableSet(set));
        return Collections.unmodifiableMap(labels);
    }
}
