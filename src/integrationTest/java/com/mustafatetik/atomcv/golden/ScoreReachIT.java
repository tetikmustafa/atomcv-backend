package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.embedding.EmbeddingProperties;
import com.mustafatetik.atomcv.embedding.TeiEmbeddingProvider;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.rewrite.RewritePlan;
import com.mustafatetik.atomcv.generation.rewrite.RewritePlanner;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScorer;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScores;
import com.mustafatetik.atomcv.generation.scoring.ScorableAtom;
import com.mustafatetik.atomcv.generation.scoring.ScorableAtomFactory;
import com.mustafatetik.atomcv.generation.scoring.ScoredAtom;
import com.mustafatetik.atomcv.generation.scoring.ScoringWeights;
import com.mustafatetik.atomcv.generation.selection.SelectionPhase;
import com.mustafatetik.atomcv.generation.selection.SelectionRequestBuilder;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.EntryNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.seed.GoldenProfile;
import com.mustafatetik.atomcv.profile.seed.GoldenProfileReader;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What a <em>real</em> vector does to Faz D's reach.
 *
 * <p>{@code PhaseDReachTest} measured everything about this question that can
 * be measured without a service: the best-matched pair in the golden set tops
 * out at 0.126 with no embedding weight, and at 0.276 with the default weights
 * and no vectors — of which 0.200 is a pedestal, because a missing vector
 * scores the neutral 0.5 and 0.40 x 0.5 is a constant every atom carries. The
 * three remaining terms are bounded by denominators no single sentence can
 * cover: three of seventeen required skills, three of twenty-two keywords, and
 * a Jaccard whose union is the posting's whole vocabulary.
 *
 * <p><strong>So one number decides whether the floor is reachable at all, and
 * nobody has measured it.</strong> Cosine is rescaled here — unrelated is 0.5,
 * opposite is 0 — so a genuinely on-topic bullet does not score 0.5 on that
 * term, it scores somewhere above it, and 0.40 of the weight rides on the
 * difference. If a real BGE-M3 puts the best bullet near 0.8 rescaled, the
 * embedding term alone is 0.32 and the floor is within reach of the importance
 * multiplier. If it puts it near 0.55, Faz D can never fire and the thresholds
 * are describing a scale the scorer does not produce.
 *
 * <p>This is a measurement rather than a guard, so it runs in its own lane and
 * prints. It asserts only what must hold however the numbers land: that every
 * atom was actually given a vector of the provider's own width, and that the
 * scores are the fractions the rest of the pipeline reads them as. Pinning a
 * value here before anybody has seen one would be pinning an expectation.
 *
 * <p><strong>The same two texts production embeds</strong>, or it would be
 * measuring something else: the English variant's plain text per atom
 * ({@code AtomEmbeddingService}), and {@code JobAnalysis#embeddingTarget} for
 * the posting — not the posting's raw text, which is mostly benefits and
 * mission statement.
 *
 * <p>Ids and numbers only in the output, never a sentence (absolute rule 4).
 */
@Tag("embedding")
class ScoreReachIT {

    private static final UUID OWNER = UUID.randomUUID();

    /** The date {@code PhaseDReachTest} pins, so the two are comparable. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);

    /** {@code RewritePlanner}'s, which are package-private there. */
    private static final double FLOOR_SCORE = 0.35;
    private static final int FULL_ADAPTATION_TERMS = 4;
    private static final String ENGLISH = "en";

    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    /** Every fixture, so the matched one has something to be matched against. */
    private static final List<String> PROFILE_NAMES = List.of(
            "master_cv_en", "senior_backend_tr", "stress_long_career", "career_changer",
            "junior_frontend_en", "academic_long", "minimal_edge");

    private static final GoldenProfile PROFILE = GoldenProfileReader.read("master_cv_en", OWNER);
    private static final JobAnalysis POSTING = analysis();

    private static final TeiEmbeddingProvider EMBEDDINGS = new TeiEmbeddingProvider(
            new EmbeddingProperties(null, null, null, null), new ObjectMapper());

    /**
     * Not a skip. This lane is only ever run deliberately, and a run that
     * quietly passed over a service that was not there would report a green
     * measurement of nothing — the third of the rules about testing.
     */
    @BeforeAll
    static void theServiceHasToBeThere() {
        assertThat(EMBEDDINGS.isHealthy())
                .as("the embedding container is not answering. Start it with "
                        + "`docker compose --profile full up -d embeddings` "
                        + "(or `make dev-full`) and run this again")
                .isTrue();
    }

    @Test
    void whatTheBestMatchedPairScoresWithRealVectors() {
        ProfileTree tree = PROFILE.tree();
        int embedded = embedEveryAtom(PROFILE);
        float[] postingVector = EMBEDDINGS.embed(POSTING.embeddingTarget());

        List<ScorableAtom> atoms =
                ScorableAtomFactory.from(tree, PROFILE.tagsByAtom(), TODAY);
        List<ScoredAtom> ranked =
                RelevanceScorer.rank(atoms, POSTING, postingVector, ScoringWeights.DEFAULT);
        List<ScoredAtom> withoutVectors =
                RelevanceScorer.rank(atoms, POSTING, null, ScoringWeights.DEFAULT);

        report(embedded, postingVector.length, ranked, withoutVectors);

        assertThat(atoms)
                .as("every scorable atom carries a vector, or the term under test is "
                        + "the neutral constant for the ones that do not")
                .isNotEmpty()
                .allSatisfy(atom -> assertThat(atom.hasEmbedding()).isTrue());
        assertThat(ranked)
                .as("a score is the fraction the rest of the pipeline reads it as")
                .allSatisfy(atom -> assertThat(atom.score()).isBetween(0.0, 1.0));
    }

    /**
     * And the answer the question was asked for: with real vectors, does Faz D
     * have anything to do?
     */
    @Test
    void whetherFazDPlansAnythingWithRealVectors() {
        ProfileTree tree = PROFILE.tree();
        embedEveryAtom(PROFILE);
        float[] postingVector = EMBEDDINGS.embed(POSTING.embeddingTarget());

        var scores = new RelevanceScores(
                RelevanceScorer.rank(
                        ScorableAtomFactory.from(tree, PROFILE.tagsByAtom(), TODAY),
                        POSTING, postingVector, ScoringWeights.DEFAULT),
                ScoringWeights.DEFAULT);
        var built = SelectionRequestBuilder.build(
                tree, TemplateCustomization.CLASSIC, CAPACITY, 1, ENGLISH, Tone.FORMAL, scores);
        SelectionState selection = SelectionPhase.select(built.request()).orElseThrow();
        RewritePlan plan = RewritePlanner.plan(tree, selection);

        double best = selection.selected().stream()
                .mapToDouble(SelectionState.SelectedAtom::score)
                .max().orElseThrow();

        System.out.printf(Locale.ROOT,
                "%n  Faz D with real vectors: %d candidate(s); best selected score %.4f, "
                        + "floor %.2f%n",
                plan.candidates().size(), best, FLOOR_SCORE);
        plan.candidates().forEach(candidate -> System.out.printf(Locale.ROOT,
                "    %s  %s%n", candidate.atomId(), candidate.intent()));
    }

    /**
     * <strong>Where the floor belongs, measured as a separation rather than
     * chosen as a number.</strong>
     *
     * <p>One pair says how high a well-matched CV scores and nothing about how
     * high a badly-matched one scores — and the floor's whole job is to tell
     * those apart, because below it a rewrite is not adaptation but invention.
     * So every golden profile is scored against the same real analysis: one of
     * them was written for this posting and the others were not, which is the
     * contrast the number has to sit inside.
     *
     * <p>The mismatched side shares a single posting, because a single posting
     * is what has a recorded Faz A analysis. It bounds the answer rather than
     * settling it: a floor above everything the unmatched profiles reach is
     * safe here, and another posting could still score higher.
     */
    @Test
    void whereTheMatchedProfileSeparatesFromTheRest() {
        float[] postingVector = EMBEDDINGS.embed(POSTING.embeddingTarget());

        System.out.printf(Locale.ROOT,
                "%n  every golden profile vs senior_java_spring_en%n"
                        + "  %-20s %6s %6s %8s %8s %8s %7s %7s %7s %7s%n",
                "profile", "atoms", "vec", "max", "2nd", "top5", ">=.35", ">=.40",
                ">=.45", ">=.50");

        for (String name : PROFILE_NAMES) {
            GoldenProfile golden = GoldenProfileReader.read(name, UUID.randomUUID());
            int embedded = embedEveryAtom(golden);
            List<ScoredAtom> ranked = RelevanceScorer.rank(
                    ScorableAtomFactory.from(golden.tree(), golden.tagsByAtom(), TODAY),
                    POSTING, postingVector, ScoringWeights.DEFAULT);
            if (ranked.isEmpty()) {
                System.out.printf(Locale.ROOT, "  %-20s %6d %6d   (no scorable atom)%n",
                        name, 0, embedded);
                continue;
            }
            List<Double> scores = ranked.stream().map(ScoredAtom::score).toList();
            System.out.printf(Locale.ROOT,
                    "  %-20s %6d %6d %8.4f %8.4f %8.4f %7d %7d %7d %7d%n",
                    name, ranked.size(), embedded,
                    scores.get(0),
                    scores.size() > 1 ? scores.get(1) : Double.NaN,
                    scores.stream().limit(5).mapToDouble(Double::doubleValue).average()
                            .orElseThrow(),
                    atLeast(scores, 0.35), atLeast(scores, FLOOR_SCORE),
                    atLeast(scores, 0.45), atLeast(scores, 0.50));

            // What the leader is riding on. The whole question about the floor
            // is whether a high score means evidence or only resemblance.
            ScoredAtom top = ranked.get(0);
            System.out.printf(Locale.ROOT,
                    "  %-20s        embed %.4f  tag %.4f  skill %.4f  keyword %.4f%s%n",
                    "", top.components().embedding(), top.components().tag(),
                    top.components().skill(), top.components().keyword(),
                    top.components().skill() == 0.0 && top.components().keyword() == 0.0
                            ? "   <- no lexical evidence at all" : "");
        }
    }

    private static long atLeast(List<Double> scores, double threshold) {
        return scores.stream().filter(score -> score >= threshold).count();
    }

    /**
     * What a rule that asked for evidence would select, at three floors.
     *
     * <p>Evidence is the lexical half: the bullet names a skill the posting
     * asked for, or one of its phrases. Resemblance is the embedding half, and
     * every profile here has plenty of it — which is the finding this counts
     * out. The length gate is kept as it is: compressing a short bullet risks
     * the meaning of a sentence that was not the problem.
     */
    @Test
    void whatAnEvidenceGateWouldSelect() {
        float[] postingVector = EMBEDDINGS.embed(POSTING.embeddingTarget());
        double[] floors = {0.30, 0.35, 0.40};

        System.out.printf(Locale.ROOT,
                "%n  candidates if a rewrite required evidence (skill or keyword > 0)%n"
                        + "  %-20s %10s %10s %10s %10s%n",
                "profile", "evidence", "+floor.30", "+floor.35", "+floor.40");

        for (String name : PROFILE_NAMES) {
            GoldenProfile golden = GoldenProfileReader.read(name, UUID.randomUUID());
            embedEveryAtom(golden);
            Map<UUID, Integer> lengths = plainTextLengths(golden);
            List<ScoredAtom> ranked = RelevanceScorer.rank(
                    ScorableAtomFactory.from(golden.tree(), golden.tagsByAtom(), TODAY),
                    POSTING, postingVector, ScoringWeights.DEFAULT);

            List<ScoredAtom> withEvidence = ranked.stream()
                    .filter(atom -> atom.components().skill() > 0
                            || atom.components().keyword() > 0)
                    .toList();
            long[] counts = new long[floors.length];
            for (int i = 0; i < floors.length; i++) {
                double floor = floors[i];
                counts[i] = withEvidence.stream()
                        .filter(atom -> atom.score() >= floor)
                        // The tier a floor-to-0.65 score lands in is COMPRESS,
                        // and COMPRESS asks for a bullet long enough to lose
                        // words without losing meaning.
                        .filter(atom -> lengths.getOrDefault(atom.atomId(), 0) > 160)
                        .count();
            }
            System.out.printf(Locale.ROOT, "  %-20s %10d %10d %10d %10d%n",
                    name, withEvidence.size(), counts[0], counts[1], counts[2]);
        }
    }

    /**
     * The rule as shipped, run end to end: selection, then {@code
     * RewritePlanner}. The counts above are a proxy computed here; this is the
     * planner's own answer, which is the one that matters.
     *
     * <p>It also prints how much evidence each candidate carries, because that
     * is the open half: {@code ADAPT} asks for a connection real enough to draw
     * out, and 0.65 on a scale whose best observed value is 0.41 asks for
     * something the scorer cannot produce.
     */
    @Test
    void whatThePlannerNowSelects() {
        float[] postingVector = EMBEDDINGS.embed(POSTING.embeddingTarget());

        System.out.printf(Locale.ROOT,
                "%n  RewritePlanner, as shipped%n  %-20s %11s %9s %s%n",
                "profile", "candidates", "best", "evidence per candidate");

        for (String name : PROFILE_NAMES) {
            GoldenProfile golden = GoldenProfileReader.read(name, UUID.randomUUID());
            embedEveryAtom(golden);
            var scores = new RelevanceScores(
                    RelevanceScorer.rank(
                            ScorableAtomFactory.from(golden.tree(), golden.tagsByAtom(), TODAY),
                            POSTING, postingVector, ScoringWeights.DEFAULT),
                    ScoringWeights.DEFAULT);
            var built = SelectionRequestBuilder.build(
                    golden.tree(), TemplateCustomization.CLASSIC, CAPACITY, 1,
                    ENGLISH, Tone.FORMAL, scores);
            SelectionState selection = SelectionPhase.select(built.request()).orElseThrow();
            RewritePlan plan = RewritePlanner.plan(golden.tree(), selection);

            Map<UUID, Integer> evidence = new LinkedHashMap<>();
            selection.selected().forEach(atom ->
                    evidence.put(atom.atomId(), atom.matchedKeywords().size()));

            StringBuilder detail = new StringBuilder();
            plan.candidates().forEach(candidate -> detail.append(String.format(Locale.ROOT,
                    "%s:%d/%.3f ", candidate.intent(),
                    evidence.getOrDefault(candidate.atomId(), 0), candidate.score())));

            if ("master_cv_en".equals(name)) {
                Map<Integer, Long> histogram = new java.util.TreeMap<>();
                selection.selected().forEach(atom -> histogram.merge(
                        atom.matchedKeywords().size(), 1L, Long::sum));
                System.out.printf(Locale.ROOT,
                        "    evidence across the %d selected atoms: %s "
                                + "(%d clear the %d-term bar for ADAPT)%n",
                        selection.selected().size(), histogram,
                        selection.selected().stream()
                                .filter(atom -> atom.matchedKeywords().size()
                                        >= FULL_ADAPTATION_TERMS)
                                .count(),
                        FULL_ADAPTATION_TERMS);
                Map<UUID, String> kinds = new LinkedHashMap<>();
                for (Atom atom : atomsOf(golden.tree())) {
                    kinds.put(atom.getId(), atom.getKind() + "/"
                            + (atom.isVerbatim() ? "verbatim" : "free"));
                }
                selection.selected().stream()
                        .filter(atom -> atom.matchedKeywords().size() >= FULL_ADAPTATION_TERMS)
                        .forEach(atom -> System.out.printf(Locale.ROOT,
                                "    the %d-term atom is a %s scoring %.4f%n",
                                atom.matchedKeywords().size(),
                                kinds.getOrDefault(atom.atomId(), "?"), atom.score()));
            }
            System.out.printf(Locale.ROOT, "  %-20s %11d %9.4f %s%n",
                    name, plan.candidates().size(),
                    selection.selected().stream()
                            .mapToDouble(SelectionState.SelectedAtom::score)
                            .max().orElse(Double.NaN),
                    detail.toString().trim());
        }
    }

    /** How long each atom prints, for the tier that asks. */
    private static Map<UUID, Integer> plainTextLengths(GoldenProfile profile) {
        Map<UUID, Integer> lengths = new LinkedHashMap<>();
        for (AtomVariant variant : profile.variants()) {
            if (ENGLISH.equals(variant.getLanguage()) && !variant.getContent().isEmpty()) {
                lengths.put(variant.getAtomId(), variant.getPlainText().length());
            }
        }
        return lengths;
    }

    /**
     * One batch, the way the importer does it, and the vector goes onto the
     * atom the tree holds — which is where {@code ScorableAtomFactory} reads
     * it from.
     */
    private static int embedEveryAtom(GoldenProfile profile) {
        ProfileTree tree = profile.tree();
        Map<UUID, AtomVariant> englishByAtom = new HashMap<>();
        for (AtomVariant variant : profile.variants()) {
            if (ENGLISH.equals(variant.getLanguage())) {
                englishByAtom.put(variant.getAtomId(), variant);
            }
        }

        List<Atom> pending = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (Atom atom : atomsOf(tree)) {
            AtomVariant english = englishByAtom.get(atom.getId());
            if (english == null || english.getContent().isEmpty()) {
                continue;
            }
            pending.add(atom);
            texts.add(english.getPlainText());
        }

        List<float[]> vectors = EMBEDDINGS.embedBatch(texts);
        assertThat(vectors)
                .as("a provider that answered a different number of vectors has not "
                        + "answered this question")
                .hasSameSizeAs(pending);
        for (int i = 0; i < pending.size(); i++) {
            pending.get(i).setEmbedding(
                    vectors.get(i), englishByAtom.get(pending.get(i).getId()).getContentHash());
        }
        return pending.size();
    }

    /** Every active atom the tree holds, sections and entries alike. */
    private static List<Atom> atomsOf(ProfileTree tree) {
        List<Atom> atoms = new ArrayList<>();
        for (SectionNode section : tree.sections()) {
            for (EntryNode entry : section.entries()) {
                for (AtomNode node : entry.atoms()) {
                    atoms.add(node.atom());
                }
            }
            for (AtomNode node : section.atoms()) {
                atoms.add(node.atom());
            }
        }
        return atoms;
    }

    private static void report(
            int embedded, int dimensions,
            List<ScoredAtom> ranked, List<ScoredAtom> withoutVectors) {

        Map<UUID, Double> flat = new LinkedHashMap<>();
        withoutVectors.forEach(atom -> flat.put(atom.atomId(), atom.score()));

        long aboveFloor = ranked.stream().filter(a -> a.score() >= FLOOR_SCORE).count();
        long aboveOldFloor = ranked.stream().filter(a -> a.score() >= 0.40).count();

        System.out.printf(Locale.ROOT,
                "%n  master_cv_en vs senior_java_spring_en — %d atoms, %d-wide vectors%n",
                embedded, dimensions);
        System.out.printf(Locale.ROOT,
                "  %-38s %8s %8s %8s %8s %8s %9s%n",
                "atom", "score", "embed", "tag", "skill", "keyword", "novector");
        ranked.stream().limit(12).forEach(atom -> System.out.printf(Locale.ROOT,
                "  %-38s %8.4f %8.4f %8.4f %8.4f %8.4f %9.4f%n",
                atom.atomId(), atom.score(),
                atom.components().embedding(), atom.components().tag(),
                atom.components().skill(), atom.components().keyword(),
                flat.getOrDefault(atom.atomId(), Double.NaN)));

        System.out.printf(Locale.ROOT,
                "  best %.4f (was %.4f without vectors) | >= floor %.2f: %d "
                        + "| >= the old 0.40: %d%n",
                ranked.get(0).score(),
                flat.getOrDefault(ranked.get(0).atomId(), Double.NaN),
                FLOOR_SCORE, aboveFloor, aboveOldFloor);
        System.out.printf(Locale.ROOT,
                "  embedding term: best %.4f, worst %.4f, mean %.4f (neutral is 0.5000)%n",
                ranked.stream().mapToDouble(a -> a.components().embedding()).max().orElseThrow(),
                ranked.stream().mapToDouble(a -> a.components().embedding()).min().orElseThrow(),
                ranked.stream().mapToDouble(a -> a.components().embedding()).average()
                        .orElseThrow());
    }

    private static JobAnalysis analysis() {
        String path = "golden/analyses/senior_java_spring_en.json";
        try (InputStream in = ScoreReachIT.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("No such golden analysis: " + path);
            }
            return new ObjectMapper().readValue(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8), JobAnalysis.class);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
