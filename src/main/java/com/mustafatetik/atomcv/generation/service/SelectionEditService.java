package com.mustafatetik.atomcv.generation.service;

import com.mustafatetik.atomcv.generation.domain.Generation;
import com.mustafatetik.atomcv.generation.domain.GenerationStatus;
import com.mustafatetik.atomcv.generation.domain.StoredSelection;
import com.mustafatetik.atomcv.generation.selection.GenerationDirectives;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobOwner;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import com.mustafatetik.atomcv.jobs.queue.JobType;
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
 * <p>No quota and no model. Everything an edit re-runs is deterministic —
 * selection, render, compile — so there is nothing here for a day's allowance
 * to be spent on, and § 44's ceiling stays where it belongs: on the calls that
 * cost money.
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
    private final MeterRegistry meters;
    private final Clock clock;

    SelectionEditService(JobQueue queue, MeterRegistry meters, Clock clock) {
        this.queue = queue;
        this.meters = meters;
        this.clock = clock;
    }

    /**
     * @param parent the generation being edited, already read through a scoped
     *               repository — this never loads it, so the IDOR defence stays
     *               where absolute rule 3 puts it
     */
    public Job enqueue(JobOwner owner, Generation parent, GenerationDirectives asked) {
        GenerationDirectives merged = GenerationDirectives
                .fromMap(parent.getDirectives())
                .and(asked);

        countManualEdits(parent.getSelectionState(), asked);

        var job = new Job(JobType.GENERATION, owner.userId(),
                new SelectionEditPayload(parent.getId(), merged).toMap(),
                clock.instant());
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
