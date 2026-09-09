package com.mustafatetik.atomcv.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.shared.error.ApiException;
import com.mustafatetik.atomcv.shared.error.ErrorCode;
import com.mustafatetik.atomcv.shared.error.ResolutionAction;
import com.mustafatetik.atomcv.shared.security.AnonymousSessionId;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * § 35.7's anonymous set, and the sentence it ends with: <em>"Sunucu yine de
 * doğrular — istemci kontrolü sadece UX."</em>
 *
 * <p>Until an anonymous person could edit anything, that sentence had nothing to
 * be true of — the block advertised three limits to somebody who could not reach
 * the endpoints, and {@code ATOM_LIMIT_EXCEEDED} sat in {@code ErrorCode} with
 * nothing throwing it. These are the cases that make it true, and every one of
 * them is about the <em>scope</em> rather than about a second question of who is
 * calling: a write below a profile takes a {@code ProfileRef}, so an endpoint
 * cannot forget to ask and one added later inherits the answer.
 */
class AnonymousLimitsTest {

    private static final ProfileRef ANONYMOUS =
            ProfileRef.ephemeral(AnonymousSessionId.of("a-session"));

    private static final UUID OWNER = UUID.randomUUID();

    private static final ProfileRef ACCOUNT =
            ProfileRef.persistent(UserContext.of(OWNER), UUID.randomUUID(), OWNER);

    // -- the capabilities an account has and a session does not ------------

    @Test
    void ananonymousCallerIsRefusedAFeatureAnAccountHas() {
        assertThatThrownBy(() -> AnonymousLimits.requireAccountFor(ANONYMOUS, "alternatives"))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> {
                    var error = ((ApiException) thrown).error();
                    assertThat(error.code()).isEqualTo(ErrorCode.FEATURE_REQUIRES_ACCOUNT);
                    assertThat(error.httpStatus()).isEqualTo(403);
                    // Which button needs an account, so the screen can say so
                    // rather than raising a generic wall.
                    assertThat(error.params()).containsEntry("feature", "alternatives");
                    assertThat(error.resolutions()).extracting("action")
                            .containsExactly(ResolutionAction.SIGN_UP);
                });
    }

    @Test
    void anaccountIsRefusedNothing() {
        assertThatCode(() -> AnonymousLimits.requireAccountFor(ACCOUNT, "alternatives"))
                .doesNotThrowAnyException();
        assertThatCode(() -> AnonymousLimits.requireRoomForAnotherAtom(ACCOUNT, 10_000))
                .doesNotThrowAnyException();
    }

    // -- the ceiling -------------------------------------------------------

    @Test
    void theSixtiethAtomIsAllowedAndTheSixtyFirstIsNot() {
        assertThatCode(() -> AnonymousLimits.requireRoomForAnotherAtom(ANONYMOUS, 59))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> AnonymousLimits.requireRoomForAnotherAtom(ANONYMOUS, 60))
                .isInstanceOf(ApiException.class)
                .satisfies(thrown -> {
                    var error = ((ApiException) thrown).error();
                    assertThat(error.code()).isEqualTo(ErrorCode.ATOM_LIMIT_EXCEEDED);
                    // Both numbers, because "you are at the limit" without the
                    // limit is a message the screen cannot write a sentence from.
                    assertThat(error.params())
                            .containsEntry("limit", 60)
                            .containsEntry("current", 60);
                });
    }

    // -- which fields are controls ------------------------------------------

    /**
     * The four the schema itself labels {@code -- kullanıcı kontrolleri}
     * (§ 04-data-model): importance, active, always_include, verbatim.
     */
    @Test
    void thefourControlsAreTheFourTheSchemaNames() {
        assertThat(AnonymousLimits.touchesAtomControls(patchWith(0.9f, null, null, null))).isTrue();
        assertThat(AnonymousLimits.touchesAtomControls(patchWith(null, true, null, null))).isTrue();
        assertThat(AnonymousLimits.touchesAtomControls(patchWith(null, null, true, null))).isTrue();
        assertThat(AnonymousLimits.touchesAtomControls(patchWith(null, null, null, true))).isTrue();
    }

    /**
     * And the scoring inputs beside them are not controls. Refusing these would
     * stop an anonymous person correcting their own sentence, which is the
     * editing the whole slice exists to allow.
     */
    @Test
    void whatTheCvSaysIsNotAControl() {
        var patch = new AtomPatch(AtomKind.BULLET, null, null, null, null, true,
                List.of("java"), List.of("300K"), List.of("Initech"));

        assertThat(AnonymousLimits.touchesAtomControls(patch)).isFalse();
    }

    /**
     * A create is asked the same question against the defaults, because the
     * controller has already turned a missing field into one by the time a
     * draft exists.
     */
    @Test
    void acreateAtTheDefaultsTouchesNothing() {
        assertThat(AnonymousLimits.touchesAtomControls(draftWith(0.5f, false, false))).isFalse();
        assertThat(AnonymousLimits.touchesAtomControls(draftWith(0.9f, false, false))).isTrue();
        assertThat(AnonymousLimits.touchesAtomControls(draftWith(0.5f, true, false))).isTrue();
        assertThat(AnonymousLimits.touchesAtomControls(draftWith(0.5f, false, true))).isTrue();
    }

    private static AtomPatch patchWith(
            Float importance, Boolean active, Boolean alwaysInclude, Boolean verbatim) {

        return new AtomPatch(null, importance, active, alwaysInclude, verbatim, null,
                null, null, null);
    }

    private static AtomDraft draftWith(
            float importance, boolean alwaysInclude, boolean verbatim) {

        return new AtomDraft(UUID.randomUUID(), null, AtomKind.BULLET,
                RichContent.plain("Moved 300K rows"), "en",
                importance, alwaysInclude, verbatim, List.of(), List.of(), List.of());
    }
}
