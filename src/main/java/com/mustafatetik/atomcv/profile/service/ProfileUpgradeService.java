package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.repository.AtomRepository;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.profile.repository.EntryRepository;
import com.mustafatetik.atomcv.profile.repository.ProfileRepository;
import com.mustafatetik.atomcv.profile.repository.SectionRepository;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The anonymous profile becomes the account's (Bolum 9, Adim 3.6).
 *
 * <p><strong>It runs inside signing in, and it has to.</strong> Signing in
 * issues a new session and a new cookie; the anonymous session id is known
 * during that one request and never again, and the profile is addressed by a
 * value derived from it. A later endpoint would be reaching for an identifier
 * the browser has already thrown away.
 *
 * <p><strong>The rows are adopted, not copied.</strong> The profile row is
 * written with the id the anonymous profile already had, so every section,
 * entry, atom and variant is saved exactly as it stands — same ids, same
 * fields. A copier would have to name every field it carried across, and the
 * first field added to an atom afterwards would be dropped by it silently.
 * The id is derived from a session id, which is secret and one-way: knowing
 * the profile id tells nobody the session it came from.
 *
 * <p>One transaction, for the reason {@code ProfileWriter} states: half a CV
 * is not a partial success, it is a profile the person has to notice is wrong.
 */
@Service
public class ProfileUpgradeService {

    private static final Logger log = LoggerFactory.getLogger(ProfileUpgradeService.class);

    private final EphemeralProfileStore store;
    private final ProfileResolver resolver;
    private final ProfileRepository profiles;
    private final SectionRepository sections;
    private final EntryRepository entries;
    private final AtomRepository atoms;
    private final AtomVariantRepository variants;
    private final JobQueue queue;
    private final Clock clock;

    ProfileUpgradeService(EphemeralProfileStore store, ProfileResolver resolver,
            ProfileRepository profiles,
            SectionRepository sections, EntryRepository entries, AtomRepository atoms,
            AtomVariantRepository variants, JobQueue queue, Clock clock) {
        this.store = store;
        this.resolver = resolver;
        this.profiles = profiles;
        this.sections = sections;
        this.entries = entries;
        this.atoms = atoms;
        this.variants = variants;
        this.queue = queue;
        this.clock = clock;
    }

    /**
     * Moves what the session built into the account, when there is something
     * to move and somewhere to put it.
     *
     * <p>Never throws. This runs on the way through signing in, and a person
     * who cannot sign in because a cache was down would have lost more than
     * the profile this was trying to save.
     */
    @Transactional
    public ProfileUpgrade upgrade(UserContext user, AnonymousSessionId session) {
        ProfileRef anonymous = ProfileRef.ephemeral(session);
        Optional<EphemeralProfile> stored;
        try {
            stored = store.find(anonymous);
        } catch (EphemeralProfileUnavailableException unreachable) {
            log.warn("Could not read an anonymous profile to upgrade it: {}",
                    unreachable.getClass().getSimpleName());
            return ProfileUpgrade.UNAVAILABLE;
        }
        if (stored.isEmpty()) {
            return ProfileUpgrade.NONE;
        }
        Optional<Profile> existing = profiles.findOwn(user);
        if (existing.isPresent() && resolver.hasContent(user)) {
            // The account brought real work of its own. Nothing is written and
            // nothing is deleted; the anonymous one runs out on its TTL.
            log.info("An account with a profile signed in from an anonymous session");
            return ProfileUpgrade.KEPT_EXISTING;
        }
        existing.ifPresent(empty -> {
            profiles.delete(user, empty);
            // Before the adopted row is written, not after: one profile per
            // user is a unique constraint and Hibernate would otherwise order
            // the insert first. See UserScopedRepository.flush.
            profiles.flush();
        });

        adopt(user, stored.get());
        store.discard(anonymous);
        queueBackgroundWork(user);
        // Counts, never a line of the CV (absolute rule 4).
        log.info("Upgraded an anonymous profile: {}", stored.get().shape());
        return ProfileUpgrade.UPGRADED;
    }

    private void adopt(UserContext user, EphemeralProfile anonymous) {
        Profile profile = new Profile(user.userId(), anonymous.profileId());
        profile.setContact(anonymous.contact());
        profile.setSourceLanguage(anonymous.sourceLanguage());
        profiles.save(user, profile);

        ProfileRef ref = ProfileRef.persistent(user, profile.getId(), profile.getOwnerId());
        // In this order because that is the order the foreign keys point in.
        anonymous.sections().forEach(section -> sections.save(ref, section));
        anonymous.entries().forEach(entry -> entries.save(ref, entry));
        anonymous.atoms().forEach(atom -> atoms.save(ref, atom));
        anonymous.variants().forEach(variant -> variants.save(ref, variant));
    }

    /**
     * The two jobs the anonymous import skipped (§ 31.6.3).
     *
     * <p>They were skipped because they write to rows an anonymous profile does
     * not have. It has them now, so this is the moment they become possible —
     * and the moment they become worth doing, since the first generation from
     * an account should not be the degraded one.
     */
    private void queueBackgroundWork(UserContext user) {
        queue.enqueue(new Job(JobType.EMBEDDING, user.userId(), Map.of(), clock.instant()));
        queue.enqueue(new Job(JobType.MEASUREMENT, user.userId(), Map.of(), clock.instant()));
    }
}
