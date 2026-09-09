package com.mustafatetik.atomcv.generation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Who a generation is for, and the three things the pipeline used a
 * {@code UserContext} for.
 *
 * <p>The anonymous half has no caller yet — the handler still refuses a job with
 * no owner while the quota subject, the measurement job and the read endpoints
 * catch up. Bolum 51.7 says a component the suite switches off has unverified
 * wiring, so it is verified here directly rather than left until it is reachable.
 */
class GenerationSubjectTest {

    private static final UUID USER = UUID.randomUUID();

    @Test
    void anaccountIsBucketedAndAttributedByItsOwnId() {
        var subject = GenerationSubject.account(ownedByAccount(), USER);

        assertThat(subject.userId()).isEqualTo(USER);
        assertThat(subject.attribution()).contains(USER);
        assertThat(subject.bucketKey()).isEqualTo(USER.toString());
        assertThat(subject.isAnonymous()).isFalse();
    }

    /**
     * <strong>Bucketed by the profile and not by the session</strong>, which is
     * the convention {@code ProfileExtractionJobHandler} set: both are stable for
     * the length of the session and one of them is the cookie.
     */
    @Test
    void ananonymousSessionIsBucketedByItsProfileAndAttributedToNobody() {
        var owned = ownedBySession();
        var subject = GenerationSubject.anonymous(owned);

        assertThat(subject.userId()).isNull();
        assertThat(subject.attribution()).isEmpty();
        assertThat(subject.bucketKey()).isEqualTo(owned.ref().id().toString());
        assertThat(subject.bucketKey()).isNotEqualTo("a-session");
        assertThat(subject.isAnonymous()).isTrue();
    }

    /**
     * Read off the scope rather than off the user being null, so "is this
     * anonymous" has one answer in the codebase — the same one
     * {@code AnonymousLimits} asks.
     */
    @Test
    void whetherItIsAnonymousComesFromTheScope() {
        assertThat(new GenerationSubject(ownedBySession(), USER, "x").isAnonymous()).isTrue();
        assertThat(new GenerationSubject(ownedByAccount(), null, "x").isAnonymous()).isFalse();
    }

    private static ProfileResolver.OwnedProfile ownedByAccount() {
        var profile = new Profile(USER);
        return new ProfileResolver.OwnedProfile(profile,
                ProfileRef.persistent(UserContext.of(USER), profile.getId(), USER));
    }

    private static ProfileResolver.OwnedProfile ownedBySession() {
        ProfileRef ref = ProfileRef.ephemeral(AnonymousSessionId.of("a-session"));
        return new ProfileResolver.OwnedProfile(
                Profile.forAnonymousSession(ref.id(), Instant.now().plusSeconds(7200)), ref);
    }
}
