package com.mustafatetik.atomcv.generation.phases.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * The cache against a real Redis (Bolum 18.6).
 *
 * <p>No Spring context: what a context would add here is wiring, and what
 * needs proving is that an analysis survives the round trip through a real
 * server and that the lifetime is actually applied. A mocked template can
 * assert that {@code set} was called with a {@code Duration}; only a server
 * can say the key expires.
 *
 * <p>{@code redis:7-alpine} is the image compose runs, so this is the same
 * server development uses.
 */
class JobAnalysisCacheIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static GenericContainer<?> redis;
    private static JobAnalysisCache cache;
    private static StringRedisTemplate template;

    @BeforeAll
    static void startRedis() {
        redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        redis.start();

        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379)));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        cache = new JobAnalysisCache(template, JSON);
    }

    @AfterAll
    static void stopRedis() {
        redis.stop();
    }

    @Test
    void ananalysisSurvivesTheRoundTripThroughARealServer() {
        cache.put("a real posting", "v1", analysis());

        var found = cache.find("a real posting", "v1");

        assertThat(found).isPresent();
        // The whole tree, not just the head: a nested record that Jackson
        // could not rebuild would come back as a miss and look like a cold
        // cache forever.
        assertThat(found.get().role().seniority()).isEqualTo(JobAnalysis.Seniority.SENIOR);
        assertThat(found.get().role().employmentType())
                .isEqualTo(JobAnalysis.EmploymentType.FULL_TIME);
        assertThat(found.get().requiredSkills()).singleElement()
                .satisfies(skill -> {
                    assertThat(skill.canonical()).isEqualTo("go");
                    assertThat(skill.importance()).isEqualTo(JobAnalysis.Importance.CRITICAL);
                });
        assertThat(found.get().experienceYears().max()).isNull();
        assertThat(found.get().embeddingTarget()).isEqualTo(
                "Senior Backend Engineer. Go. design payment systems. distributed systems");
    }

    /** Bolum 18.6: seven days, and a key with no expiry would never age out. */
    @Test
    void theEntryCarriesTheDocumentedLifetime() {
        cache.put("a posting with a lifetime", "v1", analysis());

        var remaining = template.getExpire(cache.keyFor("a posting with a lifetime", "v1"));

        assertThat(remaining).isPositive();
        assertThat(remaining).isCloseTo(JobAnalysisCache.TTL.toSeconds(),
                org.assertj.core.data.Offset.offset(60L));
    }

    @Test
    void twoPromptVersionsDoNotReadEachOthersEntries() {
        cache.put("one posting", "v1", analysis());

        assertThat(cache.find("one posting", "v2")).isEmpty();
        assertThat(cache.find("one posting", "v1")).isPresent();
    }

    /**
     * A stored value that no longer fits the record reads as a miss rather
     * than taking a generation down — adding a field to JobAnalysis makes
     * every entry written before it unreadable, and re-analysing is the right
     * answer to that.
     */
    @Test
    void anEntryThatNoLongerFitsTheRecordIsAMiss() {
        template.opsForValue().set(cache.keyFor("a stale posting", "v1"),
                "{\"requiredSkills\":\"not-a-list\"}");

        assertThat(cache.find("a stale posting", "v1")).isEmpty();
    }

    /**
     * The rule Bolum 18.6 does not state: a cache is an optimisation, and an
     * optimisation whose outage takes the product down is worse than not
     * having one. Measured against a server that is genuinely gone rather
     * than against a mock that was told to throw.
     */
    @Test
    void aServerThatIsGoneIsAMissRatherThanAFailure() {
        var unreachable = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 6399));
        unreachable.afterPropertiesSet();
        var offline = new StringRedisTemplate(unreachable);
        offline.afterPropertiesSet();
        var offlineCache = new JobAnalysisCache(offline, JSON);

        assertThat(offlineCache.find("a posting", "v1")).isEmpty();
        offlineCache.put("a posting", "v1", analysis());
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
