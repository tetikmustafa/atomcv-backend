package com.mustafatetik.atomcv.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobRepository;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.profile.service.ProfileResolver;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The order of the free gates, and what none of them cost (Bolum 35.3).
 *
 * <p>Everything here is about work that is <em>not</em> done. The integration
 * test proves the status codes; this proves that a posting nobody could read
 * never causes a profile to be loaded, and that neither refusal reaches the
 * queue.
 */
class GenerationEnqueueServiceTest {

    private static final UUID USER = UUID.randomUUID();

    /** Long enough and job-like enough to pass Bolum 18.1. */
    private static final String POSTING = """
            We are seeking a senior backend engineer for our payments team.
            Responsibilities include designing distributed services in Go and
            owning the reliability of a high throughput ledger. Requirements:
            production experience with Go and PostgreSQL, plus a track record of
            shipping. Preferred qualifications include Kubernetes. Apply with a
            short note describing the systems you have operated.
            """;

    private ProfileResolver profiles;
    private ProfileAssembler assembler;
    private JobQueue queue;
    private JobRepository jobs;
    private com.mustafatetik.atomcv.billing.QuotaService quotas;
    private com.mustafatetik.atomcv.billing.FeatureFlags flags;
    private GenerationEnqueueService service;

    private ProfileRef profile;

    @BeforeEach
    void wireTheMocks() {
        profiles = mock(ProfileResolver.class);
        assembler = mock(ProfileAssembler.class);
        queue = mock(JobQueue.class);
        jobs = mock(JobRepository.class);
        quotas = mock(com.mustafatetik.atomcv.billing.QuotaService.class);
        when(quotas.consume(any(), any())).thenReturn(Result.ok(null));
        flags = mock(com.mustafatetik.atomcv.billing.FeatureFlags.class);
        when(flags.isEnabled(any())).thenReturn(true);
        service = new GenerationEnqueueService(profiles, assembler, queue, jobs, quotas, flags,
                Clock.fixed(Instant.parse("2026-08-24T09:00:00Z"), ZoneOffset.UTC));

        var head = new Profile(USER);
        profile = ProfileRef.persistent(user(), UUID.randomUUID(), USER);
        when(profiles.owned(any())).thenReturn(new ProfileResolver.OwnedProfile(head, profile));
        when(jobs.findByIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(queue.enqueue(any())).thenAnswer(call -> call.getArgument(0));
    }

    /**
     * Four string measurements against one query. Refusing here means a paste
     * that is not a posting never loads a profile.
     */
    @Test
    void apostingIsCheckedBeforeTheProfileIsLoaded() {
        var result = service.enqueue(user(), "hire me plz", false, null, null, null);

        assertThat(result).isInstanceOf(Result.Err.class);
        assertThat(((Result.Err<Job>) result).error())
                .isInstanceOf(PipelineError.UnparseableJobDescription.class);
        verify(profiles, never()).owned(any());
        verify(queue, never()).enqueue(any());
    }

    /** An empty profile fails every attempt the retry budget allows. */
    @Test
    void anemptyProfileIsRefusedBeforeTheQueue() {
        when(assembler.load(profile)).thenReturn(new ProfileTree(profile.id(), List.of()));

        var result = service.enqueue(user(), POSTING, false, null, null, null);

        assertThat(((Result.Err<Job>) result).error())
                .isInstanceOf(PipelineError.InsufficientProfile.class);
        verify(queue, never()).enqueue(any());
    }

    /** EK D.6.1: the heuristics are cheap on purpose and a person may know better. */
    @Test
    void anacknowledgedPostingSkipsTheTextCheckButNotTheProfileOne() {
        when(assembler.load(profile)).thenReturn(new ProfileTree(profile.id(), List.of()));

        var result = service.enqueue(user(), "hire me plz", true, null, null, null);

        // Past the text gate — it reached the profile gate, and failed there.
        assertThat(((Result.Err<Job>) result).error())
                .isInstanceOf(PipelineError.InsufficientProfile.class);
        verify(profiles).owned(any());
    }

    /**
     * Bolum 30.7. Answered with the job that exists rather than with a
     * conflict: the caller asked for one generation and there is one.
     */
    @Test
    void aknownIdempotencyKeyAnswersWithoutTouchingAnything() {
        var existing = new Job(JobType.GENERATION, USER, java.util.Map.of(), Instant.EPOCH);
        when(jobs.findByIdempotencyKey(any(), any())).thenReturn(Optional.of(existing));

        var result = service.enqueue(user(), POSTING, false, null, null, "key-1");

        assertThat(result.orElseThrow()).isEqualTo(existing);
        verify(profiles, never()).owned(any());
        verify(queue, never()).enqueue(any());
    }

    /**
     * The idempotency key has to reach the row, or the unique index behind it
     * never sees a second request and the deduplication is decorative.
     */
    @Test
    void thekeyIsStoredOnTheJobItMade() {
        when(assembler.load(profile)).thenReturn(profileWithOneAtom());

        service.enqueue(user(), POSTING, false, 2, "tr", "key-1");

        var queued = ArgumentCaptor.forClass(Job.class);
        verify(queue).enqueue(queued.capture());
        assertThat(queued.getValue().getIdempotencyKey()).isEqualTo("key-1");
        assertThat(queued.getValue().getType()).isEqualTo(JobType.GENERATION);
        assertThat(queued.getValue().getOwnerId()).isEqualTo(USER);
        assertThat(GenerationPayload.from(queued.getValue().getPayload()))
                .isEqualTo(new GenerationPayload(POSTING, false, 2, "tr"));
    }

    /**
     * Bolum 44.3's brake goes ahead of the quota: a paused deployment must not
     * spend anyone's allowance on a request it is going to refuse.
     */
    @Test
    void thebrakeStopsGenerationWithoutSpendingAnything() {
        when(flags.isEnabled(any())).thenReturn(false);

        var result = service.enqueue(user(), POSTING, false, null, null, null);

        assertThat(((Result.Err<Job>) result).error())
                .isInstanceOf(PipelineError.GenerationPaused.class);
        verify(quotas, never()).consume(any(), any());
        verify(profiles, never()).owned(any());
        verify(queue, never()).enqueue(any());
    }

    /**
     * Bolum 44: the only gate that writes, and the first that runs. A user over
     * their limit should not have their profile loaded to find that out.
     */
    @Test
    void anexhaustedQuotaIsRefusedBeforeAnythingElseHappens() {
        when(quotas.consume(any(), any())).thenReturn(Result.err(
                new PipelineError.QuotaExceeded("generation", Instant.EPOCH)));

        var result = service.enqueue(user(), POSTING, false, null, null, null);

        assertThat(((Result.Err<Job>) result).error())
                .isInstanceOf(PipelineError.QuotaExceeded.class);
        verify(profiles, never()).owned(any());
        verify(queue, never()).enqueue(any());
    }

    /**
     * Bolum 44.2: nothing was generated, so nothing was spent. Without the
     * refund a user could burn a day's allowance on typos.
     */
    @Test
    void arefusedRequestGivesTheUnitBack() {
        service.enqueue(user(), "hire me plz", false, null, null, null);

        verify(quotas).refund(any(), eq(com.mustafatetik.atomcv.billing.QuotaMetric.GENERATION));
    }

    /** Answering with a job that already exists must not cost a second unit. */
    @Test
    void aknownKeyCostsNothing() {
        var existing = new Job(JobType.GENERATION, USER, java.util.Map.of(), Instant.EPOCH);
        when(jobs.findByIdempotencyKey(any(), any())).thenReturn(Optional.of(existing));

        service.enqueue(user(), POSTING, false, null, null, "key-1");

        verify(quotas, never()).consume(any(), any());
    }

    private static UserContext user() {
        return UserContext.of(USER);
    }

    private ProfileTree profileWithOneAtom() {
        var section = new com.mustafatetik.atomcv.profile.domain.Section(
                profile.id(), com.mustafatetik.atomcv.profile.domain.SectionKind.EXPERIENCE,
                "Experience", (short) 0);
        var atom = new com.mustafatetik.atomcv.profile.domain.Atom(
                profile.id(), section.getId(), null,
                com.mustafatetik.atomcv.profile.domain.AtomKind.SKILL, (short) 0);
        var variant = new com.mustafatetik.atomcv.profile.domain.AtomVariant(
                profile.id(), atom.getId(), "en",
                com.mustafatetik.atomcv.profile.domain.content.RichContent.plain("Go"));
        variant.setPrimary(true);
        return ProfileAssembler.assemble(profile.id(), List.of(section), List.of(),
                List.of(atom), List.of(variant));
    }
}
