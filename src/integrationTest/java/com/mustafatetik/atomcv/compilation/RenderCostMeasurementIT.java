package com.mustafatetik.atomcv.compilation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mustafatetik.atomcv.AbstractIntegrationTest;
import com.mustafatetik.atomcv.profile.domain.Atom;
import com.mustafatetik.atomcv.profile.domain.AtomKind;
import com.mustafatetik.atomcv.profile.domain.AtomVariant;
import com.mustafatetik.atomcv.profile.domain.Section;
import com.mustafatetik.atomcv.profile.domain.SectionKind;
import com.mustafatetik.atomcv.profile.domain.content.Mark;
import com.mustafatetik.atomcv.profile.domain.content.RichContent;
import com.mustafatetik.atomcv.profile.domain.content.Run;
import com.mustafatetik.atomcv.profile.repository.AtomVariantRepository;
import com.mustafatetik.atomcv.rendering.measurement.RenderCostService;
import com.mustafatetik.atomcv.rendering.template.TemplateCustomization;
import com.mustafatetik.atomcv.shared.security.ProfileRef;
import com.mustafatetik.atomcv.shared.security.UserContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * Profile content to stored render costs, through the real compiler
 * (Bolum 26.2).
 *
 * <p>Everything before this could be true in isolation and still not add up:
 * the renderer producing plausible LaTeX, the parser reading plausible logs,
 * the entity holding plausible numbers. This is the run that puts a real
 * sentence through TeX and finds a real point value in the database.
 */
@Tag("latex")
class RenderCostMeasurementIT extends AbstractIntegrationTest {

    static final GenericContainer<?> LATEX = new GenericContainer<>(
            new ImageFromDockerfile("atomcv-latex-test", false)
                    .withFileFromPath(".", Path.of("docker/latex")))
            .withExposedPorts(8090)
            .withStartupTimeout(Duration.ofMinutes(5));

    static {
        LATEX.start();
    }

    @DynamicPropertySource
    static void latexAddress(DynamicPropertyRegistry registry) {
        registry.add("atomcv.latex.base-url",
                () -> "http://" + LATEX.getHost() + ":" + LATEX.getMappedPort(8090));
    }

    @Autowired
    private RenderCostService renderCosts;

    @Autowired
    private AtomVariantRepository variants;

    @Autowired
    private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private ProfileRef profile;

    @BeforeEach
    void createProfile() {
        UUID userId = jdbc.queryForObject("INSERT INTO users (email) VALUES (?) RETURNING id",
                UUID.class, UUID.randomUUID() + "@example.com");
        UUID profileId = jdbc.queryForObject(
                "INSERT INTO profiles (user_id) VALUES (?) RETURNING id", UUID.class, userId);
        profile = ProfileRef.persistent(UserContext.of(userId), profileId, userId);
    }

    @Test
    void aWordingEndsUpWithAMeasuredCostInPoints() {
        UUID variantId = seedVariant(RichContent.of(
                Run.of("Built "),
                Run.of("ETL", Mark.TECHNOLOGY),
                Run.of(" pipelines processing "),
                Run.of("300K+ rows", Mark.METRIC)));

        int measured = renderCosts.measureMissing(profile, TemplateCustomization.CLASSIC);

        assertThat(measured).isEqualTo(1);
        var stored = variants.findById(profile, variantId).orElseThrow();
        assertThat(stored.getRenderCosts()).containsKey("classic:v1");
        // One line of ten-point text with its baseline: tens of points, not
        // hundreds, and certainly not zero.
        assertThat(stored.getRenderCosts().get("classic:v1")).isBetween(15.0, 60.0);
        assertThat(stored.getCostMeasuredAt()).isNotNull();
    }

    @Test
    void alongerWordingCostsMoreThanAShortOne() {
        UUID shortOne = seedVariant(RichContent.plain("Built ETL pipelines"));
        UUID longOne = seedVariant(RichContent.plain(
                "Built ETL pipelines processing 300K+ rows a day into a secure lakehouse, "
                        + "replacing a nightly batch that took four hours and failed weekly, "
                        + "and documented the migration for the team that inherited it."));

        renderCosts.measureMissing(profile, TemplateCustomization.CLASSIC);

        double small = cost(shortOne);
        double large = cost(longOne);
        assertThat(large).as("three lines cost more than one").isGreaterThan(small);
    }

    @Test
    void measuringTwiceMeasuresNothingTheSecondTime() {
        seedVariant(RichContent.plain("Built ETL pipelines"));

        assertThat(renderCosts.measureMissing(profile, TemplateCustomization.CLASSIC)).isEqualTo(1);
        assertThat(renderCosts.measureMissing(profile, TemplateCustomization.CLASSIC)).isZero();
    }

    /** Bolum 26.5: new words, new size, so the old number has to go. */
    @Test
    void editingTheWordsThrowsAwayTheMeasurement() {
        UUID variantId = seedVariant(RichContent.plain("Built ETL pipelines"));
        renderCosts.measureMissing(profile, TemplateCustomization.CLASSIC);
        double before = cost(variantId);

        tx.executeWithoutResult(status -> {
            var variant = em.find(AtomVariant.class, variantId);
            variant.setContent(RichContent.plain(
                    "Built ETL pipelines processing 300K+ rows into a secure lakehouse"));
        });

        var reloaded = variants.findById(profile, variantId).orElseThrow();
        assertThat(reloaded.getRenderCosts()).as("a measurement of words that changed").isEmpty();
        assertThat(reloaded.getCostMeasuredAt()).isNull();

        assertThat(renderCosts.measureMissing(profile, TemplateCustomization.CLASSIC)).isEqualTo(1);
        assertThat(cost(variantId)).isGreaterThan(before);
    }

    /**
     * The estimator's one promise: it never charges less than TeX does
     * (Bolum 26.5, EK D.8.7).
     *
     * <p>An underestimate is a page limit broken quietly, which is the one
     * failure the whole measurement layer exists to prevent — so this is
     * checked against the compiler on content of every length, not reasoned
     * about.
     */
    @Test
    void theEstimateIsNeverBelowTheMeasurement() {
        var wordings = java.util.List.of(
                RichContent.plain("Go"),
                RichContent.plain("Built ETL pipelines"),
                RichContent.plain("Built ETL pipelines processing 300K+ rows a day"),
                RichContent.plain("Built ETL pipelines processing 300K+ rows a day into a "
                        + "secure lakehouse, replacing a nightly batch that took four hours"),
                RichContent.plain("Led the migration of a four-service monolith to a set of "
                        + "independently deployable services, cutting deploy time from fifty "
                        + "minutes to four and taking the team's on-call load with it, while "
                        + "keeping every published API stable for the six months it took."),
                RichContent.of(Run.of("Built "), Run.of("ETL", Mark.TECHNOLOGY),
                        Run.of(" pipelines processing "), Run.of("300K+ rows", Mark.METRIC)));

        var ids = new java.util.ArrayList<UUID>();
        wordings.forEach(content -> ids.add(seedVariant(content)));
        renderCosts.measureMissing(profile, TemplateCustomization.CLASSIC);

        var capacity = com.mustafatetik.atomcv.rendering.template.TemplateRegistry
                .capacityOf(TemplateCustomization.CLASSIC).orElseThrow();
        for (int index = 0; index < wordings.size(); index++) {
            double estimated = com.mustafatetik.atomcv.rendering.measurement.RenderCostEstimator
                    .estimateBulletPt(wordings.get(index), TemplateCustomization.CLASSIC, capacity);
            assertThat(estimated)
                    .as("wording %d of %d characters", index,
                            wordings.get(index).plainText().length())
                    .isGreaterThanOrEqualTo(cost(ids.get(index)));
        }
    }

    private double cost(UUID variantId) {
        return variants.findById(profile, variantId).orElseThrow()
                .getRenderCosts().get("classic:v1");
    }

    private UUID seedVariant(RichContent content) {
        return tx.execute(status -> {
            var section = new Section(profile.id(), SectionKind.EXPERIENCE, "Experience", (short) 0);
            em.persist(section);
            var atom = new Atom(profile.id(), section.getId(), null, AtomKind.BULLET, (short) 0);
            em.persist(atom);
            var variant = new AtomVariant(profile.id(), atom.getId(), "en", content);
            variant.setPrimary(true);
            em.persist(variant);
            return variant.getId();
        });
    }
}
