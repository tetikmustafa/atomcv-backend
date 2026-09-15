package com.mustafatetik.atomcv.ingestion.github;

import java.util.List;
import java.util.Locale;

/**
 * One public repository, as the listing endpoint describes it.
 *
 * <p><strong>Only what {@code /users/{login}/repos} carries.</strong> The
 * filter also names a commit count and whether there is a README, and neither
 * is in the listing: each costs one request per repository, so a person with
 * thirty repositories would spend sixty calls against an hourly budget of
 * sixty. The signals kept — fork, size, stars, name, description — are the
 * ones the same listing already answered, and they say the same thing the
 * missing two were there to say: has anybody done anything with this.
 *
 * <p>{@code languages} is filled in afterwards and only for the repositories
 * that survived the filter, because that endpoint is one call each too.
 *
 * @param topics GitHub's own labels. Free in the listing, and the closest
 *               thing to the tags Bolum 19.2 scores against
 */
public record GitHubRepository(
        String name,
        String description,
        String url,
        boolean fork,
        boolean archived,
        int sizeKb,
        int stars,
        String primaryLanguage,
        List<String> topics,
        List<String> languages) {

    /** Names that say the repository is a lesson rather than a project. */
    private static final List<String> LEARNING_NAMES = List.of(
            "hello-world", "helloworld", "test", "tutorial", "learning", "playground",
            "sandbox", "practice", "demo", "example", "bootcamp", "exercise", "scratch",
            "dotfiles", "config", "template");

    /** Fifty kilobytes is about where a repository stops being a file. */
    private static final int MIN_SIZE_KB = 50;

    public GitHubRepository {
        topics = topics == null ? List.of() : List.copyOf(topics);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }

    public GitHubRepository withLanguages(List<String> found) {
        return new GitHubRepository(name, description, url, fork, archived, sizeKb, stars,
                primaryLanguage, topics, found);
    }

    /**
     * The {@code isSignificant}, with the two signals the listing cannot
     * answer replaced by ones it can.
     *
     * <p>A fork is somebody else's work and an archived repository is one the
     * person has closed. Past that the question is whether anyone has done
     * anything with it: a star, a description, or a subject label are each
     * evidence that it was meant to be read, and the size floor removes the
     * one-file repositories that are neither.
     */
    public boolean isSignificant() {
        if (fork || archived || sizeKb < MIN_SIZE_KB || looksLikeAlesson()) {
            return false;
        }
        return stars > 0
                || description != null && !description.isBlank()
                || !topics.isEmpty();
    }

    private boolean looksLikeAlesson() {
        // Locale.ROOT: absolute rule 7. On a Turkish default locale a
        // repository called "TEST" lowercases to "tesT" and passes a filter
        // written to catch it.
        String lower = name.toLowerCase(Locale.ROOT);
        return LEARNING_NAMES.stream()
                .anyMatch(word -> lower.equals(word)
                        || lower.startsWith(word + "-")
                        || lower.endsWith("-" + word));
    }
}
