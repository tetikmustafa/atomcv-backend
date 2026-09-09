package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.shared.error.UserFacingError;
import com.mustafatetik.atomcv.shared.security.ProfileRef;

/**
 * § 35.7's anonymous set, enforced (Bolum 9).
 *
 * <p><strong>"Sunucu yine de doğrular — istemci kontrolü sadece UX."</strong>
 * That sentence is § 35.7's own, and until an anonymous person could edit
 * anything it had nothing to be true of: the block advertised
 * {@code canEditAtomControls: false}, {@code canAddAlternatives: false} and
 * {@code maxAtoms: 60} to somebody who could not reach the endpoints at all.
 * {@code ATOM_LIMIT_EXCEEDED} has been in {@code ErrorCode} the whole time with
 * nothing throwing it.
 *
 * <p><strong>The scope is the check.</strong> Every write below a profile takes
 * a {@link ProfileRef}, and an {@code EPHEMERAL} one means the person on the
 * other end has no account. So the limit is applied where the write happens
 * rather than at four endpoints that each have to remember — and a fifth
 * endpoint added later inherits it by taking the same argument.
 *
 * <p><strong>Which fields are "atom controls" is not a guess.</strong>
 * {@code spec/04-data-model.md} labels them in the schema itself, under
 * {@code -- kullanıcı kontrolleri}: {@code importance}, {@code active},
 * {@code always_include}, {@code verbatim}. Not the scoring inputs beside them
 * — skills, metrics and proper nouns are what the CV <em>says</em>, and refusing
 * those would stop an anonymous person correcting their own sentence.
 */
final class AnonymousLimits {

    /** § 35.7's {@code maxAtoms}, and the account body has no such field. */
    static final int MAX_ATOMS = 60;

    private AnonymousLimits() {
    }

    private static boolean isAnonymous(ProfileRef profile) {
        return profile.scope() == ProfileRef.Scope.EPHEMERAL;
    }

    /**
     * Refuses a capability an account has and a session does not.
     *
     * @param feature what goes in the error's {@code feature} parameter, so the
     *                screen can say which button needs an account rather than
     *                offering a generic sign-up wall
     */
    static void requireAccountFor(ProfileRef profile, String feature) {
        if (isAnonymous(profile)) {
            throw new ApiException(UserFacingError.with(ErrorCode.FEATURE_REQUIRES_ACCOUNT)
                    .param("feature", feature)
                    .resolution(ResolutionAction.SIGN_UP)
                    .build());
        }
    }

    /**
     * The ceiling on an anonymous profile, checked before the atom is written.
     *
     * <p>An account has none: the limit exists because an anonymous profile is
     * a trial of the product and because everything in it is being carried by a
     * session that will end, not because sixty is a meaningful number of
     * sentences.
     *
     * @param current how many atoms the profile already has
     */
    static void requireRoomForAnotherAtom(ProfileRef profile, int current) {
        if (isAnonymous(profile) && current >= MAX_ATOMS) {
            throw new ApiException(UserFacingError.with(ErrorCode.ATOM_LIMIT_EXCEEDED)
                    .param("limit", MAX_ATOMS)
                    .param("current", current)
                    .resolution(ResolutionAction.SIGN_UP)
                    .build());
        }
    }

    /**
     * Whether this patch touches one of the four controls.
     *
     * <p>Read off the patch rather than applied per field: a request that sets
     * three fields and one control is refused whole, because a partial write
     * that silently dropped the control would leave the screen showing a value
     * the server does not hold.
     */
    static boolean touchesAtomControls(AtomPatch patch) {
        return patch.importance() != null
                || patch.active() != null
                || patch.alwaysInclude() != null
                || patch.verbatim() != null;
    }

    /**
     * The same question of a create, answered against the defaults.
     *
     * <p>A draft cannot say what the request left out: the controller maps a
     * missing {@code importance} to {@code 0.5} and a missing lock to
     * {@code false} before this sees it. So "did they ask for a control" is
     * "does a control differ from its default", which is the same question one
     * layer down and needs no second shape of the request to answer.
     *
     * <p>{@code active} is absent because a new atom cannot be created switched
     * off — there is no field for it on the way in.
     */
    static boolean touchesAtomControls(AtomDraft draft) {
        return draft.importance() != 0.5f || draft.alwaysInclude() || draft.verbatim();
    }
}
