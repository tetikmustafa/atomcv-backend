package com.mustafatetik.atomcv.identity.api;

/**
 * Where a person is sent once they are signed in — and the guard that keeps
 * that from being anywhere.
 *
 * <p>A caller-supplied redirect target is an open redirect: the phishing lever
 * where a link that genuinely begins at our domain, and looks it, ends on
 * someone else's login form. The person has every reason to trust the address
 * bar they started from.
 *
 * <p>Its own class, small as it is, because it is a guard: it can be given a
 * list of the shapes that have to be refused and watched refuse each one.
 * Folded into the controller it could only be tested through a full round
 * trip, and the vectors that matter would go untried.
 */
final class ReturnPath {

    private ReturnPath() {
    }

    /**
     * The path to land on, or {@code "/"} for anything that is not a plain
     * path on this site.
     *
     * <p>Four rejections and each has a reason. Missing a leading slash lets
     * {@code evil.example} through as a relative-looking absolute. Two leading
     * slashes are a protocol-relative URL, which a browser resolves as
     * absolute — the one a check for "starts with /" waves straight past. A
     * backslash is normalised to a slash by some browsers, so
     * {@code /\evil.example} becomes protocol-relative after the fact. And a
     * colon anywhere admits {@code javascript:} and every other scheme.
     */
    static String of(String next) {
        if (next == null || next.isBlank()
                || !next.startsWith("/")
                || next.startsWith("//")
                || next.contains("\\")
                || next.contains(":")) {
            return "/";
        }
        return next;
    }
}
