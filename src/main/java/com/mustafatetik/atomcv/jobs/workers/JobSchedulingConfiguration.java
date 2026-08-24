package com.mustafatetik.atomcv.jobs.workers;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns the scheduler on, and only when there is a worker to schedule.
 *
 * <p>Separate from {@link JobWorker} so that switching the worker off switches
 * the timer off with it. An application with scheduling enabled and nothing
 * scheduled is harmless but misleading — it says work is happening.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "atomcv.jobs.worker.enabled", havingValue = "true", matchIfMissing = true)
class JobSchedulingConfiguration {
}
