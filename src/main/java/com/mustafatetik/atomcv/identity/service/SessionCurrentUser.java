package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The acting user, read from the {@code sid} cookie (Bolum 40.1).
 *
 * <p><strong>Resolved on demand, not in a filter.</strong> A filter would put a
 * Redis round trip in front of every request, including the ones that never
 * ask who is calling — the health probe, the warm-up of Bolum 52.5, the
 * schema. Endpoints that need a user ask for one, and this answers.
 *
 * <p>The answer is memoised on the request, so an endpoint that calls
 * {@link #find()} and then {@link #require()} — or a controller and the service
 * below it — costs one lookup rather than one each. The attribute is also what
 * makes the sliding TTL of EK D.6.6 refresh once per request instead of once
 * per caller.
 */
@Component
public class SessionCurrentUser implements CurrentUser {

    private static final String ATTRIBUTE = SessionCurrentUser.class.getName() + ".session";

    private final SessionStore sessions;
    private final SessionCookies cookies;
    private final ObjectProvider<LocalDevSessions> localDev;

    SessionCurrentUser(SessionStore sessions, SessionCookies cookies,
            ObjectProvider<LocalDevSessions> localDev) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.localDev = localDev;
    }

    @Override
    public UserContext require() {
        return find().orElseThrow(() -> ApiException.of(
                ErrorCode.AUTHENTICATION_REQUIRED, Resolution.of(ResolutionAction.SIGN_UP)));
    }

    /**
     * Who is acting, when somebody is.
     *
     * <p>An anonymous session is a session and not a user (Adim 3.6), so it
     * answers empty here — and {@link #require()} then refuses with
     * {@code AUTHENTICATION_REQUIRED} and a {@code sign_up} way out, which is
     * exactly the right answer for a user-scoped endpoint reached without an
     * account.
     */
    @Override
    public Optional<UserContext> find() {
        return session().filter(session -> !session.isAnonymous()).map(Session::asUserContext);
    }

    /**
     * The anonymous session behind this request, when nobody has signed in on
     * it (Adim 3.6).
     *
     * <p>This is the one place an {@link AnonymousSessionId} is made, and that
     * is the point of the type: it is produced from a session already
     * established to have no user, so nothing downstream can mint an anonymous
     * scope out of a String it was handed.
     */
    @Override
    public Optional<AnonymousSessionId> anonymousSession() {
        return session()
                .filter(Session::isAnonymous)
                .map(session -> AnonymousSessionId.of(session.id()));
    }

    /** The session itself, for the endpoint whose subject is the session. */
    public Optional<Session> session() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            // Outside a request: the queue worker, the anomaly detector, a
            // scheduled task. None of them act as a user, and each one that
            // needs a subject carries it explicitly.
            return Optional.empty();
        }
        Object cached = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (cached instanceof Optional<?> memoised) {
            @SuppressWarnings("unchecked")
            Optional<Session> typed = (Optional<Session>) memoised;
            return typed;
        }
        Optional<Session> resolved = resolve(servlet.getRequest());
        attributes.setAttribute(ATTRIBUTE, resolved, RequestAttributes.SCOPE_REQUEST);
        return resolved;
    }

    private Optional<Session> resolve(HttpServletRequest request) {
        Optional<String> sid = cookies.read(request);
        if (sid.isPresent()) {
            // A cookie that no longer resolves is not a local-dev request: the
            // browser held a session that has been revoked or has expired, and
            // answering as the dev user would hide exactly that.
            return sessions.find(sid.get());
        }
        return Optional.ofNullable(localDev.getIfAvailable()).map(LocalDevSessions::session);
    }
}
