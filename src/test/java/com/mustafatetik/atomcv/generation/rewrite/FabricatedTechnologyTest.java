package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.shared.text.ClaimVocabulary;
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

    /**
     * The false positive this guard was measured producing, which is the other
     * half of the same rule: a name the page carries is not an invention, and
     * the page spells its skills the way ingestion canonicalised them.
     *
     * <p>Sixteen recorded summaries were replayed against one real profile.
     * The page carried {@code spring-cloud-gateway}, {@code netflix-eureka},
     * {@code spring-data-jpa} and {@code json-web-token}; this summary — the
     * recorded one, verbatim — names their parts the way a sentence does, and
     * every one of {@code Gateway}, {@code Netflix}, {@code Eureka},
     * {@code JPA}, {@code JSON} and {@code Token} came back as an invention.
     * The boundary counted the hyphen as a word character, so {@code Gateway}
     * was hunted for inside {@code spring-cloud-gateway} and not found.
     *
     * <p>It cost the person their summary and the product two model calls, and
     * it failed in the direction that looks like nothing being wrong: the
     * original paragraph is printed and no one is told why.
     */
    @Test
    void awordInsideAcanonicalisedPageSkillIsNotAnInvention() {
        var candidate = new AboutCandidate(UUID.randomUUID(),
                RichContent.plain("Architected scalable microservices with Java 21, "
                        + "Spring Boot and Spring Cloud."),
                List.of("distributed-systems", "java", "spring-boot", "spring-cloud",
                        "spring-cloud-gateway", "netflix-eureka", "openfeign", "rest",
                        "test-driven-development", "spring-data-jpa", "hibernate",
                        "json-web-token", "docker", "docker-compose", "microservices"),
                List.of(), "", NO_POSTING, 500);

        var issues = AboutValidator.validate(candidate,
                "Backend developer specializing in distributed systems and Java microservices "
                        + "with Spring Boot, Spring Cloud, Spring Cloud Gateway, Netflix Eureka, "
                        + "and OpenFeign. Builds high-availability services using REST, "
                        + "test-driven development, Spring Data JPA, Hibernate, JSON Web Token, "
                        + "Docker, and Docker Compose.",
                NO_POSTING);

        assertThat(issues).doesNotContain(RewriteIssue.UNSUPPORTED_CLAIM);
    }

    /** The mechanism on its own, without a validator around it. */
    @Test
    void thehyphenInAcanonicalNameIsNotAWordBoundary() {
        assertThat(ClaimVocabulary.introducedNames(
                "Builds services with Spring Data JPA and JSON Web Token.",
                List.of("spring-data-jpa", "json-web-token")))
                .isEmpty();
    }

    /**
     * And the dictionary, which this check never opened.
     *
     * <p>Reasoned rather than measured, and said out loud for that reason: no
     * recorded answer has written the abbreviation, so what stands behind this
     * is {@code aliases.txt} having had the line since it was written, not a
     * summary that was refused for it.
     */
    @Test
    void anabbreviationTheDictionaryKnowsIsNotAnInvention() {
        assertThat(SkillNames.aliases())
                .as("the pair has to be in the file, or this test proves nothing")
                .containsEntry("oop", "object-oriented-programming");

        assertThat(ClaimVocabulary.introducedNames(
                "Applies OOP across the service layer.",
                List.of("object-oriented-programming")))
                .isEmpty();
    }

    /**
     * The posting's own spelling, which its canonical form can drop.
     *
     * <p>Measured on the golden posting: five of its eighteen skills spell a
     * word their canonical form does not carry, and the loudest is
     * {@code Agile frameworks (Scrum, Kanban)} beside the canonical
     * {@code agile methodologies}. A summary writing {@code Scrum} was reported
     * as inventing a technology the posting had asked for <em>by name</em>,
     * because only the canonical form ever reached the guard.
     *
     * <p><strong>A source of names, not of permission</strong>, and the three
     * assertions are what say so: the prompt's list is untouched, the word
     * stops being an invention when the posting's wording is a source, and a
     * name neither list carries is refused exactly as before. The middle one is
     * the measured failure — the three-argument form is the code as it stood.
     */
    @Test
    void thepostingsOwnSpellingIsASourceOfNamesAndNotOfPermission() {
        var posting = new JobAnalysis(
                new JobAnalysis.Role("Senior Software Engineer", null, "backend", null, null),
                new JobAnalysis.Company("", null),
                List.of(new JobAnalysis.Skill(
                        "Agile frameworks (Scrum, Kanban)", "agile methodologies", null)),
                List.of(), List.of(), List.of(), null, List.of(), "technical", "en", 0.9,
                List.of());
        var context = RewriteContext.of(posting, "", "en", Tone.FORMAL, "bucket");

        assertThat(context.postingSkills())
                .as("the list the prompt is shown is the one it always was")
                .containsExactly("agile methodologies");
        assertThat(context.postingSkillNames())
                .as("and the posting's own wording is kept beside it")
                .containsExactly("agile frameworks (scrum, kanban)");

        var candidate = new AboutCandidate(UUID.randomUUID(),
                RichContent.plain("Backend engineer who has worked in agile teams."),
                List.of("agile"), List.of(), "", NO_POSTING, 400);
        String withScrum = "Backend engineer applying Agile and Scrum in delivery teams.";

        assertThat(AboutValidator.validate(candidate, withScrum, context.postingSkills()))
                .as("what was measured: the canonical form alone refuses it")
                .contains(RewriteIssue.UNSUPPORTED_CLAIM);

        assertThat(AboutValidator.validate(candidate, withScrum,
                context.postingSkills(), context.postingSkillNames()))
                .as("Scrum is a word the posting wrote, so it is not an invention")
                .doesNotContain(RewriteIssue.UNSUPPORTED_CLAIM);

        assertThat(AboutValidator.validate(candidate,
                "Backend engineer applying Agile and " + ABSENT + " in delivery teams.",
                context.postingSkills(), context.postingSkillNames()))
                .as("and a name neither list carries is refused exactly as before")
                .contains(RewriteIssue.UNSUPPORTED_CLAIM);
    }

    /**
     * The dictionary applied to one side of the comparison, which
     * {@link SkillNames}'s own javadoc calls worse than no dictionary at all.
     *
     * <p>{@code AboutValidator} and {@code CoverLetterValidator} canonicalise
     * the page's skills before looking a term up in them. This one did not: it
     * put the atom's skills in a set as they were stored and then asked for
     * {@code SkillNames.canonical(term)}, so an atom carrying {@code Spring
     * Boot} did not match the posting's {@code spring boot} and the rewrite that
     * named the atom's own skill was refused as an invention.
     *
     * <p><strong>Reachable, not hypothetical.</strong> Ingestion canonicalises
     * what it writes ({@code ProfileNormalizer}), but
     * {@code AtomService.patch} stores the list a client sends verbatim — so
     * anybody who edits their skills in the profile editor can produce exactly
     * this. What hid it is the escape hatch below the lookup: an atom whose
     * text repeats the word is allowed by the original-text check, and most do.
     */
    @Test
    void askillTheAtomCarriesIsAllowedHoweverItWasStored() {
        var candidate = new RewriteCandidate(UUID.randomUUID(), UUID.randomUUID(),
                RichContent.plain("Built the nightly ingestion path for the billing team."),
                List.of("Spring Boot"), List.of(), List.of(),
                0.8, 500, RewriteIntent.ADAPT, null);

        var issues = RewriteValidator.validate(candidate,
                "Built the nightly ingestion path with Spring Boot.",
                List.of("spring boot"), null, null);

        assertThat(issues)
                .as("the atom lists it, so the rewrite may name it")
                .doesNotContain(RewriteIssue.UNSUPPORTED_CLAIM);
    }

    private static RewriteCandidate bullet(String original) {
        return new RewriteCandidate(UUID.randomUUID(), UUID.randomUUID(),
                RichContent.plain(original), List.of("etl"), List.of(), List.of(),
                0.8, 500, RewriteIntent.ADAPT, null);
    }
}
