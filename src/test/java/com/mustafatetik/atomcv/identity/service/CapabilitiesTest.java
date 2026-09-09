package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaSubject;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.identity.CapabilityProperties;
import com.mustafatetik.atomcv.identity.api.dto.CapabilitiesResponse;
import com.mustafatetik.atomcv.shared.error.AccountFeature;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** § 35.7's block, for the caller who has an account and the caller who does not. */
class CapabilitiesTest {

    private static final Instant MIDNIGHT = Instant.parse("2026-08-27T00:00:00Z");

    private static final UserContext SOMEONE =
            UserContext.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private final QuotaService quotas = mock(QuotaService.class);

    /**
     * The numbers in § 35.7's example body, which is the only place the
     * anonymous set was ever written down — with one taken out of it.
     */
    @Test
    void withoutASessionTheCallerIsToldExactlyWhatTheSectionWroteDown() {
        CapabilitiesResponse capabilities = capabilities().of(Optional.empty(), null);

        assertThat(capabilities.allowedLanguages()).containsExactly("en");
        assertThat(capabilities.canCustomizeTemplate()).isFalse();
        assertThat(capabilities.canEditAtomControls()).isFalse();
        assertThat(capabilities.canAddAlternatives()).isFalse();
        assertThat(capabilities.canWriteCoverLetter()).isFalse();
        assertThat(capabilities.canSaveHistory()).isFalse();
        assertThat(capabilities.dailyProfileQuota()).isEqualTo(3);
        assertThat(capabilities.maxAtoms()).isEqualTo(60);
    }

    /**
     * <strong>Five again, and the flow behind it exists.</strong> This assertion
     * was zero for a day, while the block advertised a generation the API
     * refused. Now the request is queued as the session's own work, the pipeline
     * takes a subject rather than a user, and the result is readable by the
     * session that made it — so the number is a promise again rather than a
     * screen the user hits a 401 behind.
     *
     * <p>The profile number beside it never moved: that half was built all along,
     * which is the whole reason they are stated separately.
     */
    @Test
    void thegenerationAnonymousCallersCanNowStartIsAdvertisedAgain() {
        CapabilitiesResponse capabilities = capabilities().of(Optional.empty(), null);

        assertThat(capabilities.dailyGenerationQuota())
                .as("the anonymous flow can generate, so § 35.7's five stands")
                .isEqualTo(5);
        assertThat(capabilities.dailyProfileQuota())
                .as("importing a CV anonymously is built and stays advertised")
                .isEqualTo(3);
    }

    @Test
    void nothingHasBeenCountedForACallerWithNoSessionSoNothingRollsOver() {
        CapabilitiesResponse capabilities = capabilities().of(Optional.empty(), null);

        assertThat(capabilities.generationsUsedToday()).isZero();
        assertThat(capabilities.profilesUsedToday()).isZero();
        assertThat(capabilities.quotaResetsAt()).isNull();
    }

    /**
     * The counters come from the same service the 429 comes from. A capability
     * screen that disagrees with the refusal the user is about to get is worse
     * than no capability screen at all.
     */
    @Test
    void anAccountReadsItsQuotaFromTheServiceThatEnforcesIt() {
        when(quotas.usage(eq(QuotaSubject.of(SOMEONE)), eq(QuotaMetric.GENERATION)))
                .thenReturn(new QuotaService.Usage("generation", 3, 3, 20, 17, MIDNIGHT));
        when(quotas.usage(eq(QuotaSubject.of(SOMEONE)), eq(QuotaMetric.PROFILE_EXTRACT)))
                .thenReturn(new QuotaService.Usage("profile_extract", 1, 1, 5, 4, MIDNIGHT));

        CapabilitiesResponse capabilities = capabilities().of(Optional.of(SOMEONE), null);

        assertThat(capabilities.dailyGenerationQuota()).isEqualTo(20);
        assertThat(capabilities.generationsUsedToday()).isEqualTo(3);
        assertThat(capabilities.dailyProfileQuota()).isEqualTo(5);
        assertThat(capabilities.profilesUsedToday()).isEqualTo(1);
        assertThat(capabilities.quotaResetsAt()).isEqualTo(MIDNIGHT);
    }

    @Test
    void anAccountHasNoAtomCeilingAndNoAnonymousExpiry() {
        stubUsage();

        CapabilitiesResponse capabilities = capabilities().of(Optional.of(SOMEONE), null);

        // ATOM_LIMIT_EXCEEDED is the anonymous gate. A number here would be a
        // bar the client draws against a limit that does not exist.
        assertThat(capabilities.maxAtoms()).isNull();
        assertThat(capabilities.anonymousExpiresAt()).isNull();
    }

    @Test
    void anAccountGetsEveryGateOpenAndTheConfiguredLanguages() {
        stubUsage();

        CapabilitiesResponse capabilities =
                new Capabilities(quotas, new CapabilityProperties(List.of("en", "tr", "de")))
                        .of(Optional.of(SOMEONE), null);

        assertThat(capabilities.allowedLanguages()).containsExactly("en", "tr", "de");
        assertThat(capabilities.canCustomizeTemplate()).isTrue();
        assertThat(capabilities.canEditAtomControls()).isTrue();
        assertThat(capabilities.canAddAlternatives()).isTrue();
        assertThat(capabilities.canWriteCoverLetter()).isTrue();
        assertThat(capabilities.canSaveHistory()).isTrue();
    }

    /**
     * <strong>F-028, and the pairing is the point.</strong> § 35.7.3 refuses an
     * anonymous covering letter with {@code FEATURE_REQUIRES_ACCOUNT} and
     * {@code params.feature = cover_letter}, but the block carried no field for
     * it — so the frontend closed the control on {@code canSaveHistory}, which
     * is true today and true for the wrong reason. Every
     * {@link AccountFeature} now has a boolean here to be refused against, and
     * this test is what says the two agree.
     *
     * <p>Asserted next to {@code canSaveHistory} deliberately: the proxy worked
     * because the two happen to move together, and the day they separate is the
     * day this test stops passing for the right reason.
     */
    @Test
    void theCoverLetterIsAClosedControlAnonymouslyAndAnOpenOneForAnAccount() {
        stubUsage();

        assertThat(capabilities().of(Optional.empty(), null).canWriteCoverLetter())
                .as("§ 35.7.3 refuses it ahead of the quota, so the control is closed")
                .isFalse();
        assertThat(capabilities().of(Optional.of(SOMEONE), null).canWriteCoverLetter())
                .as("an account may ask for one, with the CV or afterwards")
                .isTrue();
    }

    /**
     * Only the templates that exist. § 35.7's example names three and the
     * registry holds one; publishing a template the renderer cannot produce is
     * a selectable option that fails at generation time.
     *
     * <p>Sorted, and that is not cosmetic: the registry is backed by an
     * immutable map whose iteration order is salted per JVM run, so an
     * unsorted copy would reach the wire in a different order on every restart
     * and make a diff of the published schema meaningless.
     */
    @Test
    void onlyTemplatesThatExistAreOfferedAndAlwaysInTheSameOrder() {
        stubUsage();
        Capabilities capabilities = capabilities();

        List<String> anonymous = capabilities.of(Optional.empty(), null).allowedTemplates();
        List<String> account = capabilities.of(Optional.of(SOMEONE), null).allowedTemplates();

        assertThat(anonymous).isEqualTo(account);
        assertThat(anonymous).containsExactly("classic");
        assertThat(anonymous).isSorted();
    }

    private Capabilities capabilities() {
        return new Capabilities(quotas, new CapabilityProperties(null));
    }

    private void stubUsage() {
        when(quotas.usage(eq(QuotaSubject.of(SOMEONE)), eq(QuotaMetric.GENERATION)))
                .thenReturn(new QuotaService.Usage("generation", 0, 0, 20, 20, MIDNIGHT));
        when(quotas.usage(eq(QuotaSubject.of(SOMEONE)), eq(QuotaMetric.PROFILE_EXTRACT)))
                .thenReturn(new QuotaService.Usage("profile_extract", 0, 0, 5, 5, MIDNIGHT));
    }
}
