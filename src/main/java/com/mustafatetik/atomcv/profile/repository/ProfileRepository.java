package com.mustafatetik.atomcv.profile.repository;


import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.security.UserScopedRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The one profile a user owns. {@code profiles.user_id} is unique, so this is
 * the whole of the finder surface.
 */
@Repository
public class ProfileRepository extends UserScopedRepository<Profile> {

    private final ProfileJpaRepository jpa;

    ProfileRepository(ProfileJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    protected JpaRepository<Profile, UUID> delegate() {
        return jpa;
    }

    /**
     * The query is already narrowed by owner, so it needs no filtering after
     * the fact — which is what a scoped subclass otherwise has to do.
     */
    public Optional<Profile> findOwn(UserContext user) {
        return jpa.findByUserId(user.userId());
    }

    /**
     * The user's profile, created if they have none — safely when several
     * requests ask at once ({@link ProfileJpaRepository#insertIfAbsent}).
     */
    public Profile findOrCreateOwn(UserContext user) {
        return findOwn(user).orElseGet(() -> {
            jpa.insertIfAbsent(user.userId());
            return findOwn(user).orElseThrow(() -> new IllegalStateException(
                    "A profile was inserted for this user and cannot be read back"));
        });
    }
}
