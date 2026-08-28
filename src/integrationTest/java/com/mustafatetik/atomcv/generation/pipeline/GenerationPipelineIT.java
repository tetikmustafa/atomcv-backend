package com.mustafatetik.atomcv.generation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.compilation.CompilationProperties;
import com.mustafatetik.atomcv.compilation.LatexCompilerClient;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.AtomCandidate;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.EntryPlan;
import com.mustafatetik.atomcv.generation.selection.SelectionRequest.SectionPlan;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Contact;
import com.mustafatetik.atomcv.profile.domain.Entry;
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
import com.mustafatetik.atomcv.shared.error.PipelineError;
import com.mustafatetik.atomcv.shared.error.Result;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A profile in, a PDF out, through the real compiler (Bolum 20-23).
 *
 * <p>This is the walking skeleton's end: everything between a stored profile
 * and a document exists and is joined up. The unit test next to it proves the
 * feedback loop's arithmetic; what this one proves is that the page count the
 * loop reacts to is a real page count, produced by the compiler that will
 * produce the user's.
 */
@Tag("latex")
@Testcontainers
class GenerationPipelineIT {

    private static final UUID PROFILE = UUID.randomUUID();
    private static final CapacityModel CAPACITY =
            TemplateRegistry.capacityOf(TemplateCustomization.CLASSIC).orElseThrow();

    @Container
    static final GenericContainer<?> LATEX = new GenericContainer<>(
            new ImageFromDockerfile("atomcv-latex-test", false)
                    .withFileFromPath(".", Path.of("docker/latex")))
            .withExposedPorts(8090)
            .withStartupTimeout(Duration.ofMinutes(5));

    private GenerationPipeline pipeline() {
        var client = new LatexCompilerClient(new CompilationProperties(
                "http://" + LATEX.getHost() + ":" + LATEX.getMappedPort(8090),
                Duration.ofSeconds(60)));
        return new GenerationPipeline(
                new LatexDocumentRenderer(), client, new SimpleMeterRegistry());
    }

    @Test
    void aProfileBecomesAOnePagePdf() {
        var fixture = careerOf(3, 4, 25.0);

        var document = pipeline().run(fixture.profile, fixture.tree, fixture.request,
                ContentRewriter.none(), TemplateCustomization.CLASSIC, Locale.ENGLISH).orElseThrow();

        assertThat(document.pageCount()).isEqualTo(1);
        assertThat(document.attempts()).isEqualTo(1);
        assertThat(document.selection().selected()).hasSize(12);
        assertThat(new String(document.pdf(), 0, 5, StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF-");
    }

    /**
     * The guard, against a deliberate violation: every atom claims to cost a
     * fifth of what it does, so selection believes a three-page CV fits. The
     * compiler disagrees, and the answer is a refusal rather than a CV that
     * quietly breaks the promise (design principle 4).
     */
    @Test
    void aPageLimitThatMeasurementGotWrongIsCaughtByTheCompiler() {
        var fixture = careerOf(6, 10, 5.0);

        var result = pipeline().run(fixture.profile, fixture.tree, fixture.request,
                ContentRewriter.none(), TemplateCustomization.CLASSIC, Locale.ENGLISH);

        assertThat(result.isErr()).isTrue();
        var error = (PipelineError.PageLimitExceeded)
                ((Result.Err<GeneratedDocument>) result).error();
        assertThat(error.actualPages()).isGreaterThan(1);
        assertThat(error.maxPages()).isEqualTo(1);
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private record Fixture(Profile profile, ProfileTree tree, SelectionRequest request) {
    }

    /** A career of the given shape, with bullets that read like real ones. */
    private static Fixture careerOf(int entryCount, int bulletsPerEntry, double declaredCostPt) {
        var section = new Section(PROFILE, SectionKind.EXPERIENCE, "Experience", (short) 0);
        var entries = new ArrayList<Entry>();
        var atoms = new ArrayList<Atom>();
        var variants = new ArrayList<AtomVariant>();
        var plans = new ArrayList<EntryPlan>();

        for (int entryIndex = 0; entryIndex < entryCount; entryIndex++) {
            var entry = new Entry(PROFILE, section.getId(),
                    "Backend Engineer", (short) entryIndex);
            entry.setOrganization("Company " + entryIndex);
            entry.setLocation("İstanbul");
            entry.setStartDate(LocalDate.of(2018 + entryIndex, 3, 1));
            entry.setEndDate(LocalDate.of(2019 + entryIndex, 6, 1));
            entries.add(entry);

            var candidates = new ArrayList<AtomCandidate>();
            for (int bullet = 0; bullet < bulletsPerEntry; bullet++) {
                var atom = new Atom(PROFILE, section.getId(), entry.getId(),
                        AtomKind.BULLET, (short) bullet);
                var variant = new AtomVariant(PROFILE, atom.getId(), "en",
                        RichContent.plain("Built ETL pipelines processing 300K+ rows a day and "
                                + "cut the nightly window from six hours to fifty minutes ("
                                + entryIndex + "." + bullet + ")"));
                variant.setPrimary(true);
                atoms.add(atom);
                variants.add(variant);
                candidates.add(new AtomCandidate(atom.getId(), variant.getId(), entry.getId(),
                        0.9 - bullet * 0.01, declaredCostPt, false, true));
            }
            plans.add(new EntryPlan(entry.getId(), (short) 2, candidates));
        }

        var profile = new Profile(UUID.randomUUID());
        profile.setHeadline("Backend Engineer");
        profile.setContact(new Contact("Mustafa Tetik", "mustafa@example.com", null,
                null, null, null, "İstanbul"));

        return new Fixture(profile,
                ProfileAssembler.assemble(PROFILE, List.of(section), entries, atoms, variants),
                new SelectionRequest(
                        List.of(new SectionPlan(section.getId(), false, plans, List.of())),
                        1, CAPACITY));
    }
}
