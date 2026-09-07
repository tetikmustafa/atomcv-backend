package com.mustafatetik.atomcv.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mustafatetik.atomcv.generation.phases.analysis.JobAnalysis;
import com.mustafatetik.atomcv.generation.render.RenderPhase;
import com.mustafatetik.atomcv.generation.rewrite.RewriteContext;
import com.mustafatetik.atomcv.generation.rewrite.RewrittenContent;
import com.mustafatetik.atomcv.generation.rewrite.TechStackEditor;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScorer;
import com.mustafatetik.atomcv.generation.scoring.RelevanceScores;
import com.mustafatetik.atomcv.generation.scoring.ScorableAtomFactory;
import com.mustafatetik.atomcv.generation.scoring.ScoringWeights;
import com.mustafatetik.atomcv.generation.selection.SelectionPhase;
import com.mustafatetik.atomcv.generation.selection.SelectionRequestBuilder;
import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.seed.GoldenProfile;
import com.mustafatetik.atomcv.profile.seed.GoldenProfileReader;
import com.mustafatetik.atomcv.rendering.latex.LatexDocumentRenderer;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.shared.text.ClaimVocabulary;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The real master CV against a real posting, end to end without a model
 * (Bolum 51.3).
 *
 * <p>Three fixtures, and each one was read rather than written:
 * {@code golden/profiles/master_cv_en.json} is the profile a real import of
 * {@code cv/master_cv.tex} produced, {@code golden/jobs/senior_java_spring_en.txt}
 * is the posting it was tailored against, and
 * {@code golden/analyses/senior_java_spring_en.json} is the recorded Faz A
 * answer for that exact text.
 *
 * <p><strong>What is asserted is the shape of the page, not the wording on
 * it.</strong> A CV hand-tailored from the same two files exists, and matching
 * it line for line would be pinning one person's judgement — including the
 * judgement's mistakes. What has to hold is what the product promises: nothing
 * on the page that is not in the profile, no category invented, a summary set
 * as one paragraph, a Tech Stack set as labelled rows, and all of it inside one
 * measured page.
 *
 * <p>No embedding service and no compiler, so it runs in the fast lane. Faz B
 * falls back to {@link ScoringWeights#WITHOUT_EMBEDDING} (Bolum 28.4), which is
 * a documented degraded mode rather than a fiction — and it is deterministic,
 * which the real one is not from a test's point of view.
 */
class GoldenMasterCvTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);
    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    private static final GoldenProfile PROFILE = GoldenProfileReader.read("master_cv_en", OWNER);
    private static final JobAnalysis POSTING = analysis();

    /** Everything between {@code \begin{document}} and the end. */
    private final String page = render();

    // ── the two shapes the reference template gives ───────────────────────

    /**
     * R1. About is one {@code \resumeItem} in a label-less list, however long
     * the paragraph is.
     *
     * <p>The profile carries four summaries — one written towards backend work,
     * one towards data, one towards AI — and the page carries exactly one of
     * them, whole. It came out as a bulleted item before, which reads as the
     * first of a list that never arrives.
     */
    @Test
    void thesummaryIsOneParagraphUnderItsHeading() {
        String about = sectionOf("About");

        assertThat(about)
                .startsWith("\\resumeParagraphListStart\n")
                .doesNotContain("\\resumeItemListStart")
                .doesNotContain("\\begin{itemize}");
        assertThat(count(about, "\\resumeItem{"))
                .as("a CV has one summary, not two")
                .isEqualTo(1);
        assertThat(paragraphOn(about))
                .as("printed whole, and one of the profile's own")
                .isIn(summariesInTheProfile());
    }

    /**
     * R2. Tech Stack is one block of {@code \textbf{Category}{: items}} rows.
     *
     * <p>The section was missing from the page entirely once, and printed flat
     * once the selection stopped charging it for furniture it never sets. This
     * asserts the third state: present, and set the way the reference document
     * sets it.
     */
    @Test
    void thetechStackIsStackedLabelledRows() {
        String stack = sectionOf("Tech Stack");

        assertThat(stack).startsWith("\\resumeInlineList{\n").endsWith("}\n");
        List<String> rows = rowsIn(stack);
        assertThat(rows).isNotEmpty();
        for (String row : rows) {
            assertThat(row)
                    .as("every row is a bold label and the list it introduces")
                    .matches("\\\\textbf\\{[^{}]+\\}\\{: .+\\}");
        }
    }

    /**
     * <strong>No category is ever synthesised.</strong>
     *
     * <p>Bolum 33 allows a Tech Stack to be filtered — items dropped, a category
     * dropped once it empties — and nothing else. A hand-tailored version of
     * this same CV grew a <em>Software Engineering Practices</em> row whose
     * terms were pulled out of narrative About and Experience text; that is the
     * one thing this section may not do, and the check is written as an
     * inclusion rather than as a list of forbidden names, so a category nobody
     * has thought of yet fails it too.
     */
    @Test
    void everyTechStackCategoryIsOneTheProfileAlreadyHad() {
        Set<String> known = labelsIn(PROFILE, SectionKind.SKILLS);

        assertThat(labelsOn(sectionOf("Tech Stack")))
                .isNotEmpty()
                .allSatisfy(label -> assertThat(known).contains(label));
    }

    /**
     * <strong>And every row is cut to the posting.</strong>
     *
     * <p>The master profile's Tech Stack is four pages' worth of everything
     * this person has ever touched — malware analysis tooling, Power Query,
     * 8086 assembly — against a Java posting. Printing it whole is the
     * complaint this filter exists for: a list of things rather than an answer
     * to the advertisement.
     *
     * <p>What may stay is the pair Bolum 33.4 allows, and nothing else: an item
     * the posting named, or one the rest of the page already talks about. The
     * check is written that way round — for each item printed, why — so an item
     * kept for a third reason nobody has thought of fails it.
     */
    @Test
    void everyTechStackItemIsEitherAskedForOrAlreadyOnThePage() {
        List<String> items = itemsOn(sectionOf("Tech Stack"));
        String rest = plainTextOf(page.replace(sectionOf("Tech Stack"), ""));

        assertThat(items).isNotEmpty();
        for (String item : items) {
            assertThat(askedFor(item) || rest.toLowerCase(Locale.ROOT)
                    .contains(bareName(item).toLowerCase(Locale.ROOT)))
                    .as("%s is on the page because the posting asked for it,"
                            + " or because the rest of the page says it", item)
                    .isTrue();
        }
    }

    /**
     * And the section is smaller than the profile's, which is the whole point:
     * a filter that filtered nothing would pass every other check here.
     */
    @Test
    void thepageCarriesFewerTechnologiesThanTheProfileDoes() {
        assertThat(itemsOn(sectionOf("Tech Stack")).size())
                .isLessThan(techStackItemsInTheProfile().size());
    }

    /** And Languages is the same row shape, which is why it shares the layout. */
    @Test
    void alanguageIsALabelAndALevel() {
        assertThat(labelsOn(sectionOf("Languages")))
                .isNotEmpty()
                .allSatisfy(label -> assertThat(labelsIn(PROFILE, SectionKind.LANGUAGES))
                        .contains(label));
    }

    // ── nothing on the page that is not in the profile ────────────────────

    /**
     * P3, checked against the page rather than against one phase of it.
     *
     * <p>{@link ClaimVocabulary#introducedNames} asks the closed question —
     * "does this name something none of its sources carry?" — and the source
     * here is the whole profile. A hand-tailored version of this CV turned
     * <em>Spring Cloud</em> into <em>Spring Core</em> in one bullet; whatever
     * the person meant by it, a name that is in the posting and in no atom is
     * the {@code UNSUPPORTED_CLAIM} case, and it must not reach a page this
     * product produced.
     */
    @Test
    void nothingOnThePageNamesATechnologyTheProfileDoesNot() {
        assertThat(ClaimVocabulary.introducedNames(plainTextOf(page), everyWordingInTheProfile()))
                .isEmpty();
    }

    // ── and it is a CV, inside one page ───────────────────────────────────

    /**
     * Six sections, in the order a reader expects, on one page.
     *
     * <p>The profile is four pages of source with almost nothing in it relevant
     * to this posting, which is the case that first produced twenty atoms all
     * from Projects. {@code SectionFloor} is what stops that, and this is the
     * assertion that it still does on the profile it was written for.
     */
    @Test
    void thepageCarriesEverySectionInReadingOrder() {
        assertThat(headingsOn(page)).containsExactly(
                "About", "Education", "Experience", "Projects", "Tech Stack", "Languages");
    }

    @Test
    void everythingFitsTheMeasuredPage() {
        SelectionState state = select();

        assertThat(state.budget().usedPt()).isLessThanOrEqualTo(state.budget().freePt());
        assertThat(state.budget().fixedPt() + state.budget().usedPt())
                .isLessThanOrEqualTo(CAPACITY.pageTextHeightPt());
    }

    /**
     * <strong>2a: the headroom is never padding.</strong>
     *
     * <p>Fourteen projects, and almost all of them Python, AI, web or security
     * work with nothing to do with a Java posting. {@code PROJECTS}' floor
     * reserves two, and what fills the rest of the page competes on score per
     * point like everything else — so a third project appears only if it earns
     * the room, never because the floor left space for it. What reaches the
     * page here is the Java work.
     */
    @Test
    void onlyProjectsThatScoreForThisPostingReachThePage() {
        List<String> printed = projectHeadingsOn(page);

        assertThat(printed).isNotEmpty();
        for (String project : printed) {
            assertThat(scoreOfProject(project))
                    .as("%s earned its place", project)
                    .isGreaterThan(scoreOfWeakestProjectInTheProfile());
        }
    }

    // ── the posting, read once and recorded (2e) ──────────────────────────

    /**
     * The posting is corporate boilerplate with no responsibilities section and
     * a redundant line about graduated engineering departments, and Faz A reads
     * it anyway.
     *
     * <p>Bolum 18.4's gate refused exactly this text until v2 of the prompt:
     * most real postings are a heading-less list of qualifications, and a rule
     * that called that "not a posting" refused the ordinary case. The recorded
     * answer is kept here so the claim can be checked without paying for it.
     */
    @Test
    void thepostingIsReadWithConfidenceAndItsSkillsAreFound() {
        assertThat(POSTING.confidence()).isGreaterThan(0.55);
        assertThat(POSTING.responsibilities()).isNotEmpty();

        Set<String> found = new LinkedHashSet<>();
        POSTING.requiredSkills().forEach(skill -> found.add(skill.canonical()));

        assertThat(found).contains(
                "java", "spring boot", "spring mvc", "spring core",
                "object-oriented programming", "design patterns",
                "test-driven development", "unit testing", "agile methodologies");
    }

    // ── the pipeline, run ─────────────────────────────────────────────────

    private static SelectionState select() {
        var scorable = ScorableAtomFactory.from(PROFILE.tree(), Map.of(), TODAY);
        var scores = new RelevanceScores(
                RelevanceScorer.rank(scorable, POSTING, ScoringWeights.WITHOUT_EMBEDDING),
                ScoringWeights.WITHOUT_EMBEDDING);
        var built = SelectionRequestBuilder.build(
                PROFILE.tree(), TemplateCustomization.CLASSIC, CAPACITY, 1, "en",
                Tone.FORMAL, scores);
        return SelectionPhase.select(built.request()).orElseThrow();
    }

    /**
     * Faz D's one deterministic half, and the only part of it this test can
     * run: the Tech Stack cut to the posting (Bolum 33.4). The rewrites need a
     * model and are absent here, which is what {@code RewrittenContent}'s
     * "absent means original" rule is for.
     */
    private static RewrittenContent techStackCutToThePosting(SelectionState state) {
        var context = RewriteContext.of(POSTING, PROFILE.profile().getSelfDescription(),
                "en", Tone.FORMAL, "golden");
        return RewrittenContent.none()
                .and(TechStackEditor.edit(PROFILE.tree(), state, context));
    }

    private static String render() {
        SelectionState state = select();
        var request = RenderPhase.build(PROFILE.profile(), PROFILE.tree(), state,
                techStackCutToThePosting(state), TemplateCustomization.CLASSIC, Locale.ENGLISH);
        String document = new LatexDocumentRenderer().renderFinal(request).value();
        return document.substring(document.indexOf("\\begin{document}"));
    }

    // ── reading the page back ─────────────────────────────────────────────

    private static final Pattern HEADING = Pattern.compile("\\\\section\\*\\{([^}]*)\\}");
    private static final Pattern PROJECT =
            Pattern.compile("\\\\resumeProjectHeading\\{\\\\textbf\\{([^{}]*)\\}\\}");
    private static final Pattern LABEL = Pattern.compile("\\\\textbf\\{([^{}]+)\\}\\{: ");
    private static final Pattern COMMAND = Pattern.compile("\\\\[a-zA-Z@]+\\*?|[{}\\\\]");

    private String sectionOf(String title) {
        return sectionOf(page, title);
    }

    private static String sectionOf(String page, String title) {
        int start = page.indexOf("\\section*{" + title + "}\n");
        assertThat(start).as("the page carries a %s section", title).isNotNegative();
        start += ("\\section*{" + title + "}\n").length();
        int next = page.indexOf("\n\\section*{", start);
        String block = page.substring(
                start, next < 0 ? page.indexOf("\\end{document}") : next + 1);
        // The blank line before the next heading belongs to the page, not to
        // the section, and it is the difference between "ends with a closing
        // brace" and "ends with whitespace".
        return block.stripTrailing() + "\n";
    }

    private static List<String> headingsOn(String document) {
        return matches(HEADING, document);
    }

    private static List<String> projectHeadingsOn(String document) {
        return matches(PROJECT, document);
    }

    private static List<String> labelsOn(String section) {
        return matches(LABEL, section);
    }

    private static List<String> rowsIn(String block) {
        String inner = block.substring(block.indexOf('\n') + 1, block.lastIndexOf("}\n"));
        return java.util.Arrays.stream(inner.split(" \\\\\\\\\n")).map(String::strip).toList();
    }

    private static String paragraphOn(String about) {
        int start = about.indexOf("\\resumeItem{") + "\\resumeItem{".length();
        return plainTextOf(about.substring(start, about.lastIndexOf("}\n")));
    }

    private static List<String> matches(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(unescape(matcher.group(1)));
        }
        return found;
    }

    /** LaTeX back to the words, which is what a claim check reads. */
    private static String plainTextOf(String latex) {
        return unescape(COMMAND.matcher(latex).replaceAll(" "));
    }

    private static String unescape(String latex) {
        return latex.replace("\\&", "&").replace("\\%", "%").replace("\\#", "#")
                .replace("\\_", "_").replace("\\$", "$").replaceAll("\\s+", " ").strip();
    }

    // ── reading the profile back ──────────────────────────────────────────

    private static Set<String> labelsIn(GoldenProfile profile, SectionKind kind) {
        Set<String> labels = new LinkedHashSet<>();
        profile.tree().sections().stream()
                .filter(section -> section.section().getKind() == kind)
                .forEach(section -> {
                    section.atoms().forEach(atom -> labelOf(atom.primaryVariant()
                            .map(v -> v.getContent().plainText()).orElse("")).ifPresent(labels::add));
                    section.entries().forEach(entry -> entry.atoms().forEach(atom ->
                            labelOf(atom.primaryVariant()
                                    .map(v -> v.getContent().plainText()).orElse(""))
                                    .ifPresent(labels::add)));
                });
        return labels;
    }

    /**
     * The technologies one inline block lists, label aside — split on the
     * commas between items and not on the commas inside a parenthetical, which
     * is the rule the editor itself splits by.
     */
    private static List<String> itemsOn(String block) {
        List<String> items = new ArrayList<>();
        for (String row : rowsIn(block)) {
            String tail = unescape(row.substring(row.indexOf("}{: ") + "}{: ".length(),
                    row.lastIndexOf('}')));
            int depth = 0;
            var item = new StringBuilder();
            for (char letter : tail.toCharArray()) {
                if (letter == '(') {
                    depth++;
                } else if (letter == ')' && depth > 0) {
                    depth--;
                } else if (letter == ',' && depth == 0) {
                    items.add(item.toString().strip());
                    item.setLength(0);
                    continue;
                }
                item.append(letter);
            }
            items.add(item.toString().strip());
        }
        return items.stream().filter(item -> !item.isBlank()).toList();
    }

    private static List<String> techStackItemsInTheProfile() {
        return itemsOn(sectionOf(rendered(PROFILE), "Tech Stack"));
    }

    /** Everything in the profile's Tech Stack, printed without a posting. */
    private static String rendered(GoldenProfile profile) {
        SelectionState state = select();
        var request = RenderPhase.build(profile.profile(), profile.tree(), state,
                RewrittenContent.none(), TemplateCustomization.CLASSIC, Locale.ENGLISH);
        String document = new LatexDocumentRenderer().renderFinal(request).value();
        return document.substring(document.indexOf("\\begin{document}"));
    }

    /** Whether the posting named this technology, under the shared rule. */
    private static boolean askedFor(String item) {
        Set<String> asked = new LinkedHashSet<>();
        POSTING.requiredSkills().forEach(skill -> asked.add(
                com.mustafatetik.atomcv.shared.text.SkillNames.canonical(
                        skill.canonical().isBlank() ? skill.name() : skill.canonical())));
        POSTING.preferredSkills().forEach(skill -> asked.add(
                com.mustafatetik.atomcv.shared.text.SkillNames.canonical(
                        skill.canonical().isBlank() ? skill.name() : skill.canonical())));
        return asked.contains(
                        com.mustafatetik.atomcv.shared.text.SkillNames.canonical(item))
                || asked.contains(
                        com.mustafatetik.atomcv.shared.text.SkillNames.canonical(bareName(item)));
    }

    /** The item without the qualification it carries in brackets. */
    private static String bareName(String item) {
        int bracket = item.indexOf('(');
        return bracket > 0 ? item.substring(0, bracket).strip() : item;
    }

    private static java.util.Optional<String> labelOf(String row) {
        int colon = row.indexOf(':');
        return colon <= 0 ? java.util.Optional.empty()
                : java.util.Optional.of(row.substring(0, colon).strip());
    }

    private static List<String> summariesInTheProfile() {
        return PROFILE.tree().sections().stream()
                .filter(section -> section.section().getKind() == SectionKind.ABOUT)
                .flatMap(section -> section.atoms().stream())
                .map(atom -> atom.primaryVariant()
                        .map(v -> v.getContent().plainText()).orElse(""))
                .toList();
    }

    private static List<String> everyWordingInTheProfile() {
        List<String> wordings = new ArrayList<>();
        PROFILE.variants().forEach(variant -> wordings.add(variant.getPlainText()));
        PROFILE.entries().forEach(entry -> {
            wordings.add(entry.getTitle());
            wordings.add(entry.getOrganization());
            wordings.add(entry.getLocation());
        });
        PROFILE.sections().forEach(section -> wordings.add(section.getTitle()));
        PROFILE.atoms().forEach(atom -> wordings.addAll(atom.getSkills()));
        var contact = PROFILE.profile().getContact();
        if (contact != null) {
            wordings.add(contact.name());
            wordings.add(contact.email());
            wordings.add(contact.linkedin());
            wordings.add(contact.github());
            wordings.add(contact.website());
            wordings.add(contact.location());
        }
        // The template's own words, which are not claims about anybody.
        wordings.addAll(List.of("Email", "Phone", "Location", "LinkedIn", "GitHub",
                "Portfolio", "Present", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"));
        return wordings;
    }

    private static double scoreOfProject(String title) {
        return PROFILE.tree().sections().stream()
                .filter(section -> section.section().getKind() == SectionKind.PROJECTS)
                .flatMap(section -> section.entries().stream())
                .filter(entry -> entry.entry().getTitle().equals(title))
                .flatMap(entry -> entry.atoms().stream())
                .mapToDouble(atom -> scores().getOrDefault(atom.atom().getId(), 0.0))
                .max()
                .orElseThrow(() -> new AssertionError("no such project in the profile: " + title));
    }

    /**
     * The weakest project in the profile: the one whose best bullet scores
     * lowest. Nothing at or below it may be on the page.
     */
    private static double scoreOfWeakestProjectInTheProfile() {
        return PROFILE.tree().sections().stream()
                .filter(section -> section.section().getKind() == SectionKind.PROJECTS)
                .flatMap(section -> section.entries().stream())
                .mapToDouble(entry -> entry.atoms().stream()
                        .mapToDouble(atom -> scores().getOrDefault(atom.atom().getId(), 0.0))
                        .max().orElse(0.0))
                .min()
                .orElseThrow();
    }

    private static Map<UUID, Double> scores() {
        var scorable = ScorableAtomFactory.from(PROFILE.tree(), Map.of(), TODAY);
        return new RelevanceScores(
                RelevanceScorer.rank(scorable, POSTING, ScoringWeights.WITHOUT_EMBEDDING),
                ScoringWeights.WITHOUT_EMBEDDING).byAtom();
    }

    private static JobAnalysis analysis() {
        String path = "golden/analyses/senior_java_spring_en.json";
        try (InputStream in = GoldenMasterCvTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("No such golden analysis: " + path);
            }
            return new ObjectMapper().readValue(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8), JobAnalysis.class);
        } catch (java.io.IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }

    private static int count(String text, String needle) {
        int found = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            found++;
        }
        return found;
    }
}
