package com.mustafatetik.atomcv.profile.service;

import com.mustafatetik.atomcv.profile.api.dto.AtomResponse;
import com.mustafatetik.atomcv.profile.api.dto.EntryResponse;
import com.mustafatetik.atomcv.profile.api.dto.ProfileExport;
import com.mustafatetik.atomcv.profile.api.dto.ProfileResponse;
import com.mustafatetik.atomcv.profile.api.dto.SectionResponse;
import com.mustafatetik.atomcv.profile.api.dto.VariantResponse;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A profile, whole, in a format the user can keep (Bolum 13.1, EK C.1).
 *
 * <p>This is not the CV renderer. There is no page budget, no template and no
 * measurement here — an export is a copy of the data, and it stays readable
 * even when the profile is far too long for any CV.
 */
@Service
public class ProfileExporter {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ProfileResolver resolver;
    private final ProfileAssembler assembler;

    ProfileExporter(ProfileResolver resolver, ProfileAssembler assembler) {
        this.resolver = resolver;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public ProfileExport export(UserContext user) {
        Profile profile = resolver.own(user);
        ProfileRef reference = ProfileRef.persistent(user, profile.getId(), profile.getOwnerId());
        ProfileTree tree = assembler.load(reference);

        return new ProfileExport(
                Instant.now(),
                ProfileResponse.of(profile),
                tree.sections().stream().map(ProfileExporter::sectionOf).toList());
    }

    /** The same content as Markdown, for a human rather than for a machine. */
    @Transactional(readOnly = true)
    public String exportAsMarkdown(UserContext user) {
        ProfileExport export = export(user);
        var out = new StringBuilder();

        ProfileResponse head = export.profile();
        out.append("# ").append(escape(nameOf(head.contact()))).append('\n');
        if (head.headline() != null) {
            out.append('\n').append(escape(head.headline())).append('\n');
        }
        contactLine(head.contact()).ifPresent(line -> out.append('\n').append(line).append('\n'));
        if (head.selfDescription() != null) {
            out.append("\n").append(escape(head.selfDescription())).append('\n');
        }

        for (ProfileExport.SectionExport section : export.sections()) {
            out.append("\n## ").append(escape(section.section().title())).append('\n');
            section.atoms().forEach(atom -> bullet(out, atom));

            for (ProfileExport.EntryExport entry : section.entries()) {
                out.append('\n').append(entryHeading(entry.entry())).append('\n');
                entry.atoms().forEach(atom -> bullet(out, atom));
            }
        }
        return out.toString();
    }

    private static String entryHeading(EntryResponse entry) {
        var heading = new StringBuilder("### ").append(escape(entry.title()));
        if (entry.organization() != null) {
            heading.append(" — ").append(escape(entry.organization()));
        }
        String dates = dateRange(entry);
        if (!dates.isEmpty()) {
            heading.append("  \n").append(dates);
        }
        return heading.toString();
    }

    private static String dateRange(EntryResponse entry) {
        if (entry.startDate() == null) {
            return "";
        }
        // An absent end date is the ongoing case, not missing data.
        String end = entry.endDate() == null ? "present" : MONTH.format(entry.endDate());
        return MONTH.format(entry.startDate()) + " – " + end;
    }

    /**
     * The primary wording, as plain text. Marks are semantic, and an export
     * that turned them into asterisks would be inventing a presentation the
     * data deliberately does not carry (design principle 1).
     */
    private static void bullet(StringBuilder out, AtomResponse atom) {
        atom.variants().stream()
                .filter(VariantResponse::primary)
                .findFirst()
                .or(() -> atom.variants().stream().findFirst())
                .ifPresent(variant -> out.append("- ").append(escape(variant.plainText())).append('\n'));
    }

    private static java.util.Optional<String> contactLine(Contact contact) {
        List<String> parts = Stream.of(contact.email(), contact.phone(), contact.location(),
                        contact.website(), contact.linkedin(), contact.github())
                .filter(value -> value != null && !value.isBlank())
                .map(ProfileExporter::escape)
                .toList();
        return parts.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(
                String.join(" · ", parts));
    }

    private static String nameOf(Contact contact) {
        return contact.name() == null || contact.name().isBlank() ? "Profile" : contact.name();
    }

    /**
     * The characters that change meaning inside a line, escaped. A headline of
     * "*not* italics" is text, and an export that renders it as emphasis has
     * quietly changed what the user wrote.
     *
     * <p>Deliberately not the full punctuation set: {@code . - # +} only mean
     * something at the start of a line, and every line here starts with a
     * prefix this code wrote. Escaping them anyway would turn an email address
     * into {@code name@example\.com} in a file people read.
     */
    private static String escape(String text) {
        var out = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            if ("\\`*_[]<>|".indexOf(character) >= 0) {
                out.append('\\');
            }
            out.append(character);
        }
        return out.toString();
    }

    private static ProfileExport.SectionExport sectionOf(ProfileTree.SectionNode node) {
        return new ProfileExport.SectionExport(
                SectionResponse.of(node.section()),
                node.entries().stream()
                        .map(entry -> new ProfileExport.EntryExport(
                                EntryResponse.of(entry.entry()),
                                entry.atoms().stream().map(ProfileExporter::atomOf).toList()))
                        .toList(),
                node.atoms().stream().map(ProfileExporter::atomOf).toList());
    }

    private static AtomResponse atomOf(ProfileTree.AtomNode node) {
        return AtomResponse.of(node.atom(),
                node.variants().stream().map(VariantResponse::of).toList());
    }
}
