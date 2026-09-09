package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaSubject;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.identity.CapabilityProperties;
import com.mustafatetik.atomcv.identity.api.dto.CapabilitiesResponse;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The capability block of § 35.7, for whoever is calling.
 *
 * <p>Two sets, and only one of them is specified. § 35.7 wrote down the
 * anonymous body — the constants below are its numbers, not invented ones —
 * and never wrote the account body; that half is {@link CapabilityProperties}
 * and reaches the frontend as {@code B-044}.
 *
 * <p>The account's quota numbers come from {@link QuotaService} rather than
 * from configuration read a second time. A capability screen that disagrees
 * with the 429 the user is about to get is worse than no capability screen.
 */
@Service
public class Capabilities {

    /**
     * § 35.7's five, and it is true again.
     *
     * <p>It was zero for a day: the block was advertising a generation
     * {@code POST /generations} refused, because the anonymous flow had a profile
     * and no pipeline. Both ends exist now — the request is queued as the
     * session's own work and paid for by the address (Bolum 44.1), the pipeline
     * takes a subject rather than a user, and the finished generation is readable
     * and downloadable by the session that owns it.
     *
     * <p>What the number does <em>not</em> cover is the cover letter: § 35.7 gives
     * that to an account, and asking for one is refused before the quota is
     * touched.
     */
    private static final int ANONYMOUS_GENERATIONS = 5;

    /**
     * Three, and unlike the line above this one is real: {@code JobOwner.of}
     * falls back to the anonymous session on {@code POST /profiles/import},
     * and {@code ProfileExtractionJobHandler} writes the ephemeral profile
     * that signing in later adopts. § 35.7's number stands.
     */
    private static final int ANONYMOUS_PROFILES = 3;

    private static final int ANONYMOUS_MAX_ATOMS = 60;

    private static final List<String> ANONYMOUS_LANGUAGES = List.of("en");

    private final QuotaService quotas;
    private final CapabilityProperties properties;

    Capabilities(QuotaService quotas, CapabilityProperties properties) {
        this.quotas = quotas;
        this.properties = properties;
    }

    /**
     * @param anonymousExpiresAt when the anonymous session runs out, or null
     *                           for an account — EK D.6.6, and § 35.7 says the
     *                           field is absent rather than null on an account,
     *                           because a countdown to nothing is a wrong
     *                           screen
     */
    public CapabilitiesResponse of(Optional<UserContext> user, Instant anonymousExpiresAt) {
        return user.map(this::forAccount).orElseGet(() -> forAnonymous(anonymousExpiresAt));
    }

    private CapabilitiesResponse forAccount(UserContext user) {
        QuotaService.Usage generations = quotas.usage(QuotaSubject.of(user), QuotaMetric.GENERATION);
        QuotaService.Usage profiles = quotas.usage(QuotaSubject.of(user), QuotaMetric.PROFILE_EXTRACT);
        return new CapabilitiesResponse(
                properties.accountLanguages(),
                templates(),
                true,
                true,
                true,
                true,
                generations.limit(),
                generations.used(),
                profiles.limit(),
                profiles.used(),
                // No ceiling on an account's profile: ATOM_LIMIT_EXCEEDED is
                // the anonymous gate, and a number here would be a bar the
                // client draws against a limit that does not exist.
                null,
                generations.resetsAt(),
                null);
    }

    /**
     * Adim 3.6 mints the session this describes. Until then it is what a
     * caller with no session is told, which is the same answer: these are the
     * limits an account would lift.
     */
    private CapabilitiesResponse forAnonymous(Instant anonymousExpiresAt) {
        return new CapabilitiesResponse(
                ANONYMOUS_LANGUAGES,
                templates(),
                false,
                false,
                false,
                false,
                ANONYMOUS_GENERATIONS,
                0,
                ANONYMOUS_PROFILES,
                0,
                ANONYMOUS_MAX_ATOMS,
                // Nothing has been counted, so nothing rolls over. The client
                // has no sentence to write until a counter exists.
                null,
                anonymousExpiresAt);
    }

    /**
     * Sorted, and that is not cosmetic: {@code ids()} is backed by an
     * immutable map whose iteration order is salted per JVM run, so an
     * unsorted copy would reach the JSON in a different order on every restart
     * and make a diff of the published schema meaningless.
     */
    private List<String> templates() {
        return TemplateRegistry.ids().stream().sorted().toList();
    }
}
