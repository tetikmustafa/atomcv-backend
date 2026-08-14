package com.mustafatetik.atomcv.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

/**
 * The stand-in user is only allowed to exist locally. Nothing else enforces
 * that — there is no second implementation to fall back to — so the annotation
 * itself is asserted.
 */
class LocalDevCurrentUserTest {

    @Test
    void theStandInUserCannotExistOutsideTheLocalProfile() {
        Profile profile = LocalDevCurrentUser.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("local");
    }

    @Test
    void itIsTheOnlyImplementationForNowAndSaysWhoIsActing() {
        assertThat(CurrentUser.class).isAssignableFrom(LocalDevCurrentUser.class);
        assertThat(new LocalDevCurrentUser(null).require())
                .isEqualTo(new UserContext(LocalDevCurrentUser.DEV_USER_ID, UserRole.USER));
    }
}
