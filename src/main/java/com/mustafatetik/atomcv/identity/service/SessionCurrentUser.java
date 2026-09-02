package com.mustafatetik.atomcv.identity.service;

import com.mustafatetik.atomcv.identity.domain.Session;
import com.mustafatetik.atomcv.identity.repository.SignInAccounts;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.Resolution;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.CurrentUser;
import com.mustafatetik.atomcv.shared.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SessionCurrentUser.class);

    private final SessionStore sessions;
    private final SessionCookies cookies;
    private final SignInAccounts accounts;
    private final ObjectProvider<LocalDevSessions> localDev;

    SessionCurrentUser(SessionStore sessions, SessionCookies cookies, SignInAccounts accounts,
            ObjectProvider<LocalDevSessions> localDev) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.accounts = accounts;
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
            return sessions.find(sid.get()).filter(this::pointsAtALiveAccount);
        }
        return Optional.ofNullable(localDev.getIfAvailable())
                .map(LocalDevSessions::session)
                .filter(this::pointsAtALiveAccount);
    }

    /**
     * <strong>A session can outlive the account it points at, and one that has
     * is not a session.</strong> Bolum 57.4's deletion revokes every session
     * of the account first and for this reason, but "first" only orders the
     * two steps — it does not make the second one impossible to observe. A
     * request already in flight when the row went, or a revocation Redis could
     * not carry out, leaves a cookie behind that resolves to a user who is not
     * there.
     *
     * <p>What that cookie used to get was a 500, and from one endpoint rather
     * than from the endpoint that mattered: every read of the profile, because
     * {@code ProfileResolver} creates the row on first use and the insert
     * broke {@code profiles.user_id}'s foreign key. Reads that create nothing
     * — the generation list, the usage counters — answered 200 as if the
     * account were fine. The right answer is the one the client already knows
     * how to act on: {@code 401 AUTHENTICATION_REQUIRED}, from here, for every
     * endpoint at once rather than for the one that happened to write.
     *
     * <p>One lookup by primary key, and the memoised attribute above means it
     * is one per request rather than one per caller. Anonymous sessions have
     * no account to check.
     */
    private boolean pointsAtALiveAccount(Session session) {
        if (session.isAnonymous() || accounts.byId(session.userId()).isPresent()) {
            return true;
        }
        // Bolum 40.1: a stored session pointing at a deleted account is the
        // state the section says must not exist, so seeing one is also the
        // moment to end it. The id is safe to log — it references a row that
        // is gone — and it is not user content.
        log.info("Session outlived the account it points at ({}); revoking it and answering "
                + "as unauthenticated", session.userId());
        sessions.revoke(session.id());
        return false;
    }
}
