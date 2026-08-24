package com.mustafatetik.atomcv.jobs.queue;

/**
 * Where a handler says how far along it is (Bolum 30.6).
 *
 * <p>Passed in rather than reached for, so that a handler can be run with a
 * sink that does nothing: the pipeline's own tests care about the document,
 * not about who was told what. It also keeps the direction right — a handler
 * reports, and what happens to the report (a row, an event, both) is the
 * queue's business.
 */
@FunctionalInterface
public interface ProgressSink {

    /** Ignores everything. What a test uses, and what a job with no watcher costs. */
    ProgressSink NONE = progress -> {
    };

    void report(JobProgress progress);
}
