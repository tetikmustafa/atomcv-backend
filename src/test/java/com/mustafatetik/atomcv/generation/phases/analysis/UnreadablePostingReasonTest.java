package com.mustafatetik.atomcv.generation.phases.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.shared.error.UnreadablePostingReason;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The eight reasons and the two checks that raise them (F-016).
 *
 * <p>It lives in this package because {@link PlausibilityGate.Verdict} is not
 * public — the mapping is checked where both halves are visible.
 *
 * <p>What these hold is a correspondence rather than a behaviour: a reason no
 * check produces would be a value the frontend writes a sentence for and never
 * sees, and a check with no reason cannot compile — but only because every
 * verdict names one, which is what the first test is here to keep true.
 */
class UnreadablePostingReasonTest {

    @Test
    void everyReasonIsRaisedBySomeCheck() {
        Set<UnreadablePostingReason> raised = Stream.concat(
                        refusals(JobDescriptionPreflight.Verdict.values())
                                .map(JobDescriptionPreflight.Verdict::reason),
                        refusals(PlausibilityGate.Verdict.values())
                                .map(PlausibilityGate.Verdict::reason))
                .collect(Collectors.toCollection(
                        () -> EnumSet.noneOf(UnreadablePostingReason.class)));

        assertThat(raised).containsExactlyInAnyOrder(UnreadablePostingReason.values());
    }

    @Test
    void noReasonIsRaisedByBothChecks() {
        // The two are asked different questions and the answer says which was
        // asked: a value both could produce would make `reason` ambiguous
        // exactly where the frontend branches on it.
        var fromPreflight = refusals(JobDescriptionPreflight.Verdict.values())
                .map(JobDescriptionPreflight.Verdict::reason).toList();
        var fromGate = refusals(PlausibilityGate.Verdict.values())
                .map(PlausibilityGate.Verdict::reason).toList();

        assertThat(fromPreflight).doesNotHaveDuplicates().noneMatch(fromGate::contains);
        assertThat(fromGate).doesNotHaveDuplicates();
    }

    @Test
    void eachSideAgreesWithTheReasonAboutWhichCheckItCameFrom() {
        assertThat(refusals(JobDescriptionPreflight.Verdict.values())
                .map(JobDescriptionPreflight.Verdict::reason))
                .allMatch(reason -> !reason.isFromGate());
        assertThat(refusals(PlausibilityGate.Verdict.values())
                .map(PlausibilityGate.Verdict::reason))
                .allMatch(UnreadablePostingReason::isFromGate);
    }

    @Test
    void anAcceptedVerdictHasNoReasonToGive() {
        assertThatThrownBy(JobDescriptionPreflight.Verdict.ACCEPTED::reason)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(PlausibilityGate.Verdict.ACCEPTED::reason)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theWireValueIsLowercaseUnderAnyDefaultLocale() {
        // Absolute rule 7. NOT_JOB_LIKE is the one that catches it: a Turkish
        // default locale turns its I into a dotless i.
        var previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(new java.util.Locale.Builder()
                    .setLanguage("tr").setRegion("TR").build());
            assertThat(UnreadablePostingReason.NOT_JOB_LIKE.wireValue())
                    .isEqualTo("not_job_like");
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    private static <V extends Enum<V>> Stream<V> refusals(V[] values) {
        return Arrays.stream(values).filter(value -> !value.name().equals("ACCEPTED"));
    }
}
