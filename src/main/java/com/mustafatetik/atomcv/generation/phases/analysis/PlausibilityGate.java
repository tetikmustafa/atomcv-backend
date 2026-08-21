package com.mustafatetik.atomcv.generation.phases.analysis;

/**
 * What the model said, judged before anything is built on it (Bolum 18.4).
 *
 * <p>A gate on the answer rather than on the input, and the reason it earns its
 * place is cost: an analysis that fails here never reaches Faz B, so a posting
 * that was not a posting is paid for once instead of through the whole
 * pipeline.
 *
 * <p>The length audit is the other half of Bolum 18.3's injection defence. The
 * fence tells the model the region is data; this notices when it stopped
 * believing that. A prompt injected into a posting does not produce a shorter
 * answer — it produces a skill named with a paragraph, or a title carrying an
 * instruction, and those have shapes.
 */
final class PlausibilityGate {

    /** Bolum 18.4, verbatim. Below this the model is guessing. */
    static final double MIN_CONFIDENCE = 0.55;

    /** One skill is a mention; two is a requirement list. */
    static final int MIN_REQUIRED_SKILLS = 2;

    // Bolum 18.4's field-length ceilings. Each is far above anything a real
    // posting produces and far below what an injected instruction needs.
    static final int MAX_SKILL_NAME = 60;
    static final int MAX_KEYWORD = 100;
    static final int MAX_TITLE = 120;
    static final int MAX_RESPONSIBILITY = 300;

    private PlausibilityGate() {
    }

    /** Why an analysis was rejected, or {@link Verdict#ACCEPTED}. */
    enum Verdict {

        ACCEPTED,

        /** The model reported it was guessing. */
        LOW_CONFIDENCE,

        /** Fewer than two required skills: nothing to score a profile against. */
        TOO_FEW_SKILLS,

        /** No responsibilities: Faz B has nothing to match bullets to. */
        NO_RESPONSIBILITIES,

        /**
         * A field is far longer than that field ever is.
         *
         * <p>Kept apart from the others because it means something different:
         * the first three say the posting was thin, this one says the answer
         * is not shaped like an analysis at all.
         */
        SUSPICIOUS_OUTPUT;

        boolean isAccepted() {
            return this == ACCEPTED;
        }
    }

    static Verdict check(JobAnalysis analysis) {
        if (analysis.confidence() < MIN_CONFIDENCE) {
            return Verdict.LOW_CONFIDENCE;
        }
        if (analysis.requiredSkills().size() < MIN_REQUIRED_SKILLS) {
            return Verdict.TOO_FEW_SKILLS;
        }
        if (analysis.responsibilities().isEmpty()) {
            return Verdict.NO_RESPONSIBILITIES;
        }
        if (hasAbnormalFieldLength(analysis)) {
            return Verdict.SUSPICIOUS_OUTPUT;
        }
        return Verdict.ACCEPTED;
    }

    private static boolean hasAbnormalFieldLength(JobAnalysis analysis) {
        return analysis.allSkills().anyMatch(skill -> skill.name().length() > MAX_SKILL_NAME)
                || analysis.keywords().stream().anyMatch(word -> word.length() > MAX_KEYWORD)
                || analysis.role().title().length() > MAX_TITLE
                || analysis.responsibilities().stream()
                        .anyMatch(duty -> duty.length() > MAX_RESPONSIBILITY);
    }
}
