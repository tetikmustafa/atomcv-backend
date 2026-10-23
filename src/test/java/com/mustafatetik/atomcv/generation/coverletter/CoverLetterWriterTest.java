package com.mustafatetik.atomcv.generation.coverletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Which language a letter is written in (F-045).
 *
 * <p>The concept document promises the CV and the letter a choice each. The
 * CV's arrived with {@code GenerationRequest.language}; the letter's came only
 * from the profile's default until the request could name one.
 */
class CoverLetterWriterTest {

    private static final UUID PROFILE = UUID.randomUUID();

    private final CoverLetterService letters = mock(CoverLetterService.class);
    private final CoverLetterWriter writer = new CoverLetterWriter(letters,
            Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void alanguageAskedForThisLetterWins() {
        assertThat(languageWritten("tr")).isEqualTo("tr");
    }

    /** The profile's {@code auto} follows the posting, which is in English. */
    @Test
    void noLanguageLeavesItToTheProfile() {
        assertThat(languageWritten(null)).isEqualTo("en");
    }

    private String languageWritten(String asked) {
        writer.write(profile(), new ProfileTree(PROFILE, List.of()), emptySelection(),
                posting(), "", asked, CoverLetterStyle.DEFAULT, "a-user", null, null);

        ArgumentCaptor<CoverLetterInput> input = ArgumentCaptor.forClass(CoverLetterInput.class);
        verify(letters).write(input.capture(), any(), anyString(), any(), any());
        return input.getValue().language();
    }

    private static Profile profile() {
        Profile profile = new Profile(UUID.randomUUID());
        profile.setContact(new Contact("Ada Lovelace", "ada@example.com", null,
                null, null, null, "Istanbul"));
        return profile;
    }

    private static SelectionState emptySelection() {
        return new SelectionState(List.of(), List.of(),
                new SelectionState.BudgetBreakdown(600, 100, 500, 300));
    }

    private static JobAnalysis posting() {
        return new JobAnalysis(
                new JobAnalysis.Role("Backend Engineer", JobAnalysis.Seniority.SENIOR,
                        "fintech", JobAnalysis.EmploymentType.FULL_TIME,
                        JobAnalysis.WorkMode.REMOTE),
                new JobAnalysis.Company("Acme", JobAnalysis.SizeHint.SCALEUP),
                List.of(), List.of(), List.of("run the platform"), List.of(),
                new JobAnalysis.ExperienceYears(5, null),
                List.of("en"), "technical", "en", 0.94, List.of());
    }
}
