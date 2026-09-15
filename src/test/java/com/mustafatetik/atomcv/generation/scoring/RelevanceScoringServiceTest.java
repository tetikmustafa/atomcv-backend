package com.mustafatetik.atomcv.generation.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.embedding.EmbeddingException;
import com.mustafatetik.atomcv.embedding.EmbeddingProvider;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The embedding service is down and scoring carries on.
 *
 * <p>The scorer itself is pure and tested next door. What is tested here is
 * the part that talks to something — which weight set a generation ran with,
 * and whether the fallback covers an outage that starts <em>during</em> the
 * generation as well as one that started before it.
 */
class RelevanceScoringServiceTest {

    private static final UUID PROFILE = UUID.randomUUID();
    private static final java.time.Clock CLOCK = java.time.Clock.fixed(
            java.time.Instant.parse("2026-08-24T09:00:00Z"), java.time.ZoneOffset.UTC);

    @Test
    void ahealthyServiceScoresWithTheEmbeddingComponent() {
        var embeddings = new StubProvider();
        var scores = serviceWith(embeddings).scoreAgainst(tree(), Map.of(), posting(), List.of());

        assertThat(scores.weights()).isEqualTo(ScoringWeights.DEFAULT);
        assertThat(embeddings.embedded).hasSize(1);
    }

    /** The synthesised target, not the posting as it was pasted. */
    @Test
    void whatIsEmbeddedIsTheSynthesisedTarget() {
        var embeddings = new StubProvider();
        var posting = posting();

        serviceWith(embeddings).scoreAgainst(tree(), Map.of(), posting, List.of());

        assertThat(embeddings.embedded).containsExactly(posting.embeddingTarget());
    }

    /**
     * The service is known to be down, so the round trip is not spent at all.
     */
    @Test
    void anunhealthyServiceIsNotCalledAndItsShareIsRedistributed() {
        var embeddings = new StubProvider();
        embeddings.healthy = false;

        var scores = serviceWith(embeddings).scoreAgainst(tree(), Map.of(), posting(), List.of());

        assertThat(scores.weights()).isEqualTo(ScoringWeights.WITHOUT_EMBEDDING);
        assertThat(embeddings.embedded).isEmpty();
    }

    /**
     * The one the health check alone does not cover. {@code isHealthy}
     * describes a moment that has already passed, so a call can still fail
     * after it returned true — and without the catch the fallback would only
     * work for an outage that started before the generation did.
     */
    @Test
    void acallThatFailsAfterAHealthyCheckFallsBackToo() {
        var embeddings = new StubProvider();
        embeddings.failOnEmbed = true;

        var scores = serviceWith(embeddings).scoreAgainst(tree(), Map.of(), posting(), List.of());

        assertThat(scores.weights()).isEqualTo(ScoringWeights.WITHOUT_EMBEDDING);
        assertThat(scores.ranked()).isNotEmpty();
        assertThat(scores.ranked()).allSatisfy(
                atom -> assertThat(atom.components().embedding()).isZero());
    }

    /**
     * The user is not told — Bolum 28.4 calls it an internal detail — but a
     * deployment that scored without vectors for a week would otherwise look
     * like a prompt problem.
     */
    @Test
    void whichWeightSetRanIsCounted() {
        var meters = new SimpleMeterRegistry();
        var embeddings = new StubProvider();
        embeddings.healthy = false;

        new RelevanceScoringService(embeddings, meters, CLOCK)
                .scoreAgainst(tree(), Map.of(), posting(), List.of());

        assertThat(meters.counter("generation.scoring.weights", "set", "without_embedding")
                .count()).isEqualTo(1.0);
        assertThat(meters.counter("generation.scoring.weights", "set", "default").count())
                .isZero();
    }

    @Test
    void thescoresAreIndexedByAtomAndAnUnscoredAtomRanksLast() {
        var fixture = new Fixture();
        var section = fixture.section();
        var scored = fixture.atom(section, "Go and Postgres");
        var inactive = fixture.atom(section, "Unrelated");
        inactive.setActive(false);

        var scores = serviceWith(new StubProvider())
                .scoreAgainst(fixture.tree(), Map.of(), posting(), List.of());

        assertThat(scores.byAtom()).containsKey(scored.getId());
        assertThat(scores.scoreOf(inactive, null)).isZero();
    }

    /** Through the whole service rather than through the scorer. */
    @Test
    void thesameProfileAndPostingScoreTheSameTwice() {
        var service = serviceWith(new StubProvider());
        var tree = tree();

        assertThat(service.scoreAgainst(tree, Map.of(), posting(), List.of()).ranked())
                .isEqualTo(service.scoreAgainst(tree, Map.of(), posting(), List.of()).ranked());
    }

    /**
     * The directive, doing something measurable.
     *
     * <p>Neither atom says a word the posting uses -- one is about Kafka, the
     * other about design reviews -- so against the posting alone they score
     * the same and the tie is broken by id, which is no ranking at all. Naming
     * "kafka" is a person saying the posting forgot to.
     *
     * <p><strong>The formula is untouched.</strong> The term joins the
     * posting's keywords and tags; the four weights of Bolum 19.1 are the same
     * four. A directive changes the input to one generation, which is the
     * whole reason Bolum 18.7 keeps it out of the cached analysis.
     */
    @Test
    void anemphasisedTermLiftsTheAtomThatCarriesIt() {
        var fixture = new Fixture();
        var section = fixture.section();
        var kafka = fixture.atom(section, "Moved the event bus onto Kafka");
        fixture.atom(section, "Ran the weekly design review");
        var service = serviceWith(new StubProvider());

        var without = service.scoreAgainst(fixture.tree(), Map.of(), posting(), List.of());
        var with = service.scoreAgainst(
                fixture.tree(), Map.of(), posting(), List.of("kafka"));

        assertThat(with.scoreOf(kafka, null))
                .as("the term the person named is now one the ranking reads")
                .isGreaterThan(without.scoreOf(kafka, null));
    }

    /**
     * <strong>An emphasis nobody carries re-ranks nothing, and it does move
     * the numbers.</strong> The keyword coverage is a fraction of the terms,
     * so a term no atom carries enlarges the denominator and every score falls
     * together -- which leaves the order exactly as it was.
     *
     * <p>Written because the first version of this test asserted the scores
     * were unchanged and was wrong about the product rather than about the
     * code. The claim worth making is the one a user would notice: emphasis
     * re-orders a CV, it does not invent relevance that is not there.
     */
    @Test
    void anemphasisNoAtomCarriesLeavesTheOrderAlone() {
        var service = serviceWith(new StubProvider());
        var tree = tree();

        assertThat(service.scoreAgainst(tree, Map.of(), posting(), List.of("cobol")).ranked())
                .extracting(ScoredAtom::atomId)
                .isEqualTo(service.scoreAgainst(tree, Map.of(), posting(), List.of()).ranked()
                        .stream().map(ScoredAtom::atomId).toList());
    }

    private static RelevanceScoringService serviceWith(EmbeddingProvider embeddings) {
        return new RelevanceScoringService(embeddings, new SimpleMeterRegistry(), CLOCK);
    }

    private static ProfileTree tree() {
        var fixture = new Fixture();
        var section = fixture.section();
        fixture.atom(section, "Built payment systems in Go");
        fixture.atom(section, "Ran Postgres at high availability");
        return fixture.tree();
    }

    private static JobAnalysis posting() {
        return new JobAnalysis(
                new JobAnalysis.Role("Senior Backend Engineer", JobAnalysis.Seniority.SENIOR,
                        "fintech", JobAnalysis.EmploymentType.FULL_TIME,
                        JobAnalysis.WorkMode.REMOTE),
                new JobAnalysis.Company("Acme", JobAnalysis.SizeHint.SCALEUP),
                List.of(new JobAnalysis.Skill("Go", "go", JobAnalysis.Importance.CRITICAL)),
                List.of(new JobAnalysis.Skill("Terraform", "terraform", null)),
                List.of("design and scale payment systems"),
                List.of("distributed systems", "high availability"),
                new JobAnalysis.ExperienceYears(5, null),
                List.of("en"), "technical", "en", 0.94, List.of());
    }

    /** An embedding provider that answers, refuses, or breaks mid-call. */
    private static final class StubProvider implements EmbeddingProvider {

        private final List<String> embedded = new ArrayList<>();
        private boolean healthy = true;
        private boolean failOnEmbed;

        @Override
        public int dimensions() {
            return Atom.EMBEDDING_DIMENSIONS;
        }

        @Override
        public boolean isHealthy() {
            return healthy;
        }

        @Override
        public float[] embed(String text) {
            if (failOnEmbed) {
                throw new EmbeddingException("the service went away", null);
            }
            embedded.add(text);
            return new float[Atom.EMBEDDING_DIMENSIONS];
        }

        @Override
        public List<float[]> embedBatch(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }
    }

    private static final class Fixture {

        private final List<Section> sections = new ArrayList<>();
        private final List<Atom> atoms = new ArrayList<>();
        private final List<AtomVariant> variants = new ArrayList<>();

        Section section() {
            var section = new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
            sections.add(section);
            return section;
        }

        Atom atom(Section section, String text) {
            var atom = new Atom(
                    PROFILE, section.getId(), null, AtomKind.SKILL, (short) atoms.size());
            var variant = new AtomVariant(PROFILE, atom.getId(), "en", RichContent.plain(text));
            variant.setPrimary(true);
            atoms.add(atom);
            variants.add(variant);
            return atom;
        }

        ProfileTree tree() {
            return ProfileAssembler.assemble(PROFILE, sections, List.of(), atoms, variants);
        }
    }
}
