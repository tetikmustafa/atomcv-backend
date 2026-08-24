package com.mustafatetik.atomcv.jobs.workers;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How this instance works the queue (Bolum 30.4).
 *
 * @param enabled        false leaves the queue alone. Integration tests drive
 *                       the worker by hand, and a scheduler firing underneath
 *                       them would claim the rows they are asserting about.
 * @param concurrency    how many jobs this instance runs at once. Bolum 30 does
 *                       not give a number; two is chosen because a generation
 *                       spends most of its time waiting on a provider or on
 *                       TeX, and one instance that can only wait once wastes
 *                       the wait.
 * @param pollInterval   how often the queue is asked. Short enough that a user
 *                       on the screen does not feel it, long enough that an
 *                       idle deployment is not running a statement twice a
 *                       second.
 * @param heartbeatEvery Bolum 30.4, and it must stay well under
 *                       {@code staleAfter} — a heartbeat that fires as rarely
 *                       as the collector looks makes every slow job a zombie.
 * @param staleAfter     how long a silent worker is given before its jobs go
 *                       back to the queue
 * @param shutdownGrace  how long a shutdown waits for jobs in hand before it
 *                       gives up and hands them back. A property rather than a
 *                       constant so the test that proves the handing back can
 *                       run in a second instead of thirty.
 */
@ConfigurationProperties(prefix = "atomcv.jobs.worker")
public record JobWorkerProperties(
        boolean enabled,
        int concurrency,
        Duration pollInterval,
        Duration heartbeatEvery,
        Duration staleAfter,
        Duration shutdownGrace) {

    public JobWorkerProperties {
        concurrency = concurrency < 1 ? 2 : concurrency;
        pollInterval = pollInterval == null ? Duration.ofMillis(500) : pollInterval;
        heartbeatEvery = heartbeatEvery == null ? Duration.ofSeconds(20) : heartbeatEvery;
        staleAfter = staleAfter == null ? Duration.ofMinutes(2) : staleAfter;
        shutdownGrace = shutdownGrace == null ? Duration.ofSeconds(30) : shutdownGrace;
        if (heartbeatEvery.compareTo(staleAfter) >= 0) {
            throw new IllegalArgumentException(
                    "A heartbeat that fires no more often than the collector looks makes"
                            + " every running job a zombie: " + heartbeatEvery + " vs "
                            + staleAfter);
        }
    }
}
