package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bolum 21.6's five checks, one at a time.
 *
 * <p>The third is the one this product exists for. The other four catch a
 * model being careless; that one catches it writing something the person will
 * be asked about in an interview and cannot answer.
 */
class RewriteValidatorTest {

    private static final List<String> POSTING =
            List.of("kubernetes", "microsoft-fabric", "terraform");

    // -- 3. the claim that was never made ----------------------------------

    /**
     * The failure mode with zero tolerance. The posting asked for Kubernetes,
     * the atom has never touched Kubernetes, and the model helpfully worked it
     * in — this is a false CV, not a better one.
     */
    @Test
    void aclaimTheAtomCannotSupportIsRefused() {
        var candidate = candidate("Moved 300K rows with Microsoft Fabric",
                List.of("microsoft-fabric"), List.of("300K"), List.of("Microsoft Fabric"));

        var issues = RewriteValidator.validate(candidate,
                "Moved 300K rows with Microsoft Fabric on Kubernetes", POSTING, null, null);

        assertThat(issues).containsExactly(RewriteIssue.UNSUPPORTED_CLAIM);
    }

    /** A skill the atom does have is exactly what the phase is for. */
    @Test
    void askillTheAtomDoesHaveMayBeSaidInThePostingsWords() {
        var candidate = candidate("Built the nightly job on Microsoft Fabric",
                List.of("microsoft-fabric"), List.of(), List.of("Microsoft Fabric"));

        var issues = RewriteValidator.validate(candidate,
                "Built the batch pipeline on Microsoft Fabric", POSTING, null, null);

        assertThat(issues).isEmpty();
    }

    /**
     * <strong>The word the person wrote themselves.</strong> Extraction misses
     * skills — it reads a CV, it does not interrogate one — and an atom whose
     * list is short would otherwise have every rewrite of it refused for
     * keeping a word it was told to keep. That is not a guard, it is an
     * outage that looks like a guard.
     */
    @Test
    void atechnologyAlreadyInTheOriginalIsNotACharge() {
        var candidate = candidate("Ran the cluster on Kubernetes for two years",
                List.of("linux"), List.of(), List.of());

        var issues = RewriteValidator.validate(candidate,
                "Ran the Kubernetes cluster for two years", POSTING, null, null);

        assertThat(issues).isEmpty();
    }

    /** Whole words: "Java" is not a claim to have written "JavaScript". */
    @Test
    void anameInsideAnotherWordIsNotAMention() {
        var candidate = candidate("Wrote the importer in Java", List.of("java"),
                List.of(), List.of());

        var issues = RewriteValidator.validate(candidate,
                "Wrote the importer in Java", List.of("javascript"), null, null);

        assertThat(issues).isEmpty();
    }

    // -- 1 and 2. what may not be lost -------------------------------------

    @Test
    void anumberThatWentMissingIsRefused() {
        var candidate = candidate("Moved 300000 rows nightly",
                List.of("etl"), List.of("300000"), List.of());

        var issues = RewriteValidator.validate(candidate,
                "Moved hundreds of thousands of rows nightly", POSTING, null, null);

        assertThat(issues).containsExactly(RewriteIssue.NUMBER_LOST);
    }

    /**
     * Digits, not strings: one locale writes 300,000 and another 300.000, and
     * a check that rejected the separator would reject correct answers — the
     * failure mode that gets a guard switched off.
     */
    @Test
    void thesameNumberWrittenWithADifferentSeparatorSurvives() {
        var candidate = candidate("Moved 300,000 rows nightly",
                List.of("etl"), List.of("300,000"), List.of());

        var issues = RewriteValidator.validate(candidate,
                "Moved 300.000 rows through the batch pipeline", POSTING, null, null);

        assertThat(issues).isEmpty();
    }

    @Test
    void anameThatWasRewordedIsRefused() {
        var candidate = candidate("Led the migration at Brisa",
                List.of("java"), List.of(), List.of("Brisa"));

        var issues = RewriteValidator.validate(candidate,
                "Led the migration at a large manufacturer", POSTING, null, null);

        assertThat(issues).containsExactly(RewriteIssue.PROPER_NOUN_LOST);
    }

    /** Capitalisation is typography, not a change to the claim. */
    @Test
    void anameWrittenWithDifferentCaseIsStillTheName() {
        var candidate = candidate("Led the migration at BRISA",
                List.of("java"), List.of(), List.of("BRISA"));

        var issues = RewriteValidator.validate(candidate,
                "Led the migration at Brisa", POSTING, null, null);

        assertThat(issues).isEmpty();
    }

    // -- 4. the page promise -----------------------------------------------

    /**
     * The ceiling is checked and not merely asked for. Faz C chose these atoms
     * by their measured cost, so a longer line spends a page the document has
     * already promised.
     */
    @Test
    void ananswerOverTheCeilingIsRefusedEvenIfEverythingElseIsRight() {
        var candidate = candidate("Short line", List.of("java"), List.of(), List.of(), 11);

        var issues = RewriteValidator.validate(candidate,
                "A very much longer line that says the same thing at four times the length",
                POSTING, null, null);

        assertThat(issues).containsExactly(RewriteIssue.TOO_LONG);
    }

    // -- 5. whether it still says what it said ------------------------------

    @Test
    void ananswerThatDriftedTooFarIsRefused() {
        var candidate = candidate("Built the nightly job", List.of("etl"),
                List.of(), List.of());

        var issues = RewriteValidator.validate(candidate, "Built the nightly job", POSTING,
                new float[] {1, 0, 0}, new float[] {0, 1, 0});

        assertThat(issues).containsExactly(RewriteIssue.SEMANTIC_DRIFT);
    }

    @Test
    void ananswerThatStillMeansTheSameThingPasses() {
        var candidate = candidate("Built the nightly job", List.of("etl"),
                List.of(), List.of());

        var issues = RewriteValidator.validate(candidate, "Built the nightly job", POSTING,
                new float[] {1, 0.2f, 0}, new float[] {1, 0, 0});

        assertThat(issues).isEmpty();
    }

    /**
     * <strong>A check that could not run is not a check that passed.</strong>
     * The embedding service being down is a reason to skip this rule, and the
     * other four still stand — but nothing here may report a similarity it did
     * not measure.
     */
    @Test
    void anunreachableEmbeddingServiceSkipsTheDriftCheckAndNothingElse() {
        var candidate = candidate("Moved 300K rows", List.of("etl"), List.of("300K"),
                List.of());

        assertThat(RewriteValidator.validate(candidate, "Moved 300K rows", POSTING, null, null))
                .isEmpty();
        assertThat(RewriteValidator.validate(candidate, "Moved rows", POSTING, null, null))
                .containsExactly(RewriteIssue.NUMBER_LOST);
    }

    // -- fixtures ----------------------------------------------------------

    /**
     * The ceiling is deliberately out of the way here. Every case but one is
     * about a different rule, and a fixture that tripped the length check too
     * would report two issues and hide which one it was testing.
     */
    private static RewriteCandidate candidate(String original, List<String> skills,
            List<String> metrics, List<String> properNouns) {
        return candidate(original, skills, metrics, properNouns, 500);
    }

    private static RewriteCandidate candidate(String original, List<String> skills,
            List<String> metrics, List<String> properNouns, int maxChars) {
        return new RewriteCandidate(UUID.randomUUID(), UUID.randomUUID(),
                RichContent.plain(original), skills, metrics, properNouns,
                0.8, maxChars, RewriteIntent.ADAPT, null);
    }
}
