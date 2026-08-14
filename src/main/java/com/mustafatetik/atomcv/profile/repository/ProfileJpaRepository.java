package com.mustafatetik.atomcv.profile.repository;

import com.mustafatetik.atomcv.profile.domain.Profile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private; reached through {@link ProfileRepository}. */
interface ProfileJpaRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUserId(UUID userId);
}
