package com.mustafatetik.atomcv.ingestion.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What the GitHub import needs, which is almost nothing (Bolum 31.8).
 *
 * @param token optional, and only ever about the rate limit. Bolum 31.8 reads
 *              public data, so every request here works without one;
 *              unauthenticated GitHub allows sixty an hour <em>per address</em>,
 *              which one deployment shares among everybody, and a token raises
 *              that to five thousand. No scope is requested and none is used,
 *              so a fine-grained token with read-only public access is the
 *              right thing to put here.
 *
 *              <p>Deliberately not the provider token of Bolum 40.6.1: that one
 *              belongs to a person and is not stored, and this one belongs to
 *              the deployment.
 */
@ConfigurationProperties(prefix = "atomcv.github")
public record GitHubProperties(String token) {

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
