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
     * The acting user's profile, created on first use.
     *
     * <p>{@code profiles.user_id} is unique, so a user has exactly one profile
     * and its absence is not a failure — it means the account is new. Answering
     * 404 instead would make every client handle "you have no profile yet" as
     * an error state on the way to creating the same empty row.
     */
    @Transactional
    public Profile own(UserContext user) {
        return profiles.findOwn(user)
                .orElseGet(() -> profiles.save(user, new Profile(user.userId())));
    }

    /** The same profile, as the scope everything below it is read within. */
    @Transactional
    public ProfileRef resolve(UserContext user) {
        return owned(user).ref();
    }

    /**
     * Both at once, for a caller that needs the profile's own fields as well
     * as the scope below it — a generation needs the header and the
     * preferences, and reading the row twice to get them would be waste.
     */
    @Transactional
    public OwnedProfile owned(UserContext user) {
        Profile profile = own(user);
        return new OwnedProfile(profile,
                ProfileRef.persistent(user, profile.getId(), profile.getOwnerId()));
    }

    /** A profile and the scope it is read within, resolved together. */
    public record OwnedProfile(Profile profile, ProfileRef ref) {
    }
}
