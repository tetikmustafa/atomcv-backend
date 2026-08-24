package com.mustafatetik.atomcv.jobs.queue;

/**
 * Where the queue announces that something happened (Bolum 30.6).
 *
 * <p>An interface with no-op defaults, so the worker can be built without a
 * listener: the integration tests that assert on rows do not want an SSE
 * registry, and a queue that could not run without one would be a queue
 * coupled to HTTP.
 *
 * <p>Announcing is not recording. Every event here has already been written to
 * the job row by the time it is published — an event reaches whoever is
 * connected right now, and the row is what anyone else is caught up from
 * (EK D.6.4).
 */
public interface JobEvents {

    /** Announces nothing. The default when no one is listening. */
    JobEvents NONE = new JobEvents() {
    };

    /** The job moved along. */
    default void progress(Job job) {
    }

    /**
     * The job reached a state it will not leave.
     *
     * <p>The last thing a stream carries. A stream that closed without one is
     * the failure EK D.6.4 exists to prevent — a spinner over work that
     * finished — and the status endpoint is the way back from it.
     */
    default void terminal(Job job) {
    }
}
