package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScorer;
import com.mustafatetik.atomcv.generation.scoring.ScorableAtomFactory;
import com.mustafatetik.atomcv.generation.scoring.ScoredAtom;
import com.mustafatetik.atomcv.generation.scoring.ScoringWeights;
import com.mustafatetik.atomcv.profile.seed.GoldenProfile;
import com.mustafatetik.atomcv.profile.seed.GoldenProfileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Whether anything can clear the bar {@code ADAPT} is gated on.
 *
 * <p><strong>This bar has been wrong once already.</strong> {@code ADAPT} was
 * gated on a relevance score of 0.65 that real vectors could never produce —
 * cosine sits in a narrow band, so the tier was switched off by arithmetic
 * while looking implemented. The fix moved the gate to counted evidence and
 * the repository wrote the lesson down: <em>an unreachable threshold makes a
 * closed feature look done.</em> Nothing then checked whether the new bar was
 * reachable either, and the open decision about it stayed open on the grounds
 * that it never seemed to fire.
 *
 * <p><strong>It is reachable, and that is what this measures.</strong>
 * Evidence is set intersection — the posting's skills and keywords against the
 * atom's — so it needs no vectors and no service, which is why this runs in
 * the ordinary suite while {@code ScoreReachIT} needs a real embedding server.
 *
 * <p><strong>What it does not claim.</strong> One analysed posting is not a
 * corpus. This says the gate is not arithmetically shut; it does not say 4 is
 * the right number, and that is still a question for production data
 * (STATUS.md). What it does guarantee is that the first fault cannot come back
 * silently: a change that puts the bar out of reach again fails here.
 */
class AdaptBarReachTest {

    /**
     * The seven, and the one posting there is an analysis for. The other nine
     * golden postings are raw text and would need a model call to become
     * comparable — which is exactly why the tuning question is still open.
     */
    private static final List<String> PROFILES = List.of(
            "master_cv_en", "senior_backend_tr", "stress_long_career", "career_changer",
            "junior_frontend_en", "academic_long", "minimal_edge");

    private static final String ANALYSIS = "golden/analyses/senior_java_spring_en.json";

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

    @Test
    void somethingInThegoldenCorpusClearsTheAdaptBar() {
        List<ScoredAtom> everything = scoreEverything();

        int best = everything.stream()
                .mapToInt(atom -> atom.matchedTerms().size())
                .max()
                .orElse(0);

        assertThat(best)
                .as("nothing in the corpus reaches ADAPT's bar of %d: the tier is "
                        + "switched off by arithmetic again, as it was at 0.65",
                        RewritePlanner.FULL_ADAPTATION_TERMS)
                .isGreaterThanOrEqualTo(RewritePlanner.FULL_ADAPTATION_TERMS);
    }

    /**
     * The shape of the evidence, printed rather than asserted.
     *
     * <p>Measured 2026-09-16 over 218 atoms: {@code {0=206, 1=4, 2=2, 3=5,
     * 5=1}}. Two of the seven profiles carry any overlap with this posting at
     * all, which is what a Java posting against an academic CV should look
     * like. The cliff is worth knowing before anybody retunes: **nothing sits
     * at exactly 4**, so moving the bar to 3 would take the eligible set from
     * one atom to six, and moving it to 5 would change nothing.
     */
    @Test
    void theshapeOfTheEvidenceIsWorthPrinting() {
        Map<Integer, Long> histogram = new TreeMap<>();
        scoreEverything().forEach(atom ->
                histogram.merge(atom.matchedTerms().size(), 1L, Long::sum));

        long total = histogram.values().stream().mapToLong(Long::longValue).sum();
        var report = new StringBuilder(String.format(Locale.ROOT,
                "%n  ADAPT evidence across %d golden atoms: %s%n", total, histogram));
        for (int bar = 1; bar <= 5; bar++) {
            final int at = bar;
            long pass = histogram.entrySet().stream()
                    .filter(e -> e.getKey() >= at)
                    .mapToLong(Map.Entry::getValue)
                    .sum();
            report.append(String.format(Locale.ROOT, "    bar=%d -> %d atoms (%.1f%%)%n",
                    bar, pass, 100.0 * pass / total));
        }
        System.out.print(report);

        assertThat(total)
                .as("the corpus was actually read; an empty one would pass everything above")
                .isGreaterThan(200);
    }

    /**
     * Every golden atom scored against the one posting, with no vectors
     * anywhere: {@code matchedTerms} is set intersection and reads none, and
     * the cosine term falls back to its neutral value.
     */
    private static List<ScoredAtom> scoreEverything() {
        JobAnalysis posting = posting();
        var everything = new ArrayList<ScoredAtom>();
        for (String name : PROFILES) {
            GoldenProfile golden = GoldenProfileReader.read(name, UUID.randomUUID());
            everything.addAll(RelevanceScorer.rank(
                    ScorableAtomFactory.from(golden.tree(), golden.tagsByAtom(), TODAY),
                    posting, null, ScoringWeights.DEFAULT));
        }
        return everything;
    }

    private static JobAnalysis posting() {
        try (InputStream in = AdaptBarReachTest.class.getClassLoader()
                .getResourceAsStream(ANALYSIS)) {
            if (in == null) {
                throw new IllegalStateException("No such golden analysis: " + ANALYSIS);
            }
            return new ObjectMapper().readValue(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8), JobAnalysis.class);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
