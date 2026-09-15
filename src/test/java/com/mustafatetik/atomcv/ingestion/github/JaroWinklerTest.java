package com.mustafatetik.atomcv.ingestion.github;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The distance behind the matching, and the numbers the threshold was chosen
 * against.
 *
 * <p>Written because {@code MERGE_THRESHOLD} is a decision that reads as a
 * magic number otherwise. What it has to do is put the pairs below on the
 * right sides of one line — and the expensive mistake is the merge, because a
 * wrong one puts a link on somebody's description of different work.
 */
class JaroWinklerTest {

    private static final double THRESHOLD = GitHubImportService.MERGE_THRESHOLD;

    /**
     * The pair this exists for. Normalised, a repository name and the project
     * title it belongs to are usually the same string with different
     * punctuation.
     */
    @Test
    void arepositoryNameMatchesTheProjectItIs() {
        assertThat(score("order-management-system", "Order Management System"))
                .isGreaterThanOrEqualTo(THRESHOLD);
        assertThat(score("atomcv-backend", "AtomCV Backend"))
                .isGreaterThanOrEqualTo(THRESHOLD);
        assertThat(score("weather-radar", "Weather Radar App"))
                .isGreaterThanOrEqualTo(THRESHOLD);
    }

    /** Two different projects in one profile do not. */
    @Test
    void twoDifferentProjectsDoNot() {
        assertThat(score("weather-radar", "Order Management System")).isLessThan(THRESHOLD);
        assertThat(score("invoice-parser", "Invoice Dashboard")).isLessThan(THRESHOLD);
        assertThat(score("atomcv-frontend", "AtomCV Backend")).isLessThan(THRESHOLD);
    }

    /**
     * <strong>The near miss the threshold was moved for.</strong> Different
     * work by the same person, sharing a whole prefix -- and Jaro-Winkler pays
     * a bonus for exactly that, so this scores 0.92. An earlier threshold of
     * 0.86 was picked by eye and would have merged them, putting a link to a
     * gateway on somebody's description of an API.
     */
    @Test
    void anearMissIsNotAmatch() {
        assertThat(score("payments-api", "Payments API Gateway"))
                .isGreaterThan(0.9)
                .isLessThan(THRESHOLD);
    }

    /**
     * <strong>And the one this cannot do, written down rather than
     * hidden.</strong> An abbreviation is a true match and scores below the
     * near miss above it; no single string distance separates the two, which
     * is why an embedding was named as well. It is offered as a new project
     * instead, which the person can decline -- the safe direction.
     */
    @Test
    void anabbreviationIsMissedAndIsOfferedAsAnewProjectInstead() {
        assertThat(score("order-mgmt-system", "Order Management System"))
                .isLessThan(THRESHOLD);
    }

    @Test
    void theedgesAreDefined() {
        assertThat(JaroWinkler.similarity("same", "same")).isEqualTo(1);
        assertThat(JaroWinkler.similarity("", "anything")).isZero();
        assertThat(JaroWinkler.similarity(null, "anything")).isZero();
        assertThat(JaroWinkler.similarity("anything", null)).isZero();
    }

    /** Normalisation is the service's, and this is what it does for the pairs above. */
    private static double score(String repository, String title) {
        return JaroWinkler.similarity(
                GitHubImportService.normalised(repository),
                GitHubImportService.normalised(title));
    }
}
