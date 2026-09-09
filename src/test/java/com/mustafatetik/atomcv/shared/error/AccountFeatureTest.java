package com.mustafatetik.atomcv.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The closed vocabulary of {@code FEATURE_REQUIRES_ACCOUNT.params.feature}
 * (F-030).
 *
 * <p>Small, and it earns its place twice: the wire values are what the
 * frontend's message catalogue is keyed on, and the default locale is what
 * absolute rule 7 exists about.
 */
class AccountFeatureTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreTheLocale() {
        Locale.setDefault(original);
    }

    @Test
    void everyValueIsLowercaseAndUnderscored() {
        assertThat(AccountFeature.values())
                .extracting(AccountFeature::wireValue)
                .containsExactly("atom_controls", "alternatives", "cover_letter", "feedback");
    }

    /**
     * <strong>And this one does not fail today, which is worth saying.</strong>
     * None of the four values carries an {@code I}, so a bare
     * {@code toLowerCase()} would pass every assertion here on a Turkish
     * machine as well — § 51.7 asks for a guard that has been seen to fail, and
     * this is not one yet.
     *
     * <p>It is written anyway because of what the next value costs. A feature
     * named {@code IMPORT_HISTORY} or {@code VARIANTS_AI} emits {@code ı}
     * where the frontend's catalogue has {@code i}, and the sentence renders as
     * a raw key. The failure would then appear here, at the enum, rather than
     * in a screenshot — and adding the case afterwards means somebody has to
     * think of it afterwards.
     */
    @Test
    void aturkishDefaultLocaleDoesNotChangeWhatGoesOnTheWire() {
        Locale.setDefault(Locale.forLanguageTag("tr"));

        assertThat(AccountFeature.values())
                .extracting(AccountFeature::wireValue)
                .allSatisfy(value -> assertThat(value).isEqualTo(value.toLowerCase(Locale.ROOT)))
                .containsExactly("atom_controls", "alternatives", "cover_letter", "feedback");
    }
}
