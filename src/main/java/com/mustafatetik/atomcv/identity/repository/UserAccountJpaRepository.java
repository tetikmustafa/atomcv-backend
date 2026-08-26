package com.mustafatetik.atomcv.identity.repository;

import com.mustafatetik.atomcv.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Package-private, like every other Spring Data interface here. */
interface UserAccountJpaRepository extends JpaRepository<UserAccount, UUID> {

    /**
     * The cast is not decoration, and a derived {@code findByEmail} is wrong
     * here.
     *
     * <p>{@code users.email} is {@code CITEXT}, but JDBC binds the parameter
     * as {@code varchar}, and Postgres resolves {@code citext = varchar} by
     * casting the citext down to {@code text} — a <strong>case-sensitive</strong>
     * comparison. The unique index still behaves case-insensitively, so the
     * two halves disagree: the lookup misses an account that exists, sign-in
     * decides this is a new person, and the insert dies on
     * {@code users_email_key} with a 500. Casting the parameter up to
     * {@code citext} instead picks the case-insensitive operator and uses that
     * same index.
     *
     * <p>{@code OAuthApiIT} signs in twice with an address differing only in
     * case, which is what found this.
     */
    @Query(value = "SELECT * FROM users WHERE email = CAST(:email AS citext)",
            nativeQuery = true)
    Optional<UserAccount> findByEmailIgnoringCase(@Param("email") String email);
}
