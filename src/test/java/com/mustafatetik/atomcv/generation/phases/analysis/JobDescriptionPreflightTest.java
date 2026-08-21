package com.mustafatetik.atomcv.generation.phases.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.phases.analysis.JobDescriptionPreflight.Verdict;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Bolum 18.1, and design principle 5 with it: everything that can refuse a
 * generation runs before anything is paid for.
 */
class JobDescriptionPreflightTest {

    /** Bolum 18.1: an empty posting is general CV mode, not a bad posting. */
    @Test
    void anEmptyPostingPassesBecauseThereIsNothingToAnalyse() {
        assertThat(JobDescriptionPreflight.check(null)).isEqualTo(Verdict.ACCEPTED);
        assertThat(JobDescriptionPreflight.check("")).isEqualTo(Verdict.ACCEPTED);
        assertThat(JobDescriptionPreflight.check("   \n  ")).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void aRealTurkishPostingPasses() {
        assertThat(JobDescriptionPreflight.check(turkishPosting())).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void aRealEnglishPostingPasses() {
        assertThat(JobDescriptionPreflight.check(englishPosting())).isEqualTo(Verdict.ACCEPTED);
    }

    // ── Length ───────────────────────────────────────────────────────────

    @Test
    void aHandfulOfWordsIsTooShort() {
        assertThat(JobDescriptionPreflight.check("Backend developer wanted, apply now."))
                .isEqualTo(Verdict.TOO_SHORT);
    }

    /**
     * Long enough in characters and still too few words: a hundred and fifty
     * characters of one long token is not a posting.
     */
    @Test
    void enoughCharactersButTooFewWordsIsStillTooShort() {
        var padded = "responsibilities requirements " + "x".repeat(200);

        assertThat(JobDescriptionPreflight.check(padded)).isEqualTo(Verdict.TOO_SHORT);
    }

    @Test
    void aWholePagePastedInIsTooLong() {
        var huge = englishPosting() + " filler".repeat(6000);

        assertThat(JobDescriptionPreflight.check(huge)).isEqualTo(Verdict.TOO_LONG);
    }

    /**
     * Length is checked before entropy, so a 40,000 character paste is
     * refused for what it is rather than for being repetitive.
     */
    @Test
    void tooLongIsReportedAheadOfLowEntropy() {
        var repetitive = "deneyim ".repeat(5_000);

        assertThat(repetitive.length()).isGreaterThan(20_000);
        assertThat(JobDescriptionPreflight.check(repetitive)).isEqualTo(Verdict.TOO_LONG);
    }

    // ── Entropy ──────────────────────────────────────────────────────────

    @Test
    void awallOfTheSamePhraseIsLowEntropy() {
        var loop = "deneyim aranan pozisyon ".repeat(60);

        assertThat(loop.length()).isBetween(150, 20_000);
        assertThat(JobDescriptionPreflight.check(loop)).isEqualTo(Verdict.LOW_ENTROPY);
    }

    /**
     * The check that would be easiest to make too strict. Turkish inflects
     * heavily, so the same stem appears in several forms and the ratio stays
     * high — a threshold tuned on English alone would refuse real postings.
     */
    @Test
    void realProseSitsWellAboveTheEntropyFloor() {
        assertThat(JobDescriptionPreflight.check(turkishPosting())).isEqualTo(Verdict.ACCEPTED);
        assertThat(JobDescriptionPreflight.check(englishPosting())).isEqualTo(Verdict.ACCEPTED);
    }

    // ── Signal words ─────────────────────────────────────────────────────

    @Test
    void proseThatIsNotAPostingIsRefused() {
        var essay = IntStream.range(0, 60)
                .mapToObj(index -> "sentence" + index)
                .collect(Collectors.joining(" "));

        assertThat(JobDescriptionPreflight.check(essay)).isEqualTo(Verdict.NOT_JOB_LIKE);
    }

    /** Bolum 18.1 asks for at least two, so exactly one is not enough. */
    @Test
    void oneSignalWordIsNotEnough() {
        var almost = "deneyim " + IntStream.range(0, 60)
                .mapToObj(index -> "kelime" + index)
                .collect(Collectors.joining(" "));

        assertThat(JobDescriptionPreflight.check(almost)).isEqualTo(Verdict.NOT_JOB_LIKE);
    }

    @Test
    void twoSignalWordsAreEnough() {
        var enough = "deneyim yetkinlik " + IntStream.range(0, 60)
                .mapToObj(index -> "kelime" + index)
                .collect(Collectors.joining(" "));

        assertThat(JobDescriptionPreflight.check(enough)).isEqualTo(Verdict.ACCEPTED);
    }

    /**
     * Distinct signal words, not occurrences: a posting that says "deneyim"
     * nine times has said one thing, not nine.
     */
    @Test
    void theSameSignalWordRepeatedCountsOnce() {
        var repeated = "deneyim deneyim deneyim deneyim " + IntStream.range(0, 60)
                .mapToObj(index -> "kelime" + index)
                .collect(Collectors.joining(" "));

        assertThat(JobDescriptionPreflight.check(repeated)).isEqualTo(Verdict.NOT_JOB_LIKE);
    }

    /**
     * Absolute rule 7, as this file would break it. Under a Turkish locale
     * {@code "TERCIHEN".toLowerCase()} keeps a dotless i and stops matching
     * the dictionary — the posting would be refused for a reason no one would
     * think to look for.
     */
    @Test
    void theDictionaryStillMatchesUnderATurkishDefaultLocale() {
        var original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var shouting = "TERCIHEN NITELIK " + IntStream.range(0, 60)
                    .mapToObj(index -> "KELIME" + index)
                    .collect(Collectors.joining(" "));

            assertThat(JobDescriptionPreflight.check(shouting)).isEqualTo(Verdict.ACCEPTED);
        } finally {
            Locale.setDefault(original);
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────

    private static String turkishPosting() {
        return """
                Ödeme sistemleri ekibimize kıdemli bir backend geliştirici arıyoruz.
                Pozisyon, günde milyonlarca işlem taşıyan servislerin tasarımından ve
                ölçeklenmesinden sorumlu olacak.

                Aranan nitelikler:
                - Java veya Go ile en az beş yıl deneyim
                - Dağıtık sistemlerde çalışma tecrübesi
                - PostgreSQL üzerinde performans ayarı yetkinliği
                - Kubernetes ile üretim ortamı işletmiş olmak

                Tercihen:
                - Ödeme altyapısı alanında görev almış olmak
                - Terraform bilgisi

                Başvuru için özgeçmişinizi iletmeniz yeterlidir. Ekip uzaktan çalışmaktadır.
                """;
    }

    private static String englishPosting() {
        return """
                We are seeking a senior backend engineer for our payments team.

                Responsibilities:
                - Design and scale payment processing systems
                - Own service reliability and on-call rotation
                - Mentor engineers across the team

                Requirements:
                - Five years of experience with Java or Go
                - Strong background in distributed systems
                - Production experience with PostgreSQL and Kubernetes

                Preferred qualifications:
                - Exposure to fintech or regulated environments
                - Familiarity with Terraform

                Apply with a short note about a system you have scaled. The role is remote.
                """;
    }
}
