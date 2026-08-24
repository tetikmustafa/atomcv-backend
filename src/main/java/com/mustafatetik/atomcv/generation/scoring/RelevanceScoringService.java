package com.mustafatetik.atomcv.generation.scoring;

import com.mustafatetik.atomcv.embedding.EmbeddingException;
import com.mustafatetik.atomcv.embedding.EmbeddingProvider;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Faz B, with the two things {@link RelevanceScorer} deliberately does not
 * have: a vector for the posting, and an opinion about whether the embedding
 * service is up (Bolum 19, Bolum 28.4).
 *
 * <p>The scorer stays pure. Everything that talks to something — the embedding
 * call, the health check, the meter — is here, so the determinism test of
 * Bolum 51.2 still has a function it can run twice.
 */
@Service
public class RelevanceScoringService {

    private static final Logger log = LoggerFactory.getLogger(RelevanceScoringService.class);

    private final EmbeddingProvider embeddings;
    private final MeterRegistry meters;
    private final Clock clock;

    RelevanceScoringService(EmbeddingProvider embeddings, MeterRegistry meters, Clock clock) {
        this.embeddings = embeddings;
        this.meters = meters;
        this.clock = clock;
    }

    /**
     * @param tagsByAtom the profile's own vocabulary, from
     *                   {@code TagRepository.labelsByAtom}
     */
    public RelevanceScores scoreAgainst(
            ProfileTree tree, Map<UUID, Set<String>> tagsByAtom, JobAnalysis posting) {

        // The date reaches the factory, never the scorer: Bolum 19.4's
        // criteria need one and Bolum 51.2's determinism test needs the
        // scoring itself to be a pure function of its arguments.
        List<ScorableAtom> atoms = ScorableAtomFactory.from(
                tree, tagsByAtom, LocalDate.now(clock));
        float[] postingVector = postingVector(posting);
        ScoringWeights weights = postingVector == null
                ? ScoringWeights.WITHOUT_EMBEDDING
                : ScoringWeights.DEFAULT;

        // Counts, never content (absolute rule 4). Which weight set ran is the
        // only way to see from production that a deployment has been scoring
        // without vectors — the user is not told, Bolum 28.4 calls it an
        // internal detail.
        meters.counter("generation.scoring.weights",
                        "set", postingVector == null ? "without_embedding" : "default")
                .increment();

        return new RelevanceScores(
                RelevanceScorer.rank(atoms, posting, postingVector, weights), weights);
    }

    /**
     * The posting as a vector, or null to score without one.
     *
     * <p>Two ways to get null, and they are different failures with the same
     * answer. The health check is Bolum 28.4's: ask before spending a round
     * trip on a service that is known to be down. The catch is because that
     * check is a signal and not a promise — {@code isHealthy} describes a
     * moment that has already passed, and a call can still fail after it
     * returned true. Without the catch the fallback would work only for an
     * outage that started before the generation did.
     */
    private float[] postingVector(JobAnalysis posting) {
        if (!embeddings.isHealthy()) {
            log.info("The embedding service is not answering; scoring without it (Bolum 28.4)");
            return null;
        }
        try {
            // Bolum 18.5: the synthesised target, not the raw posting. A
            // posting is mostly benefits and mission statements, and
            // embedding all of it points the vector at whatever the company
            // writes most of.
            return embeddings.embed(posting.embeddingTarget());
        } catch (EmbeddingException unavailable) {
            // The message, never the posting. Which service failed is worth a
            // log line; what it was asked to embed is user content.
            log.warn("The embedding call failed mid-generation; scoring without it: {}",
                    unavailable.getMessage());
            return null;
        }
    }
}
