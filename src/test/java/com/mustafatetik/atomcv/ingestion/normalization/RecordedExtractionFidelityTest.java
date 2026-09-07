package com.mustafatetik.atomcv.ingestion.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * P3's guard, measured against a real extraction rather than a written one.
 *
 * <p>{@link ExtractionFidelityTest} proves the rule: an atom naming something
 * the document does not say raises a warning, and one that only rewords does
 * not. What it cannot say is how often the rule is <em>wrong</em> — and that
 * number is the whole question, because a check that flags a clean import is
 * one the person learns to click past, and it decides whether this can ever
 * become a refusal rather than a warning.
 *
 * <p>It was not askable at all until {@code local-record} began keeping the
 * document beside the answer: the extraction was recorded and the file it was
 * read from was gone, so measuring meant uploading a CV and reading the logs.
 *
 * <p><strong>Skipped where the recording is not on disk</strong>, which is
 * everywhere but a clone that has run {@code make record}. The recording is the
 * developer's own CV and {@code .gitignore} keeps that whole directory out of a
 * public repository — so this is a measurement the machine that has the data
 * takes, not a fixture anybody can commit.
 */
class RecordedExtractionFidelityTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Path RECORDINGS =
            Path.of("src", "test", "resources", "fixtures", "llm", "profile_extraction");

    /**
     * Zero, and it has been zero on an eighty-four-atom CV. A single false
     * positive here is worth reading rather than tuning around: the rule
     * compares against the whole document, so anything it reports is a name the
     * file genuinely does not carry.
     */
    @Test
    void arealExtractionRaisesNoWarningAgainstTheDocumentItWasReadFrom() throws IOException {
        List<Recording> recordings = recorded();
        if (recordings.isEmpty()) {
            return;
        }

        var reported = new ArrayList<String>();
        int atoms = 0;
        int entries = 0;
        for (Recording recording : recordings) {
            for (var section : recording.profile().sections()) {
                for (var entry : section.entries()) {
                    entries++;
                    atoms += entry.atoms().size();
                    ExtractionFidelity.check(entry, recording.source())
                            .ifPresent(warning -> reported.add(warning.detail()));
                }
            }
        }

        assertThat(atoms)
                .as("a recording worth measuring against")
                .isPositive();
        assertThat(reported)
                .as("%d atoms in %d entries across %d recording(s)",
                        atoms, entries, recordings.size())
                .isEmpty();
    }

    /** An answer and the prompt it was read from, where both are on disk. */
    private record Recording(ExtractedProfile profile, String source) {
    }

    private static List<Recording> recorded() throws IOException {
        if (!Files.isDirectory(RECORDINGS)) {
            return List.of();
        }
        var found = new ArrayList<Recording>();
        try (Stream<Path> answers = Files.list(RECORDINGS)) {
            for (Path answer : answers.filter(RecordedExtractionFidelityTest::isAnswer).toList()) {
                Path source = sourceBeside(answer);
                if (Files.isRegularFile(source)) {
                    found.add(new Recording(
                            JSON.readValue(answer.toFile(), ExtractedProfile.class),
                            Files.readString(source, StandardCharsets.UTF_8)));
                }
            }
        }
        return found;
    }

    private static boolean isAnswer(Path file) {
        return file.getFileName().toString().endsWith(".json");
    }

    /** {@code v1-abc.json} and {@code v1-abc.source.txt} share a stem. */
    private static Path sourceBeside(Path answer) {
        String name = answer.getFileName().toString();
        return answer.resolveSibling(
                name.substring(0, name.length() - ".json".length()) + ".source.txt");
    }
}
