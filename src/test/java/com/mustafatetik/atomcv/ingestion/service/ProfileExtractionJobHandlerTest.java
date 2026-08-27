package com.mustafatetik.atomcv.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.ingestion.extraction.DocumentFormat;
import com.mustafatetik.atomcv.ingestion.extraction.ExtractedText;
import com.mustafatetik.atomcv.ingestion.normalization.NormalizedProfile;
import com.mustafatetik.atomcv.ingestion.normalization.ProfileNormalizer;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractionWarningCode;
import com.mustafatetik.atomcv.ingestion.structuring.ProfileStructuring;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobOutcome;
import com.mustafatetik.atomcv.jobs.queue.JobProgress;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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

    private final ProfileStructuring structuring = mock(ProfileStructuring.class);
    private final ProfileNormalizer normalizer = mock(ProfileNormalizer.class);
    private final ProfileWriter writer = mock(ProfileWriter.class);
    private final QuotaService quotas = mock(QuotaService.class);

    private final ProfileExtractionJobHandler handler =
            new ProfileExtractionJobHandler(structuring, normalizer, writer, quotas);

    private final List<JobProgress> reported = new ArrayList<>();

    @Test
    void itAnswersForTheExtractionJobType() {
        assertThat(handler.type()).isEqualTo(JobType.PROFILE_EXTRACT);
    }

    // -- the happy path ----------------------------------------------------

    @Test
    void aReadableCvIsStructuredNormalisedAndWritten() {
        var profile = new Profile(USER);
        when(structuring.structure(any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any())).thenReturn(normalized(2, 5, 1));
        when(writer.write(any(), any())).thenReturn(profile);

        JobOutcome outcome = handler.handle(job(), reported::add);

        assertThat(outcome).isInstanceOf(JobOutcome.Completed.class);
        verify(writer).write(any(), any());
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
        when(structuring.structure(any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any())).thenReturn(normalized(2, 5, 1));
        when(writer.write(any(), any())).thenReturn(profile);

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
        when(structuring.structure(any(), any())).thenReturn(Result.ok(extracted()));
        when(normalizer.normalize(any())).thenReturn(normalized(1, 1, 0));
        when(writer.write(any(), any())).thenReturn(new Profile(USER));

        handler.handle(job(), reported::add);

        assertThat(reported).extracting(JobProgress::phase)
                .containsExactly("structuring", "normalizing", "saving");
        assertThat(reported).extracting(JobProgress::pct).isSorted();
    }

    // -- when a stage refuses ----------------------------------------------

    /**
     * Bolum 44.2: the unit was taken when the upload was accepted, and a
     * person who got no profile out of it has not had one.
     */
    @Test
    void aRefusedExtractionGivesTheAllowanceBack() {
        when(structuring.structure(any(), any()))
                .thenReturn(Result.err(new PipelineError.NothingExtracted()));

        handler.handle(job(), reported::add);

        verify(quotas).refund(any(), eq(QuotaMetric.PROFILE_EXTRACT));
        verify(writer, never()).write(any(), any());
    }

    @Test
    void aCvThatYieldedNothingIsNotWorthAnotherAttempt() {
        when(structuring.structure(any(), any()))
                .thenReturn(Result.err(new PipelineError.NothingExtracted()));

        var failed = (JobOutcome.Failed) handler.handle(job(), reported::add);

        assertThat(failed.error().code()).isEqualTo(ErrorCode.EXTRACTION_EMPTY);
        // The same document goes back to the same model; three failures
        // instead of one buys nothing (Bolum 30.5).
        assertThat(failed.retryable()).isFalse();
    }

    @Test
    void aLanguageThatCouldNotBeSettledBecomesAQuestionCarryingItsGuess() {
        when(structuring.structure(any(), any())).thenReturn(
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
        when(structuring.structure(any(), any())).thenReturn(
                Result.err(new PipelineError.AllProvidersUnavailable(List.of("openrouter"))));

        var failed = (JobOutcome.Failed) handler.handle(job(), reported::add);

        assertThat(failed.error().code()).isEqualTo(ErrorCode.ALL_PROVIDERS_UNAVAILABLE);
        assertThat(failed.retryable()).isTrue();
    }

    /**
     * Nothing enqueues an anonymous extraction yet. Refusing beats writing a
     * profile that would belong to nobody and be readable by everybody.
     */
    @Test
    void anExtractionWithNoOwnerIsRefusedRatherThanGivenOne() {
        var orphan = new Job(JobType.PROFILE_EXTRACT, null,
                ProfileExtractionPayload.of(document()).asMap(), Instant.EPOCH);

        var failed = (JobOutcome.Failed) handler.handle(orphan, reported::add);

        assertThat(failed.error().code()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(failed.retryable()).isFalse();
        verify(writer, never()).write(any(), any());
    }

    // -- fixtures ----------------------------------------------------------

    private static Job job() {
        return new Job(JobType.PROFILE_EXTRACT, USER,
                ProfileExtractionPayload.of(document()).asMap(), Instant.EPOCH);
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
