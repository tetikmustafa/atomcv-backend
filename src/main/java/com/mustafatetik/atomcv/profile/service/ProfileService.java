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
     *
     * <p>Every field is written unconditionally, {@code sourceLanguage}
     * included (F-004). It used to be the one exception: omitted, it kept its
     * stored value while the five around it were cleared, so the same request
     * was a replace for most of the head and a merge for one field of it. The
     * column is {@code NOT NULL}, so there is no null to clear it to and
     * falling back to the default would silently turn a Turkish-authored
     * profile into an English one — the field is required in the body instead,
     * and omitting it is a 400 rather than a silent keep.
     */
    @Transactional
    public Profile replace(UserContext user, String ifMatch, ProfileHeadUpdate update) {
        Profile profile = resolver.own(user);
        EntityTags.requireMatch(ifMatch, profile.getVersion());

        profile.setHeadline(update.headline());
        profile.setContact(update.contact());
        profile.setSelfDescription(update.selfDescription());
        profile.setSourceLanguage(update.sourceLanguage());
        profile.setEnabledLanguages(update.enabledLanguages());
        return saveWithCompleteness(user, profile);
    }

    /** Preferences are replaced on their own, so a headline edit cannot reset them. */
    @Transactional
    public Profile replacePreferences(UserContext user, String ifMatch, Preferences preferences) {
        Profile profile = resolver.own(user);
        EntityTags.requireMatch(ifMatch, profile.getVersion());

        profile.setPreferences(preferences);
        return saveWithCompleteness(user, profile);
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
     * Saves the head and answers with the completeness of what was just
     * written, not of what was there before (F-003).
     *
     * <p>Two of the seven terms in the formula — contact and
     * {@code selfDescription} — live on the head, so a write that touches
     * either moves the figure. Answering with the stored value made
     * {@code PUT} return the percentage from before the request, and a bar
     * drawn from it showed the previous edit; two writes with no completeness
     * change agree, which is what kept it hidden.
     *
     * <p>The tree load this costs is charged to the head endpoints only. The
     * section, entry and atom endpoints do not answer with the head, so they
     * still leave the figure to the next read (Bolum 31.9) — the invariant is
     * that a response carrying {@code completeness} carries a current one, not
     * that the column is current after every write.
     */
    private Profile saveWithCompleteness(UserContext user, Profile profile) {
        ProfileRef reference = ProfileRef.persistent(user, profile.getId(), profile.getOwnerId());
        profile.setCompleteness(CompletenessCalculator.of(profile, assembler.load(reference)));
        return profiles.save(user, profile);
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
