package com.mustafatetik.atomcv.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The identifier is the whole credential, so its only interesting properties
 * are how much randomness it carries and that it survives a cookie unchanged.
 */
class SessionIdsTest {

    @Test
    void itCarries256BitsOfRandomness() {
        // 32 bytes in unpadded base64url is 43 characters.
        assertThat(SessionIds.next()).hasSize(43);
    }

    @Test
    void itIsSafeInACookieValueWithoutEscaping() {
        assertThat(SessionIds.next()).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void tenThousandDrawsCollideNever() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(SessionIds.next());
        }
        assertThat(seen).hasSize(10_000);
    }
}
