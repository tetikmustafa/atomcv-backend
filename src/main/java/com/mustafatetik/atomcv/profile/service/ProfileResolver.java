package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.repository.ProfileRepository;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place a {@link UserContext} becomes a {@link ProfileRef}.
 *
 * <p>Everything below a profile is scoped by profile id, and that is only safe
 * because the id can be traced back to an ownership check. Keeping the mapping
 * here means there is one line to audit rather than one per endpoint.
 */
@Service
public class ProfileResolver {

    private final ProfileRepository profiles;

    ProfileResolver(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    /**
     * Resolves the acting user's profile, creating it on first use.
     *
     * <p>{@code profiles.user_id} is unique, so a user has exactly one profile
     * and its absence is not a failure — it means the account is new. Answering
     * 404 instead would make every client handle "you have no profile yet" as
     * an error state on the way to creating the same empty row.
     */
    @Transactional
    public ProfileRef resolve(UserContext user) {
        Profile profile = profiles.findOwn(user)
                .orElseGet(() -> profiles.save(user, new Profile(user.userId())));
        return ProfileRef.persistent(user, profile.getId(), profile.getOwnerId());
    }
}
