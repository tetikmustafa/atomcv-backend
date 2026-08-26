package com.mustafatetik.atomcv.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.ApplicationRunner;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

/**
 * The local seed is only allowed to exist locally, and is no longer allowed to
 * decide who is acting. Nothing else enforces either — there is no second
 * implementation to fall back to — so both are asserted here.
 */
class LocalDevUserTest {

    @Test
    void theSeedCannotExistOutsideTheLocalProfile() {
        Profile profile = LocalDevUser.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("local");
    }

    /**
     * The half that used to answer "who is acting" belongs to a session now.
     * If this class ever implements {@link CurrentUser} again it becomes a
     * second, profile-scoped answer to a question that must have exactly one —
     * and the one it gives is the same user for everybody.
     */
    @Test
    void itSeedsARowAndNothingMore() {
        assertThat(CurrentUser.class.isAssignableFrom(LocalDevUser.class)).isFalse();
        assertThat(ApplicationRunner.class).isAssignableFrom(LocalDevUser.class);
    }
}
