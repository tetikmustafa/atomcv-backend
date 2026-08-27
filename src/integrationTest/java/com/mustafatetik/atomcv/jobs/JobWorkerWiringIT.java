package com.mustafatetik.atomcv.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.jobs.queue.JobType;
import com.mustafatetik.atomcv.jobs.workers.JobWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The worker can actually be built by Spring.
 *
 * <p>Written after {@code make dev} failed to start and no test had noticed.
 * {@code JobWorker} had two constructors and neither was annotated, so Spring
 * looked for a no-arg one and gave up — but the whole integration suite
 * switches the worker off so its scheduler cannot claim rows other tests are
 * asserting on, and the two tests that exercise it build it by hand. Between
 * them, <strong>nothing had ever asked Spring to create this bean</strong>.
 *
 * <p>That is the shape of the gap, not the annotation: a component disabled
 * everywhere is a component whose wiring is unverified, and the failure lands
 * on whoever runs the application rather than on CI. This class exists to hold
 * one context where it is on.
 *
 * <p>Its own context and its own database, because the point is a context that
 * starts with the worker enabled and the shared one deliberately does not.
 */
@SpringBootTest(properties = {
        "atomcv.jobs.worker.enabled=true",
        // Far enough out that the scheduler cannot take anything while the
        // context is up. The heartbeat is left alone: pushing it past
        // staleAfter is refused by JobWorkerProperties itself, which is the
        // guard doing its job and was the first thing this test hit.
        "atomcv.jobs.worker.poll-interval=PT1H",
        "atomcv.anomaly.enabled=false"})
@ActiveProfiles("local")
class JobWorkerWiringIT {

    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JobWorker worker;

    @Test
    void thecontextStartsWithTheWorkerOn() {
        assertThat(worker).isNotNull();
        assertThat(worker.workerId()).isNotBlank();
    }

    /**
     * And it found the handler. A worker wired to an empty list starts
     * perfectly well and fails every job it takes as unhandled — which looks
     * like a broken pipeline rather than a missing bean.
     */
    @Test
    void theworkerCanRunEveryJobTypeThisApplicationEnqueues() {
        assertThat(context.getBeansOfType(
                com.mustafatetik.atomcv.jobs.queue.JobHandler.class).values())
                .extracting(com.mustafatetik.atomcv.jobs.queue.JobHandler::type)
                // Adim 3.4 added the second one. The list grows as the
                // application learns to enqueue a type, and the types it does
                // not enqueue yet are deliberately absent — a handler for one
                // of those would be a bean nothing can reach.
                .contains(JobType.GENERATION, JobType.PROFILE_EXTRACT,
                        JobType.EMBEDDING, JobType.MEASUREMENT, JobType.TRANSLATION);
    }
}
