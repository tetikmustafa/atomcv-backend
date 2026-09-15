package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.phases.analysis.JobDescriptionPreflight;
import com.mustafatetik.atomcv.generation.phases.analysis.JobDescriptionPreflight.Verdict;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The heuristics, against the postings of Bolum 51.3.
 *
 * <p><strong>Why these and not the ones already in
 * {@code JobDescriptionPreflightTest}.</strong> Those inputs were written by
 * the person writing the test, which makes them a check that the code does what
 * its author meant. These are postings: written as a recruiter would write one,
 * long before anyone looked at the thresholds, and a heuristic that rejects one
 * of them has found a real customer rather than a bad test.
 *
 * <p><strong>Seven of the nine are accepted, and that is the point.</strong>
 * Bolum 18.1 is a door that is meant to be almost always open — it refuses the
 * two shapes nobody can analyse and asks about everything else. A gate that
 * turned away a Turkish posting, a bilingual one, one with no requirements
 * list, or one carrying an injection attempt would be worse than no gate at
 * all: the injection is caught by the layers reading the model's
 * <em>answer</em>, and refusing the text at the door would refuse real
 * postings that merely quote a system message.
 *
 * <p>The order of the checks is what {@code very_long_corporate} holds: twelve
 * thousand characters of real prose repeat themselves enough to look
 * low-entropy, and the length check running first is why that file is accepted
 * rather than refused for being repetitive.
 */
class GoldenPostingsTest {

    private static final String PATH = "golden/jobs/%s.txt";

    static Stream<Arguments> everyPosting() {
        return Stream.of(
                // The two shapes nobody can analyse.
                Arguments.of("vague_short", Verdict.TOO_SHORT),
                Arguments.of("unrelated_marketing", Verdict.NOT_JOB_LIKE),

                // And everything else, which the door lets through.
                Arguments.of("backend_go_k8s_en", Verdict.ACCEPTED),
                Arguments.of("data_engineer_tr", Verdict.ACCEPTED),
                Arguments.of("anonymous_company", Verdict.ACCEPTED),
                Arguments.of("mixed_language", Verdict.ACCEPTED),
                Arguments.of("no_requirements_section", Verdict.ACCEPTED),
                Arguments.of("injection_attempt", Verdict.ACCEPTED),
                Arguments.of("very_long_corporate", Verdict.ACCEPTED),
                Arguments.of("senior_java_spring_en", Verdict.ACCEPTED));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPosting")
    void thepreflightAnswersWhatTheSetSaysItShould(String name, Verdict expected) {
        assertThat(JobDescriptionPreflight.check(posting(name))).isEqualTo(expected);
    }

    /**
     * <strong>A Turkish posting is prose to this gate.</strong> The signal
     * vocabulary is bilingual and the count is of <em>distinct</em> signals —
     * a posting saying "deneyim" nine times has said one thing, not nine.
     * Written out because the vocabulary is the only part of the heuristic
     * that a new language breaks, and because {@code data_engineer_tr} passing
     * tells you nothing about which of the two halves did the work.
     */
    @Test
    void theturkishVocabularyIsWhatCarriesTheTurkishPosting() {
        String posting = posting("data_engineer_tr");

        assertThat(posting).contains("Sorumluluklar").contains("Aranan nitelikler");
        assertThat(JobDescriptionPreflight.check(posting)).isEqualTo(Verdict.ACCEPTED);
    }

    /**
     * The injection fixture is data here and nowhere else.
     *
     * <p>The first layer is structural and the third reads the answer; neither
     * is this door, and this test exists to say so. A preflight that started
     * refusing postings containing the words "ignore all previous
     * instructions" would refuse a posting quoting them, and would still not
     * stop an attempt phrased any other way.
     */
    @Test
    void theinjectionAttemptIsNotRefusedAtTheDoor() {
        String posting = posting("injection_attempt");

        assertThat(posting).contains("Ignore all previous instructions");
        assertThat(JobDescriptionPreflight.check(posting)).isEqualTo(Verdict.ACCEPTED);
    }

    /**
     * Length before entropy, "yoksa 40.000 karakterlik tekrarli bir yapistirma
     * 'tekrarli oldugu icin' reddedilir, gercekte oldugu sey icin degil". This
     * file is the other side of that ordering — long, repetitive in the way
     * corporate prose is, and a real posting.
     */
    @Test
    void alongRealPostingIsNotRefusedForRepeatingItself() {
        String posting = posting("very_long_corporate");

        assertThat(posting.length()).isGreaterThan(10_000);
        assertThat(JobDescriptionPreflight.check(posting)).isEqualTo(Verdict.ACCEPTED);
    }

    private static String posting(String name) {
        String resource = String.format(PATH, name);
        try (InputStream in = GoldenPostingsTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No golden posting at " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
