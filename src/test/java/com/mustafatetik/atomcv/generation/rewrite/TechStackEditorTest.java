package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Entry;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.AtomNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.EntryNode;
import com.mustafatetik.atomcv.profile.domain.ProfileTree.SectionNode;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Bolum 33.4's rule for a Tech Stack, which is a rule about what may be
 * removed and a much stricter one about what may be added.
 *
 * <p>Nothing here needs an LLM, and that is the point: a category invented or
 * an item moved between two would be a claim the profile does not carry, and
 * the only way to be sure it cannot happen is for there to be no code that
 * could write one.
 */
class TechStackEditorTest {

    private static final UUID PROFILE = UUID.randomUUID();

    @Test
    void anitemThePostingAsksForStays() {
        var row = row("Backend: Java, Spring Boot, COBOL");

        assertThat(printed(edit(page(row), posting("java", "spring-boot"))))
                .containsExactly("Backend: Java, Spring Boot");
    }

    /**
     * The second way in. A CV whose Tech Stack drops a technology its own
     * bullets talk about reads as an oversight, and the reader has been told
     * about it either way.
     */
    @Test
    void anitemThePageAlreadyTalksAboutStays() {
        var row = row("Backend: Java, Kafka, COBOL");
        var bullet = bullet("Moved three million events a day through Kafka", List.of());

        assertThat(printed(edit(page(row, bullet), posting("java"))))
                .containsExactly("Backend: Java, Kafka");
    }

    /** And the skill arrays ingestion wrote, which is the reliable half. */
    @Test
    void anitemAnotherAtomListsAsAskillStays() {
        var row = row("Backend: Java, Kafka, COBOL");
        var bullet = bullet("Moved three million events a day", List.of("Kafka"));

        assertThat(printed(edit(page(row, bullet), posting("java"))))
                .containsExactly("Backend: Java, Kafka");
    }

    /**
     * A whole word, or nothing. {@code Go} inside {@code Google} is not the
     * language, and a Tech Stack that kept it would be claiming one.
     */
    @Test
    void amentionInsideAlongerWordIsNotAmention() {
        var row = row("Backend: Java, Go");
        var bullet = bullet("Built the Google Analytics export", List.of());

        assertThat(printed(edit(page(row, bullet), posting("java"))))
                .containsExactly("Backend: Java");
    }

    @Test
    void arowThatKeepsEverythingIsNotTouchedAtAll() {
        var row = row("Backend: Java, Spring Boot");

        assertThat(edit(page(row), posting("java", "spring-boot"))).isEmpty();
    }

    /**
     * Bolum 33.4's one deletion: a category with nothing left in it goes. The
     * empty content is how it says so — the renderer prints no row for it.
     */
    @Test
    void acategoryThatKeepsNothingIsDropped() {
        var row = row("Mainframe: COBOL, JCL");

        Map<UUID, RichContent> edited = edit(page(row), posting("java"));

        assertThat(edited).hasSize(1);
        assertThat(edited.values().iterator().next().isEmpty()).isTrue();
    }

    @Test
    void thecategoryLabelIsKeptExactlyAsTheProfileWroteIt() {
        var row = row("Frontend & Web Technologies: React, Angular, jQuery");

        assertThat(printed(edit(page(row), posting("react"))))
                .containsExactly("Frontend & Web Technologies: React");
    }

    /**
     * No new items, no new categories, no item moved between two — whatever the
     * posting asks for. The editor's whole vocabulary is the row it was given.
     */
    @Test
    void nothingThePostingNamesIsAddedToArowThatDidNotHaveIt() {
        var row = row("Backend: Java, COBOL");

        assertThat(printed(edit(page(row), posting("java", "kubernetes", "terraform"))))
                .containsExactly("Backend: Java")
                .noneMatch(line -> line.toLowerCase(Locale.ROOT).contains("kubernetes"));
    }

    /**
     * Absolute rule 7, in the one place a skills matrix meets it. Under a
     * Turkish default locale {@code SQL} lowercases to {@code sqı}, and a row
     * matched that way would lose every item the posting asked for.
     */
    @Test
    void amatchIsMadeUnderTheRootLocaleAndNotTheDefaultOne() {
        Locale.setDefault(Locale.of("tr", "TR"));
        try {
            var row = row("Data: SQL, INFORMIX");

            assertThat(printed(edit(page(row), posting("sql"))))
                    .containsExactly("Data: SQL");
        } finally {
            Locale.setDefault(Locale.ENGLISH);
        }
    }

    /** A posting with no skills at all leaves the Tech Stack alone. */
    @Test
    void apostingThatNamedNothingChangesNothing() {
        var row = row("Backend: Java, COBOL");

        assertThat(edit(page(row), posting())).isEmpty();
    }

    /** A row with no category label is still a row, and is still filtered. */
    @Test
    void arowWithNoLabelIsFilteredJustTheSame() {
        var row = row("Java, Spring Boot, COBOL");

        assertThat(printed(edit(page(row), posting("java", "spring-boot"))))
                .containsExactly("Java, Spring Boot");
    }

    // -- fixtures ----------------------------------------------------------

    private static Map<UUID, RichContent> edit(Page page, RewriteContext context) {
        return TechStackEditor.edit(page.tree(), page.selection(), context);
    }

    private static List<String> printed(Map<UUID, RichContent> edited) {
        return edited.values().stream()
                .map(RichContent::plainText)
                .filter(text -> !text.isEmpty())
                .toList();
    }

    private static RewriteContext posting(String... skills) {
        return new RewriteContext(List.of(skills), List.of(), "", "en",
                Tone.FORMAL.wireValue(), "bucket");
    }

    /**
     * A Tech Stack section of inline rows, and an experience section under it
     * for whatever bullets the case needs — which is the shape a real profile
     * has, and the shape "already on the page" is decided in.
     */
    private static Page page(AtomNode... atoms) {
        var rows = new ArrayList<AtomNode>();
        var bullets = new ArrayList<AtomNode>();
        for (AtomNode atom : atoms) {
            (atom.atom().getKind() == AtomKind.SKILL ? rows : bullets).add(atom);
        }

        var stack = new Section(PROFILE, SectionKind.SKILLS, "Tech Stack", (short) 0);
        stack.setLayout(SectionLayout.INLINE_LIST);
        var experience = new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 1);
        var entry = new Entry(PROFILE, experience.getId(), "Backend Engineer", (short) 0);

        var tree = new ProfileTree(PROFILE, List.of(
                new SectionNode(stack, List.of(), List.copyOf(rows)),
                new SectionNode(experience,
                        List.of(new EntryNode(entry, List.copyOf(bullets))), List.of())));

        var selected = new ArrayList<SelectionState.SelectedAtom>();
        for (AtomNode atom : atoms) {
            selected.add(new SelectionState.SelectedAtom(atom.atom().getId(),
                    atom.variants().get(0).getId(), 0.7, 12.0, false));
        }
        return new Page(tree, new SelectionState(selected, List.of(),
                new SelectionState.BudgetBreakdown(600, 100, 500, 300)));
    }

    private record Page(ProfileTree tree, SelectionState selection) {
    }

    private static AtomNode row(String text) {
        return node(AtomKind.SKILL, text, List.of());
    }

    private static AtomNode bullet(String text, List<String> skills) {
        return node(AtomKind.BULLET, text, skills);
    }

    private static AtomNode node(AtomKind kind, String text, List<String> skills) {
        var atom = new Atom(PROFILE, UUID.randomUUID(),
                kind == AtomKind.BULLET ? UUID.randomUUID() : null, kind, (short) 0);
        atom.setSkills(skills);
        var wording = new AtomVariant(PROFILE, atom.getId(), "en", RichContent.plain(text));
        wording.setPrimary(true);
        return new AtomNode(atom, List.of(wording));
    }
}
