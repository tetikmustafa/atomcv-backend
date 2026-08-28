package com.mustafatetik.atomcv.retention;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How long this deployment keeps the text a person pasted or uploaded.
 *
 * <p>Two windows rather than one, because the two are read by different things
 * for different lengths of time. A finished job's payload is dead the moment
 * the job ends — nothing reads it again. A generation's posting is what the
 * person would look at to remember which application a CV belongs to, so it
 * outlives the job by a good margin and still goes.
 *
 * @param enabled false in the integration suite, where a sweep firing on a
 *                schedule would clear rows other tests are asserting on
 * @param cron    when the sweep runs. After the nightly backup (Adim V.8), so
 *                that the last copy of a cleared row is at most a day old
 * @param jobPayload      how long a terminal job keeps the input it ran on
 * @param jobDescription  how long a generation keeps the posting it was
 *                        written against
 */
@ConfigurationProperties(prefix = "atomcv.retention")
public record RetentionProperties(
        boolean enabled, String cron, Duration jobPayload, Duration jobDescription) {

    public RetentionProperties {
        cron = cron == null || cron.isBlank() ? "0 30 3 * * *" : cron;
        jobPayload = positiveOr(jobPayload, Duration.ofDays(7));
        jobDescription = positiveOr(jobDescription, Duration.ofDays(30));
    }

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
