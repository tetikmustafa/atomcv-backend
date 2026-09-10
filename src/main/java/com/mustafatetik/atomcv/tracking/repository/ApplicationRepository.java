package com.mustafatetik.atomcv.tracking.repository;

import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.security.UserScopedRepository;
import com.mustafatetik.atomcv.tracking.domain.Application;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Applications, scoped to whoever is asking (absolute rule 3).
 *
 * <p>Every row here has a `user_id`, so this is a {@link UserScopedRepository}
 * and there is nothing to argue about: an application id reaches a browser in
 * the list and again in every edit, which makes it exactly the identifier that
 * rule exists for.
 */
@Repository
public class ApplicationRepository extends UserScopedRepository<Application> {

    private final ApplicationJpaRepository jpa;

    ApplicationRepository(ApplicationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<Application, UUID> delegate() {
        return jpa;
    }

    /**
     * The whole history, newest first.
     *
     * <p>Not paginated, unlike generations. A person applies to tens of jobs
     * and not thousands, the screen is a table they scan rather than a feed
     * they scroll, and a cursor would be machinery for a page that does not
     * exist. When somebody has enough rows for it to matter, they will also
     * want filtering by status, and the two arrive together.
     */
    public List<Application> findAll(UserContext user) {
        return jpa.findByUserIdOrderByAppliedAtDescIdDesc(user.userId());
    }

    public long countFor(UserContext user) {
        return jpa.countByUserId(user.userId());
    }
}
