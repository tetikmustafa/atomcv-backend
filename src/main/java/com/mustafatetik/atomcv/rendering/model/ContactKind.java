package com.mustafatetik.atomcv.rendering.model;

import java.util.Locale;

/**
 * The contact fields a header can print, and what to call them (Bolum 22).
 *
 * <p>The block used to be the bare values joined by a middle dot, which reads
 * as a list of strings rather than as a way to reach someone: an address, a
 * number and three URLs with nothing saying which was which.
 *
 * <p><strong>The labels are here rather than in a message file, and that is a
 * deviation worth naming.</strong> Every other user-visible sentence in this
 * product is the frontend's, in ICU, because a server sentence cannot be
 * translated by the client that shows it. These are different in one way that
 * decides it: they are printed <em>into the PDF</em>, in the CV's own content
 * language, which is a choice the person made about the document and not about
 * their interface. A Turkish CV with English labels would be wrong even for a
 * reader whose interface is English.
 *
 * <p>So the vocabulary is closed, it is six words long, and it is keyed by the
 * content language rather than by the request's. Anything outside the two
 * languages the product has falls back to English rather than printing a key.
 */
public enum ContactKind {

    EMAIL("Email", "E-posta"),
    PHONE("Phone", "Telefon"),
    LOCATION("Location", "Konum"),
    LINKEDIN("LinkedIn", "LinkedIn"),
    GITHUB("GitHub", "GitHub"),
    WEBSITE("Portfolio", "Portfolyo");

    private final String english;
    private final String turkish;

    ContactKind(String english, String turkish) {
        this.english = english;
        this.turkish = turkish;
    }

    /** Locale.ROOT on the comparison: absolute rule 7 holds for "TR" too. */
    public String labelIn(Locale language) {
        String tag = language == null ? "en" : language.getLanguage().toLowerCase(Locale.ROOT);
        return "tr".equals(tag) ? turkish : english;
    }
}
