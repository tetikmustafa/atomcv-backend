package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Contact;
import java.util.List;

/**
 * What a caller wants the profile head to become.
 *
 * <p>Domain types, not the API request record: the service layer does not
 * depend on the shape of an HTTP body, so a second caller — the CV import in
 * Stage 2, a seeder in Adim 1.9 — does not have to build one.
 */
public record ProfileHeadUpdate(
        String headline,
        Contact contact,
        String selfDescription,
        String sourceLanguage,
        List<String> enabledLanguages) {

    public ProfileHeadUpdate {
        contact = contact == null ? Contact.EMPTY : contact;
        enabledLanguages = enabledLanguages == null ? List.of() : List.copyOf(enabledLanguages);
    }
}
