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
        // Every way in takes a type that carries the check, and neither of the
        // two can be conjured from a bare id. Adim 3.6 added the second: an
        // anonymous scope has no user to compare against, so what stands in
        // for the comparison is AnonymousSessionId — which only the module
        // that can see a session is able to make.
        assertThat(factories).allSatisfy(factory ->
                assertThat(parameterTypes(factory))
                        .as("%s", factory.getName())
                        .containsAnyOf(UserContext.class, AnonymousSessionId.class));
    }

    /**
     * And the second one is a real gate rather than a wrapper: it refuses a
     * blank id, so "no session" cannot become "a scope".
     */
    @Test
    void theAnonymousPathRefusesSomethingThatIsNotASession() {
        assertThatThrownBy(() -> AnonymousSessionId.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProfileRef.ephemeral(null))
                .isInstanceOf(NullPointerException.class);
    }

    /** Two anonymous sessions are two profiles, and one session is always the same one. */
    @Test
    void theAnonymousScopeIsDerivedFromTheSessionAndIsStable() {
        var once = ProfileRef.ephemeral(AnonymousSessionId.of("a-session"));
        var again = ProfileRef.ephemeral(AnonymousSessionId.of("a-session"));
        var other = ProfileRef.ephemeral(AnonymousSessionId.of("another-session"));

        assertThat(once).isEqualTo(again);
        assertThat(once).isNotEqualTo(other);
        assertThat(once.scope()).isEqualTo(ProfileRef.Scope.EPHEMERAL);
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
