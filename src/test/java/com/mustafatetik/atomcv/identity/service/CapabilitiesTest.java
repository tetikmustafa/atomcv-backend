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
        assertThat(capabilities.canSaveHistory()).isFalse();
        assertThat(capabilities.dailyProfileQuota()).isEqualTo(3);
        assertThat(capabilities.maxAtoms()).isEqualTo(60);
    }

    /**
     * <strong>Sapma against § 35.7's five, and the reason is measured.</strong>
     * Anonymous generation is not built: {@code POST /generations} calls
     * {@code CurrentUser.require()} and answers {@code AUTHENTICATION_REQUIRED}
     * to a caller with no account, and {@code GenerationJobHandler} refuses a
     * job with no owner as a second line. Advertising five was a promise the
     * API breaks on the first click, and no other field in the block says
     * otherwise — {@code canSaveHistory} is about keeping a generation, not
     * about making one.
     *
     * <p>The profile number beside it stays three because that half <em>is</em>
     * built, which is the whole point of stating them separately: this is not
     * "anonymous callers can do nothing", it is "they cannot do this one".
     */
    @Test
    void thegenerationAnonymousCallersCannotStartIsNotAdvertisedToThem() {
        CapabilitiesResponse capabilities = capabilities().of(Optional.empty(), null);

        assertThat(capabilities.dailyGenerationQuota())
                .as("POST /generations refuses an anonymous caller, so the block "
                        + "must not offer them a quota for it")
                .isZero();
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
        assertThat(capabilities.canSaveHistory()).isTrue();
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
