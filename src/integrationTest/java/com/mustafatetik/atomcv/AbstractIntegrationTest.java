package com.mustafatetik.atomcv;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One database and one application context for the whole integration suite.
 *
 * <p>The {@code local} profile is active because it is the only profile that
 * supplies a {@link com.mustafatetik.atomcv.shared.security.CurrentUser}. That
 * is deliberate: there is no fallback implementation, so an environment that
 * has not decided who is acting fails to start rather than serving one user's
 * data to everyone. Tests live under the same rule as development does.
 *
 * <p>Hibernate statistics are on for the whole suite. They cost nothing here
 * and they are how the six-query budget of Bolum 52.2 is measured; leaving the
 * property on a single class meant it disappeared the moment that class was
 * refactored, and the counter then read zero without failing.
 *
 * <p>The container is started here and never stopped — the singleton pattern
 * Testcontainers documents. {@code @Testcontainers} with {@code @Container}
 * would stop it after the first test class while Spring's cached context still
 * pointed at its port, and every later class would fail on a refused
 * connection. Ryuk removes it when the JVM exits.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        // The worker is driven by hand in the tests that care about it. A
        // scheduler firing underneath the others would claim the very rows
        // they are asserting about, and the failure would look like a flake.
        "atomcv.jobs.worker.enabled=false",
        // Likewise: a detector firing on the schedule would report on rows
        // other tests are still writing. AnomalyDetectorIT calls it directly.
        "atomcv.anomaly.enabled=false"})
@ActiveProfiles("local")
@Import(AbstractIntegrationTest.CsrfOnEveryRequest.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    /**
     * Bolum 40.1 puts sessions in Redis, so the suite needs a real one. With
     * none, {@code SessionStore} fails every lookup and fails it the way it is
     * meant to — as nobody signed in — and a test of the session would be
     * asserting against an outage rather than against the store. Bolum 18.6's
     * analysis cache lands here too; it had been tolerating the same absence
     * unnoticed, because a cache miss and an outage look alike from outside.
     */
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    /**
     * CSRF is on for the whole suite, and no test had to be edited for it.
     *
     * <p>The alternative was switching the filter off in tests, and CLAUDE.md
     * has the answer to that: a guard the suite disables has unverified
     * wiring. One default request post-processor puts a valid token on every
     * mutating call in the suite, so what the tests exercise is the chain as
     * production runs it. {@code CsrfRejectionIT} is the other half — it
     * performs the same call <em>without</em> the post-processor and asserts
     * the refusal, because a guard that has never failed is not known to work.
     *
     * <p><strong>{@code @Import} and not detection.</strong> A nested
     * {@code @TestConfiguration} is picked up automatically only on the class
     * actually being run, never on a base class it inherits from — without
     * the annotation above, this bean is silently absent and every mutating
     * call in the suite answers 403.
     */
    @TestConfiguration
    static class CsrfOnEveryRequest {

        @Bean
        MockMvcBuilderCustomizer defaultCsrfToken() {
            return builder -> builder.defaultRequest(get("/").with(csrf()));
        }
    }
}
