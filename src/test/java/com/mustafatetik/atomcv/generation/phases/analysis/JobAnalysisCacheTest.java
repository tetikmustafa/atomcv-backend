package com.mustafatetik.atomcv.generation.phases.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Bolum 18.6: the key, the normalisation, and what happens when Redis is not there. */
class JobAnalysisCacheTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JobAnalysisCache cache = new JobAnalysisCache(redis, JSON);

    // ── Normalisation (Bolum 18.6) ───────────────────────────────────────

    /**
     * The same posting pasted from a PDF and from a browser differs in exactly
     * this. Two entries for one posting is a cache that misses on purpose.
     */
    @Test
    void thesamePostingWithDifferentWhitespaceIsTheSameKey() {
        var fromBrowser = "We are seeking a senior backend engineer.\nApply within.";
        var fromPdf = "  We are seeking  a senior backend\r\n\r\nengineer.   Apply within.  ";

        assertThat(JobAnalysisCache.normalize(fromPdf))
                .isEqualTo(JobAnalysisCache.normalize(fromBrowser));
        assertThat(cache.keyFor(fromPdf, "v1")).isEqualTo(cache.keyFor(fromBrowser, "v1"));
    }

    @Test
    void adifferentPostingIsADifferentKey() {
        assertThat(cache.keyFor("one posting", "v1"))
                .isNotEqualTo(cache.keyFor("another posting", "v1"));
    }

    /**
     * The key never carries the posting itself — only a hash of it
     * (Bolum 18.6: the raw text is not stored).
     */
    @Test
    void theKeyIsAHashAndNotTheTextItself() {
        var key = cache.keyFor("We are seeking a senior backend engineer", "v1");

        assertThat(key).startsWith("jd:v1:").doesNotContain("seeking", "engineer");
        assertThat(key).matches("jd:v1:[0-9a-f]{64}");
    }

    /**
     * Bolum 18.6 keys on the posting alone. The version is added because a
     * prompt change has to invalidate — and because an A/B experiment
     * (Bolum 53.3) would otherwise measure nothing: the bucket sent to v2
     * would read whatever v1 had already cached for that posting.
     */
    @Test
    void twoPromptVersionsDoNotShareAnEntry() {
        assertThat(cache.keyFor("the same posting", "v1"))
                .isNotEqualTo(cache.keyFor("the same posting", "v2"));
    }

    // ── Round trip ───────────────────────────────────────────────────────

    @Test
    void ananalysisComesBackAsItWentIn() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(JSON.writeValueAsString(analysis()));

        var found = cache.find("a posting", "v1");

        assertThat(found).isPresent();
        assertThat(found.get().role().title()).isEqualTo("Senior Backend Engineer");
        assertThat(found.get().requiredSkills()).hasSize(1);
    }

    @Test
    void anAbsentEntryIsAMiss() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);

        assertThat(cache.find("a posting", "v1")).isEmpty();
    }

    @Test
    void anEntryIsWrittenWithTheDocumentedLifetime() {
        when(redis.opsForValue()).thenReturn(values);

        cache.put("a posting", "v1", analysis());

        org.mockito.Mockito.verify(values).set(
                anyString(), anyString(), org.mockito.ArgumentMatchers.eq(JobAnalysisCache.TTL));
        assertThat(JobAnalysisCache.TTL).hasDays(7);
    }

    // ── The rule Bolum 18.6 does not state ───────────────────────────────

    /**
     * A cache is an optimisation, and an optimisation whose outage takes the
     * product down is worse than not having one. Every call is wrapped for
     * this, and this is the test that says so.
     */
    @Test
    void aReadAgainstAnAbsentRedisIsAMissRatherThanAFailure() {
        when(redis.opsForValue()).thenThrow(
                new RedisConnectionFailureException("no route to host"));

        assertThat(cache.find("a posting", "v1")).isEmpty();
    }

    @Test
    void aWriteAgainstAnAbsentRedisIsSwallowed() {
        when(redis.opsForValue()).thenThrow(
                new RedisConnectionFailureException("no route to host"));

        cache.put("a posting", "v1", analysis());
    }

    /**
     * Adding a field to {@link JobAnalysis} makes every entry written before
     * it unreadable. Re-analysing is the right answer to that as well, so it
     * takes the same path as an outage.
     */
    @Test
    void anEntryThatNoLongerFitsTheRecordIsAMiss() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("{\"requiredSkills\": \"not-a-list\"}");

        assertThat(cache.find("a posting", "v1")).isEmpty();
    }

    private static JobAnalysis analysis() {
        return new JobAnalysis(
                new JobAnalysis.Role("Senior Backend Engineer", JobAnalysis.Seniority.SENIOR,
                        "fintech", JobAnalysis.EmploymentType.FULL_TIME,
                        JobAnalysis.WorkMode.REMOTE),
                new JobAnalysis.Company("Acme Payments", JobAnalysis.SizeHint.SCALEUP),
                List.of(new JobAnalysis.Skill("Go", "go", JobAnalysis.Importance.CRITICAL)),
                List.of(), List.of("design payment systems"), List.of("distributed systems"),
                new JobAnalysis.ExperienceYears(5, null),
                List.of("en"), "technical", "en", 0.94, List.of());
    }
}
