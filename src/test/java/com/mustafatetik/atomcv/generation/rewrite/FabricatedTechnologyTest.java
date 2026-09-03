package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.shared.text.SkillNames;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * P3, against the shape that got past it (Bolum 21.6, 21.7).
 *
 * <p>A generated CV reached a real person's hands saying they were "eager to
 * explore modern caching and message queues (Redis, Kafka)". Redis was on the
 * page. Kafka was nowhere in the profile, nowhere in the posting, and — the
 * part that mattered — nowhere in {@code aliases.txt} either, so the guard
 * never asked about it. It walks the names it knows and tests the answer for
 * each, which means an invention it has not heard of is not refused, it is
 * invisible.
 *
 * <p>Every {@code UNSUPPORTED_CLAIM} test written before this one used
 * Kubernetes, and Kubernetes is in the file. The guard had therefore never
 * been seen to fail on the case it exists for, which § 51.7 says is the same
 * as not knowing it works.
 *
 * <p>So the fixtures here are built the other way round on purpose: the
 * technology is <strong>deliberately absent</strong> from the dictionary, the
 * skills and the posting alike. If a future change reintroduces a
 * closed-vocabulary check, these fail.
 */
class FabricatedTechnologyTest {

    /**
     * Not in {@code aliases.txt}, and that is the point. Checked rather than
     * assumed, because the day someone adds it this test would quietly go back
     * to proving what the old ones proved.
     */
    private static final String ABSENT = "Kafka";

    private static final List<String> NO_POSTING = List.of();

    @Test
    void theFabricatedTechnologyIsAbsentFromEveryListTheGuardCouldKnowIt() {
        assertThat(SkillNames.aliases())
                .as("the dictionary must not know it, or this test proves nothing")
                .doesNotContainKey(ABSENT.toLowerCase(Locale.ROOT))
                .doesNotContainValue(ABSENT.toLowerCase(Locale.ROOT));
    }

    /** The summary that actually shipped, minus the profile that could support it. */
    @Test
    void asummaryMayNotInventATechnologyNoDictionaryKnows() {
        var candidate = new AboutCandidate(UUID.randomUUID(),
                RichContent.plain("An agile fast learner building sustainable systems."),
                List.of("redis"), List.of(), "", NO_POSTING, 400);

        var issues = AboutValidator.validate(candidate,
                "Eager to explore modern caching and message queues (Redis, " + ABSENT + ").",
                NO_POSTING);

        assertThat(issues).contains(RewriteIssue.UNSUPPORTED_CLAIM);
    }

    /** The same sentence with the invention taken out is the one that should pass. */
    @Test
    void thesameSummaryWithoutTheInventionIsAccepted() {
        var candidate = new AboutCandidate(UUID.randomUUID(),
                RichContent.plain("An agile fast learner building sustainable systems."),
                List.of("redis"), List.of(), "", NO_POSTING, 400);

        var issues = AboutValidator.validate(candidate,
                "Eager to explore modern caching (Redis).", NO_POSTING);

        assertThat(issues).isEmpty();
    }

    @Test
    void abulletMayNotInventATechnologyNoDictionaryKnows() {
        var candidate = bullet("Built the nightly ingestion path.");

        var issues = RewriteValidator.validate(candidate,
                "Built the nightly ingestion path on " + ABSENT + ".",
                NO_POSTING, null, null);

        assertThat(issues).contains(RewriteIssue.UNSUPPORTED_CLAIM);
    }

    /**
     * The other half, and the reason this is not simply "refuse proper nouns":
     * a name the person wrote themselves is theirs to keep (Bolum 21.6.1).
     */
    @Test
    void anameThePersonAlreadyWroteIsNotAnInvention() {
        var candidate = bullet("Built the nightly ingestion path on " + ABSENT + ".");

        var issues = RewriteValidator.validate(candidate,
                "Rebuilt the nightly ingestion path on " + ABSENT + ".",
                NO_POSTING, null, null);

        assertThat(issues).doesNotContain(RewriteIssue.UNSUPPORTED_CLAIM);
    }

    private static RewriteCandidate bullet(String original) {
        return new RewriteCandidate(UUID.randomUUID(), UUID.randomUUID(),
                RichContent.plain(original), List.of("etl"), List.of(), List.of(),
                0.8, 500, RewriteIntent.ADAPT, null);
    }
}
