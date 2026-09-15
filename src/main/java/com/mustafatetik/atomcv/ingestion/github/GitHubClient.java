package com.mustafatetik.atomcv.ingestion.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * GitHub's public API, read (Bolum 31.8).
 *
 * <p><strong>Public data only, and that is the whole design.</strong> Bolum
 * 31.8 asks for no private repository permission, and because it asks for none
 * this needs no stored provider token — which matters, because Bolum 40.6.1
 * deliberately leaves {@code oauth_identities.access_token_enc} null and says a
 * token would arrive with key management. It does not have to: these endpoints
 * answer without one.
 *
 * <p><strong>A token is still read when there is one</strong>, and only for the
 * rate limit: unauthenticated GitHub allows sixty requests an hour per address,
 * which a single deployment shares among everybody. With
 * {@code GITHUB_API_TOKEN} set it is five thousand. The requests are the same
 * requests either way, and nothing here asks for a scope.
 *
 * <p>The host is a constant. Bolum 42.2's SSRF surface is the login in the
 * path, and {@link GitHubLogin} refuses anything that is not one.
 */
@Component
public class GitHubClient implements GitHubRepositories {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);

    private static final String API = "https://api.github.com";

    /** A suggestion screen waits on this; a slow provider must not hold it open. */
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    /**
     * Bolum 31.8 asks for fifteen, sorted by what was touched last. Thirty,
     * because the filter below throws most of them away and a second page is a
     * second request: the interesting repositories of somebody with a lot of
     * forks are not all in the first fifteen.
     */
    private static final int PAGE_SIZE = 30;

    /**
     * How many survivors get their language breakdown fetched. One call each,
     * so it is the number that decides what this costs -- ten is more projects
     * than a CV has room for.
     */
    static final int MAX_LANGUAGE_LOOKUPS = 10;

    private final HttpClient http;
    private final ObjectMapper json;
    private final GitHubProperties properties;

    GitHubClient(ObjectMapper json, GitHubProperties properties) {
        this.json = json;
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * The public repositories of one account, most recently pushed first, with
     * the significant ones' languages filled in.
     *
     * <p>Empty when the account does not exist, when GitHub refuses, or when it
     * cannot be reached. None of those is an error a person can act on and all
     * three mean the same thing to the screen: there is nothing to suggest.
     */
    @Override
    public List<GitHubRepository> repositoriesOf(GitHubLogin login) {
        Optional<JsonNode> listing = get(API + "/users/" + login.value()
                + "/repos?sort=pushed&per_page=" + PAGE_SIZE + "&type=owner");
        if (listing.isEmpty() || !listing.get().isArray()) {
            return List.of();
        }

        var repositories = new ArrayList<GitHubRepository>();
        for (JsonNode node : listing.get()) {
            repositories.add(repositoryOf(node));
        }

        var withLanguages = new ArrayList<GitHubRepository>(repositories.size());
        int fetched = 0;
        for (GitHubRepository repository : repositories) {
            if (repository.isSignificant() && fetched < MAX_LANGUAGE_LOOKUPS) {
                withLanguages.add(repository.withLanguages(languagesOf(login, repository)));
                fetched++;
            } else {
                withLanguages.add(repository);
            }
        }
        return List.copyOf(withLanguages);
    }

    private List<String> languagesOf(GitHubLogin login, GitHubRepository repository) {
        // The repository name comes back from GitHub rather than from a person,
        // so it is not the SSRF surface the login is -- but it is still put in
        // a path, and a name that is not a name means a request nobody meant.
        if (!repository.name().matches("[A-Za-z0-9._-]{1,100}")) {
            return List.of();
        }
        Optional<JsonNode> languages = get(
                API + "/repos/" + login.value() + "/" + repository.name() + "/languages");
        if (languages.isEmpty()) {
            return List.of();
        }
        var names = new ArrayList<String>();
        languages.get().fieldNames().forEachRemaining(names::add);
        return List.copyOf(names);
    }

    private static GitHubRepository repositoryOf(JsonNode node) {
        var topics = new ArrayList<String>();
        for (JsonNode topic : node.path("topics")) {
            topics.add(topic.asText());
        }
        return new GitHubRepository(
                node.path("name").asText(""),
                node.path("description").asText(null),
                node.path("html_url").asText(""),
                node.path("fork").asBoolean(false),
                node.path("archived").asBoolean(false),
                node.path("size").asInt(0),
                node.path("stargazers_count").asInt(0),
                node.path("language").asText(null),
                topics,
                List.of());
    }

    private Optional<JsonNode> get(String uri) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();
        if (properties.hasToken()) {
            request.header("Authorization", "Bearer " + properties.token());
        }

        try {
            HttpResponse<String> response =
                    http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // The status and nothing else. A body from GitHub can echo the
                // path, and the path carries a person's account name.
                log.info("GitHub answered {} for a repository listing", response.statusCode());
                return Optional.empty();
            }
            return Optional.of(json.readTree(response.body()));
        } catch (IOException | InterruptedException | RuntimeException unreachable) {
            if (unreachable instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.info("Could not reach GitHub: {}", unreachable.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
