package com.mustafatetik.atomcv.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The IDOR defense, exercised on both bases: a row that belongs to someone
 * else must read as absent and must never be written.
 */
class ScopedRepositoryTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID PROFILE = UUID.randomUUID();
    private static final UUID OTHER_PROFILE = UUID.randomUUID();

    private static final UserContext USER = UserContext.of(OWNER);

    // ─── test doubles ───

    private record ProfileRow(UUID id, UUID profileId) implements ProfileOwned {
        @Override
        public UUID getProfileId() {
            return profileId;
        }
    }

    private record UserRow(UUID id, UUID ownerId) implements UserOwned {
        @Override
        public UUID getOwnerId() {
            return ownerId;
        }
    }

    @Nested
    class ProfileScoped {

        @SuppressWarnings("unchecked")
        private final JpaRepository<ProfileRow, UUID> delegate = mock(JpaRepository.class);

        private final ProfileScopedRepository<ProfileRow> repository =
                new ProfileScopedRepository<>() {
                    @Override
                    protected JpaRepository<ProfileRow, UUID> delegate() {
                        return delegate;
                    }
                };

        private final ProfileRef profile = ProfileRef.persistent(USER, PROFILE, OWNER);

        @Test
        void ownRowIsReturned() {
            var row = new ProfileRow(UUID.randomUUID(), PROFILE);
            when(delegate.findById(row.id())).thenReturn(Optional.of(row));

            assertThat(repository.findById(profile, row.id())).contains(row);
            assertThat(repository.exists(profile, row.id())).isTrue();
        }

        @Test
        void aRowFromAnotherProfileReadsAsAbsent() {
            var foreign = new ProfileRow(UUID.randomUUID(), OTHER_PROFILE);
            when(delegate.findById(foreign.id())).thenReturn(Optional.of(foreign));

            assertThat(repository.findById(profile, foreign.id())).isEmpty();
            assertThat(repository.exists(profile, foreign.id())).isFalse();
        }

        @Test
        void writingIntoAnotherProfileIsRefusedBeforeItReachesTheDelegate() {
            var foreign = new ProfileRow(UUID.randomUUID(), OTHER_PROFILE);

            assertThatThrownBy(() -> repository.save(profile, foreign))
                    .isInstanceOf(CrossTenantAccessException.class);
            assertThatThrownBy(() -> repository.delete(profile, foreign))
                    .isInstanceOf(CrossTenantAccessException.class);

            verify(delegate, never()).save(any());
            verify(delegate, never()).delete(any());
        }

        @Test
        void ownRowIsSavedAndDeleted() {
            var row = new ProfileRow(UUID.randomUUID(), PROFILE);
            when(delegate.save(row)).thenReturn(row);

            assertThat(repository.save(profile, row)).isEqualTo(row);
            repository.delete(profile, row);

            verify(delegate).save(row);
            verify(delegate).delete(row);
        }

        @Test
        void aMissingScopeIsAProgrammingError() {
            assertThatThrownBy(() -> repository.findById(null, UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class UserScoped {

        @SuppressWarnings("unchecked")
        private final JpaRepository<UserRow, UUID> delegate = mock(JpaRepository.class);

        private final UserScopedRepository<UserRow> repository =
                new UserScopedRepository<>() {
                    @Override
                    protected JpaRepository<UserRow, UUID> delegate() {
                        return delegate;
                    }
                };

        @Test
        void ownRowIsReturned() {
            var row = new UserRow(UUID.randomUUID(), OWNER);
            when(delegate.findById(row.id())).thenReturn(Optional.of(row));

            assertThat(repository.findById(USER, row.id())).contains(row);
        }

        @Test
        void anotherUsersRowReadsAsAbsent() {
            var foreign = new UserRow(UUID.randomUUID(), UUID.randomUUID());
            when(delegate.findById(foreign.id())).thenReturn(Optional.of(foreign));

            assertThat(repository.findById(USER, foreign.id())).isEmpty();
        }

        @Test
        void writingAnotherUsersRowIsRefusedBeforeItReachesTheDelegate() {
            var foreign = new UserRow(UUID.randomUUID(), UUID.randomUUID());

            assertThatThrownBy(() -> repository.save(USER, foreign))
                    .isInstanceOf(CrossTenantAccessException.class);

            verify(delegate, never()).save(any());
        }

        @Test
        void anAdminHasNoExtraReach() {
            var admin = new UserContext(UUID.randomUUID(), UserRole.ADMIN);
            var row = new UserRow(UUID.randomUUID(), OWNER);
            when(delegate.findById(row.id())).thenReturn(Optional.of(row));

            assertThat(repository.findById(admin, row.id())).isEmpty();
        }
    }
}
