package com.mustafatetik.atomcv.ingestion.github;

/**
 * Jaro-Winkler, for matching a repository name to a project somebody already
 * wrote about.
 *
 * <p><strong>Why this and not an embedding.</strong> Bolum 31.8 names both,
 * and the pairs this has to catch are spelling rather than meaning:
 * {@code order-management-system} against "Order Management System",
 * {@code atomcv-backend} against "AtomCV". An embedding round trip per
 * repository buys nothing for those and costs a call each on a screen that is
 * meant to open quickly; a string distance answers exactly the question being
 * asked.
 *
 * <p>Its own implementation rather than a dependency, for the reason Bolum 5.4
 * gives about SDKs and Bolum 53.3 gives about Guava: it is thirty lines of
 * arithmetic with a published specification, and a library for it is a
 * transitive tree and a version to watch.
 *
 * <p>Pure, so the suggestion list is the same list twice (Bolum 19.6's habit).
 */
final class JaroWinkler {

    /** The standard prefix weight; four characters is the standard cap. */
    private static final double PREFIX_WEIGHT = 0.1;
    private static final int MAX_PREFIX = 4;

    /** Winkler's own condition for applying the prefix bonus at all. */
    private static final double BOOST_THRESHOLD = 0.7;

    private JaroWinkler() {
    }

    static double similarity(String left, String right) {
        if (left == null || right == null) {
            return 0;
        }
        if (left.equals(right)) {
            return 1;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }

        double jaro = jaro(left, right);
        if (jaro < BOOST_THRESHOLD) {
            return jaro;
        }
        int prefix = 0;
        while (prefix < Math.min(MAX_PREFIX, Math.min(left.length(), right.length()))
                && left.charAt(prefix) == right.charAt(prefix)) {
            prefix++;
        }
        return jaro + prefix * PREFIX_WEIGHT * (1 - jaro);
    }

    private static double jaro(String left, String right) {
        int window = Math.max(left.length(), right.length()) / 2 - 1;
        if (window < 0) {
            window = 0;
        }

        boolean[] leftMatched = new boolean[left.length()];
        boolean[] rightMatched = new boolean[right.length()];
        int matches = 0;

        for (int i = 0; i < left.length(); i++) {
            int from = Math.max(0, i - window);
            int to = Math.min(i + window + 1, right.length());
            for (int j = from; j < to; j++) {
                if (!rightMatched[j] && left.charAt(i) == right.charAt(j)) {
                    leftMatched[i] = true;
                    rightMatched[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) {
            return 0;
        }

        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < left.length(); i++) {
            if (!leftMatched[i]) {
                continue;
            }
            while (!rightMatched[k]) {
                k++;
            }
            if (left.charAt(i) != right.charAt(k)) {
                transpositions++;
            }
            k++;
        }

        double m = matches;
        return (m / left.length() + m / right.length() + (m - transpositions / 2.0) / m) / 3;
    }
}
