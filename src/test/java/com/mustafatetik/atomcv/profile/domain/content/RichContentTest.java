package com.mustafatetik.atomcv.profile.domain.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RichContentTest {

    @Test
    void plainTextConcatenatesRunsInOrder() {
        var content = RichContent.of(
                Run.of("Built "),
                Run.of("ETL", Mark.TECHNOLOGY),
                Run.of(" pipelines processing "),
                Run.of("300K+ rows", Mark.METRIC));

        assertThat(content.plainText()).isEqualTo("Built ETL pipelines processing 300K+ rows");
    }

    @Test
    void emptyContentHasEmptyPlainText() {
        assertThat(RichContent.EMPTY.plainText()).isEmpty();
        assertThat(RichContent.EMPTY.isEmpty()).isTrue();
        assertThat(RichContent.plain("").isEmpty()).isTrue();
    }

    // ─── contentHash: only plainText may move it (Bolum 16.2) ───

    @Test
    void hashIgnoresMarks() {
        var unmarked = RichContent.of(Run.of("Built ETL pipelines"));
        var marked = RichContent.of(
                Run.of("Built "),
                Run.of("ETL", Mark.TECHNOLOGY),
                Run.of(" pipelines"));

        assertThat(marked.plainText()).isEqualTo(unmarked.plainText());
        assertThat(marked.contentHash()).isEqualTo(unmarked.contentHash());
    }

    @Test
    void hashIgnoresRunBoundaries() {
        var oneRun = RichContent.of(Run.of("Reduced latency by 40%"));
        var manyRuns = RichContent.of(
                Run.of("Reduced "),
                Run.of("latency "),
                Run.of("by "),
                Run.of("40%"));

        assertThat(manyRuns.contentHash()).isEqualTo(oneRun.contentHash());
    }

    @Test
    void hashIgnoresAnHrefChange() {
        var first = RichContent.of(Run.link("mustafatetik.com", "https://mustafatetik.com"));
        var second = RichContent.of(Run.link("mustafatetik.com", "https://www.mustafatetik.com"));

        assertThat(second.contentHash()).isEqualTo(first.contentHash());
    }

    @Test
    void hashFollowsTheText() {
        var before = RichContent.plain("Reduced latency by 40%");
        var after = RichContent.plain("Reduced latency by 45%");

        assertThat(after.contentHash()).isNotEqualTo(before.contentHash());
    }

    @Test
    void hashFollowsWhitespaceToo() {
        assertThat(RichContent.plain("a b").contentHash())
                .isNotEqualTo(RichContent.plain("a  b").contentHash());
    }

    @Test
    void hashIsSha256OfTheUtf8PlainText() {
        // Fixed vectors: a stored content_hash outlives any refactoring here.
        assertThat(RichContent.EMPTY.contentHash())
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(RichContent.plain("ölçüm").contentHash())
                .isEqualTo("1788655b44cddf1f19094da6300299f361c185cf1878bd18c704c37cd5f227cf");
    }

    // ─── absolute rule 4: content never reaches a log line ───

    @Test
    void toStringCarriesShapeNotContent() {
        var content = RichContent.of(
                Run.of("Built "),
                Run.link("mustafatetik.com", "https://mustafatetik.com"));

        assertThat(content.toString())
                .doesNotContain("Built")
                .doesNotContain("mustafatetik.com")
                .isEqualTo("RichContent[runs=2, chars=22]");
        assertThat(content.runs().get(1).toString())
                .doesNotContain("mustafatetik.com")
                .isEqualTo("Run[chars=16, marks=[link]]");
    }

    // ─── structural invariants ───

    @Test
    void runsAreDefensivelyCopied() {
        var mutable = new ArrayList<Run>();
        mutable.add(Run.of("first"));
        var content = new RichContent(mutable);

        mutable.add(Run.of(" second"));

        assertThat(content.plainText()).isEqualTo("first");
        assertThat(content.runs()).isUnmodifiable();
    }

    @Test
    void nullRunListBecomesEmpty() {
        assertThat(new RichContent(null).runs()).isEmpty();
        assertThat(new Run("text", null, null).marks()).isEmpty();
    }

    @Test
    void aLinkRunNeedsAnHref() {
        assertThatThrownBy(() -> new Run("mustafatetik.com", List.of(Mark.LINK), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyALinkRunMayCarryAnHref() {
        assertThatThrownBy(() -> new Run("ETL", List.of(Mark.TECHNOLOGY), "https://example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marksAreOpenEndedButKnowTheirOwnVocabulary() {
        assertThat(Mark.METRIC.isKnown()).isTrue();
        assertThat(new Mark("sarcasm").isKnown()).isFalse();
        assertThatThrownBy(() -> new Mark(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
