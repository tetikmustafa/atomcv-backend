package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * What the store answers when Redis does not, which is a different question
 * from what it answers when there is nothing there.
 *
 * <p>The rest of this class is exercised against a real Redis in {@code
 * SessionStoreIT}. Only the failure paths are here, because a container that
 * works is the wrong place to find out what happens when one does not.
 */
class SessionStoreTest {

    private static final UUID SOMEONE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    private final SessionStore store = new SessionStore(
            redis,
            new ObjectMapper(),
            new SessionProperties(null, null, null, null, null, true),
            Clock.fixed(Instant.parse("2026-09-02T09:00:00Z"), ZoneOffset.UTC));

    /**
     * <strong>Bolum 40.1's "aninda" rests on this one operation.</strong> It
     * used to warn and answer zero, which reads to the caller exactly like a
     * user who was signed in nowhere — and {@link AccountDeletionService}
     * deletes the account on that answer. A cookie that outlives its account
     * is the state the section says cannot happen.
     */
    @Test
    void aRevocationRedisCouldNotCarryOutIsRaisedAndNotCountedAsNone() {
        when(redis.opsForSet()).thenThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> store.revokeAllFor(SOMEONE))
                .isInstanceOf(SessionStoreUnavailableException.class);
    }

    /** And a user with nothing to revoke is still nothing to revoke. */
    @Test
    void aUserWithNoLiveSessionIsZeroRatherThanAFailure() {
        @SuppressWarnings("unchecked")
        SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(sets);
        when(sets.members(anyString())).thenReturn(Set.of());

        assertThat(store.revokeAllFor(SOMEONE)).isZero();
    }
}
