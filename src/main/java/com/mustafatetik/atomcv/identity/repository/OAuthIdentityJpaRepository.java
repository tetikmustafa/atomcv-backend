package com.mustafatetik.atomcv.identity.repository;

import com.mustafatetik.atomcv.identity.domain.OAuthIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private, like every other Spring Data interface here. */
interface OAuthIdentityJpaRepository extends JpaRepository<OAuthIdentity, UUID> {

    Optional<OAuthIdentity> findByProviderAndProviderUid(String provider, String providerUid);
}
