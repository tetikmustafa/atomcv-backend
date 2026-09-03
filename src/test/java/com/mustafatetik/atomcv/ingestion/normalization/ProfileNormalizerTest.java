package com.mustafatetik.atomcv.ingestion.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile.ExtractedAtom;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile.ExtractedContact;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile.ExtractedEntry;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile.ExtractedSection;
import com.mustafatetik.atomcv.ingestion.structuring.ExtractedProfile.ExtractionWarning;
import com.mustafatetik.atomcv.shared.wire.ExtractionWarningCode;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bolum 31.5's seven steps on a whole document.
 *
 * <p>What is being asserted throughout is that the model was <em>not</em>
 * trusted with any of them. A model asked for a canonical skill name, a date
 * format or an ordering will comply most of the time, and most of the time is
 * the worst possible rate for something a later comparison depends on being
 * exact.
 */
class ProfileNormalizerTest {

    private final ProfileNormalizer normalizer = new ProfileNormalizer();

    // -- dates (step 2) ----------------------------------------------------

    @Test
    void aDateTheModelLeftInTheDocumentsOwnWordsIsRead() {
        var normalized = normalize(profileWith(
                entry("Data Engineer", "Brisa", "Eylül 2023", "Aralık 2024")));

        var entry = normalized.sections().get(0).entries().get(0);
        assertThat(entry.start()).isEqualTo(YearMonth.of(2023, 9));
        assertThat(entry.end()).isEqualTo(YearMonth.of(2024, 12));
        assertThat(normalized.warnings()).isEmpty();
    }

    /**
     * Left null and raised as a question, never guessed at. A plausible wrong
     * date is the one error nobody proofreads out.
     */
    @Test
    void aDateThatCannotBeReadBecomesNullAndAWarning() {
        var normalized = normalize(profileWith(
                entry("Data Engineer", "Brisa", "sometime in the spring", "2024-12")));

        assertThat(normalized.sections().get(0).entries().get(0).start()).isNull();
        assertThat(normalized.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo(ExtractionWarningCode.AMBIGUOUS_DATE);
            assertThat(warning.detail()).contains("startDate");
        });
    }

    /** A year with no month says so, because it is a different question to ask. */
    @Test
    void aYearWithNoMonthSaysThatIsWhatItWas() {
        var normalized = normalize(profileWith(
                entry("Data Engineer", "Brisa", "2019", "2024-12")));

        assertThat(normalized.warnings()).singleElement().satisfies(warning ->
                assertThat(warning.detail()).contains("year with no month"));
    }

    /**
     * An absent end date means the person is still there. A warning on every
     * current job would train people to click past the one that matters.
     */
    @Test
    void anAbsentEndDateIsNotAWarning() {
        var normalized = normalize(profileWith(
                entry("Data Engineer", "Brisa", "2023-09", null)));

        assertThat(normalized.warnings()).isEmpty();
        assertThat(normalized.sections().get(0).entries().get(0).isOngoing()).isTrue();
    }

    @Test
    void anEntryThatEndsBeforeItStartsIsFlagged() {
        var normalized = normalize(profileWith(
                entry("Data Engineer", "Brisa", "2024-09", "2023-01")));

        assertThat(normalized.warnings()).extracting(ExtractionWarning::code)
                .contains(ExtractionWarningCode.OVERLAPPING_DATES);
    }

    @Test
    void anEntryWithContentButNoOrganizationIsFlagged() {
        var normalized = normalize(profileWith(
                entry("Data Engineer", "", "2023-09", "2024-12")));

        assertThat(normalized.warnings()).extracting(ExtractionWarning::code)
                .contains(ExtractionWarningCode.MISSING_ORGANIZATION);
    }

    /** The model's own warnings survive; the review screen does not care who noticed. */
    @Test
    void theModelsWarningsAreKeptAlongsideTheOnesFound() {
        var extracted = new ExtractedProfile("tr", 0.96, ExtractedContact.EMPTY,
                List.of(new ExtractedSection(SectionKind.EXPERIENCE, "Deneyim",
                        List.of(entry("Data Engineer", "Brisa", "not a date", "2024-12")))),
                List.of(new ExtractionWarning(ExtractionWarningCode.UNCLEAR_SECTION,
                        "a heading could not be placed", "sections[1]")));

        var normalized = normalize(extracted);

        assertThat(normalized.warnings()).extracting(ExtractionWarning::code)
                .containsExactlyInAnyOrder(ExtractionWarningCode.UNCLEAR_SECTION,
                        ExtractionWarningCode.AMBIGUOUS_DATE);
    }

    // -- where a warning happened (F-018) ----------------------------------

    /**
     * <strong>The path names the section, and every section used to be
     * section zero.</strong> The form this replaced was
     * {@code sections.entries[i]} with no section in it at all, so a warning
     * on Education's first row and one on Experience's first row were written
     * down as the same place. The frontend could not open the right one, which
     * is half of what Bolum 31.6 asks for.
     */
    @Test
    void awarningNamesTheSectionItHappenedIn() {
        var normalized = normalize(new ExtractedProfile("en", 0.99,
                ExtractedContact.EMPTY,
                List.of(new ExtractedSection(SectionKind.EDUCATION, "Education",
                                List.of(entry("BSc", "A university", "2015-09", "2019-06"))),
                        new ExtractedSection(SectionKind.EXPERIENCE, "Experience",
                                List.of(entry("Engineer", "Brisa", "not a date", null)))),
                List.of()));

        assertThat(normalized.warnings()).singleElement().satisfies(warning ->
                assertThat(EntryPath.parse(warning.path())).hasValue(new EntryPath(1, 0)));
    }

    /**
     * <strong>And it names the row after the sort, not before it.</strong>
     * Warnings used to be written down against the index the model answered
     * in, and {@code newestFirst} reorders the entries a line later — so the
     * only case where the path was readable at all, a single section, was
     * exactly the case where it could point at the wrong row.
     *
     * <p>Here the unreadable date is on the entry the model listed first and
     * the sort puts last: the old code said entry 0, the row is entry 1.
     */
    @Test
    void awarningNamesTheRowTheReaderSeesAndNotTheOneTheModelSent() {
        var normalized = normalize(sectionOf(SectionKind.EXPERIENCE,
                entry("Oldest", "A", "not a date", "2020-01"),
                entry("Newest", "C", "2024-01", null)));

        assertThat(normalized.sections().get(0).entries())
                .extracting(NormalizedProfile.NormalizedEntry::title)
                .containsExactly("Newest", "Oldest");
        assertThat(normalized.warnings()).singleElement().satisfies(warning ->
                assertThat(EntryPath.parse(warning.path())).hasValue(new EntryPath(0, 1)));
    }

    // -- ordering (steps 5 and 6) ------------------------------------------

    @Test
    void experienceIsOrderedNewestFirstAndNumberedAfterwards() {
        var normalized = normalize(sectionOf(SectionKind.EXPERIENCE,
                entry("Oldest", "A", "2019-01", "2020-01"),
                entry("Newest", "C", "2024-01", null),
                entry("Middle", "B", "2021-06", "2023-12")));

        var entries = normalized.sections().get(0).entries();
        assertThat(entries).extracting(NormalizedProfile.NormalizedEntry::title)
                .containsExactly("Newest", "Middle", "Oldest");
        assertThat(entries).extracting(NormalizedProfile.NormalizedEntry::displayOrder)
                .containsExactly((short) 0, (short) 1, (short) 2);
    }

    /**
     * Last, not first. A missing date is the least information there is, and
     * the top of the list is the position a reader reads as "most recent".
     */
    @Test
    void anEntryWithNoStartDateSinksToTheBottom() {
        var normalized = normalize(sectionOf(SectionKind.EXPERIENCE,
                entry("Undated", "A", null, null),
                entry("Dated", "B", "2021-06", "2023-12")));

        assertThat(normalized.sections().get(0).entries())
                .extracting(NormalizedProfile.NormalizedEntry::title)
                .containsExactly("Dated", "Undated");
    }

    /**
     * Education keeps the document's order. A CV lists degrees newest first
     * too, but a reader expects the highest qualification at the top and those
     * are not always the same row — reordering would overrule the person for
     * no gain.
     */
    @Test
    void educationKeepsTheOrderTheDocumentPutItIn() {
        var normalized = normalize(sectionOf(SectionKind.EDUCATION,
                entry("BSc", "A university", "2015-09", "2019-06"),
                entry("MSc", "A university", "2019-09", "2021-06")));

        assertThat(normalized.sections().get(0).entries())
                .extracting(NormalizedProfile.NormalizedEntry::title)
                .containsExactly("BSc", "MSc");
    }

    @Test
    void everySectionAndAtomIsNumberedToo() {
        var normalized = normalize(new ExtractedProfile("en", 0.99,
                ExtractedContact.EMPTY,
                List.of(new ExtractedSection(SectionKind.EXPERIENCE, "Experience",
                                List.of(entryWithAtoms("A", "Org", atom("one"), atom("two")))),
                        new ExtractedSection(SectionKind.EDUCATION, "Education", List.of())),
                List.of()));

        assertThat(normalized.sections())
                .extracting(NormalizedProfile.NormalizedSection::displayOrder)
                .containsExactly((short) 0, (short) 1);
        assertThat(normalized.sections().get(0).entries().get(0).atoms())
                .extracting(NormalizedProfile.NormalizedAtom::displayOrder)
                .containsExactly((short) 0, (short) 1);
    }

    // -- skills, tags and runs (steps 1, 3 and 7) --------------------------

    @Test
    void skillsAreCanonicalisedAndDeduplicatedKeepingTheirOrder() {
        var atom = new ExtractedAtom("built things", null, List.of(), List.of(),
                List.of("React.js", "PostgreSQL", "react", "  "),
                List.of(), List.of(), List.of());

        var normalized = normalize(profileWith(entryWithAtoms("A", "Org", atom)));

        assertThat(normalized.atoms()).singleElement().satisfies(one ->
                assertThat(one.skills()).containsExactly("react", "postgres"));
    }

    @Test
    void tagsTakeTheFormTheColumnEnforces() {
        var atom = new ExtractedAtom("built things", null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of("  Data Engineering ", "ETL", "etl"));

        var normalized = normalize(profileWith(entryWithAtoms("A", "Org", atom)));

        assertThat(normalized.atoms()).singleElement().satisfies(one ->
                assertThat(one.tags()).containsExactly("data engineering", "etl"));
    }

    @Test
    void emphasisBecomesRunsAndTheSentenceSurvives() {
        var atom = new ExtractedAtom(
                "Engineered ETL pipelines using Microsoft Fabric",
                "Engineered ETL pipelines using Microsoft Fabric",
                List.of("Microsoft Fabric"), List.of("Microsoft Fabric"),
                List.of("microsoft-fabric"), List.of(), List.of(), List.of());

        var normalized = normalize(profileWith(entryWithAtoms("A", "Org", atom)));

        assertThat(normalized.atoms()).singleElement().satisfies(one -> {
            assertThat(one.source().plainText())
                    .isEqualTo("Engineered ETL pipelines using Microsoft Fabric");
            assertThat(one.source().runs()).hasSize(2);
            assertThat(one.english().plainText()).isNotEmpty();
        });
    }

    /**
     * Bolum 21 reads an absent English variant as "the source is the English".
     * A duplicate would be a second row to keep in step for no gain.
     */
    @Test
    void anEnglishCvGetsNoSecondCopyOfItself() {
        var normalized = normalize(
                profileWith(entryWithAtoms("A", "Org", atom("Engineered ETL pipelines"))));

        assertThat(normalized.atoms()).singleElement().satisfies(one -> {
            assertThat(one.source().plainText()).isEqualTo("Engineered ETL pipelines");
            assertThat(one.english().isEmpty()).isTrue();
        });
    }

    /** {@code plainText} and {@code contentHash} are the domain's, over the plain text. */
    @Test
    void reMarkingASentenceDoesNotChangeItsContentHash() {
        var marked = normalize(profileWith(entryWithAtoms("A", "Org",
                new ExtractedAtom("Engineered ETL pipelines", null,
                        List.of("ETL"), List.of(), List.of(), List.of(), List.of(), List.of()))));
        var unmarked = normalize(
                profileWith(entryWithAtoms("A", "Org", atom("Engineered ETL pipelines"))));

        assertThat(marked.atoms().get(0).source().contentHash())
                .isEqualTo(unmarked.atoms().get(0).source().contentHash());
    }

    // -- contact -----------------------------------------------------------

    @Test
    void theContactBlockBecomesTheDomainsOwnRecordWithBlanksAsAbsences() {
        var extracted = new ExtractedProfile("en", 0.99,
                new ExtractedContact("  Ada Lovelace ", "ada@example.com", "   ",
                        null, null, null, "Istanbul"),
                List.of(), List.of());

        var contact = normalize(extracted).contact();

        assertThat(contact.name()).isEqualTo("Ada Lovelace");
        assertThat(contact.email()).isEqualTo("ada@example.com");
        // Blank is an absence, so Contact.isEmpty means what it says.
        assertThat(contact.phone()).isNull();
        assertThat(contact.location()).isEqualTo("Istanbul");
    }

    /** Statistics only; the CV's own words never reach a log (absolute rule 4). */
    @Test
    void theShapeThatReachesALogLineCarriesNoneOfTheText() {
        var normalized = normalize(profileWith(
                entryWithAtoms("Data Engineer", "Brisa", atom("Engineered ETL pipelines"))));

        assertThat(normalized.shape())
                .doesNotContain("Brisa", "Engineered", "ETL", "Data Engineer");
        assertThat(normalized.shape()).contains("atoms=1", "sections=1");
    }

    // -- fixtures ----------------------------------------------------------

    private static ExtractedAtom atom(String text) {
        return new ExtractedAtom(text, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }

    private static ExtractedEntry entry(
            String title, String organization, String start, String end) {
        return new ExtractedEntry(title, organization, "Istanbul", start, end,
                List.of(atom("did the work")));
    }

    private static ExtractedEntry entryWithAtoms(
            String title, String organization, ExtractedAtom... atoms) {
        return new ExtractedEntry(title, organization, "Istanbul", "2023-09", null,
                List.of(atoms));
    }

    private static ExtractedProfile profileWith(ExtractedEntry entry) {
        return sectionOf(SectionKind.EXPERIENCE, entry);
    }

    private static ExtractedProfile sectionOf(SectionKind kind, ExtractedEntry... entries) {
        return new ExtractedProfile("tr", 0.96, ExtractedContact.EMPTY,
                List.of(new ExtractedSection(kind, "Deneyim", List.of(entries))), List.of());
    }

    /**
     * The document is not the subject here; {@code ExtractionFidelityTest}
     * owns that comparison. Null means "no document to check against", which
     * is the editor's case and leaves every other rule untouched.
     */
    private NormalizedProfile normalize(ExtractedProfile extracted) {
        return normalizer.normalize(extracted, null);
    }

}
