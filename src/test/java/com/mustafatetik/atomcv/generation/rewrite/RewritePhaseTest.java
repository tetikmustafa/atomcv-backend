package com.mustafatetik.atomcv.generation.rewrite;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.generation.selection.SelectionState;
import com.mustafatetik.atomcv.generation.selection.SelectionState.SelectedAtom;
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
import com.mustafatetik.atomcv.profile.domain.Tone;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Bolum 21.5 — eight bullets at once, and every way that can go wrong.
 *
 * <p>The phase is the one part of Faz D that has to answer with a CV whatever
 * happens to it. The cases below are the four things that do happen: a
 * provider that is slow, one bullet that throws, a second attempt at the same
 * generation, and a posting with nothing to write towards.
 */
class RewritePhaseTest {

    private static final UUID PROFILE = UUID.randomUUID();

    /**
     * <strong>The point of the phase.</strong> Eight calls that each take a
     * tenth of a second are eight tenths of a second in a loop and one tenth
     * on virtual threads — and this is a generation somebody is watching a
     * progress bar for.
     */
    @Test
    void eightBulletsAreRewrittenAtTheSameTimeAndNotOneAfterTheOther() {
        var arrivals = new CountDownLatch(8);
        var phase = phaseOf(new StubRewriter(candidate -> {
            arrivals.countDown();
            try {
                // Every task has to be inside the phase at once for this to
                // return; a sequential implementation deadlocks here and the
                // test fails on the assertion below rather than hanging.
                if (!arrivals.await(5, TimeUnit.SECONDS)) {
                    return null;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return null;
            }
            return RichContent.plain("rewritten " + candidate.atomId());
        }));

        var fixture = strongMatches(8);
        var rewritten = phase.rewrite(
                fixture.tree(), fixture.selection(), context(), RewrittenContent.none());

        assertThat(arrivals.getCount()).isZero();
        assertThat(rewritten.byAtom()).hasSize(8);
    }

    /**
     * One bullet failing costs that bullet its rewrite and nothing else.
     * Bolum 21.5's scope in the spec cancels its siblings on a failure, which
     * would throw away seven answers that were already paid for.
     */
    @Test
    void abulletThatThrowsDoesNotTakeTheOthersWithIt() {
        var fixture = strongMatches(4);
        UUID doomed = fixture.selected.get(0).atomId();
        var phase = phaseOf(new StubRewriter(candidate -> {
            if (candidate.atomId().equals(doomed)) {
                throw new IllegalStateException("the provider fell over");
            }
            return RichContent.plain("rewritten " + candidate.atomId());
        }));

        var rewritten = phase.rewrite(
                fixture.tree(), fixture.selection(), context(), RewrittenContent.none());

        assertThat(rewritten.byAtom()).hasSize(3);
        assertThat(rewritten.covers(doomed)).isFalse();
    }

    /**
     * <strong>Bolum 21.6's answer, seen from the phase.</strong> A rewrite
     * that was refused comes back as the original, and an atom printed from
     * the profile does not need an entry saying so.
     */
    @Test
    void abulletThatKeptItsOriginalIsNotRecordedAsRewritten() {
        var fixture = strongMatches(2);
        var phase = phaseOf(new StubRewriter(RewriteCandidate::original));

        var rewritten = phase.rewrite(
                fixture.tree(), fixture.selection(), context(), RewrittenContent.none());

        assertThat(rewritten.isEmpty()).isTrue();
    }

    /**
     * <strong>The compile loop must not pay twice.</strong> A document that
     * came out too long selects again from a smaller budget, and the atoms
     * that survive are the ones already rewritten.
     */
    @Test
    void asecondPassDoesNotAskAgainForWhatIsAlreadyRewritten() {
        var fixture = strongMatches(3);
        var stub = new StubRewriter(candidate -> RichContent.plain("rewritten"));
        var phase = phaseOf(stub);

        var first = phase.rewrite(
                fixture.tree(), fixture.selection(), context(), RewrittenContent.none());
        var second = phase.rewrite(fixture.tree(), fixture.selection(), context(), first);

        assertThat(stub.calls.get()).isEqualTo(3);
        assertThat(second.byAtom()).hasSize(3);
    }

    /**
     * <strong>A posting that named no skills does not get a rewrite.</strong>
     * Not for cost: Bolum 21.6's unsupported-claim check is measured against
     * the posting's vocabulary, so with an empty one a rewrite could name any
     * technology at all and pass every check. The guard is an honesty guard.
     */
    @Test
    void apostingWithNoSkillsIsNotWrittenTowardsAtAll() {
        var fixture = strongMatches(3);
        var stub = new StubRewriter(candidate -> RichContent.plain("rewritten"));
        var phase = phaseOf(stub);

        var rewritten = phase.rewrite(fixture.tree(), fixture.selection(),
                new RewriteContext(List.of(), List.of(), "", "en",
                        Tone.FORMAL.wireValue(), "bucket"),
                RewrittenContent.none());

        assertThat(stub.calls.get()).isZero();
        assertThat(rewritten.isEmpty()).isTrue();
    }

    /** Nothing worth rewriting is not a failure, and costs nothing. */
    @Test
    void aselectionWithNoCandidatesCostsNothing() {
        var fixture = weakMatches(3);
        var stub = new StubRewriter(candidate -> RichContent.plain("rewritten"));

        var rewritten = phaseOf(stub).rewrite(
                fixture.tree(), fixture.selection(), context(), RewrittenContent.none());

        assertThat(stub.calls.get()).isZero();
        assertThat(rewritten.isEmpty()).isTrue();
    }

    /**
     * <strong>Bolum 21.7 travels in the same fan-out.</strong> The summary is
     * given the whole page, so it is the slowest task here; running it after
     * the bullets would add its latency to theirs for nothing.
     */
    @Test
    void theaboutIsWrittenAlongsideTheBulletsAndNotAfterThem() {
        var fixture = strongMatches(3);
        var stub = new StubRewriter(candidate -> RichContent.plain("rewritten"));
        var about = new RecordingAbout(RichContent.plain("A synthesised summary."));
        var phase = new RewritePhase(stub, about);

        var rewritten = phase.rewrite(
                fixture.treeWithAbout(), fixture.selectionWithAbout(), context(),
                RewrittenContent.none());

        assertThat(about.calls.get()).isEqualTo(1);
        assertThat(rewritten.byAtom()).hasSize(4);
        assertThat(rewritten.orOriginal(fixture.aboutId(), RichContent.EMPTY).plainText())
                .isEqualTo("A synthesised summary.");
    }

    /** And it is not asked for twice when the compile loop goes round again. */
    @Test
    void theaboutIsNotSynthesisedAgainOnASecondPass() {
        var fixture = strongMatches(1);
        var stub = new StubRewriter(candidate -> RichContent.plain("rewritten"));
        var about = new RecordingAbout(RichContent.plain("A synthesised summary."));
        var phase = new RewritePhase(stub, about);

        var first = phase.rewrite(fixture.treeWithAbout(), fixture.selectionWithAbout(),
                context(), RewrittenContent.none());
        phase.rewrite(fixture.treeWithAbout(), fixture.selectionWithAbout(), context(), first);

        assertThat(about.calls.get()).isEqualTo(1);
    }

    // -- fixtures ----------------------------------------------------------

    /**
     * The phase with its About half stubbed out to answer with the original.
     * Bolum 21.7 has its own tests; here it would be a ninth task nothing in
     * the fixture has a paragraph for.
     */
    private static RewritePhase phaseOf(BulletRewriteService rewriter) {
        return new RewritePhase(rewriter, new StubAbout());
    }

    private static RewriteContext context() {
        return new RewriteContext(List.of("java", "postgres"), List.of(), "",
                "en", Tone.FORMAL.wireValue(), "bucket");
    }

    /** Atoms that score well above Bolum 21.2's ceiling, so all are candidates. */
    private static Fixture strongMatches(int count) {
        return fixtureOf(count, 0.90);
    }

    /** Atoms below Bolum 21.2's floor: printed, never sent. */
    private static Fixture weakMatches(int count) {
        return fixtureOf(count, 0.10);
    }

    private static Fixture fixtureOf(int count, double score) {
        var nodes = new ArrayList<AtomNode>();
        var selected = new ArrayList<SelectedAtom>();
        for (int i = 0; i < count; i++) {
            Atom row = new Atom(PROFILE, UUID.randomUUID(), UUID.randomUUID(),
                    AtomKind.BULLET, (short) i);
            row.setSkills(List.of("java"));
            var wording = new AtomVariant(PROFILE, row.getId(), "en",
                    RichContent.plain("Built the ingest path number " + i));
            wording.setPrimary(true);
            nodes.add(new AtomNode(row, List.of(wording)));
            selected.add(new SelectedAtom(row.getId(), wording.getId(), score, 12.0, false));
        }
        return new Fixture(nodes, selected);
    }

    private record Fixture(List<AtomNode> nodes, List<SelectedAtom> selected, AtomNode about) {

        Fixture(List<AtomNode> nodes, List<SelectedAtom> selected) {
            this(nodes, selected, aboutNode());
        }

        ProfileTree tree() {
            return new ProfileTree(PROFILE, List.of(experience()));
        }

        /** The same profile, with an About section the person actually has. */
        ProfileTree treeWithAbout() {
            Section section = new Section(PROFILE, SectionKind.ABOUT, "About", (short) 1);
            return new ProfileTree(PROFILE, List.of(experience(),
                    new SectionNode(section, List.of(), List.of(about))));
        }

        UUID aboutId() {
            return about.atom().getId();
        }

        SelectionState selection() {
            return new SelectionState(selected, List.of(),
                    new SelectionState.BudgetBreakdown(600, 100, 500, 300));
        }

        SelectionState selectionWithAbout() {
            var withAbout = new ArrayList<>(selected);
            withAbout.add(new SelectedAtom(about.atom().getId(),
                    about.variants().get(0).getId(), 0.5, 12.0, false));
            return new SelectionState(withAbout, List.of(),
                    new SelectionState.BudgetBreakdown(600, 100, 500, 300));
        }

        private SectionNode experience() {
            Section section =
                    new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
            Entry entry = new Entry(PROFILE, section.getId(), "Data Engineer", (short) 0);
            return new SectionNode(section, List.of(new EntryNode(entry, nodes)), List.of());
        }

        private static AtomNode aboutNode() {
            Atom row = new Atom(PROFILE, UUID.randomUUID(), null,
                    AtomKind.ABOUT_PARAGRAPH, (short) 0);
            var wording = new AtomVariant(PROFILE, row.getId(), "en",
                    RichContent.plain("Backend engineer, mostly on data platforms."));
            wording.setPrimary(true);
            return new AtomNode(row, List.of(wording));
        }
    }

    /** An About service that keeps the person's paragraph, whatever it is. */
    private static final class StubAbout extends AboutSynthesisService {

        StubAbout() {
            super(null, null);
        }

        @Override
        public RichContent synthesise(AboutCandidate candidate, RewriteContext context) {
            return candidate.original();
        }
    }

    /** One that answers, and counts how often it was asked. */
    private static final class RecordingAbout extends AboutSynthesisService {

        private final RichContent answer;
        private final AtomicInteger calls = new AtomicInteger();

        RecordingAbout(RichContent answer) {
            super(null, null);
            this.answer = answer;
        }

        @Override
        public RichContent synthesise(AboutCandidate candidate, RewriteContext context) {
            calls.incrementAndGet();
            return answer;
        }
    }

    /**
     * A {@link BulletRewriteService} that answers from a function instead of
     * from a provider. Subclassed rather than mocked because the phase's whole
     * job is what it does with several answers at once, and that is easier to
     * say with a latch than with stubbing.
     */
    private static final class StubRewriter extends BulletRewriteService {

        private final java.util.function.Function<RewriteCandidate, RichContent> answer;
        private final AtomicInteger calls = new AtomicInteger();

        StubRewriter(java.util.function.Function<RewriteCandidate, RichContent> answer) {
            super(null, null, null);
            this.answer = answer;
        }

        @Override
        public RichContent rewrite(RewriteCandidate candidate, RewriteContext context) {
            calls.incrementAndGet();
            RichContent rewritten = answer.apply(candidate);
            return rewritten == null ? candidate.original() : rewritten;
        }
    }
}
