package com.mustafatetik.atomcv.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasParameterTypes.Predicates.rawParameterTypes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architectural rules from Bolum 51.4, plus the two that section leaves as
 * placeholders and defines elsewhere (Bolum 48.1 and 38.4).
 *
 * <p>These are deliberately in place before the packages have content:
 * retrofitting them later means cleaning up accumulated violations first.
 */
@AnalyzeClasses(
        packages = "com.mustafatetik.atomcv",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BUSINESS_MODULES =
            "..identity..|..profile..|..ingestion..|..generation..|..rendering..|..llm.."
                    + "|..embedding..|..compilation..|..jobs..|..tracking..|..billing..|..email..";

    @ArchTest
    static final ArchRule noCycles = slices()
            .matching("com.mustafatetik.atomcv.(*)..")
            .should().beFreeOfCycles();

    /** Bolum 10.2, rule 4: shared must not depend on any business module. */
    @ArchTest
    static final ArchRule sharedIsIndependent = noClasses()
            .that().resideInAPackage("..shared..")
            .should().dependOnClassesThat().resideInAnyPackage(BUSINESS_MODULES.split("\\|"));

    /**
     * The IDOR defense. Absolute rule 3 covers controllers <em>and</em>
     * services, so this is enforced across both, not only {@code ..api..} as
     * the snippet in Bolum 51.4 has it.
     */
    @ArchTest
    static final ArchRule noRawRepositoryInApiOrService = noClasses()
            .that().resideInAnyPackage("..api..", "..service..")
            .should().dependOnClassesThat().areAssignableTo(Repository.class);

    /**
     * The other half of the same defense. Ownership is checked by the scoped
     * bases in {@code shared.security}, so a Spring Data interface must not
     * escape the repository package that wraps it — not into the domain, not
     * into an assembler, not into a worker.
     *
     * <p>Stated per module rather than globally: a module gains this line when
     * it gains a repository, and the queue in Bolum 30 keeps its own package
     * layout.
     */
    @ArchTest
    static final ArchRule profileDataIsReachedThroughAScopedRepository = noClasses()
            .that().resideInAPackage("..profile..")
            .and().resideOutsideOfPackage("..profile.repository..")
            .should().dependOnClassesThat().areAssignableTo(Repository.class);

    /**
     * Absolute rule 6: rendering is deterministic by design, so it may never
     * reach for an LLM.
     *
     * <p>This carried {@code allowEmptyShould(true)} while the rendering
     * module was still empty, granted here alone rather than globally so that
     * every other rule would fail loudly if a package rename emptied it. Adim
     * 1.4 filled the module and the grant is gone: the rule now matches real
     * classes, and emptying it again is a failure rather than a pass.
     */
    @ArchTest
    static final ArchRule renderersAreDeterministic = noClasses()
            .that().resideInAPackage("..rendering..")
            .should().dependOnClassesThat().resideInAPackage("..llm..");

    /**
     * Bolum 48.1: user content is never logged; log {@code ContentShape}
     * instead.
     *
     * <p>Scope limit worth knowing: this catches any method named
     * debug/info/warn/error that <em>declares</em> a content parameter, which
     * covers custom log helpers. It cannot catch {@code log.info("{}", content)}
     * — that call binds to slf4j's {@code info(String, Object...)}, so the
     * content type is erased before bytecode analysis sees it. The structural
     * guard for that case is keeping content out of {@code toString()}, which
     * lands with RichContent in Stage 1.
     */
    @ArchTest
    static final ArchRule noContentInLogs = noClasses()
            .should().callMethodWhere(
                    target(nameMatching("debug|info|warn|error"))
                            .and(target(rawParameterTypes(describe(
                                    "any parameter assignable to RichContent",
                                    types -> types.stream().anyMatch(assignableTo(
                                            "com.mustafatetik.atomcv.profile.domain.content.RichContent")))))));

    /**
     * Bolum 38.4: the Turkish locale turns "TITLE" into "tıtle" and "instagram"
     * into "İNSTAGRAM", silently breaking skill matching. Case conversion must
     * always name a locale.
     */
    @ArchTest
    static final ArchRule noLocaleSensitiveCase = noClasses()
            .should().callMethod(String.class, "toLowerCase")
            .orShould().callMethod(String.class, "toUpperCase");
}
