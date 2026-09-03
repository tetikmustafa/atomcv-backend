package com.mustafatetik.atomcv.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.billing.QuotaSubject;
import com.mustafatetik.atomcv.ingestion.extraction.DocumentFormat;
import com.mustafatetik.atomcv.ingestion.extraction.ExtractedText;
import com.mustafatetik.atomcv.ingestion.normalization.NormalizedProfile;
import com.mustafatetik.atomcv.ingestion.normalization.ProfileNormalizer;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile;
import com.mustafatetik.atomcv.shared.wire.ExtractionWarningCode;
import com.mustafatetik.atomcv.ingestion.structuring.ProfileStructuring;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobProgress;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * The job behind Bolum 31.4 and 31.5, with the three stages stubbed.
 *
 * <p>What is under test is the orchestration and nothing else: the order of
 * the stages, what the person is told when one of them refuses, whether the
 * allowance comes back, and what the terminal event of Bolum 30.6 carries.
 * Each stage has its own tests, and repeating them here would be asserting the
 * mocks.
 */
class ProfileExtractionJobHandlerTest {

    private static final UUID USER = UUID.randomUUID();

    private static final QuotaSubject ALLOWANCE =
            QuotaSubject.of(UserContext.of(USER));

    private static final AnonymousSessionId SESSION =
            AnonymousSessionId.of("an-anonymous-session");

    private static final QuotaSubject ADDRESS = QuotaSubject.ofAddress("198.51.100.7");

    private final ProfileStructuring structuring = mock(ProfileStructuring.class);
    private final ProfileNormalizer normalizer = mock(ProfileNormalizer.class);
    private final ProfileWriter writer = mock(ProfileWriter.class);
    private final QuotaService quotas = mock(QuotaService.class);
    private final JobQueue queue = mock(JobQueue.class);

    private final EphemeralProfileWriter ephemeral = mock(EphemeralProfileWriter.class);

    private final ProfileExtractionJobHandler handler = new ProfileExtractionJobHandler(
            structuring, normalizer, writer, ephemeral, quotas, queue,
            Clock.fixed(Instant.parse("2026-08-27T09:00:00Z"), ZoneOffset.UTC));

    private final List<JobProgress> reported = new ArrayList<>();

    @Test
    void itAnswersForTheExtractionJobType() {
        assertThat(handler.type()).isEqualTo(JobType.PROFILE_EXTRACT);
    }

    // -- the happy path ----------------------------------------------------

    @Test
    void aReadableCvIsStructuredNormalisedAndWritten() {
        var profile = new Profile(USER);
        when(structuring.structure(any(), any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any(), any())).thenReturn(normalized(2, 5, 1));
        when(writer.write(any(), any(), anyBoolean())).thenReturn(profile);

        JobOutcome outcome = handler.handle(job(), reported::add);

        assertThat(outcome).isInstanceOf(JobOutcome.Completed.class);
        verify(writer).write(any(), any(), anyBoolean());
    }

    /**
     * Bolum 30.6's terminal event: counts and ids, never content. The warning
     * count is on it because Bolum 31.6's screen opens on the sections that
     * have one, and the client has to know whether to before it fetches
     * anything.
     */
    @Test
    void theTerminalEventCarriesCountsAndNoneOfTheCv() {
        var profile = new Profile(USER);
        when(structuring.structure(any(), any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any(), any())).thenReturn(normalized(2, 5, 1));
        when(writer.write(any(), any(), anyBoolean())).thenReturn(profile);

        var result = ((JobOutcome.Completed) handler.handle(job(), reported::add)).result();

        assertThat(result).containsEntry("profileId", profile.getId().toString());
        assertThat(result).containsEntry("sectionCount", 2);
        assertThat(result).containsEntry("atomCount", 5);
        assertThat(result).containsEntry("warningCount", 1);
        assertThat(result).containsEntry("detectedLanguage", "tr");
        assertThat(result.toString()).doesNotContain("Lovelace");
    }

    @Test
    void theProgressSaysWhichStageItIsOnAndInOrder() {
        when(structuring.structure(any(), any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any(), any())).thenReturn(normalized(1, 1, 0));
        when(writer.write(any(), any(), anyBoolean())).thenReturn(new Profile(USER));

        handler.handle(job(), reported::add);

        assertThat(reported).extracting(JobProgress::phase)
                .containsExactly("structuring", "normalizing", "saving");
        assertThat(reported).extracting(JobProgress::pct).isSorted();
    }

    /**
     * Bolum 31.6's background box. The screen opens the moment the profile
     * exists and these arrive underneath it — done inline they would add
     * twenty seconds to the one moment the product asks anybody to wait.
     */
    @Test
    void theVectorsAndTheHeightsAreQueuedRatherThanWaitedFor() {
        when(structuring.structure(any(), any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any(), any())).thenReturn(normalized(1, 1, 0));
        when(writer.write(any(), any(), anyBoolean())).thenReturn(new Profile(USER));

        handler.handle(job(), reported::add);

        var queued = ArgumentCaptor.forClass(Job.class);
        verify(queue, times(2)).enqueue(queued.capture());
        assertThat(queued.getAllValues()).extracting(Job::getType)
                .containsExactly(JobType.EMBEDDING, JobType.MEASUREMENT);
    }

    /**
     * And after the write, which is not a detail.
     *
     * <p>{@code ProfileWriter} owns its own transaction; a worker that picked
     * one of these up before it committed would find an empty profile and do
     * nothing — not a failure it could report, just a profile quietly never
     * embedded. The ordering is the whole guard, so it is asserted rather than
     * left to the order the lines happen to be in.
     */
    @Test
    void theBackgroundWorkIsQueuedOnlyOnceThereIsAProfileToDoItTo() {
        when(structuring.structure(any(), any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any(), any())).thenReturn(normalized(1, 1, 0));
        when(writer.write(any(), any(), anyBoolean())).thenReturn(new Profile(USER));

        handler.handle(job(), reported::add);

        InOrder order = inOrder(writer, queue);
        order.verify(writer).write(any(), any(), anyBoolean());
        order.verify(queue, times(2)).enqueue(any());
    }

    // -- when a stage refuses ----------------------------------------------

    /**
     * Bolum 44.2: the unit was taken when the upload was accepted, and a
     * person who got no profile out of it has not had one.
     */
    @Test
    void aRefusedExtractionGivesTheAllowanceBack() {
        when(structuring.structure(any(), any(), any()))
                .thenReturn(Result.err(new PipelineError.NothingExtracted()));

        handler.handle(job(), reported::add);

        verify(quotas).refund(eq(ALLOWANCE), eq(QuotaMetric.PROFILE_EXTRACT));
        verify(writer, never()).write(any(), any(), anyBoolean());
        // And nothing is queued to embed a profile that was never written.
        verify(queue, never()).enqueue(any());
    }

    @Test
    void aCvThatYieldedNothingIsNotWorthAnotherAttempt() {
        when(structuring.structure(any(), any(), any()))
                .thenReturn(Result.err(new PipelineError.NothingExtracted()));

        var failed = (JobOutcome.Failed) handler.handle(job(), reported::add);

        assertThat(failed.error().code()).isEqualTo(ErrorCode.EXTRACTION_EMPTY);
        // The same document goes back to the same model; three failures
        // instead of one buys nothing (Bolum 30.5).
        assertThat(failed.retryable()).isFalse();
    }

    @Test
    void aLanguageThatCouldNotBeSettledBecomesAQuestionCarryingItsGuess() {
        when(structuring.structure(any(), any(), any())).thenReturn(
                Result.err(new PipelineError.LanguageUndetected(List.of("tr"))));

        var failed = (JobOutcome.Failed) handler.handle(job(), reported::add);

        assertThat(failed.error().code()).isEqualTo(ErrorCode.LANGUAGE_UNDETECTED);
        assertThat(failed.error().params()).containsEntry("detectedCandidates", List.of("tr"));
    }

    /**
     * An outage is the one refusal here that is worth repeating, and it is the
     * one the queue must be told about — a false {@code retryable} would send
     * the person to the manual form because a provider was down.
     */
    @Test
    void aProviderOutageIsRetryableAndSaysWhoWasTried() {
        when(structuring.structure(any(), any(), any())).thenReturn(
                Result.err(new PipelineError.AllProvidersUnavailable(List.of("openrouter"))));

        var failed = (JobOutcome.Failed) handler.handle(job(), reported::add);

        assertThat(failed.error().code()).isEqualTo(ErrorCode.ALL_PROVIDERS_UNAVAILABLE);
        assertThat(failed.retryable()).isTrue();
    }

    /**
     * A job belonging to neither an account nor a session. {@code JobOwner}
     * makes that unconstructable, so a row like this was written before the
     * type existed or by hand — and a profile written for nobody would be
     * readable by everybody.
     */
    @Test
    void anExtractionWithNoOwnerAtAllIsRefusedRatherThanGivenOne() {
        var orphan = new Job(JobType.PROFILE_EXTRACT, null,
                ProfileExtractionPayload.of(document(), ALLOWANCE, false).asMap(), Instant.EPOCH);

        var failed = (JobOutcome.Failed) handler.handle(orphan, reported::add);

        assertThat(failed.error().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(failed.retryable()).isFalse();
        verify(writer, never()).write(any(), any(), anyBoolean());
        verify(ephemeral, never()).write(any(), any());
    }

    // -- the anonymous half (Bolum 9, Adim 3.6) ----------------------------

    /**
     * <strong>The promise of Bolum 9, at the one line that could break it.</strong>
     * Somebody who has not signed up gets a profile and leaves no row behind,
     * and there is exactly one place that decides which of the two writers
     * runs. A branch that fell through to the persistent one would keep every
     * visible behaviour and quietly write a stranger's CV into the database.
     */
    @Test
    void ananonymousUploadIsWrittenToTheEphemeralStoreAndNowhereElse() {
        when(structuring.structure(any(), any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any(), any())).thenReturn(normalized(2, 5, 0));

        JobOutcome outcome = handler.handle(anonymousJob(ADDRESS), reported::add);

        assertThat(outcome).isInstanceOf(JobOutcome.Completed.class);
        verify(ephemeral).write(eq(ProfileRef.ephemeral(SESSION)), any());
        verify(writer, never()).write(any(), any(), anyBoolean());
    }

    /**
     * The terminal event is the same event, and it has to be: Bolum 30.6's
     * client renders one screen from it, and an anonymous profile that
     * answered without a {@code profileId} would have nowhere to send the
     * person next.
     */
    @Test
    void ananonymousUploadAnswersWithTheProfileTheStoreHolds() {
        when(structuring.structure(any(), any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any(), any())).thenReturn(normalized(2, 5, 0));

        var result = ((JobOutcome.Completed)
                handler.handle(anonymousJob(ADDRESS), reported::add)).result();

        assertThat(result).containsEntry("profileId",
                ProfileRef.ephemeral(SESSION).id().toString());
        assertThat(result).containsEntry("atomCount", 5);
    }

    /**
     * No embedding, no measurement. Both write to rows an anonymous profile
     * does not have; queueing them would fail a job that had already
     * succeeded, and the person would be told their CV did not import.
     */
    @Test
    void nobackgroundWorkIsQueuedForAProfileWithNoRows() {
        when(structuring.structure(any(), any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any(), any())).thenReturn(normalized(2, 5, 0));

        handler.handle(anonymousJob(ADDRESS), reported::add);

        verify(queue, never()).enqueue(any());
    }

    /**
     * <strong>Bolum 44.2, and the reason the payload carries the subject at
     * all.</strong> An anonymous upload is paid for by an address, which the
     * worker cannot see — it runs outside the request. Refunding the wrong
     * subject is worse than not refunding: it credits somebody who never
     * spent, and leaves the person who did still paying for a failure.
     */
    @Test
    void arefusedAnonymousExtractionGivesTheAddressItsAllowanceBack() {
        when(structuring.structure(any(), any(), any()))
                .thenReturn(Result.err(new PipelineError.NothingExtracted()));

        handler.handle(anonymousJob(ADDRESS), reported::add);

        verify(quotas).refund(eq(ADDRESS), eq(QuotaMetric.PROFILE_EXTRACT));
        verify(ephemeral, never()).write(any(), any());
    }

    /**
     * The prompt experiment of Bolum 53.3 buckets by caller, and an anonymous
     * caller's identifier is the session cookie. It is bucketed by the profile
     * id derived from it instead — equally stable, and not a credential being
     * passed around as a name.
     */
    @Test
    void ananonymousCallerIsBucketedByProfileAndNotByTheirCookie() {
        when(structuring.structure(any(), any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any(), any())).thenReturn(normalized(1, 1, 0));

        handler.handle(anonymousJob(ADDRESS), reported::add);

        var bucketKey = ArgumentCaptor.forClass(String.class);
        verify(structuring).structure(any(), bucketKey.capture(), any());
        assertThat(bucketKey.getValue())
                .isEqualTo(ProfileRef.ephemeral(SESSION).id().toString())
                .isNotEqualTo(SESSION.value());
    }

    // -- fixtures ----------------------------------------------------------

    private static Job job() {
        return new Job(JobType.PROFILE_EXTRACT, USER,
                ProfileExtractionPayload.of(document(), ALLOWANCE, false).asMap(), Instant.EPOCH);
    }

    private static Job anonymousJob(QuotaSubject allowance) {
        Job job = new Job(JobType.PROFILE_EXTRACT, null,
                ProfileExtractionPayload.of(document(), allowance, false).asMap(), Instant.EPOCH);
        job.setAnonSessionId(SESSION.value());
        return job;
    }

    private static ExtractedText document() {
        return new ExtractedText(
                "Ada Lovelace, Analytical Engine programmer", DocumentFormat.PDF, false);
    }

    private static ExtractedProfile extracted() {
        return new ExtractedProfile("tr", 0.96,
                ExtractedProfile.ExtractedContact.EMPTY, List.of(), List.of());
    }

    private static NormalizedProfile normalized(int sections, int atoms, int warnings) {
        var atomList = new ArrayList<NormalizedProfile.NormalizedAtom>();
        for (short i = 0; i < atoms; i++) {
            atomList.add(new NormalizedProfile.NormalizedAtom(
                    RichContent.plain("Lovelace"), RichContent.EMPTY,
                    List.of(), List.of(), List.of(), List.of(), i));
        }
        var sectionList = new ArrayList<NormalizedProfile.NormalizedSection>();
        for (short i = 0; i < sections; i++) {
            sectionList.add(new NormalizedProfile.NormalizedSection(
                    SectionKind.EXPERIENCE, "Deneyim", i,
                    i == 0
                            ? List.of(new NormalizedProfile.NormalizedEntry(
                                    "Data Engineer", "Brisa", "Istanbul",
                                    null, null, (short) 0, atomList))
                            : List.of()));
        }
        var warningList = new ArrayList<ExtractedProfile.ExtractionWarning>();
        for (int i = 0; i < warnings; i++) {
            warningList.add(new ExtractedProfile.ExtractionWarning(
                    ExtractionWarningCode.AMBIGUOUS_DATE,
                    "a date could not be read", "sections[0]"));
        }
        return new NormalizedProfile("tr", Contact.EMPTY, sectionList, warningList);
    }
}
