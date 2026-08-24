package com.mustafatetik.atomcv.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns the scheduler on for the whole application.
 *
 * <p>It used to hang off the job worker's own switch, which was wrong as soon
 * as a second thing needed scheduling: turning the worker off would silently
 * take the anomaly detector (Bolum 44.3) with it, and nothing would say so.
 * Each scheduled component now carries its own {@code @ConditionalOnProperty}
 * and the timer itself is unconditional — a scheduler with nothing scheduled
 * costs one idle thread.
 */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
