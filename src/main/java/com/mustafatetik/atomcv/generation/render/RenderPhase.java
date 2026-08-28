package com.mustafatetik.atomcv.generation.render;

import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.EntryNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Faz E — from what was chosen to what is printed (Bolum 22).
 *
 * <p>Pure and static: a profile, a selection and a template go in, a
 * {@link RenderRequest} comes out. It is the last place that knows an atom has
 * an id — the renderer is given text and nothing else, so it cannot make a
 * selection decision of its own.
 *
 * <p>Order comes from the profile, never from the selection. Selection ranks
 * by score; a CV that printed its bullets in score order would read as if it
 * had been shuffled.
 *
 * <p>Faz D reaches it as a map of replacements rather than as an edit to the
 * profile: what the person wrote is theirs, and a rewrite is true of one
 * generation. An atom missing from that map is printed as written, which is
 * how Bolum 21.6's "then use the original" becomes something the renderer
 * cannot get wrong.
 */
public final class RenderPhase {

    /**
     * Bolum 32 gives multilingual rendering its own vocabulary; until then the
     * two languages the product ships in are spelled out here, and anything
     * else falls back to English (EK D.8.6).
     */
    private static final Map<String, String> ONGOING = Map.of(
            "en", "Present",
            "tr", "Halen");

    private RenderPhase() {
    }

    public static RenderRequest build(
            Profile profile,
            ProfileTree tree,
            SelectionState selection,
            RewrittenContent rewritten,
            TemplateCustomization customization,
            Locale contentLanguage) {

        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(selection, "selection");
        RewrittenContent rewrites = rewritten == null ? RewrittenContent.none() : rewritten;
        Locale language = contentLanguage == null ? Locale.ENGLISH : contentLanguage;

        Map<UUID, UUID> chosenVariant = new HashMap<>();
        for (SelectedAtom atom : selection.selected()) {
            chosenVariant.put(atom.atomId(), atom.variantId());
        }

        Set<UUID> openedWithoutAtoms = Set.copyOf(selection.headerOnlyEntries());

        List<RenderRequest.RenderableSection> sections = new ArrayList<>();
        for (SectionNode section : tree.sections()) {
            List<RichContent> loose = contentOf(section.atoms(), chosenVariant, rewrites);

            List<RenderRequest.RenderableEntry> entries = new ArrayList<>();
            for (EntryNode entry : section.entries()) {
                List<RichContent> bullets = contentOf(entry.atoms(), chosenVariant, rewrites);
                // What selection opened is what is printed. A heading with
                // nothing under it reaches the page only where selection said
                // so and paid for it; one it never opened would spend points
                // the budget never accounted for. A degree line is the case
                // this exists for — no bullets, and nothing dishonest about
                // that (Bolum 20.2).
                if (!bullets.isEmpty() || openedWithoutAtoms.contains(entry.entry().getId())) {
                    entries.add(renderable(entry.entry(), bullets, language));
                }
            }

            // A section heading with nothing under it is not printed, for the
            // same reason: nothing charged for it.
            if (!loose.isEmpty() || !entries.isEmpty()) {
                sections.add(new RenderRequest.RenderableSection(
                        section.section().getTitle(), entries, loose));
            }
        }

        return new RenderRequest(header(profile), sections, customization, language);
    }

    private static RenderRequest.ProfileHeader header(Profile profile) {
        Contact contact = profile.getContact() == null ? Contact.EMPTY : profile.getContact();
        List<String> lines = new ArrayList<>();
        for (String value : List.of(
                orEmpty(contact.email()),
                orEmpty(contact.phone()),
                orEmpty(contact.location()),
                orEmpty(contact.linkedin()),
                orEmpty(contact.github()),
                orEmpty(contact.website()))) {
            if (!value.isBlank()) {
                lines.add(value);
            }
        }
        return new RenderRequest.ProfileHeader(
                orEmpty(contact.name()), profile.getHeadline(), lines);
    }

    private static RenderRequest.RenderableEntry renderable(
            Entry entry, List<RichContent> bullets, Locale language) {

        return new RenderRequest.RenderableEntry(
                orEmpty(entry.getTitle()),
                orEmpty(entry.getOrganization()),
                orEmpty(entry.getLocation()),
                dateRange(entry, language),
                bullets);
    }

    /**
     * The atoms of one node, in profile order, with the wording selection
     * chose for each — or the rewrite of it, where Faz D produced one.
     */
    private static List<RichContent> contentOf(
            List<AtomNode> atoms, Map<UUID, UUID> chosenVariant, RewrittenContent rewritten) {

        List<RichContent> content = new ArrayList<>();
        for (AtomNode node : atoms) {
            UUID atomId = node.atom().getId();
            // containsKey, not get: a selected atom whose variant was left
            // unset falls back to the primary wording rather than vanishing.
            if (!chosenVariant.containsKey(atomId)) {
                continue;
            }
            UUID variantId = chosenVariant.get(atomId);
            node.variants().stream()
                    .filter(variant -> variant.getId().equals(variantId))
                    .findFirst()
                    .or(node::primaryVariant)
                    .map(AtomVariant::getContent)
                    // An atom that was not selected is not rewritten either,
                    // so the gate above comes first: a rewrite can never put
                    // a line on the page that selection did not pay for.
                    .map(original -> rewritten.orOriginal(atomId, original))
                    .ifPresent(content::add);
        }
        return content;
    }

    /** "Mar 2021 – Present", or nothing at all when the entry carries no dates. */
    static String dateRange(Entry entry, Locale language) {
        LocalDate start = entry.getStartDate();
        if (start == null) {
            return "";
        }
        DateTimeFormatter month = DateTimeFormatter.ofPattern("MMM yyyy", language);
        String end = entry.getEndDate() == null
                ? ONGOING.getOrDefault(language.getLanguage(), ONGOING.get("en"))
                : month.format(entry.getEndDate());
        return month.format(start) + " – " + end;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
