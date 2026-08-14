package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.repository.ProfileRepository;
import com.mustafatetik.atomcv.shared.security.UserContext;
import com.mustafatetik.atomcv.shared.util.EntityTags;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes to the profile head.
 *
 * <p>Reading and scoping stay in {@link ProfileResolver}: that answers "whose
 * profile", this answers "what changed". Both go through the same user-scoped
 * repository.
 */
@Service
public class ProfileService {

    private final ProfileResolver resolver;
    private final ProfileRepository profiles;

    ProfileService(ProfileResolver resolver, ProfileRepository profiles) {
        this.resolver = resolver;
        this.profiles = profiles;
    }

    /**
     * Replaces the head. The precondition is checked inside the transaction
     * that writes, so nothing can slip between the check and the save — and the
     * version column catches a concurrent writer even then.
     */
    @Transactional
    public Profile replace(UserContext user, String ifMatch, ProfileHeadUpdate update) {
        Profile profile = resolver.own(user);
        EntityTags.requireMatch(ifMatch, profile.getVersion());

        profile.setHeadline(update.headline());
        profile.setContact(update.contact());
        profile.setSelfDescription(update.selfDescription());
        if (update.sourceLanguage() != null) {
            profile.setSourceLanguage(update.sourceLanguage());
        }
        profile.setEnabledLanguages(update.enabledLanguages());
        return profiles.save(user, profile);
    }

    /** Preferences are replaced on their own, so a headline edit cannot reset them. */
    @Transactional
    public Profile replacePreferences(UserContext user, String ifMatch, Preferences preferences) {
        Profile profile = resolver.own(user);
        EntityTags.requireMatch(ifMatch, profile.getVersion());

        profile.setPreferences(preferences);
        return profiles.save(user, profile);
    }
}
