package com.mustafatetik.atomcv.rendering.html;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.profile.domain.SectionLayout;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.rendering.model.RenderRequest;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The third renderer (Bolum 22.6, Bolum 1.2's fourth claim).
 *
 * <p>The claim under test is not that the file is pretty. It is that the same
 * {@link RichContent} reaches a third output format without the content
 * knowing anything about any of them — the marks are semantic, and each
 * renderer decides what they look like.
 */
class HtmlDocumentWriterTest {

    private final HtmlDocumentWriter writer = new HtmlDocumentWriter();

    @Test
    void thesameMarksThatAreBoldInLatexAreBoldHere() {
        String html = writer.write(request(bullet()));

        assertThat(html)
                .contains("<strong>ETL</strong>")
                .contains("<strong>300K+ rows</strong>");
    }

    /**
     * Bolum 16.2: a mark this version has never heard of renders as plain
     * text rather than failing or disappearing.
     */
    @Test
    void anunknownMarkIsPrintedPlainly() {
        RichContent content = new RichContent(List.of(
                Run.of("Ran the "),
                Run.of("nightly batch", new Mark("experimental"))));

        String html = writer.write(request(content));

        assertThat(html).contains("Ran the nightly batch");
        assertThat(html).doesNotContain("experimental");
    }

    /**
     * <strong>Bolum 42.3, in one assertion.</strong> "LaTeX'te guvenliydi"
     * varsayimi digerlerine tasinmaz: a CV is user content, and what is inert
     * in LaTeX is a script tag here. The escaping is central and this is the
     * proof it runs.
     */
    @Test
    void auserWhoWritesMarkupGetsTextBack() {
        RichContent content = RichContent.plain(
                "Led <script>alert('x')</script> & \"quoted\" work");

        String html = writer.write(request(content));

        assertThat(html)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;")
                .contains("&amp;")
                .contains("&quot;quoted&quot;");
    }

    /** A link's href is inside an attribute, which is where a quote escapes. */
    @Test
    void alinkCannotBreakOutOfItsAttribute() {
        RichContent content = new RichContent(List.of(
                new Run("the write-up", List.of(Mark.LINK),
                        "https://example.com/\" onmouseover=\"steal()")));

        String html = writer.write(request(content));

        assertThat(html).doesNotContain("onmouseover=\"steal");
        assertThat(html).contains("&quot; onmouseover=&quot;");
    }

    /**
     * <strong>Nothing is fetched.</strong> The file is opened from a downloads
     * folder or handed to a parser, and every one of those breaks on a
     * reference to something that is not there.
     */
    @Test
    void thedocumentAsksTheNetworkForNothing() {
        String html = writer.write(request(bullet()));

        assertThat(html)
                .doesNotContain("<link ")
                .doesNotContain("<script")
                .doesNotContain("@import")
                .doesNotContain("src=");
    }

    /**
     * Structure, because the reader may be a machine. A heading is a heading
     * and a bullet is a list item; there is no table and no column.
     */
    @Test
    void thestructureIsWhatAnAtsReads() {
        String html = writer.write(request(bullet()));

        assertThat(html)
                .contains("<h1>Ada Lovelace</h1>")
                .contains("<h2>EXPERIENCE</h2>")
                .contains("<li>")
                .doesNotContain("<table");
    }

    /** Bolum 33.4.1: an inline row is a bold label and plain text after it. */
    @Test
    void aninlineRowSetsItsLabelInBoldAndNothingElse() {
        var section = new RenderRequest.RenderableSection("Tech Stack",
                SectionLayout.INLINE_LIST,
                List.of(new RenderRequest.RenderableEntry("Languages", "", "", "",
                        List.of(bullet()))),
                List.of());

        String html = writer.write(new RenderRequest(header(), List.of(section),
                TemplateCustomization.CLASSIC, Locale.ENGLISH));

        assertThat(html).contains("<strong>Languages:</strong>");
        // The items are plain: seventy percent of a skill matrix in bold
        // emphasises nothing.
        assertThat(html).contains("Engineered ETL pipelines processing 300K+ rows");
        assertThat(html).doesNotContain("<strong>ETL</strong>");
    }

    private static RichContent bullet() {
        return new RichContent(List.of(
                Run.of("Engineered "),
                Run.of("ETL", Mark.TECHNOLOGY),
                Run.of(" pipelines processing "),
                Run.of("300K+ rows", Mark.METRIC)));
    }

    private static RenderRequest request(RichContent atom) {
        var section = new RenderRequest.RenderableSection("Experience",
                SectionLayout.ENTRY_LIST,
                List.of(new RenderRequest.RenderableEntry("Data Engineer", "Brisa",
                        "Istanbul", "2023 - present", List.of(atom))),
                List.of());
        return new RenderRequest(header(), List.of(section),
                TemplateCustomization.CLASSIC, Locale.ENGLISH);
    }

    private static RenderRequest.ProfileHeader header() {
        return new RenderRequest.ProfileHeader("Ada Lovelace", "Data Engineer",
                List.of(new RenderRequest.ContactLine("email", "ada@example.com", "")));
    }
}
