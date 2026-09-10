package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.billing.QuotaMetric;
import com.mustafatetik.atomcv.billing.QuotaService;
import com.mustafatetik.atomcv.billing.QuotaSubject;
import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.GenerationStatus;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.selection.GenerationDirectives;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobOwner;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.shared.error.Result;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The manual half of Faz G: a toggle, checked and queued (Bolum 24.4).
 *
 * <p><strong>Two doors and two prices.</strong> A hand toggle re-runs nothing
 * but deterministic work — selection, render, compile — so there is nothing
 * for a day's allowance to be spent on. A sentence has to be read first, and
 * that reading is a model call, so it comes off the same ceiling a generation
 * does. § 44 stays where it belongs: on the calls that cost money.
 *
 * <p><strong>Checked against the parent's own snapshot.</strong> An id that was
 * never a candidate for this CV is refused rather than ignored. Ignoring it
 * would answer 202 and produce a document identical to the one the person was
 * looking at, which reads as the feature being broken; and the only way to send
 * an unknown id is a client bug or somebody else's atom, neither of which is
 * worth a compilation.
 */
@Service
public class SelectionEditService {

    private final JobQueue queue;
    private final QuotaService quotas;
    private final MeterRegistry meters;
    private final Clock clock;

    SelectionEditService(
            JobQueue queue, QuotaService quotas, MeterRegistry meters, Clock clock) {

        this.queue = queue;
        this.quotas = quotas;
        this.meters = meters;
        this.clock = clock;
    }

    /**
     * A hand toggle: the ids are already decided, so nothing is spent.
     *
     * @param parent the generation being edited, already read through a scoped
     *               repository — this never loads it, so the IDOR defence stays
     *               where absolute rule 3 puts it
     */
    public Job enqueue(JobOwner owner, Generation parent, GenerationDirectives asked) {
        GenerationDirectives merged = GenerationDirectives
                .fromMap(parent.getDirectives())
                .and(asked);

        countManualEdits(parent.getSelectionState(), asked);

        return enqueue(owner, new SelectionEditPayload(parent.getId(), merged));
    }

    /**
     * A sentence: the ids are not decided yet, and reading it costs a call
     * (Bolum 24.2).
     *
     * <p>The ceiling is taken here rather than in the worker, for the reason
     * Bolum 44.2 takes it at every other queue point: a request that is going
     * to be refused must not have been accepted first. The worker gives it
     * back when the sentence named no line, or when the re-run failed.
     *
     * <p>The parent's own directives ride along and the parse merges onto
     * them, so "and take the other one out too" is a second edit of the same
     * document rather than a fresh one.
     *
     * @return the queued job, or the refusal the quota gave
     */
    public Result<Job> enqueue(
            JobOwner owner, QuotaSubject allowance, Generation parent, String instruction) {

        Result<Void> spent = quotas.consume(allowance, QuotaMetric.GENERATION);
        if (spent.isErr()) {
            return spent.map(ignored -> null);
        }

        var payload = new SelectionEditPayload(parent.getId(),
                GenerationDirectives.fromMap(parent.getDirectives()), instruction, allowance);
        return Result.ok(enqueue(owner, payload));
    }

    private Job enqueue(JobOwner owner, SelectionEditPayload payload) {
        var job = new Job(JobType.GENERATION, owner.userId(), payload.toMap(), clock.instant());
        job.setAnonSessionId(owner.anonSessionId());
        return queue.enqueue(job);
    }

    /**
     * What the parent generation may be told to do, and what it may not.
     *
     * @return the offending ids, or an empty list when the edit is answerable
     */
    public List<String> unknownIn(Generation parent, GenerationDirectives asked) {
        Map<UUID, Double> weighed = parent.getSelectionState().scoresByCandidate();
        var unknown = new ArrayList<String>();
        for (UUID atomId : asked.includeAtoms()) {
            if (!weighed.containsKey(atomId)) {
                unknown.add(atomId.toString());
            }
        }
        for (UUID atomId : asked.excludeAtoms()) {
            if (!weighed.containsKey(atomId)) {
                unknown.add(atomId.toString());
            }
        }
        return unknown;
    }

    /** An edit of a generation that has already been edited is an edit of the newer one. */
    public boolean isStale(Generation parent) {
        return parent.getStatus() == GenerationStatus.SUPERSEDED;
    }

    /**
     * Bolum 24.5, and it is a gauge on the selection algorithm rather than a
     * feature.
     *
     * <p>A person including something by hand means Faz B ranked it too low,
     * and excluding something means it ranked it too high. The score it
     * competed on is the interesting part — a manual include of a 0.7 atom is
     * a budget that was too tight, a manual include of a 0.1 atom is a scoring
     * miss — so the bucket travels as a tag.
     *
     * <p>This is not a learning system. Nothing reads these counters back into
     * the algorithm; they are for the developer deciding what to change
     * (Bolum 24.5).
     */
    private void countManualEdits(StoredSelection snapshot, GenerationDirectives asked) {
        Map<UUID, Double> weighed = snapshot.scoresByCandidate();
        for (UUID atomId : asked.includeAtoms()) {
            meters.counter("selection.manual_include",
                    "atomScore", bucket(weighed.get(atomId))).increment();
        }
        for (UUID atomId : asked.excludeAtoms()) {
            meters.counter("selection.manual_exclude",
                    "atomScore", bucket(weighed.get(atomId))).increment();
        }
    }

    /**
     * Tenths, as a tag value.
     *
     * <p>A tag is a dimension and an unbounded one is a memory leak, so the
     * score is bucketed rather than reported: eleven values at most, and the
     * question being asked — "how good was what the algorithm got wrong" —
     * cannot tell 0.412 from 0.418 anyway.
     */
    private static String bucket(Double score) {
        if (score == null) {
            return "unknown";
        }
        int tenth = (int) Math.floor(Math.max(0.0, Math.min(1.0, score)) * 10);
        return "0." + tenth;
    }
}
