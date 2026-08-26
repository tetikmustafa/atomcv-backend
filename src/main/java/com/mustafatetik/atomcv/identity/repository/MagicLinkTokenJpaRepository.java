package com.mustafatetik.atomcv.identity.repository;

import com.mustafatetik.atomcv.identity.domain.MagicLinkToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private, like every other Spring Data interface here. */
interface MagicLinkTokenJpaRepository extends JpaRepository<MagicLinkToken, UUID> {

    Optional<MagicLinkToken> findBySelector(String selector);

    /**
     * Redemption, as one statement.
     *
     * <p>Reading {@code used_at} and then writing it would let two requests
     * arriving together both see an unused token and both sign in — a race
     * that never shows up in testing and hands a second session to whoever
     * else has the link. The {@code WHERE used_at IS NULL} makes the database
     * settle it, and the row count says who won.
     *
     * @return 1 for the request that redeemed it, 0 for every other
     */
    @Modifying
    @Query("update MagicLinkToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Every other outstanding link for this user, spent at once.
     *
     * <p>Bolum 40.2 does not ask for it. It closes the window anyway: someone
     * who requested a link to an address they do not own leaves a live token
     * behind, and signing in is the moment the real owner can be sure nothing
     * of theirs is still redeemable.
     */
    @Modifying
    @Query("update MagicLinkToken t set t.usedAt = :now "
            + "where t.userId = :userId and t.usedAt is null")
    int spendOutstandingFor(@Param("userId") UUID userId, @Param("now") Instant now);
}
