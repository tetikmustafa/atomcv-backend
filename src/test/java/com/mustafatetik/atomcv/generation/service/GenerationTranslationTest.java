package com.mustafatetik.atomcv.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.generation.scoring.ScoredAtom;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.profile.service.VariantTranslationService;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The second step: what it spends, and what it answers.
 *
 * <p>Two things are worth a test here and they pull against each other. It has
 * to be <strong>bounded</strong> — this is the one place in a generation where
 * a cost could scale with the size of a profile rather than with the size of a
 * page — and it has to be <strong>all or nothing</strong>, because half a
 * translation is the mixed-language CV F-013 was opened about.
 */
class GenerationTranslationTest {

    private static final UUID USER = UUID.randomUUID();

    private VariantTranslationService translations;
    private GenerationTranslation translation;
    private ProfileRef profile;

    @BeforeEach
    void wireTheMocks() {
        translations = mock(VariantTranslationService.class);
        translation = new GenerationTranslation(translations);
        profile = ProfileRef.persistent(UserContext.of(USER), UUID.randomUUID(), USER);
    }

    /** The first step of the section, and the cheap one: nothing to do. */
    @Test
    void aprofileThatAlreadyHasEveryWordingCostsNothing() {
        ProfileTree tree = treeOf(2, "tr", "en");

        assertThat(carry(tree, "en")).isTrue();
        verify(translations, never())
                .translateInto(any(), any(), any(), anyString(), anyString(), any());
    }

    @Test
    void everyMissingWordingIsTranslatedAndTheAnswerIsYes() {
        ProfileTree tree = treeOf(3, "tr");
        answerWith(Result.ok(null));

        assertThat(carry(tree, "en")).isTrue();
        verify(translations, org.mockito.Mockito.times(3))
                .translateInto(any(), any(), any(), eq("en"), anyString(), any());
    }

    /**
     * <strong>One refusal decides the document.</strong> Two of the three were
     * translated and saved — that work is not thrown away, and the next
     * generation will find them — but this one comes out in the language the
     * profile was written in, every atom of it.
     */
    @Test
    void onefailureMeansTheWholeDocumentFallsBack() {
        ProfileTree tree = treeOf(3, "tr");
        var refused = new java.util.concurrent.atomic.AtomicInteger();
        when(translations.translateInto(any(), any(), any(), anyString(), anyString(), any()))
                .thenAnswer(call -> refused.getAndIncrement() == 1
                        ? Result.err(new PipelineError.TranslationRejected())
                        : Result.ok(null));

        assertThat(carry(tree, "en")).isFalse();
    }

    /**
     * <strong>Bounded, and this is the test that says by how much.</strong> A
     * profile with two hundred atoms would otherwise be two hundred calls for
     * one CV; the budget is more than a page holds, so what is dropped is
     * competing for room it was never going to get.
     */
    @Test
    void onlyTheHighestScoringAtomsInTheBudgetArePaidFor() {
        ProfileTree tree = treeOf(GenerationTranslation.TRANSLATION_BUDGET + 25, "tr");
        answerWith(Result.ok(null));

        assertThat(carry(tree, "en")).isTrue();
        verify(translations, org.mockito.Mockito.times(GenerationTranslation.TRANSLATION_BUDGET))
                .translateInto(any(), any(), any(), anyString(), anyString(), any());
    }

    /** Best first, because the budget is what makes the order matter. */
    @Test
    void therankingDecidesWhichWordingsAreBought() {
        ProfileTree tree = treeOf(3, "tr");
        List<ScoredAtom> ranked = rankedLowestFirst(tree);
        answerWith(Result.ok(null));

        translation.ensureWordingsIn(profile, tree, "en", ranked.subList(0, 1), "bucket", USER);

        var atom = ArgumentCaptor.forClass(Atom.class);
        verify(translations)
                .translateInto(any(), atom.capture(), any(), anyString(), anyString(), any());
        assertThat(atom.getValue().getId()).isEqualTo(ranked.get(0).atomId());
    }

    /**
     * <strong>An atom nobody ever wrote is not bought, and does not fail the
     * document either.</strong> There is nothing to translate from, so a call
     * would be a call that cannot succeed; and it is the same atom {@code
     * canBeWrittenIn} declines to ask, for the same reason — selection already
     * counts an atom with no wording, and a defect upstream should not decide
     * what language a CV comes out in.
     */
    @Test
    void anatomWithNoWordingAtAllIsNotBoughtAndDoesNotFailTheDocument() {
        ProfileTree tree = withOneEmptyAtom();
        answerWith(Result.ok(null));

        assertThat(carry(tree, "en")).isTrue();
        verify(translations, org.mockito.Mockito.times(1))
                .translateInto(any(), any(), any(), anyString(), anyString(), any());
    }

    /** An atom that was scored and is not in the tree is not a purchase. */
    @Test
    void ascoreForAnatomTheTreeDoesNotHaveIsIgnored() {
        ProfileTree tree = treeOf(1, "tr");
        List<ScoredAtom> ranked = rankedLowestFirst(tree);
        ranked.add(new ScoredAtom(UUID.randomUUID(), 0.9, 0.9,
                new ScoredAtom.Components(0.9, 0.9, 0.9, 0.9)));
        answerWith(Result.ok(null));

        assertThat(translation.ensureWordingsIn(profile, tree, "en", ranked, "bucket", USER))
                .isTrue();
        verify(translations, org.mockito.Mockito.times(1))
                .translateInto(any(), any(), any(), anyString(), anyString(), any());
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private boolean carry(ProfileTree tree, String language) {
        return translation.ensureWordingsIn(
                profile, tree, language, rankedLowestFirst(tree), "bucket", USER);
    }

    private void answerWith(Result<AtomVariant> answer) {
        when(translations.translateInto(any(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(answer);
    }

    /**
     * Deliberately not the tree's own order: the budget is only meaningful if
     * the ranking is what decides, so the fixture makes the two disagree.
     */
    private static List<ScoredAtom> rankedLowestFirst(ProfileTree tree) {
        var ranked = new ArrayList<ScoredAtom>();
        tree.sections().forEach(section -> section.atoms().forEach(node ->
                ranked.add(new ScoredAtom(node.atom().getId(), 0.5, 0.5,
                        new ScoredAtom.Components(0.5, 0.5, 0.5, 0.5)))));
        java.util.Collections.reverse(ranked);
        return ranked;
    }

    /** One atom with a Turkish wording, one with none at all. */
    private ProfileTree withOneEmptyAtom() {
        UUID id = profile.id();
        var section = new Section(id, SectionKind.EXPERIENCE, "Experience", (short) 0);
        var written = new Atom(id, section.getId(), null, AtomKind.BULLET, (short) 0);
        var empty = new Atom(id, section.getId(), null, AtomKind.BULLET, (short) 1);
        var wording = new AtomVariant(id, written.getId(), "tr", RichContent.plain("tr wording"));
        wording.setPrimary(true);

        return ProfileAssembler.assemble(id, List.of(section), List.of(),
                List.of(written, empty), List.of(wording));
    }

    private ProfileTree treeOf(int atoms, String... languages) {
        UUID id = profile.id();
        var section = new Section(id, SectionKind.EXPERIENCE, "Experience", (short) 0);
        var all = new ArrayList<Atom>();
        var variants = new ArrayList<AtomVariant>();
        for (int index = 0; index < atoms; index++) {
            var atom = new Atom(id, section.getId(), null, AtomKind.BULLET, (short) index);
            all.add(atom);
            for (String language : languages) {
                var variant = new AtomVariant(id, atom.getId(), language,
                        RichContent.plain(language + " wording " + index));
                variant.setPrimary(languages[0].equals(language));
                variants.add(variant);
            }
        }
        return ProfileAssembler.assemble(id, List.of(section), List.of(), all, variants);
    }
}
