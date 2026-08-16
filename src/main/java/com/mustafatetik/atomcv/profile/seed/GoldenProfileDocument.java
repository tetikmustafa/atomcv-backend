package com.mustafatetik.atomcv.profile.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.Tone;
import java.time.LocalDate;
import java.util.List;

/**
 * A profile as a fixture (Bolum 51.3).
 *
 * <p>Not the export shape. An export carries an id and a version for every
 * row, which a person writing a fixture by hand would have to invent sixty
 * times, and which would be wrong the moment the file was loaded into a second
 * database. Here identity is the file's position in the tree, and the reader
 * mints the ids (EK D.8.9).
 *
 * <p>Everything but the text is optional and defaults to what a new row would
 * have, so a fixture says only what makes it interesting.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record GoldenProfileDocument(
        String name,
        String description,
        String headline,
        String sourceLanguage,
        /** Defaults to the profile's own, which is what a new row carries. */
        List<String> enabledLanguages,
        Contact contact,
        Integer maxPages,
        List<Section> sections) {

    public record Contact(
            String name, String email, String phone,
            String linkedin, String github, String website, String location) {
    }

    public record Section(
            SectionKind kind,
            String title,
            SectionLayout layout,
            Boolean alwaysInclude,
            Boolean verbatim,
            Boolean active,
            List<Entry> entries,
            List<Atom> atoms) {
    }

    public record Entry(
            String title,
            String organization,
            String location,
            LocalDate startDate,
            LocalDate endDate,
            String url,
            Float importance,
            Short minAtoms,
            Boolean alwaysInclude,
            Boolean verbatim,
            Boolean active,
            List<Atom> atoms) {
    }

    /**
     * @param text         the wording, as plain text — marks are a rendering
     *                     concern and a fixture that carried them would be
     *                     asserting on the renderer instead of on selection
     * @param language     defaults to the profile's own source language
     * @param alternatives further wordings of the same fact. {@code text} is
     *                     the primary one; these are not, and each must claim a
     *                     different language-and-tone pair, as the API requires
     */
    public record Atom(
            String text,
            String language,
            AtomKind kind,
            Float importance,
            Boolean active,
            Boolean alwaysInclude,
            Boolean verbatim,
            Boolean verified,
            List<String> skills,
            List<String> metrics,
            List<String> properNouns,
            List<Wording> alternatives) {
    }

    /** One more way of saying the same atom. */
    public record Wording(String text, String language, Tone tone) {
    }
}
