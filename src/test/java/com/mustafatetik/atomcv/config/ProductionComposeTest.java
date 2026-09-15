package com.mustafatetik.atomcv.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The production topology, read as a document rather than as text.
 *
 * <p><strong>Nothing else parses this file before a deploy does.</strong> It
 * is excluded from every lane on purpose — `.claude/settings.json` refuses to
 * run it and the PreToolUse hook refuses again — so a mistake in it is found
 * by the server or not at all. Parsing it here costs nothing and catches the
 * class of error that makes a compose file fail at `up`: a mis-indented
 * service, a volume named in one place and not the other.
 *
 * <p>The assertions below are the decisions, not the shape. Each one is a
 * thing that would work in testing and fail on the machine.
 */
class ProductionComposeTest {

    /** From the repository root, which is where Gradle runs a test from. */
    private static final Path COMPOSE = Path.of("docker-compose.prod.yml");
    private static final Path NGINX = Path.of("docker/nginx/nginx.conf");

    @Test
    void thefileParsesAndDescribesTheServicesBolum11Names() {
        Map<String, Object> services = services();

        assertThat(services).containsKeys(
                "nginx", "frontend", "backend", "postgres", "redis", "latex",
                "embeddings", "umami");
    }

    /**
     * Bolum 52.5's archive lives on a volume. Written into the container's own
     * layer it would be produced on every start and read on none, because a
     * deploy recreates the container — a cache that is only ever a cost.
     */
    @Test
    void theclassDataArchiveOutlivesTheContainer() {
        assertThat(service("backend").get("volumes").toString())
                .contains("backendcache:/var/cache/atomcv");
        assertThat(volumes()).containsKey("backendcache");
    }

    /**
     * <strong>Analytics is opt-in, and the default deployment must not need
     * it.</strong> Umami is half a gigabyte and reports nothing until the
     * frontend has a website id; started by default it would be an empty
     * dashboard charged to an eight-gigabyte machine.
     */
    @Test
    void analyticsOnlyStartsWhenItIsAskedFor() {
        assertThat(service("umami").get("profiles"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly("analytics");
    }

    /** Its own database, so that an Umami upgrade migrates nothing of ours. */
    @Test
    void analyticsMigratesItsOwnDatabase() {
        assertThat(service("umami").get("environment").toString())
                .contains("postgres:5432/umami");
        assertThat(service("postgres").get("volumes").toString())
                .as("and the init script that creates it runs on first init")
                .contains("docker-entrypoint-initdb.d");
        assertThat(Path.of("docker/postgres/init-umami.sql")).exists();
    }

    /**
     * <strong>The one that would have taken the whole site down.</strong>
     * nginx resolves a literal upstream name at start-up and refuses to start
     * when it does not resolve. With analytics off — the default — a literal
     * {@code proxy_pass http://umami:3000} means nginx does not come up at
     * all, so the site is down because an optional dashboard is absent. A
     * variable defers the lookup to the request and turns that into a 502 on
     * one path.
     */
    @Test
    void theanalyticsUpstreamIsResolvedPerRequestAndNotAtStartUp() {
        String nginx = read(NGINX);

        assertThat(nginx)
                .as("a variable upstream, so nginx starts without umami")
                .contains("proxy_pass $umami_upstream;")
                .doesNotContain("proxy_pass http://umami:3000;");
        assertThat(nginx)
                .as("a variable upstream needs a resolver, or nginx 502s always")
                .contains("resolver 127.0.0.11");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> services() {
        return (Map<String, Object>) document().get("services");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> volumes() {
        return (Map<String, Object>) document().get("volumes");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> service(String name) {
        Object service = services().get(name);
        assertThat(service).as("no %s service", name).isNotNull();
        return (Map<String, Object>) service;
    }

    /**
     * Loaded with {@code ${...}} left alone. Compose substitutes those from
     * {@code .env}; SnakeYAML reads them as ordinary strings, which is all
     * this test needs and avoids pretending to know what the server's
     * environment holds.
     */
    private static Map<String, Object> document() {
        try (InputStream in = Files.newInputStream(COMPOSE)) {
            Object loaded = new Yaml().load(in);
            assertThat(loaded).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) loaded;
            return map;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(COMPOSE + " is what this test is about", unreadable);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(path + " is what this test is about", unreadable);
        }
    }
}
