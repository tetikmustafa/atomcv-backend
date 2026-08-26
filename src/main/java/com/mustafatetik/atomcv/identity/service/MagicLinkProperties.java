package com.mustafatetik.atomcv.identity.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the link in the email points.
 *
 * <p>At the frontend, not here, and that is Bolum 40.3: the address in the
 * email is a {@code GET} that renders a page with a button on it. Corporate
 * mail scanners follow links automatically, and a one-shot token spent by a
 * scanner is a sign-in the person never got. The redemption is the
 * {@code POST} that button makes.
 *
 * @param verifyBaseUrl the origin the person opens, without a trailing slash
 * @param verifyPath    the page that carries the button
 */
@ConfigurationProperties(prefix = "atomcv.magic-link")
public record MagicLinkProperties(String verifyBaseUrl, String verifyPath) {

    public MagicLinkProperties {
        verifyBaseUrl = verifyBaseUrl == null || verifyBaseUrl.isBlank()
                ? "http://localhost:3000"
                : verifyBaseUrl.replaceAll("/+$", "");
        verifyPath = verifyPath == null || verifyPath.isBlank() ? "/verify" : verifyPath;
    }
}
