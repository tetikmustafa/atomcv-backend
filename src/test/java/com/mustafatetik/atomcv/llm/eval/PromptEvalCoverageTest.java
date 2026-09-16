package com.mustafatetik.atomcv.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every prompt is either scored by a suite or written down as unscored.
 *
 * <p><strong>Written because "there is an eval lane" was read as "the prompts
 * are evaluated"</strong> (denetim, beşinci tur). There was one suite, for Faz
 * D, and the CI lane fired whenever <em>any</em> file under
 * {@code prompts/} changed — so editing {@code job_analysis/v2.md} ran the
 * covering letter's neighbour, scored it, and went green. Two of § 53.5's
 * thresholds had floors and no observations behind them, which is the shape
 * this repository has now met several times: a declaration read as a
 * measurement.
 *
 * <p><strong>The exemptions are the point, not a loophole.</strong> A prompt
 * with no suite is a decision — § 53.5 sets floors for Faz A, D and F and for
 * nothing else, and inventing thresholds for the other five would be inventing
 * product decisions in a test file. What this class refuses is the third
 * state: a prompt nobody has decided about. Adding one now fails here, on a
 * lane that costs nothing, rather than being discovered by an audit.
 */
class PromptEvalCoverageTest {

    private static final Path PROMPTS = Path.of("src/main/resources/prompts");

    private static final Path SUITES =
            Path.of("src/integrationTest/java/com/mustafatetik/atomcv/llm/eval");

    /** Prompt id to the suite that scores it. */
    private static final Map<String, String> SCORED = Map.of(
            "job_analysis", "JobAnalysisEvalIT.java",
            "bullet_rewrite", "BulletRewriteEvalIT.java");

    /** Prompt id to why nothing scores it, which has to be a reason. */
    private static final Map<String, String> UNSCORED = unscored();

    private static Map<String, String> unscored() {
        // Insertion order, because this is read by a person and a salted one
        // would reorder the list on every run for no reason.
        var reasons = new LinkedHashMap<String, String>();
        reasons.put("profile_extraction",
                "Scored on the production path instead: ExtractionFidelity checks every "
                        + "extracted atom against the uploaded document and raises "
                        + "UNSUPPORTED_BY_SOURCE. A sampled rate would say less than a "
                        + "check that runs on every import.");
        reasons.put("translation",
                "The validator refuses a translation that dropped a number or a proper "
                        + "noun and the wording stays stale, so a bad answer is visible "
                        + "in the product rather than in a score.");
        reasons.put("about_synthesis",
                "No floor in section 53.5. The paragraph it writes is checked by the same "
                        + "rewrite validator, and its own quality is a judgement nobody "
                        + "has reduced to a number yet.");
        reasons.put("cover_letter",
                "No floor in section 53.5, and the one measurement that exists is a word "
                        + "band recorded in notes (v1 against v2). Worth a suite the day "
                        + "somebody sets a threshold for a cliche rate.");
        reasons.put("selection_edit",
                "It returns line numbers, not prose. Correctness is exact and is covered "
                        + "by integration tests that need no model judgement.");
        return Map.copyOf(reasons);
    }

    @Test
    void everyPromptIsEitherScoredOrDeliberatelyNot() {
        assertThat(promptIds())
                .as("a prompt nobody has decided about; add a suite or a reason")
                .allSatisfy(id -> assertThat(SCORED.containsKey(id) || UNSCORED.containsKey(id))
                        .as("prompt '%s'", id)
                        .isTrue());
    }

    /**
     * And neither list names a prompt that is not there — the failure that put
     * {@code atom_rewrite} and {@code edit_intent} in the chapter for a stage,
     * pointing readers at directories nobody could open.
     */
    @Test
    void neitherListInventsAprompt() {
        List<String> real = promptIds();

        assertThat(Stream.concat(SCORED.keySet().stream(), UNSCORED.keySet().stream()))
                .as("named here, absent from prompts/")
                .allSatisfy(named -> assertThat(real).contains(named));
    }

    /** A suite named here is a file somebody can open. */
    @Test
    void everyNamedSuiteExists() {
        assertThat(SCORED.values())
                .allSatisfy(suite -> assertThat(SUITES.resolve(suite)).exists());
    }

    /**
     * <strong>A suite is named after its prompt, and that is load-bearing.</strong>
     *
     * <p>The CI lane derives the file name from the changed directory to work
     * out whether the prompt somebody edited has a suite at all — which is the
     * thing it used to get wrong, running the Faz D suite for a Faz A change
     * and reporting green. Deriving it beats a second copy of this map in
     * YAML, and this is what keeps the derivation true.
     */
    @Test
    void asuiteIsNamedAfterThePromptItScores() {
        assertThat(SCORED).allSatisfy((id, suite) ->
                assertThat(suite).isEqualTo(suiteNameFor(id)));
    }

    /** {@code job_analysis} to {@code JobAnalysisEvalIT.java}. */
    static String suiteNameFor(String promptId) {
        var name = new StringBuilder();
        for (String word : promptId.split("_")) {
            name.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return name.append("EvalIT.java").toString();
    }

    /** The assertion that keeps the three above from passing on an empty read. */
    @Test
    void thepromptsWereActuallyRead() {
        assertThat(promptIds()).hasSizeGreaterThan(5);
    }

    private static List<String> promptIds() {
        try (Stream<Path> tree = Files.list(PROMPTS)) {
            return tree.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException missing) {
            throw new UncheckedIOException(
                    "prompts/ is what this test is about", missing);
        }
    }
}
