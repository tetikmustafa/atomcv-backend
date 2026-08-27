package com.mustafatetik.atomcv.identity.challenge;

/**
 * Whether the thing on the other end is a person (Bolum 44.4).
 *
 * <p>Named for what it does and not for who provides it, the same reasoning
 * that made the observability variables {@code OTLP_*} rather than
 * {@code AXIOM_*}: leaving Cloudflare would not turn this name into a lie, and
 * the wire code the frontend renders is {@code CHALLENGE_FAILED} for the same
 * reason.
 *
 * <p>An interface with two implementations because a deployment without a
 * secret has to be a readable branch rather than an absent bean —
 * {@code EmailSenderConfig} settles the same question the same way.
 */
public interface Challenge {

    /**
     * @param token what the widget produced, as the client sent it. May be
     *              {@code null} or blank; a challenge that is switched on
     *              treats that as a failure rather than as an absence, because
     *              a client that omits it is exactly the client this exists to
     *              stop
     * @return whether the request may proceed
     */
    boolean passed(String token);
}
