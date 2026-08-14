package com.mustafatetik.atomcv;

import org.springframework.boot.test.context.SpringBootTest;
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
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("local")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }
}
