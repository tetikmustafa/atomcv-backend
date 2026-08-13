package com.mustafatetik.atomcv.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileRefTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID PROFILE = UUID.randomUUID();

    @Test
    void resolvingOwnProfileYieldsAReference() {
        var ref = ProfileRef.persistent(UserContext.of(OWNER), PROFILE, OWNER);

        assertThat(ref.id()).isEqualTo(PROFILE);
        assertThat(ref.scope()).isEqualTo(ProfileRef.Scope.PERSISTENT);
    }

    @Test
    void resolvingSomeoneElsesProfileIsRefused() {
        var intruder = UserContext.of(UUID.randomUUID());

        assertThatThrownBy(() -> ProfileRef.persistent(intruder, PROFILE, OWNER))
                .isInstanceOf(CrossTenantAccessException.class);
    }

    @Test
    void anAdminIsNoExceptionToTheRule() {
        var admin = new UserContext(UUID.randomUUID(), UserRole.ADMIN);

        // Bolum 41.4: the role decides what an endpoint does, never whose rows
        // it may touch. Support access goes through a granted support_grant.
        assertThatThrownBy(() -> ProfileRef.persistent(admin, PROFILE, OWNER))
                .isInstanceOf(CrossTenantAccessException.class);
    }

    /**
     * The guarantee is that a reference cannot be produced without comparing
     * the acting user against the profile's owner. A convenience factory added
     * later would quietly remove it, so the shape of the type is asserted
     * rather than assumed.
     */
    @Test
    void thereIsNoUncheckedWayToBuildOne() {
        assertThat(ProfileRef.class.getConstructors()).isEmpty();

        var factories = Arrays.stream(ProfileRef.class.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> method.getReturnType() == ProfileRef.class)
                .toList();

        assertThat(factories).isNotEmpty();
        assertThat(factories)
                .allSatisfy(factory -> assertThat(parameterTypes(factory)).contains(UserContext.class));
    }

    @Test
    void equalityFollowsTheIdentifier() {
        var first = ProfileRef.persistent(UserContext.of(OWNER), PROFILE, OWNER);
        var second = ProfileRef.persistent(UserContext.of(OWNER), PROFILE, OWNER);

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(
                ProfileRef.persistent(UserContext.of(OWNER), UUID.randomUUID(), OWNER));
    }

    private static Class<?>[] parameterTypes(Method method) {
        return method.getParameterTypes();
    }
}
