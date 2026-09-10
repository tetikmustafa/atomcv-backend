package com.mustafatetik.atomcv.rendering.model;

import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The block at the top of the page, assembled from a profile (Bolum 22.2).
 *
 * <p>Here rather than in the render phase because two things build it now and
 * they must build the same one. The renderer prints it; the measurement
 * document sets it in order to find out how tall it is, and a header measured
 * from slightly different text is a measurement of a different header — the
 * same trap as measuring a bullet at a width no bullet is ever set at
 * (Bolum 22.4).
 *
 * <p>The labels are translated, so the same profile has a different header in
 * each language and, being text, a different height.
 */
public final class ProfileHeaders {

    private ProfileHeaders() {
    }

    public static RenderRequest.ProfileHeader of(Profile profile, Locale language) {
        Contact contact = profile.getContact() == null ? Contact.EMPTY : profile.getContact();
        List<RenderRequest.ContactLine> lines = new ArrayList<>();
        addContact(lines, ContactKind.EMAIL, contact.email(), language);
        addContact(lines, ContactKind.PHONE, contact.phone(), language);
        addContact(lines, ContactKind.LOCATION, contact.location(), language);
        addContact(lines, ContactKind.LINKEDIN, contact.linkedin(), language);
        addContact(lines, ContactKind.GITHUB, contact.github(), language);
        addContact(lines, ContactKind.WEBSITE, contact.website(), language);
        return new RenderRequest.ProfileHeader(
                orEmpty(contact.name()), profile.getHeadline(), lines);
    }

    private static void addContact(List<RenderRequest.ContactLine> lines,
            ContactKind kind, String value, Locale language) {

        String printed = orEmpty(value).strip();
        if (printed.isBlank()) {
            return;
        }
        lines.add(new RenderRequest.ContactLine(
                kind.labelIn(language), printed, hrefFor(kind, printed)));
    }

    /**
     * Where a contact line points, or nothing.
     *
     * <p>A phone number and a city are not links. The rest are, and the value
     * is what the person typed, which may already carry a scheme, so one is
     * only added when there is none. Nothing here trusts the string as LaTeX:
     * escaping happens in the renderer, on both the href and the text.
     */
    private static String hrefFor(ContactKind kind, String value) {
        return switch (kind) {
            case EMAIL -> "mailto:" + value;
            case PHONE, LOCATION -> "";
            case LINKEDIN, GITHUB, WEBSITE ->
                    value.startsWith("http://") || value.startsWith("https://")
                            ? value
                            : "https://" + value;
        };
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
