package com.mustafatetik.atomcv.generation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mustafatetik.atomcv.compilation.CompilationException;
import com.mustafatetik.atomcv.compilation.CompiledDocument;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.generation.selection.SelectionPhase;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.AtomCandidate;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.SectionPlan;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Profile;
import com.mustafatetik.atomcv.profile.domain.ProfileTree;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.service.ProfileAssembler;
import com.mustafatetik.atomcv.rendering.latex.LatexDocumentRenderer;
import com.mustafatetik.atomcv.rendering.template.CapacityModel;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.rendering.template.TemplateRegistry;
import com.mustafatetik.atomcv.shared.error.CompilationFailureKind;
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Faz C to Faz F end to end, with the compiler stubbed (Bolum 23.1).
 *
 * <p>The renderer is real: what is under test is the feedback loop, and a
 * loop that fed a fake renderer would prove nothing about the document whose
 * length it is reacting to.
 */
class GenerationPipelineTest {

    private static final UUID PROFILE = UUID.randomUUID();
    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();
    private static final double BULLET_PT = 25.0;

    private final LatexCompilerClient compiler = mock(LatexCompilerClient.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final GenerationPipeline pipeline =
            new GenerationPipeline(new LatexDocumentRenderer(), compiler, meters);

    @Test
    void aDocumentThatFitsIsReturnedOnTheFirstAttempt() {
        var fixture = profileOf(40);
        when(compiler.compile(anyString())).thenReturn(pdf(1));

        var document = run(fixture).orElseThrow();

        assertThat(document.pageCount()).isEqualTo(1);
        assertThat(document.attempts()).isEqualTo(1);
        assertThat(document.budgetFactor()).isEqualTo(1.0);
        assertThat(document.selection().selected()).isNotEmpty();
        verify(compiler, times(1)).compile(anyString());
    }

    /** Bolum 23.1: a long document costs content, never a call to an LLM. */
    @Test
    void aDocumentThatRanLongIsSelectedAgainWithLessRoom() {
        var fixture = profileOf(40);
        when(compiler.compile(anyString())).thenReturn(pdf(2), pdf(1));

        var document = run(fixture).orElseThrow();

        int atFullBudget = SelectionPhase.select(fixture.request)
                .orElseThrow().selected().size();

        assertThat(document.attempts()).isEqualTo(2);
        assertThat(document.budgetFactor()).isEqualTo(GenerationPipeline.BUDGET_STEP);
        assertThat(document.selection().selected().size()).isLessThan(atFullBudget);
        assertThat(meters.counter("generation.budget.overshoot").count()).isEqualTo(1);
    }

    @Test
    void aDocumentThatStaysTooLongIsRefusedAfterTwoRetries() {
        var fixture = profileOf(40);
        when(compiler.compile(anyString())).thenReturn(pdf(3));

        var result = run(fixture);

        var error = (PipelineError.PageLimitExceeded) ((Result.Err<GeneratedDocument>) result)
                .error();
        assertThat(error.actualPages()).isEqualTo(3);
        assertThat(error.maxPages()).isEqualTo(1);
        verify(compiler, times(GenerationPipeline.MAX_RETRIES + 1)).compile(anyString());
    }

    @Test
    void aRefusedDocumentIsCarriedBackRatherThanThrown() {
        var fixture = profileOf(4);
        when(compiler.compile(anyString())).thenThrow(new CompilationException(
                CompilationFailureKind.INVALID_DOCUMENT, "no", "! Undefined control sequence.",
                null));

        var result = run(fixture);

        var error = (PipelineError.CompilationFailed) ((Result.Err<GeneratedDocument>) result)
                .error();
        assertThat(error.kind()).isEqualTo(CompilationFailureKind.INVALID_DOCUMENT);
        assertThat(error.texLog()).contains("Undefined control sequence");
    }

    /** Design principle 5: the checks that can fail run before anything is paid for. */
    @Test
    void contentThatCannotFitNeverReachesTheCompiler() {
        var fixture = profileOf(40, true);

        var result = run(fixture);

        assertThat(result.isErr()).isTrue();
        assertThat(((Result.Err<GeneratedDocument>) result).error())
                .isInstanceOf(PipelineError.ConflictingPreferences.class);
        verify(compiler, never()).compile(anyString());
    }

    @Test
    void theRenderedDocumentCarriesOnlyTheSelectedBullets() {
        var fixture = profileOf(40);
        when(compiler.compile(anyString())).thenReturn(pdf(1));

        var document = run(fixture).orElseThrow();

        assertThat(document.selection().selected().size()).isLessThan(40);
        assertThat(document.selection().rejected()).isNotEmpty();
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private Result<GeneratedDocument> run(Fixture fixture) {
        return pipeline.run(fixture.profile, fixture.tree, fixture.request,
                TemplateCustomization.CLASSIC, Locale.ENGLISH);
    }

    private record Fixture(Profile profile, ProfileTree tree, SelectionRequest request) {
    }

    private static Fixture profileOf(int atomCount) {
        return profileOf(atomCount, false);
    }

    /** One section of loose atoms — enough of them that the page has to choose. */
    private static Fixture profileOf(int atomCount, boolean allPinned) {
        var section = new Section(PROFILE, SectionKind.SKILLS, "Skills", (short) 0);
        var atoms = new ArrayList<Atom>();
        var variants = new ArrayList<AtomVariant>();
        var candidates = new ArrayList<AtomCandidate>();

        for (int index = 0; index < atomCount; index++) {
            var atom = new Atom(PROFILE, section.getId(), null, AtomKind.BULLET, (short) index);
            var variant = new AtomVariant(PROFILE, atom.getId(), "en",
                    RichContent.plain("Delivered outcome number " + index));
            variant.setPrimary(true);
            atoms.add(atom);
            variants.add(variant);
            candidates.add(new AtomCandidate(atom.getId(), variant.getId(), null,
                    0.5 + index * 0.01, BULLET_PT, allPinned, true));
        }

        var tree = ProfileAssembler.assemble(
                PROFILE, List.of(section), List.of(), atoms, variants);
        var request = new SelectionRequest(
                List.of(new SectionPlan(section.getId(), false, List.of(), candidates)),
                1, CAPACITY);
        return new Fixture(new Profile(UUID.randomUUID()), tree, request);
    }

    private static CompiledDocument pdf(int pages) {
        return new CompiledDocument("%PDF-1.7".getBytes(StandardCharsets.UTF_8), pages);
    }
}
