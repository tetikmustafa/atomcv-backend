package com.mustafatetik.atomcv.generation.phases.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * The same posting, analysed once (Bolum 18.6).
 *
 * <p>Worth having because the same text arrives again far more often than it
 * looks: Faz G's edit loop re-runs the pipeline, trying another template or
 * another language re-runs it, and a popular posting arrives from many people.
 * Each of those is a call that costs nothing on the second pass.
 *
 * <p><strong>The raw posting is never stored.</strong> The key is a hash of it
 * and the value is the analysis — which is derived from a public job ad, not
 * from the user's own profile.
 *
 * <p><strong>A cache failure is a miss, never a failed generation.</strong>
 * Bolum 18.6 does not say so, and it has to be said: this is an optimisation,
 * and an optimisation whose outage takes the product down is worse than not
 * having it. Every Redis call here is wrapped for that reason.
 */
@Component
public class JobAnalysisCache {

    /** Bolum 18.6. Long enough to cover a return visit, short enough to age out. */
    static final Duration TTL = Duration.ofDays(7);

    private static final String PREFIX = "jd:";

    private static final Logger log = LoggerFactory.getLogger(JobAnalysisCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public JobAnalysisCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public Optional<JobAnalysis> find(String jobDescription, String promptVersion) {
        try {
            var cached = redis.opsForValue().get(keyFor(jobDescription, promptVersion));
            return cached == null
                    ? Optional.empty()
                    : Optional.of(json.readValue(cached, JobAnalysis.class));
        } catch (Exception unavailableOrStale) {
            // Includes a stored value that no longer fits the record — a field
            // added to JobAnalysis makes every entry from before it unreadable,
            // and re-analysing is the right answer to that too.
            log.warn("Job analysis cache read failed, continuing as a miss: {}",
                    unavailableOrStale.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void put(String jobDescription, String promptVersion, JobAnalysis analysis) {
        try {
            redis.opsForValue().set(keyFor(jobDescription, promptVersion),
                    json.writeValueAsString(analysis), TTL);
        } catch (Exception unavailable) {
            log.warn("Job analysis cache write failed, continuing without it: {}",
                    unavailable.getClass().getSimpleName());
        }
    }

    /**
     * {@code jd:{promptVersion}:{sha256}}.
     *
     * <p>Bolum 18.6 keys on the posting alone. The version is here because a
     * prompt change has to invalidate: without it a v2 prompt would serve v1's
     * answers for a week, and — worse — an A/B experiment would be measuring
     * nothing at all, since the bucket sent to v2 would read whatever v1 had
     * already cached for that posting (Bolum 53.3).
     */
    String keyFor(String jobDescription, String promptVersion) {
        return PREFIX + promptVersion + ":" + JobDescriptionDigest.of(jobDescription);
    }

    /** Kept for the tests that were written against it; see {@link JobDescriptionDigest}. */
    static String normalize(String jobDescription) {
        return JobDescriptionDigest.normalize(jobDescription);
    }
}
