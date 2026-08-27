package com.mustafatetik.atomcv.shared.math;

/**
 * The one cosine in this codebase.
 *
 * <p>Two phases compare embeddings — Faz B scores atoms against a posting, and
 * Faz D checks that a rewrite still means what it meant — and a second
 * implementation of the same arithmetic is a place for the two to disagree by
 * a rounding rule nobody would ever find.
 *
 * <p>What is <em>not</em> here is what to do when the vectors cannot be
 * compared. Faz B treats a missing embedding as a neutral half-score so that a
 * profile without vectors still ranks; Faz D treats it as "cannot check", and
 * a check that cannot run must not silently pass as one that did. Those are
 * different decisions and they stay with the callers that own them.
 */
public final class Vectors {

    private Vectors() {
    }

    /**
     * @throws IllegalArgumentException when the vectors are absent or of
     *         different lengths — comparing them would produce a number that
     *         looks like a similarity and is not one
     */
    public static double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length != right.length) {
            throw new IllegalArgumentException(
                    "Two vectors of the same length are needed to compare them");
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
            leftNorm += (double) left[i] * left[i];
            rightNorm += (double) right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            // A zero vector has no direction, so it has no angle to anything.
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
