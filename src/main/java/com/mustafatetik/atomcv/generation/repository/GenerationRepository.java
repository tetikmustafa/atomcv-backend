package com.mustafatetik.atomcv.generation.repository;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.security.UserScopedRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * A user's own generations (Bolum 41.2).
 *
 * <p>The generation id reaches a browser twice — in the job's terminal event
 * and in the download link — so every read here is scoped. Absolute rule 3.
 */
@Repository
public class GenerationRepository extends UserScopedRepository<Generation> {

    private final GenerationJpaRepository jpa;

    GenerationRepository(GenerationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<Generation, UUID> delegate() {
        return jpa;
    }

    /**
     * Newest first, ties broken by id.
     *
     * <p>Two generations of the same profile a second apart is ordinary — Faz
     * G's edit loop does exactly that — and {@code created_at} alone leaves
     * their order to the database. EK D.8.7's cursor pagination arrives with
     * the listing endpoint; the limit is here so nothing accidentally loads a
     * year of history to show ten rows.
     */
    public List<Generation> findRecent(UserContext user, int limit) {
        return jpa.findByUserIdOrderByCreatedAtDescIdDesc(user.userId(), Limit.of(limit));
    }

    /**
     * One page of history, resuming after {@code cursor} when there is one
     * (F-020, EK D.8.7).
     *
     * <p>Asks for one row more than it will hand back, and that extra row is
     * the whole of "is there a next page". Counting instead would be a second
     * query answering a question this one already knows, and it would answer it
     * about a different instant.
     */
    public Page findPage(UserContext user, GenerationCursor cursor, int limit) {
        List<Generation> found = cursor == null
                ? jpa.findByUserIdOrderByCreatedAtDescIdDesc(user.userId(), Limit.of(limit + 1))
                : jpa.findPageAfter(user.userId(), cursor.createdAt(), cursor.id(),
                        Limit.of(limit + 1));

        boolean more = found.size() > limit;
        List<Generation> items = more ? found.subList(0, limit) : found;
        Generation last = items.isEmpty() ? null : items.get(items.size() - 1);

        return new Page(List.copyOf(items),
                more && last != null
                        ? new GenerationCursor(last.getCreatedAt(), last.getId())
                        : null);
    }

    /**
     * How many this person has, all of them.
     *
     * <p>Here rather than derived from a page: the account-deletion screen has
     * to say what goes, and a number that meant "at least this many" in the one
     * irreversible place would be worse than no number (F-020).
     */
    public long countFor(UserContext user) {
        return jpa.countByUserId(user.userId());
    }

    /**
     * @param nextCursor null when this page is the end of the history
     */
    public record Page(List<Generation> items, GenerationCursor nextCursor) {
    }
}
