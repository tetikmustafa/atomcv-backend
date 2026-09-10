package com.mustafatetik.atomcv.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A measured header outlives nothing but its own text (Bolum 26.4).
 *
 * <p>The cost is the height of a block of words. Change the words and the
 * height is a claim about a header that is no longer printed — and a stale one
 * is worse than none, because none falls back and is measured again while a
 * stale one is charged with confidence.
 */
class ProfileHeaderCostTest {

    private static final String KEY = Profile.headerKey("classic:v5", "en");

    @Test
    void whatWasMeasuredIsWhatComesBack() {
        var profile = new Profile(UUID.randomUUID());

        profile.recordHeaderCost(KEY, 67.36);

        assertThat(profile.getHeaderCosts()).containsEntry(KEY, 67.36);
    }

    @Test
    void anewHeadlineForgetsWhatTheHeaderMeasured() {
        var profile = new Profile(UUID.randomUUID());
        profile.setHeadline("Backend Engineer");
        profile.recordHeaderCost(KEY, 67.36);

        profile.setHeadline("Backend Engineer, distributed systems and payments");

        assertThat(profile.getHeaderCosts()).isEmpty();
    }

    @Test
    void anewContactLineForgetsItToo() {
        var profile = new Profile(UUID.randomUUID());
        profile.setContact(new Contact("Ada Lovelace", "ada@example.com",
                null, null, null, null, null));
        profile.recordHeaderCost(KEY, 67.36);

        profile.setContact(new Contact("Ada Lovelace", "ada@example.com",
                "+90 555 000 00 00", "in/ada", "ada", "ada.dev", "Istanbul"));

        assertThat(profile.getHeaderCosts())
                .as("six contact fields wrap where one did not")
                .isEmpty();
    }

    /**
     * And setting the same value again keeps it. A save that rewrites every
     * field would otherwise throw away a measurement nothing had changed, and
     * the next generation would pay for a compilation to learn the same number.
     */
    @Test
    void writingTheSameHeadlineBackKeepsIt() {
        var profile = new Profile(UUID.randomUUID());
        profile.setHeadline("Backend Engineer");
        profile.recordHeaderCost(KEY, 67.36);

        profile.setHeadline("Backend Engineer");

        assertThat(profile.getHeaderCosts()).containsEntry(KEY, 67.36);
    }

    /** Two languages print different labels, so they are two measurements. */
    @Test
    void alanguageIsPartOfTheKey() {
        assertThat(Profile.headerKey("classic:v5", "tr"))
                .isNotEqualTo(Profile.headerKey("classic:v5", "en"));
    }
}
