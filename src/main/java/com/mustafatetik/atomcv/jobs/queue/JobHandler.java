package com.mustafatetik.atomcv.jobs.queue;

/**
 * What actually does the work of one job type (Bolum 30).
 *
 * <p>One handler per {@link JobType}, found by the worker at startup. A type
 * with no handler is a job that fails rather than one that sits in the queue
 * forever — an unhandled row is a deployment mistake, and a queue that hides
 * it is worse than one that reports it.
 *
 * <p>Implementations live in the module that owns the work, not here: the
 * generation handler is part of {@code generation}, which already holds
 * everything it needs. The queue knows how to run a handler and nothing about
 * what any of them do.
 */
public interface JobHandler {

    JobType type();

    /**
     * Runs the job.
     *
     * @param progress where to say how far along it is. Called or not called
     *                 as the work allows: a handler that cannot honestly
     *                 divide itself into phases reports nothing rather than
     *                 inventing percentages, and the terminal event still
     *                 arrives.
     *
     * <p>Returning {@link JobOutcome.Failed} is the ordinary way to fail: the
     * error is one the user is told about, and the handler has decided whether
     * another attempt is worth making. Throwing is the other way, and the
     * worker treats it as an unexpected failure — retried while attempts
     * remain, because an exception is by definition something nobody reasoned
     * about.
     */
    JobOutcome handle(Job job, ProgressSink progress);
}
