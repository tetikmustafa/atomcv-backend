package com.mustafatetik.atomcv.ingestion.github;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A GitHub account name, checked before it becomes part of a URL.
 *
 * <p><strong>This is the SSRF boundary of this module</strong>. The host is a
 * constant and nothing a person types decides where a request goes — but a
 * login is interpolated into a path, and a path is where {@code../} lives. A
 * value that reaches {@code api.github.com/users/..%2f..} is a request to
 * somewhere nobody chose.
 *
 * <p>The rule is GitHub's own and is narrow enough to be worth stating rather
 * than escaping: alphanumerics and single hyphens, never leading or trailing,
 * at most 39 characters. Anything else is refused rather than encoded, because
 * a login that needs encoding is not a login.
 *
 * <p>Lowercased with {@code Locale.ROOT} (absolute rule 7). GitHub logins are
 * case-insensitive, and a Turkish default locale would write "ı" for "I" in a
 * name that then matches nobody.
 */
public record GitHubLogin(String value) {

    private static final Pattern VALID =
            Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$");

    public GitHubLogin {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a GitHub login");
        }
        value = value.toLowerCase(Locale.ROOT);
    }

    /** Empty rather than an exception: the caller is reading somebody's profile. */
    public static Optional<GitHubLogin> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.strip();
        return VALID.matcher(trimmed).matches()
                ? Optional.of(new GitHubLogin(trimmed))
                : Optional.empty();
    }

    /**
     * The login inside a GitHub URL, which is the form a CV carries.
     *
     * <p>{@code profiles.contact.github} holds whatever the extraction read
     * off the page — usually {@code https://github.com/torvalds}, sometimes
     * with a trailing slash, occasionally the bare name. The last non-empty
     * path segment is the login in all three, and anything with more than one
     * segment after the host is a repository rather than an account.
     */
    public static Optional<GitHubLogin> fromProfileUrl(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String path = url.strip()
                .replaceFirst("^https?://", "")
                .replaceFirst("^(www\\.)?github\\.com/", "");

        // Exactly one segment, and the emptiness matters: a trailing slash is
        // still an account, and two segments is a *repository*. Reading the
        // owner out of one would import somebody else's work on the strength
        // of a link in their CV -- which is often precisely a link to
        // somebody else's work.
        List<String> segments = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
        return segments.size() == 1 ? parse(segments.get(0)) : Optional.empty();
    }

    @Override
    public String toString() {
        return value;
    }
}
