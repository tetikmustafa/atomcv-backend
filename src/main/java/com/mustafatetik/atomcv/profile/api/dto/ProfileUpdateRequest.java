package com.mustafatetik.atomcv.profile.api.dto;

import com.mustafatetik.atomcv.profile.domain.Contact;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A replacement for the profile head.
 *
 * <p>{@code PUT} semantics: a field left out is cleared, not kept — with no
 * exceptions, which is why {@code sourceLanguage} and {@code enabledLanguages}
 * are required rather than clearable. Preferences are not part of it — they
 * have their own endpoint, so a client changing a headline cannot reset
 * someone's writing style by omission.
 *
 * <p>Lengths are bounded here rather than in the database. The columns are
 * {@code TEXT} on purpose (a headline in Turkish is longer than in English,
 * and nobody should discover a limit mid-sentence), but an unbounded field is
 * an unbounded row, an unbounded render and an unbounded prompt.
 */
@Schema(name = "ProfileUpdate", description = "Replacement values for the profile head")
public record ProfileUpdateRequest(

        @Size(max = 200)
        @Schema(example = "Backend Engineer")
        String headline,

        @jakarta.validation.Valid ContactRequest contact,

        @Size(max = 4000)
        String selfDescription,

        // Required, unlike the three above it. Those are text a user may
        // genuinely have none of, so leaving one out clears it. This one has a
        // NOT NULL column behind it: there is nothing to clear it to, and
        // quietly keeping the stored value would make the same request a
        // replace for the rest of the head and a merge for this field (F-004).
        // `required` in the schema is derived from this annotation, the way it
        // is for enabledLanguages below. Saying it a second time with
        // `requiredMode` would let the published contract and the constraint
        // that enforces it drift apart, and the schema is what the frontend
        // generates its type from.
        @NotBlank
        @Size(min = 2, max = 16)
        @Schema(description = "The language the profile is authored in", example = "en")
        String sourceLanguage,

        @NotEmpty
        @Schema(description = "At least one; the first is the working language")
        List<@Size(min = 2, max = 16) String> enabledLanguages) {

    /** The contact block, with the constraints the domain record does not carry. */
    @Schema(name = "ContactUpdate")
    public record ContactRequest(
            @Size(max = 120) String name,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String phone,
            @Size(max = 300) String linkedin,
            @Size(max = 300) String github,
            @Size(max = 300) String website,
            @Size(max = 120) String location) {

        public Contact toContact() {
            return new Contact(name, email, phone, linkedin, github, website, location);
        }
    }

    public Contact contactOrEmpty() {
        return contact == null ? Contact.EMPTY : contact.toContact();
    }
}
