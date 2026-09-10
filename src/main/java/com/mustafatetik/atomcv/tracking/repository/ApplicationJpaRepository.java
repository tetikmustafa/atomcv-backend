package com.mustafatetik.atomcv.tracking.repository;

import com.mustafatetik.atomcv.tracking.domain.Application;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private; reached through {@link ApplicationRepository}. */
interface ApplicationJpaRepository extends JpaRepository<Application, UUID> {

    /**
     * Newest first, ties broken by id.
     *
     * <p>`applied_at` is a date, so several applications on one day is the
     * ordinary case rather than a rare one -- somebody works through a list of
     * companies in an afternoon. Ordering on the date alone would leave their
     * order to the database and shuffle the screen between two reads.
     */
    List<Application> findByUserIdOrderByAppliedAtDescIdDesc(UUID userId);

    long countByUserId(UUID userId);
}
