package com.mustafatetik.atomcv.identity.api;

import com.mustafatetik.atomcv.identity.domain.OAuthProvider;
import com.mustafatetik.atomcv.identity.oauth.OAuthClient;
import com.mustafatetik.atomcv.identity.oauth.OAuthExchange;
import com.mustafatetik.atomcv.identity.oauth.OAuthFailure;
import com.mustafatetik.atomcv.identity.oauth.OAuthProperties;
import com.mustafatetik.atomcv.identity.oauth.OAuthStateStore;
import com.mustafatetik.atomcv.identity.service.OAuthLoginService;
import com.mustafatetik.atomcv.identity.service.SessionCookies;
import com.mustafatetik.atomcv.identity.service.SignInHandover;
import com.mustafatetik.atomcv.identity.service.SignInOutcome;
import com.mustafatetik.atomcv.profile.service.ProfileUpgrade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The OAuth round trip (Bolum 40.6).
 *
 * <p><strong>Both endpoints are browser navigations, so every outcome is a
 * redirect.</strong> Nobody reads a JSON body here — the person is looking at
 * a page — so a failure lands on the frontend's error route carrying
 * {@code OAUTH_FAILED} and its reason, and a success lands on the frontend
 * with the session cookie set.
 *
 * <p><strong>Success does not land on the destination directly, and that is
 * not a detour.</strong> The session cookie is {@code SameSite=Strict}
 * (Bolum 40.1), and a browser withholds a Strict cookie from a request whose
 * redirect chain began on another site — which this one did, at the provider.
 * Redirecting straight to the app would render the first page signed out and
 * only a manual refresh would fix it: a bug that reads as a flaky login. The
 * landing route exists so the client can ask {@code /auth/session} with a
 * same-origin fetch, which does carry the cookie, and route on from there.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Signing in through a provider")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    private final OAuthProperties properties;
    private final OAuthStateStore states;
    private final OAuthLoginService logins;
    private final SessionCookies cookies;
    private final SignInHandover handover;
    private final Map<OAuthProvider, OAuthClient> clients;

    OAuthController(OAuthProperties properties, OAuthStateStore states,
            OAuthLoginService logins, SessionCookies cookies, SignInHandover handover,
            List<OAuthClient> clients) {
        this.properties = properties;
        this.states = states;
        this.logins = logins;
        this.cookies = cookies;
        this.handover = handover;
        var byProvider = new EnumMap<OAuthProvider, OAuthClient>(OAuthProvider.class);
        clients.forEach(client -> byProvider.put(client.provider(), client));
        this.clients = Map.copyOf(byProvider);
    }

    @Operation(
            summary = "Which providers this deployment can sign people in with",
            description = """
                    A provider with no credentials configured is absent rather \
                    than broken, so the client renders the buttons this list \
                    names and no others.""")
    @GetMapping("/providers")
    public ResponseEntity<List<String>> providers() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Arrays.stream(OAuthProvider.values())
                        .filter(properties::isEnabled)
                        .map(OAuthProvider::wireValue)
                        .toList());
    }

    @Operation(summary = "Begin signing in — redirects to the provider")
    @GetMapping("/oauth/{provider}/start")
    public ResponseEntity<Void> start(
            @PathVariable String provider,
            @RequestParam(name = "next", required = false) String next) {

        Optional<OAuthProvider> known = enabled(provider);
        if (known.isEmpty()) {
            return redirectTo(errorUri(OAuthFailure.PROVIDER_DISABLED));
        }
        OAuthProvider target = known.get();
        String state = states.begin(target, ReturnPath.of(next));
        return redirectTo(clients.get(target).authorizationUri(state));
    }

    @Operation(summary = "Where the provider sends the browser back")
    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String providerError) {

        Optional<OAuthProvider> known = enabled(provider);
        if (known.isEmpty()) {
            return redirectTo(errorUri(OAuthFailure.PROVIDER_DISABLED));
        }
        OAuthProvider target = known.get();

        // Redeemed before anything else is looked at, and exactly once. A
        // callback URL replayed from a history file therefore fails here,
        // rather than reaching the provider with a code somebody else holds.
        var begun = states.redeem(state, target);
        if (begun.isEmpty()) {
            return redirectTo(errorUri(OAuthFailure.STATE_INVALID));
        }

        if (providerError != null && !providerError.isBlank()) {
            // access_denied is the person pressing cancel. Not a fault, and
            // reporting it as one would be the product blaming them for it.
            return redirectTo(errorUri("access_denied".equals(providerError)
                    ? OAuthFailure.DECLINED
                    : OAuthFailure.PROVIDER_UNAVAILABLE));
        }
        if (code == null || code.isBlank()) {
            return redirectTo(errorUri(OAuthFailure.PROVIDER_UNAVAILABLE));
        }

        OAuthExchange exchange = clients.get(target).exchange(code);
        if (exchange instanceof OAuthExchange.Failed failed) {
            return redirectTo(errorUri(failed.reason()));
        }
        var account = ((OAuthExchange.Account) exchange).value();

        SignInOutcome outcome = logins.signIn(account);
        if (outcome instanceof SignInOutcome.Refused refused) {
            return redirectTo(errorUri(refused.reason()));
        }
        var session = ((SignInOutcome.SignedIn) outcome).session();
        // Before the cookie is replaced: after this response the browser no
        // longer holds the anonymous session, and the profile built under it
        // is addressed by a value derived from that id (Adim 3.6).
        ProfileUpgrade upgrade = handover.follow(session);
        // The provider and the outcome, never the address or the subject.
        log.info("Signed in through {}", target.wireValue());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.id()).toString())
                .location(landingUri(begun.get(), upgrade))
                .build();
    }

    /** Unknown and unconfigured answer the same way: there is no such button. */
    private Optional<OAuthProvider> enabled(String provider) {
        return OAuthProvider.fromWire(provider).filter(properties::isEnabled);
    }

    private ResponseEntity<Void> redirectTo(URI target) {
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }

    /**
     * Where a signed-in person lands, and what happened to their anonymous
     * work on the way.
     *
     * <p>{@code profile} is carried in the URL rather than fetched afterwards
     * because it is a one-time fact: a value the client reads once and acts on
     * — most often by saying nothing at all. Kept on the session instead, it
     * would be answered to every {@code /session} call for a fortnight and the
     * client would have to remember whether it had already shown the message.
     */
    private URI landingUri(String returnTo, ProfileUpgrade upgrade) {
        return URI.create(properties.redirectBaseUrl() + properties.landingPath()
                + "?next=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8)
                + "&profile=" + upgrade.wireValue());
    }

    private URI errorUri(OAuthFailure reason) {
        return URI.create(properties.redirectBaseUrl() + properties.errorPath()
                + "?code=OAUTH_FAILED&reason=" + reason.wireValue());
    }

}
