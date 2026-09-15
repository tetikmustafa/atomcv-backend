package com.mustafatetik.atomcv.ingestion.github;

import java.util.List;

/**
 * Where a public GitHub account's repositories come from (Bolum 6's Ports and
 * Adapters, Bolum 31.8).
 *
 * <p>One method, and the interface exists for the reason Bolum 6 gives about
 * every external service: what this module is worth testing for is the
 * matching and the writing, and a test that reached GitHub would be measuring
 * GitHub's uptime. {@link GitHubClient} is the adapter.
 */
public interface GitHubRepositories {

    /**
     * @return what the account has, most recently pushed first, with the
     *         significant ones' languages filled in. Empty when the account
     *         does not exist, when GitHub refuses, and when it cannot be
     *         reached -- none of the three is something a person can act on
     */
    List<GitHubRepository> repositoriesOf(GitHubLogin login);
}
