package com.mustafatetik.atomcv.jobs.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import com.mustafatetik.atomcv.jobs.queue.Job;
import com.mustafatetik.atomcv.jobs.queue.JobEvents;
import com.mustafatetik.atomcv.jobs.queue.JobQueue;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The same event, on the other instance.
 *
 * <p><strong>What this closes.</strong> {@link SseRegistry} is in-process, and
 * Bolum 30.6 says so with a date on it: one instance runs the workers and
 * serves the streams, so the moment there are two, a watcher connected to A
 * hears nothing about a job running on B. The section names the way out in one
 * line — {@code NOTIFY job_progress} — and Bolum 50.3's readiness table already
 * claims it. This is that line, with the parts the line does not say.
 *
 * <p><strong>The payload carries an identifier, not an event.</strong> Bolum
 * 30.6's own rule is "the row first, then the announcement", and a notification
 * that carried the event would be a second copy of the truth with its own way
 * of being wrong — and a Postgres payload is capped at 8000 bytes, which a
 * failed generation's error map can reach. So the receiving instance is told
 * which job moved and loads the row, which is the same thing a reconnecting
 * client is caught up from.
 *
 * <p><strong>An instance does not listen to itself.</strong> The notification
 * goes out on a pooled connection and comes back on the listening one, so
 * without the sender's id every local watcher would be sent everything twice.
 *
 * <p><strong>A dedicated connection, outside the pool.</strong> {@code LISTEN}
 * holds a session for as long as the process lives; taking that from a pool of
 * ten would be spending a tenth of the database on it, and the first thing to
 * notice would be something unrelated timing out.
 *
 * <p>Nothing here is load-bearing for correctness. A notification that is lost,
 * dropped or never sent costs a watcher its live update and nothing else: the
 * row is written either way, {@code GET /jobs/{id}} answers from it, and the
 * stream's own reconnect sends the current state. That is why the listener
 * logs and reconnects rather than failing the application.
 */
@Component
@Primary
public class JobNotifications implements JobEvents, SmartLifecycle {

    /** Bolum 30.6 names it. An identifier, so it cannot need quoting. */
    static final String CHANNEL = "job_progress";

    /**
     * How long a poll waits before looking at {@code running} again. Long
     * enough that an idle deployment is not spinning, short enough that a
     * shutdown does not visibly hang on it.
     */
    private static final int POLL_MS = 1_000;

    /** After a dropped connection. The database is usually back before this. */
    private static final long RECONNECT_MS = 2_000;

    private static final Logger log = LoggerFactory.getLogger(JobNotifications.class);

    /**
     * Who this instance is, for the whole of its life. Not the worker id:
     * workers come and go within a process, and what has to be told apart here
     * is one process from another.
     */
    private final String instanceId = UUID.randomUUID().toString();

    private final SseRegistry local;
    private final JobQueue queue;
    private final DataSource pool;
    private final DataSourceProperties datasource;
    private final ObjectMapper json;

    private volatile boolean running;
    private volatile Thread listener;

    JobNotifications(SseRegistry local, JobQueue queue, DataSource pool,
            DataSourceProperties datasource, ObjectMapper json) {
        this.local = local;
        this.queue = queue;
        this.pool = pool;
        this.datasource = datasource;
        this.json = json;
    }

    /** Who this process says it is on the wire. Read by the test that proves
     * an instance does not deliver its own announcements to itself. */
    String instanceId() {
        return instanceId;
    }

    // ── the sending half ──────────────────────────────────────────────────

    @Override
    public void progress(Job job) {
        local.progress(job);
        announce(job.getId());
    }

    @Override
    public void terminal(Job job) {
        local.terminal(job);
        announce(job.getId());
    }

    /**
     * Tells the other instances that this job moved.
     *
     * <p>On a pooled connection and in its own statement, so it is not inside
     * whatever transaction the caller had: a {@code NOTIFY} is delivered on
     * commit, and one enlisted in a transaction that rolls back would leave a
     * watcher waiting on an event that was never coming.
     */
    private void announce(UUID jobId) {
        try (Connection connection = pool.getConnection();
                PreparedStatement notify =
                        connection.prepareStatement("SELECT pg_notify(?, ?)")) {
            notify.setString(1, CHANNEL);
            notify.setString(2, json.writeValueAsString(
                    Map.of("job", jobId.toString(), "from", instanceId)));
            notify.execute();
        } catch (SQLException | RuntimeException | com.fasterxml.jackson.core.JsonProcessingException
                failed) {
            // A watcher on another instance misses one update. The row is
            // written, the status endpoint answers from it, and the next event
            // on this job carries the same news.
            log.debug("Could not announce job {}: {}", jobId, failed.toString());
        }
    }

    // ── the listening half ────────────────────────────────────────────────

    @Override
    public void start() {
        if (!isPostgres()) {
            // Nothing to listen on. The local registry still works, which is
            // every deployment of one instance.
            log.info("No Postgres behind the datasource; job events stay in-process");
            return;
        }
        running = true;
        listener = Thread.ofVirtual().name("job-notifications").start(this::listen);
    }

    @Override
    public void stop() {
        running = false;
        Thread waiting = listener;
        if (waiting != null) {
            waiting.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Last up and first down.
     *
     * <p>Started after the worker so that a notification never arrives before
     * there is anything to hand it to, and stopped before it so that the
     * shutdown's own terminal events still go out.
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void listen() {
        while (running) {
            try (Connection connection = open()) {
                PGConnection pg = connection.unwrap(PGConnection.class);
                try (Statement listen = connection.createStatement()) {
                    listen.execute("LISTEN " + CHANNEL);
                }
                log.info("Listening on {} as instance {}", CHANNEL, instanceId);
                pump(pg);
            } catch (SQLException dropped) {
                if (!running) {
                    return;
                }
                log.warn("The {} listener lost its connection: {}", CHANNEL, dropped.toString());
                sleepBeforeReconnecting();
            }
        }
    }

    private void pump(PGConnection pg) throws SQLException {
        while (running) {
            PGNotification[] arrived = pg.getNotifications(POLL_MS);
            if (arrived == null) {
                continue;
            }
            for (PGNotification notification : arrived) {
                deliver(notification.getParameter());
            }
        }
    }

    /**
     * One announcement, turned back into an event for this instance's watchers.
     *
     * <p>Everything here is best-effort by design. A payload this instance
     * cannot read, a job id that names no row, a row that vanished — none of
     * them is a reason to drop the listener, and every one of them costs
     * exactly one live update.
     */
    void deliver(String payload) {
        try {
            Map<?, ?> announcement = json.readValue(payload, Map.class);
            if (instanceId.equals(announcement.get("from"))) {
                // Our own. The local registry was told before this went out.
                return;
            }
            UUID jobId = UUID.fromString(String.valueOf(announcement.get("job")));
            if (local.watcherCount(jobId) == 0) {
                // Nobody here is watching, which is the common case with more
                // than one instance: no read, no work.
                return;
            }
            queue.find(jobId).ifPresent(job -> {
                if (job.getStatus().isTerminal()) {
                    local.terminal(job);
                } else {
                    local.progress(job);
                }
            });
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException unreadable) {
            log.debug("Ignoring an announcement on {}: {}", CHANNEL, unreadable.toString());
        }
    }

    /**
     * A connection of its own, built from the same settings as the pool.
     *
     * <p>Through {@link DriverManager} rather than {@link DataSource#getConnection()}
     * on purpose: this session is held for the life of the process, and a
     * pooled one would be a permanent tenth of the pool.
     */
    private Connection open() throws SQLException {
        return DriverManager.getConnection(url(), username(), password());
    }

    /**
     * <strong>The pool's own settings, not the configured ones.</strong>
     *
     * <p>{@link DataSourceProperties} carries what {@code spring.datasource.*}
     * said, and that is not always where the pool actually points: a
     * {@code @ServiceConnection} container and a cloud connector both configure
     * the {@link DataSource} directly and leave the properties at their
     * defaults. Reading the properties made the listener dial a database that
     * was not there and reconnect every two seconds for the life of the suite
     * — the first version of this class did exactly that, and it looked like a
     * delivery bug rather than an address one.
     */
    private String url() {
        return fromPool(HikariDataSource::getJdbcUrl, datasource.determineUrl());
    }

    private String username() {
        return fromPool(HikariDataSource::getUsername, datasource.determineUsername());
    }

    private String password() {
        return fromPool(HikariDataSource::getPassword, datasource.determinePassword());
    }

    private String fromPool(java.util.function.Function<HikariDataSource, String> read,
            String configured) {
        try {
            if (pool.isWrapperFor(HikariDataSource.class)) {
                String actual = read.apply(pool.unwrap(HikariDataSource.class));
                if (actual != null && !actual.isBlank()) {
                    return actual;
                }
            }
        } catch (SQLException | RuntimeException notHikari) {
            log.debug("Reading the pool's settings failed: {}", notHikari.toString());
        }
        return configured;
    }

    private boolean isPostgres() {
        String url = url();
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    private void sleepBeforeReconnecting() {
        try {
            Thread.sleep(RECONNECT_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
