package com.mustafatetik.atomcv.shared.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Who the caller is, for the layer that counts callers.
 *
 * <p><strong>{@code getRemoteAddr()} and nothing else, deliberately.</strong>
 * Reading {@code X-Forwarded-For} here would hand every client a free way to
 * pick its own bucket — the header is a request header, so a script that
 * rotates it is unlimited. Behind a proxy the right fix is
 * {@code server.forward-headers-strategy}, which lets Spring's
 * {@code ForwardedHeaderFilter} rewrite the request before it reaches any of
 * this, and which is only safe because the proxy is the one that sets the
 * header.
 *
 * <p><strong>The trap that follows from it:</strong> with the strategy unset
 * behind Nginx, every caller arrives as the proxy's address and the whole
 * deployment shares one bucket. That is a limiter that looks configured,
 * passes its tests, and locks everybody out on the tenth request. Written down
 * in {@code spec/11-operations.md} Bolum 46.
 */
public final class ClientIp {

    private ClientIp() {
    }

    /**
     * @return the caller's address, or a constant standing in for "no address"
     *         — a container that cannot report one is still counted, and
     *         counted together, which is the safe direction
     */
    public static String of(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }
}
