package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import java.util.List;
import java.util.UUID;

/**
 * A whole profile as one value (Bolum 9, Adim 3.6).
 *
 * <p><strong>One document, not four collections.</strong> A persistent profile
 * is rows because rows are what a person edits one at a time over months; an
 * anonymous one exists for two hours and is written twice — once by the
 * import, once by whatever the review screen changed. Splitting it into
 * per-entity access would mean reading and rewriting the whole thing on every
 * call anyway, and would invite a race that a single value cannot have.
 *
 * <p>The four lists are the same flat lists {@link ProfileAssembler#assemble}
 * takes, and the tree is built with that same static method — so an anonymous
 * profile is checked for cross-tenant rows by exactly the code a persistent
 * one is, rather than by a second implementation that could disagree.
 *
 * @param profileId      derived from the session (see {@code ProfileRef.ephemeral})
 * @param contact        the header block
 * @param sourceLanguage what the CV was written in
 */
public record EphemeralProfile(
        UUID profileId,
        Contact contact,
        String sourceLanguage,
        List<Section> sections,
        List<Entry> entries,
        List<Atom> atoms,
        List<AtomVariant> variants) {

    public EphemeralProfile {
        contact = contact == null ? Contact.EMPTY : contact;
        sourceLanguage = sourceLanguage == null || sourceLanguage.isBlank()
                ? "en" : sourceLanguage;
        sections = sections == null ? List.of() : List.copyOf(sections);
        entries = entries == null ? List.of() : List.copyOf(entries);
        atoms = atoms == null ? List.of() : List.copyOf(atoms);
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    /** The shape everything downstream of a profile already reads. */
    public ProfileTree tree() {
        return ProfileAssembler.assemble(profileId, sections, entries, atoms, variants);
    }

    /** Statistics only; not one word of the CV (absolute rule 4). */
    public String shape() {
        return "sections=" + sections.size()
                + " entries=" + entries.size()
                + " atoms=" + atoms.size()
                + " variants=" + variants.size()
                + " language=" + sourceLanguage;
    }
}
