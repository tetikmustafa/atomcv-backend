package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Preferences;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.repository.AnonymousProfiles;
import com.mustafatetik.atomcv.profile.repository.ProfileRepository;
import com.mustafatetik.atomcv.profile.service.ProfileResolver.OwnedProfile;
import com.mustafatetik.atomcv.shared.util.EntityTags;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes to the profile head.
 *
 * <p>Reading and scoping stay in {@link CallerProfiles}: that answers "whose
 * profile", this answers "what changed".
 *
 * <p><strong>The caller's profile, account or anonymous session.</strong> This
 * used to take a {@code UserContext}, so the head was the one part of the
 * profile a guest could not reach: sections, entries and atoms moved to
 * {@link CallerProfiles} when the editor started working without an account,
 * and this class was left behind. Every screen that opens the editor reads the
 * head first, so a guest who had just imported a CV met "you need to be signed
 * in" on the review screen that cannot be skipped — measured on 2026-10-24,
 * through the real frontend.
 */
@Service
public class ProfileService {

    private final CallerProfiles callers;
    private final ProfileRepository profiles;
    private final AnonymousProfiles anonymous;
    private final ProfileAssembler assembler;

    ProfileService(CallerProfiles callers, ProfileRepository profiles,
            AnonymousProfiles anonymous, ProfileAssembler assembler) {
        this.callers = callers;
        this.profiles = profiles;
        this.anonymous = anonymous;
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
    public Profile replace(String ifMatch, ProfileHeadUpdate update) {
        OwnedProfile owned = callers.owned();
        Profile profile = owned.profile();
        EntityTags.requireMatch(ifMatch, profile.getVersion());

        profile.setHeadline(update.headline());
        profile.setContact(update.contact());
        profile.setSelfDescription(update.selfDescription());
        profile.setSourceLanguage(update.sourceLanguage());
        profile.setEnabledLanguages(update.enabledLanguages());
        return saveWithCompleteness(owned);
    }

    /** Preferences are replaced on their own, so a headline edit cannot reset them. */
    @Transactional
    public Profile replacePreferences(String ifMatch, Preferences preferences) {
        OwnedProfile owned = callers.owned();
        Profile profile = owned.profile();
        EntityTags.requireMatch(ifMatch, profile.getVersion());

        profile.setPreferences(preferences);
        return saveWithCompleteness(owned);
    }

    /**
     * The head with a fresh completeness figure.
     *
     * <p>Recomputed on read rather than on every write. The formula counts
     * across the whole profile, so a write would have to load it all to update
     * one number, and every section, entry and atom endpoint would carry that
     * cost. Reading is where the figure is looked at, and the stored column
     * exists for the preflight gate in Stage 2 — so it is written back only
     * when it actually moved.
     */
    @Transactional
    public Profile readOwn() {
        OwnedProfile owned = callers.owned();
        Profile profile = owned.profile();

        short completeness = CompletenessCalculator.of(profile, assembler.load(owned.ref()));
        if (completeness != profile.getCompleteness()) {
            profile.setCompleteness(completeness);
            return save(owned);
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
     * still leave the figure to the next read — the invariant is that a
     * response carrying {@code completeness} carries a current one, not that
     * the column is current after every write.
     */
    private Profile saveWithCompleteness(OwnedProfile owned) {
        Profile profile = owned.profile();
        profile.setCompleteness(CompletenessCalculator.of(profile, assembler.load(owned.ref())));
        return save(owned);
    }

    /**
     * The head goes back through the repository that can reach it: the
     * user-scoped one for an account, {@link AnonymousProfiles} for a session —
     * which takes the ephemeral ref and refuses anything else.
     */
    private Profile save(OwnedProfile owned) {
        return callers.user()
                .map(user -> profiles.save(user, owned.profile()))
                .orElseGet(() -> anonymous.save(owned.ref(), owned.profile()));
    }

    /**
     * Deletes the profile and everything under it.
     *
     * <p>The account survives: a user without a profile is a user who has not
     * started one, and the next read gives them an empty one. Requires
     * If-Match, because this is the one call that cannot be undone.
     *
     * <p>A guest's goes the same way, and the next read creates an empty one
     * under the same session — starting over, which is what a guest asking
     * this wants.
     */
    @Transactional
    public void delete(String ifMatch) {
        OwnedProfile owned = callers.owned();
        Profile profile = owned.profile();
        EntityTags.requireMatch(ifMatch, profile.getVersion());
        callers.user().ifPresentOrElse(
                user -> profiles.delete(user, profile),
                () -> anonymous.delete(owned.ref()));
    }
}
