package com.mustafatetik.atomcv.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * {@code profiles.contact} and {@code profiles.preferences} are stored as JSONB
 * and read back by Jackson, so a shape that writes but cannot be read is a row
 * that cannot be loaded.
 *
 * <p>This is a fast unit test on purpose. The same defect first showed up as
 * five failing integration tests: a derived {@code isEmpty()} getter was
 * written as an {@code "empty"} property that the record could not accept back.
 */
class ProfileJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void contactRoundTrips() throws Exception {
        var contact = new Contact("Mustafa Tetik", "mustafa@example.com", "+90 555 000 00 00",
                "https://linkedin.com/in/example", "https://github.com/example",
                "https://example.com", "İstanbul, Türkiye");

        var json = mapper.writeValueAsString(contact);

        assertThat(json).doesNotContain("empty");
        assertThat(mapper.readValue(json, Contact.class)).isEqualTo(contact);
    }

    @Test
    void anEmptyContactRoundTripsAndWritesNothing() throws Exception {
        var json = mapper.writeValueAsString(Contact.EMPTY);

        assertThat(json).isEqualTo("{}");
        assertThat(mapper.readValue(json, Contact.class)).isEqualTo(Contact.EMPTY);
    }

    @Test
    void preferencesRoundTripWithTheToneLowercase() throws Exception {
        var preferences = new Preferences(
                new Preferences.WritingStyle(true, Tone.TECHNICAL, false, "Lead with leadership"),
                new Preferences.Defaults(2, "modern", "tr", "en"));

        var json = mapper.writeValueAsString(preferences);

        assertThat(json).contains("\"tone\":\"technical\"");
        assertThat(mapper.readValue(json, Preferences.class)).isEqualTo(preferences);
    }

    @Test
    void theDefaultsSurviveAnEmptyStoredObject() throws Exception {
        // What the column's own DEFAULT '{}' produces on an untouched profile.
        var preferences = mapper.readValue("{}", Preferences.class);

        assertThat(preferences).isEqualTo(Preferences.DEFAULTS);
        assertThat(preferences.defaults().maxPages()).isEqualTo(1);
        assertThat(preferences.writingStyle().tone()).isEqualTo(Tone.FORMAL);
    }

    @Test
    void theDocumentedShapeIsAccepted() throws Exception {
        // Bolum 14.3, verbatim.
        var preferences = mapper.readValue("""
                {
                  "writingStyle": {
                    "emphasizeMetrics": true,
                    "tone": "formal",
                    "conciseSentences": false,
                    "customInstructions": "Liderlik deneyimlerimi öne çıkar"
                  },
                  "defaults": {
                    "maxPages": 1,
                    "templateId": "classic",
                    "cvLanguage": "auto",
                    "coverLetterLanguage": "auto"
                  }
                }
                """, Preferences.class);

        assertThat(preferences.writingStyle().emphasizeMetrics()).isTrue();
        assertThat(preferences.writingStyle().tone()).isEqualTo(Tone.FORMAL);
        assertThat(preferences.defaults().templateId()).isEqualTo("classic");
    }

    @Test
    void theDocumentedContactShapeIsAccepted() throws Exception {
        // Bolum 14.2, verbatim.
        var contact = mapper.readValue("""
                {
                  "name": "Mustafa Tetik",
                  "email": "...",
                  "phone": "+90 ...",
                  "linkedin": "https://linkedin.com/in/...",
                  "github": "https://github.com/...",
                  "website": "https://mustafatetik.com",
                  "location": "İstanbul, Türkiye"
                }
                """, Contact.class);

        assertThat(contact.name()).isEqualTo("Mustafa Tetik");
        assertThat(contact.location()).isEqualTo("İstanbul, Türkiye");
        assertThat(contact.isEmpty()).isFalse();
    }
}
