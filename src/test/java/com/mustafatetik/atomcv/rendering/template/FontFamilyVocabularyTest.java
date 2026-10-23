package com.mustafatetik.atomcv.rendering.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.api.dto.PreferencesUpdateRequest;
import com.mustafatetik.atomcv.profile.domain.Preferences;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The font families as two modules spell them (F-041).
 *
 * <p>The customization endpoints carry the {@link FontFamily} enum; the
 * profile's own appearance cannot import it (rendering depends on profile, and
 * the cycle is refused), so it writes the four values out. Written-out lists
 * drift: until F-041 the profile accepted three and the enum held four, so
 * {@code book} could be saved under a name and refused as a default, and no
 * typecheck on either side could see it.
 */
class FontFamilyVocabularyTest {

    private static final List<String> WIRE_VALUES = Arrays.stream(FontFamily.values())
            .map(FontFamily::wireValue)
            .toList();

    @Test
    void theProfileWriteSchemaPublishesEveryFamily() throws NoSuchFieldException {
        Schema schema = PreferencesUpdateRequest.AppearanceRequest.class
                .getDeclaredField("fontFamily").getAnnotation(Schema.class);

        assertThat(schema.allowableValues()).containsExactlyElementsOf(WIRE_VALUES);
    }

    @Test
    void theProfileReadSchemaPublishesEveryFamily() throws NoSuchFieldException {
        Schema schema = Preferences.Appearance.class
                .getDeclaredField("fontFamily").getAnnotation(Schema.class);

        assertThat(schema.allowableValues()).containsExactlyElementsOf(WIRE_VALUES);
    }

    /** The published list and the enforced one are separate strings too. */
    @Test
    void theProfileAcceptsEveryFamilyInEitherCase() throws NoSuchFieldException {
        String regex = PreferencesUpdateRequest.AppearanceRequest.class
                .getDeclaredField("fontFamily").getAnnotation(Pattern.class).regexp();

        for (FontFamily family : FontFamily.values()) {
            assertThat(family.wireValue()).matches(regex);
            assertThat(family.name()).matches(regex);
        }
        assertThat("comic").doesNotMatch(regex);
    }

    /** Rows written before F-041 hold {@code SANS}; the wire says {@code sans}. */
    @Test
    void aStoredFamilyReadsBackInTheEnumsSpelling() {
        var stored = new Preferences.Appearance(null, null, null, "SANS", null);

        assertThat(stored.fontFamily()).isEqualTo(FontFamily.SANS.wireValue());
        assertThat(FontFamily.fromWireValue(stored.fontFamily().toUpperCase(Locale.ROOT)))
                .isEqualTo(FontFamily.SANS);
    }
}
