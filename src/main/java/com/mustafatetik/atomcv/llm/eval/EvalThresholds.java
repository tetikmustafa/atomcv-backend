package com.mustafatetik.atomcv.llm.eval;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bolum 53.5's table, as code.
 *
 * <p>Here rather than spelled into each assertion, because a threshold
 * repeated in three suites is three numbers that can disagree — and the one
 * that would disagree quietly is the one nobody is watching.
 *
 * <p><strong>One of these is not a threshold.</strong> Invented technology is
 * zero tolerance: not "almost never", not "under a percent". A CV that claims
 * a skill because a posting asked for it is not a better CV, it is a false one,
 * and the person has to defend it in an interview. Everything else here is a
 * quality bar; that one is the product's promise.
 */
public final class EvalThresholds {

    /** Faz D: the numbers in a bullet survived the rewrite. */
    public static final String NUMBERS_PRESERVED = "numbers_preserved";

    /** Faz D: the names did. */
    public static final String ENTITIES_PRESERVED = "entities_preserved";

    /** Faz D, and the one that blocks: no technology the atom never claimed. */
    public static final String NO_NEW_TECHNOLOGIES = "no_new_technologies";

    /** Faz D: a rewrite that grew by a quarter costs a line somebody else needed. */
    public static final String LENGTH_WITHIN_BOUNDS = "length_within_bounds";

    /** Faz D: how often the validator threw an answer away. */
    public static final String VALIDATION_ACCEPTED = "validation_accepted";

    /** Faz A: the answer fitted the schema. */
    public static final String SCHEMA_CONFORMS = "schema_conforms";

    /** Faz A: the skills the posting insisted on were found. */
    public static final String REQUIRED_SKILLS_FOUND = "required_skills_found";

    private static final Map<String, Double> FLOORS = floors();

    private EvalThresholds() {
    }

    private static Map<String, Double> floors() {
        var floors = new LinkedHashMap<String, Double>();
        floors.put(SCHEMA_CONFORMS, 0.99);
        floors.put(REQUIRED_SKILLS_FOUND, 0.90);
        floors.put(NUMBERS_PRESERVED, 0.98);
        floors.put(ENTITIES_PRESERVED, 0.98);
        // Zero invented technologies means a rate of exactly one.
        floors.put(NO_NEW_TECHNOLOGIES, 1.00);
        floors.put(LENGTH_WITHIN_BOUNDS, 0.75);
        floors.put(VALIDATION_ACCEPTED, 0.95);
        return Map.copyOf(floors);
    }

    /**
     * @return the floor this metric has to clear
     * @throws IllegalArgumentException for a metric with no threshold. A suite
     *         measuring something nobody set a bar for is measuring nothing:
     *         the number would be printed, read as fine, and mean nothing
     */
    public static double floorFor(String metric) {
        Double floor = FLOORS.get(metric);
        if (floor == null) {
            throw new IllegalArgumentException(
                    "No threshold is set for " + metric + "; add it to Bolum 53.5 first");
        }
        return floor;
    }

    /** Whether a metric is the one that blocks rather than warns. */
    public static boolean isBlocking(String metric) {
        return NO_NEW_TECHNOLOGIES.equals(metric);
    }

    public static Verdict verdictFor(String metric, double rate) {
        if (rate >= floorFor(metric)) {
            return Verdict.PASSED;
        }
        return isBlocking(metric) ? Verdict.BLOCKED : Verdict.BELOW;
    }

    public static String describe(String metric) {
        return String.format(Locale.ROOT, "%.0f%%", floorFor(metric) * 100);
    }

    /** What a rate did against its floor. */
    public enum Verdict {

        PASSED("ok"),

        /** Under the bar. Worth a conversation, not necessarily a refusal. */
        BELOW("low"),

        /**
         * Under a bar that is the product's promise rather than a quality
         * target. A prompt that invents a technology does not ship.
         */
        BLOCKED("BLOCKER");

        private final String symbol;

        Verdict(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }
}
