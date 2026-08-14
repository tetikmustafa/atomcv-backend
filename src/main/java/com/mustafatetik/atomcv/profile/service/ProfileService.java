package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.repository.ProfileRepository;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
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
    private final ProfileAssembler assembler;

    ProfileService(ProfileResolver resolver, ProfileRepository profiles,
            ProfileAssembler assembler) {
        this.resolver = resolver;
        this.profiles = profiles;
        this.assembler = assembler;
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

    /**
     * The head with a fresh completeness figure.
     *
     * <p>Recomputed on read rather than on every write. The formula counts
     * across the whole profile (Bolum 31.9), so a write would have to load it
     * all to update one number, and every section, entry and atom endpoint
     * would carry that cost. Reading is where the figure is looked at, and the
     * stored column exists for the preflight gate in Stage 2 — so it is
     * written back only when it actually moved.
     */
    @Transactional
    public Profile readOwn(UserContext user) {
        Profile profile = resolver.own(user);
        ProfileRef reference = ProfileRef.persistent(user, profile.getId(), profile.getOwnerId());

        short completeness = CompletenessCalculator.of(profile, assembler.load(reference));
        if (completeness != profile.getCompleteness()) {
            profile.setCompleteness(completeness);
            return profiles.save(user, profile);
        }
        return profile;
    }

    /**
     * Deletes the profile and everything under it.
     *
     * <p>The account survives: a user without a profile is a user who has not
     * started one, and the next read gives them an empty one. Requires
     * If-Match, because this is the one call that cannot be undone.
     */
    @Transactional
    public void delete(UserContext user, String ifMatch) {
        Profile profile = resolver.own(user);
        EntityTags.requireMatch(ifMatch, profile.getVersion());
        profiles.delete(user, profile);
    }
}
