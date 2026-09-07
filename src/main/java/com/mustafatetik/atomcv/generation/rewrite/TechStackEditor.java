package com.mustafatetik.atomcv.generation.rewrite;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.shared.text.SkillNames;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Tech Stack cut down to the posting, without an LLM (Bolum 33.4).
 *
 * <p>A master profile's Tech Stack is everything the person has ever used. A
 * generated CV's is what this posting is about — and a row of twenty
 * technologies of which three are relevant reads as a list of things rather
 * than as an answer to the advertisement.
 *
 * <p><strong>It only ever removes.</strong> Bolum 33.4 fixes the shape: filter
 * items within the categories the person wrote, drop a category when nothing
 * of it survives, and never invent a category or move an item between two. So
 * this reads the row, keeps some of its items and writes the rest of the row
 * back unchanged — there is no path here that produces a name the profile did
 * not already carry, which is the same guarantee {@code RewriteValidator}
 * enforces for a sentence and the reason this is not a prompt.
 *
 * <p><strong>Two ways for an item to stay.</strong> The posting naming it is
 * the obvious one. The second is that the item is already somewhere else on
 * the page — under an experience bullet, in a project — because a CV whose
 * Tech Stack omits a technology its own bullets talk about reads as an
 * oversight, and the reader has already been told about it either way.
 *
 * <p>What it does not do is decide the page is empty. A row that loses every
 * item is dropped; a section that loses every row prints as a heading with
 * nothing under it, and Faz E is where that is noticed.
 */
public final class TechStackEditor {

    /**
     * Where a row's label ends: the same rule {@code InlineRow} renders by, so
     * the label this keeps is the label the page bolds.
     */
    private static final int MAX_LABEL_CHARS = 60;

    private TechStackEditor() {
    }

    /**
     * Which sections this may touch: a Tech Stack, and nothing that merely
     * shares its shape.
     *
     * <p>Languages is set as the same inline list of labelled rows and is
     * <strong>not</strong> a Tech Stack. Filtering it against a posting would
     * drop a language the advertisement did not think to ask for, which is not
     * a technology a reader can do without seeing — the section is one unit and
     * it is either printed or it is not.
     */
    private static boolean isATechStack(SectionNode section) {
        return section.section().getKind() == SectionKind.SKILLS
                && section.section().getLayout() == SectionLayout.INLINE_LIST;
    }

    /**
     * The items of one row, split on the commas between them and not on the
     * commas inside them.
     *
     * <p>"Spring Cloud (Gateway, Eureka)" is one item. A plain split on commas
     * made it two, one of which was "Eureka)" — and the halves then failed to
     * match anything, so the qualified entries were exactly the ones that got
     * cut.
     */
    private static List<String> itemsOf(String tail) {
        var items = new ArrayList<String>();
        var item = new StringBuilder();
        int depth = 0;
        for (int at = 0; at < tail.length(); at++) {
            char letter = tail.charAt(at);
            if (letter == '(' || letter == '[') {
                depth++;
            } else if ((letter == ')' || letter == ']') && depth > 0) {
                depth--;
            } else if (letter == ',' && depth == 0) {
                add(items, item);
                continue;
            }
            item.append(letter);
        }
        add(items, item);
        return items;
    }

    private static void add(List<String> items, StringBuilder item) {
        String written = item.toString().strip();
        item.setLength(0);
        if (!written.isEmpty()) {
            items.add(written);
        }
    }

    /**
     * @return the rows that changed, as content to print in place of the
     *         profile's own — a row that keeps every item is absent, and a row
     *         that keeps none is present and empty
     */
    public static Map<UUID, RichContent> edit(
            ProfileTree tree, SelectionState selection, RewriteContext context) {

        // Through SkillNames, both sides. RewriteContext lowercases the
        // posting's own spelling and stops there, so "Spring Boot" arrives as
        // "spring boot" while an item off the page reduces to "spring-boot" —
        // and the two never met. Every skill the posting asked for was being
        // matched by the page-mention rule instead, or not at all.
        Set<String> posting = new HashSet<>();
        context.postingSkills().forEach(skill -> posting.add(SkillNames.canonical(skill)));
        posting.remove("");
        if (posting.isEmpty()) {
            return Map.of();
        }

        Set<UUID> onThePage = new HashSet<>();
        selection.selected().stream().map(SelectedAtom::atomId).forEach(onThePage::add);

        List<AtomNode> rows = new ArrayList<>();
        var elsewhere = new Elsewhere();
        for (SectionNode section : tree.sections()) {
            boolean inlineList = isATechStack(section);
            // Both, and in that order, because that is what the renderer sets:
            // an imported Tech Stack hangs each category off an entry of its
            // own, a hand-built one leaves them loose under the section, and
            // an inline list prints the two the same way. Reading only the
            // loose ones filtered nothing at all on the profile this was
            // written for.
            List<AtomNode> all = new ArrayList<>(section.atoms());
            section.entries().forEach(entry -> all.addAll(entry.atoms()));

            for (AtomNode node : all) {
                if (!onThePage.contains(node.atom().getId())) {
                    continue;
                }
                if (inlineList) {
                    rows.add(node);
                } else {
                    elsewhere.add(node);
                }
            }
        }

        var edited = new LinkedHashMap<UUID, RichContent>();
        for (AtomNode row : rows) {
            wordingOf(row, selection)
                    .flatMap(wording -> filter(wording.getContent(), posting, elsewhere))
                    .ifPresent(kept -> edited.put(row.atom().getId(), kept));
        }
        return edited;
    }

    /**
     * @return the row as it should print, or empty when every item stays and
     *         there is nothing to say
     */
    private static Optional<RichContent> filter(
            RichContent row, Set<String> posting, Elsewhere elsewhere) {

        String text = row.plainText();
        int colon = labelEnd(text);
        String label = colon < 0 ? "" : text.substring(0, colon);
        String tail = colon < 0 ? text : text.substring(colon + 1);

        List<String> items = itemsOf(tail);
        if (items.isEmpty()) {
            return Optional.empty();
        }

        var kept = new ArrayList<String>(items.size());
        for (String item : items) {
            if (namesOf(item).stream().anyMatch(name ->
                    posting.contains(SkillNames.canonical(name))
                            || elsewhere.mentions(name))) {
                kept.add(item);
            }
        }
        if (kept.size() == items.size()) {
            return Optional.empty();
        }
        if (kept.isEmpty()) {
            // The category emptied. Printing "Databases:" with nothing after
            // it is worse than not printing the category at all.
            return Optional.of(RichContent.plain(""));
        }
        return Optional.of(RichContent.plain(
                label.isEmpty() ? String.join(", ", kept)
                        : label + ": " + String.join(", ", kept)));
    }

    /**
     * The names one item may be recognised under: what it says, and what it
     * says with its parenthetical dropped.
     *
     * <p>A Tech Stack qualifies its entries — "Spring Cloud (Gateway, Eureka)",
     * "Assembly (8086)" — and no posting and no sentence ever spells one that
     * way. Without this the qualified items are the ones that get cut, which is
     * backwards: a person writes the parenthetical about the technology they
     * know best.
     */
    private static List<String> namesOf(String item) {
        int bracket = item.indexOf('(');
        String bare = bracket > 0 ? item.substring(0, bracket).strip() : "";
        return bare.isEmpty() || bare.equals(item) ? List.of(item) : List.of(item, bare);
    }

    /** {@code InlineRow}'s rule, in the one form both sides have to agree on. */
    private static int labelEnd(String text) {
        int colon = text.indexOf(':');
        if (colon < 1 || colon > MAX_LABEL_CHARS || colon + 1 >= text.length()) {
            return -1;
        }
        if (!Character.isWhitespace(text.charAt(colon + 1))) {
            return -1;
        }
        return text.substring(0, colon).chars().anyMatch(Character::isLetter) ? colon : -1;
    }

    private static Optional<AtomVariant> wordingOf(AtomNode node, SelectionState selection) {
        UUID variantId = selection.selected().stream()
                .filter(selected -> selected.atomId().equals(node.atom().getId()))
                .map(SelectedAtom::variantId)
                .findFirst()
                .orElse(null);
        return node.variants().stream()
                .filter(variant -> variant.getId().equals(variantId))
                .findFirst()
                .or(node::primaryVariant);
    }

    /**
     * Everything the rest of the page says, in the two forms a technology can
     * be recognised in.
     *
     * <p>The skill arrays are the reliable half — they were written by
     * ingestion for exactly this kind of comparison. The sentences are the
     * other half, and they are searched for the item's own spelling rather
     * than its canonical one: a bullet says "Spring Boot", not "spring-boot".
     * The search is bounded at both ends so that {@code Go} does not match
     * {@code Google} and {@code R} does not match every word with an R in it.
     */
    private static final class Elsewhere {

        private final Set<String> skills = new LinkedHashSet<>();
        private final List<String> sentences = new ArrayList<>();

        void add(AtomNode node) {
            node.atom().getSkills().forEach(skill -> skills.add(SkillNames.canonical(skill)));
            node.variants().stream()
                    .map(variant -> variant.getContent().plainText())
                    .filter(text -> !text.isBlank())
                    .forEach(text -> sentences.add(text.toLowerCase(Locale.ROOT)));
        }

        boolean mentions(String item) {
            String canonical = SkillNames.canonical(item);
            if (!canonical.isEmpty() && skills.contains(canonical)) {
                return true;
            }
            String needle = item.strip().toLowerCase(Locale.ROOT);
            // One letter is not a mention. "C" and "R" are real languages and
            // the posting may ask for either by name — that is the exact-set
            // check above, and it is safe. Hunting for a single letter in a
            // page of prose is not: "M (Power Query)" stayed on a Java CV
            // because some sentence somewhere had a lone "m" in it.
            if (needle.length() < 2) {
                return false;
            }
            Pattern spelling = Pattern.compile(
                    "(?<![\\p{L}\\p{N}])" + Pattern.quote(needle) + "(?![\\p{L}\\p{N}])");
            for (String sentence : sentences) {
                Matcher found = spelling.matcher(sentence);
                if (found.find()) {
                    return true;
                }
            }
            return false;
        }
    }
}
