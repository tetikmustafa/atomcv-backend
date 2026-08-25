package com.mustafatetik.atomcv.generation.phases.analysis;

import com.mustafatetik.atomcv.shared.error.UnreadablePostingReason;

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

    /**
     * Why an analysis was rejected, or {@link Verdict#ACCEPTED}.
     *
     * <p>Each refusal names the {@link UnreadablePostingReason} it travels as.
     * Naming it here rather than mapping it in the phase is what makes the
     * mapping exhaustive: a verdict added without one does not compile, and
     * a refusal that reached the wire with no reason is exactly the defect
     * {@code reason} was added to close (F-016).
     */
    enum Verdict {

        ACCEPTED(null),

        /** The model reported it was guessing. */
        LOW_CONFIDENCE(UnreadablePostingReason.LOW_CONFIDENCE),

        /** Fewer than two required skills: nothing to score a profile against. */
        TOO_FEW_SKILLS(UnreadablePostingReason.TOO_FEW_SKILLS),

        /** No responsibilities: Faz B has nothing to match bullets to. */
        NO_RESPONSIBILITIES(UnreadablePostingReason.NO_RESPONSIBILITIES),

        /**
         * A field is far longer than that field ever is.
         *
         * <p>Kept apart from the others because it means something different:
         * the first three say the posting was thin, this one says the answer
         * is not shaped like an analysis at all.
         */
        SUSPICIOUS_OUTPUT(UnreadablePostingReason.SUSPICIOUS_OUTPUT);

        private final UnreadablePostingReason reason;

        Verdict(UnreadablePostingReason reason) {
            this.reason = reason;
        }

        boolean isAccepted() {
            return this == ACCEPTED;
        }

        /** @throws IllegalStateException if called on {@link #ACCEPTED} */
        UnreadablePostingReason reason() {
            if (reason == null) {
                throw new IllegalStateException("ACCEPTED is not a refusal");
            }
            return reason;
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
