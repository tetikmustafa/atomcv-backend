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

    /**
     * The substitution, which is the same class of fault wearing a familiar
     * name.
     *
     * <p>A CV tailored by hand for a posting that asks for "Spring Core"
     * changed one bullet's <em>Spring Cloud</em> to <em>Spring Core</em>. Two
     * different things: one is a service-discovery and gateway stack, the other
     * is the container at the bottom of the framework, and the person who wrote
     * the bullet built the first. Whatever was meant by it, a product that did
     * the same would be putting a claim on a page that no atom supports — and
     * this is exactly the shape Bolum 21.6's third check exists for, because
     * the posting is where the temptation comes from.
     *
     * <p>Note what carries it: not the alias file, which knows neither name,
     * but the posting's own skills. That is the vocabulary a stuffed answer
     * draws from, and it is why the check reads {@code postingSkills} rather
     * than a fixed list.
     */
    @Test
    void asubstitutionTowardsThePostingIsAnUnsupportedClaim() {
        List<String> posting = List.of("java", "spring boot", "spring core", "spring mvc");
        var candidate = new RewriteCandidate(UUID.randomUUID(), UUID.randomUUID(),
                RichContent.plain("Architected a backend system transitioning from monolithic "
                        + "to microservices utilizing Java 21, Spring Boot, and Spring Cloud."),
                List.of("java", "spring-boot", "spring-cloud"), List.of(), List.of(),
                0.8, 500, RewriteIntent.ADAPT, null);

        var swapped = RewriteValidator.validate(candidate,
                "Architected a backend system transitioning from monolithic to microservices "
                        + "utilizing Java 21, Spring Boot, and Spring Core.",
                posting, null, null);
        assertThat(swapped)
                .as("Spring Core is the posting's word, and no atom's")
                .contains(RewriteIssue.UNSUPPORTED_CLAIM);

        var kept = RewriteValidator.validate(candidate,
                "Architected a backend system moving from a monolith to microservices with "
                        + "Java 21, Spring Boot and Spring Cloud.",
                posting, null, null);
        assertThat(kept)
                .as("the person's own stack, reworded, is not a claim")
                .doesNotContain(RewriteIssue.UNSUPPORTED_CLAIM);
    }

    /**
     * And the trap that would have let it through, checked directly.
     *
     * <p>Absolute rule 7: a Turkish default locale lowercases {@code SQL} to
     * {@code sqı}, so a guard that folded case without a locale would stop
     * recognising half the names it knows — and a guard that recognises nothing
     * refuses nothing. Every fold in this path names {@code Locale.ROOT}; this
     * is what says so out loud.
     */
    @Test
    void theguardStillRefusesUnderATurkishLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var candidate = new RewriteCandidate(UUID.randomUUID(), UUID.randomUUID(),
                    RichContent.plain("Integrated structured enterprise data using SQL queries."),
                    List.of("sql"), List.of(), List.of(), 0.8, 500, RewriteIntent.ADAPT, null);

            var issues = RewriteValidator.validate(candidate,
                    "Integrated structured enterprise data using SQL Server queries.",
                    List.of("sql server"), null, null);

            assertThat(issues).contains(RewriteIssue.UNSUPPORTED_CLAIM);
        } finally {
            Locale.setDefault(before);
        }
    }

    private static RewriteCandidate bullet(String original) {
        return new RewriteCandidate(UUID.randomUUID(), UUID.randomUUID(),
                RichContent.plain(original), List.of("etl"), List.of(), List.of(),
                0.8, 500, RewriteIntent.ADAPT, null);
    }
}
