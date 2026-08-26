package com.mustafatetik.atomcv.identity.oauth;

import java.util.Locale;

/**
 * Why a sign-in did not happen — the closed vocabulary behind
 * {@code OAUTH_FAILED}'s {@code reason} parameter.
 *
 * <p>One code with a reason rather than six codes, which is the shape
 * {@code F-016} asked for and {@code UNPARSEABLE_JOB_DESCRIPTION} already
 * uses: the frontend resolves one ICU key with a {@code select}, and a reason
 * added later falls into its {@code other} branch instead of rendering a raw
 * key to a user.
 */
public enum OAuthFailure {

    /** Forged, expired, replayed, or redeemed at the wrong provider. */
    STATE_INVALID,

    /** The person pressed "cancel" on the consent screen. Not an error. */
    DECLINED,

    /** This deployment has no credentials for that provider. */
    PROVIDER_DISABLED,

    /** The token or profile call failed, timed out, or answered badly. */
    PROVIDER_UNAVAILABLE,

    /** The provider returned no address we can use as an account. */
    EMAIL_MISSING,

    /**
     * The provider knows the address but does not vouch for it. Linking on an
     * unverified address is an account takeover: anyone can add someone else's
     * email to their own provider account.
     */
    EMAIL_UNVERIFIED,

    /**
     * The address belongs to an account that was deleted. Said plainly rather
     * than generically: the person just proved they own the address, so there
     * is nothing left to protect by being vague, and "something went wrong"
     * would send them round the same loop.
     */
    ACCOUNT_DISABLED;

    /** Absolute rule 7: the wire value must not depend on the default locale. */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
