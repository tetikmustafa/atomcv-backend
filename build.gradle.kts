import java.time.Duration

plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    // Bolum 47.1 runs `spotlessCheck` and nothing configured a formatter, so
    // there was no format gate at all. Deliberately narrow -- see the
    // `spotless` block below.
    id("com.diffplug.spotless") version "8.10.2"
}

group = "com.mustafatetik"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Libraries Spring Boot's BOM pins one patch behind a fix, and the image scan
// on the Deploy workflow is where that shows up: Trivy fails the job on HIGH,
// so main goes red on every push until the override lands. Overriding the BOM's
// property is the documented way to take a security patch before the next Boot
// release carries it — and it is a property rather than a dependency line
// because all three arrive transitively: netty through the mail and Redis
// clients, pgjdbc through the driver, Tomcat through spring-boot-starter-web.
//
// **Each goes when Boot's BOM catches up.** An override that outlives its
// reason is a pin that quietly holds a library back, which is the same failure
// in the other direction.
//
// Checked 2026-09-09: none of the three may go yet. Boot 3.5.16 is the newest
// release on the 3.5 line and its BOM still pins postgresql 42.7.11, netty
// 4.1.135.Final and tomcat 10.1.55 — every one of them below the fix.
//
// The same check, run against GitHub's advisory database rather than by eye,
// moved netty on and left pgjdbc alone — and the two answers came out opposite
// to what the release lists suggested, because the newest release and the
// newest fix are different questions.
//
// pgjdbc's newest advisory is patched at exactly 42.7.12, which is where this
// already is; 42.7.13 answers nothing, so it is not taken. Netty went to
// 4.1.137.Final for CVE-2026-59903 — with one honest qualification, measured in
// the image rather than assumed: the advisory is against `netty-codec-http`,
// and this image does not carry it. `docker run` on the built image lists seven
// netty artifacts (buffer, codec, common, handler, resolver, transport,
// transport-native-unix-common) and the HTTP codec is not one of them, because
// netty is here as the transport under Lettuce and the mail client while Tomcat
// serves the HTTP. So that bump is keeping the line current, not closing an
// exposure. The override itself exists for CVE-2026-59901, which is against
// `netty-codec` — and that one does ship.
//
// **Nothing here is watched automatically, and Deploy is hand-run.** Dependabot
// cannot see a Gradle `extra` property, which is why it has bumped five declared
// dependencies in this repository and never these three, and Trivy only runs on
// a workflow nobody triggers until there is a VPS. So `SecurityPatchFloorTest`
// asserts the three versions on the runtime classpath instead: a floor rather
// than an equality, so deleting an override once the BOM catches up keeps it
// green, and a mistyped property name fails it. Newly published advisories are
// still a hand check — the test holds the ground already won.
extra["postgresql.version"] = "42.7.12"   // CVE-2026-54291, SCRAM downgrade
extra["netty.version"] = "4.1.137.Final"  // CVE-2026-59901, -59903
// Three CRITICALs at once, all published after 2026-09-02 — the scan was clean
// on the push that added the two above and failed on the next one without
// anything in this repository having changed. A security constraint bypass, an
// authentication bypass and an unauthorized-access hole, which is the whole
// front door of a servlet container.
//
// 10.1.59 and not the 10.1.58 the advisory names: that one was never published
// to Maven Central -- the 10.1 line goes 10.1.57, 10.1.59 -- and 10.1.59 is the
// first release on this line that carries the fix. Staying on 10.1.x rather
// than the 11.0.25 also listed is deliberate; 11 is the Servlet 6.1 line and
// Boot 3 is built against 6.0.
extra["tomcat.version"] = "10.1.59"       // CVE-2026-65182, -65905, -68525

// Integration tests live in their own source set so that `gradlew test` stays
// fast and free of Docker. CI runs `test` and `integrationTest` as separate
// steps (Bolum 47.1).
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
        // And the unit lane's output, for the one thing both lanes share:
        // Bolum 52.6's budget reader. The alternative was a second copy of it,
        // and two readers of one file drift apart exactly when the file
        // changes -- which is the moment the budget is supposed to be read.
        compileClasspath += sourceSets.test.get().output
        runtimeClasspath += sourceSets.test.get().output
    }
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Bolum 40.1 and EK D.6.6. Sessions are ours — Redis, our own store — and
    // this is here for the filter chain and for the double-submit CSRF filter
    // EK D.6.6 names. Spring Session is deliberately not used: the sliding TTL
    // and the anonymous-to-account handover of Adim 3.6 both need the store.
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Bolum 40.2's magic link has to leave the building. Resend is the
    // production sender and speaks HTTP, but local development sends to
    // Mailpit over SMTP -- which is the point of having Mailpit in compose:
    // the email is read as a person would read it, not as a log line.
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // Bolum 44.3 wants the counters somewhere an operator can see them, and
    // Bolum 2's table picked Axiom — observability data should not live on the
    // machine being observed. OTLP is the wire format Axiom ingests, so this
    // is the whole integration: the exporter is inert until a URL is set, and
    // the dataset it points at is created in Adim 3.1.
    implementation("io.micrometer:micrometer-registry-otlp")
    // Bolum 18.6 caches the job analysis. Lettuce underneath, which the
    // starter brings: the cache is consulted on the hot path and a blocking
    // client there would hold a request thread through a network round trip.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // Bolum 28: atoms.embedding is vector(1024) and Hibernate has no type for
    // it on its own. This module adds SqlTypes.VECTOR with a pgvector dialect
    // contribution, so the column is mapped rather than read through a
    // hand-written converter that ddl-auto=validate could not check.
    // Version pinned to hibernate-core's: Spring Boot's BOM manages the
    // core but not this module, so an unpinned coordinate does not resolve.
    implementation("org.hibernate.orm:hibernate-vector:6.6.56.Final")
    // JSON merge-patch needs three states — absent, null, value — and Java has
    // no tri-state Optional: Jackson deserializes an absent Optional field as
    // Optional.empty(), the same as an explicit null. This library owns that
    // distinction. Springdoc does not render it as a nullable field on its own:
    // each field needs @Schema(implementation = ..., types = {..., "null"}) or
    // the wrapper leaks into the published schema. OpenApiSchemaIT holds it.
    implementation("org.openapitools:jackson-databind-nullable:0.2.11")

    // The published schema is the API contract: the frontend generates its
    // types from it, so enums and headers have to reach it, not only payloads.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.9.1")
    // Bolum 31.3. PDFBox reads the text out of a PDF and, unlike the
    // alternatives, executes nothing while doing it (Bolum 42.1) -- no
    // JavaScript, no embedded action. Versions pinned because Spring Boot's
    // BOM manages neither: an unpinned coordinate does not resolve.
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    // Likewise for DOCX, and for the same reason: POI's text API reads
    // document parts and never runs a macro.
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    // EK C.1 asks that errors reach somewhere a person looks. Axiom takes the
    // logs and the metrics; a stack trace with the request that produced it is
    // a different question and this answers it. Inert with no DSN, which is
    // every profile but prod -- so nothing is shipped from a developer's
    // machine. Absolute rule 4 still holds: send-default-pii stays off, so no
    // request body, no headers, no address reaches the vendor.
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.55.0")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // No redis module: a GenericContainer running redis:7-alpine is the same
    // image compose uses, and one fewer dependency to keep in step.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// javac still defaults to the platform charset, which is windows-1254 on a
// Turkish Windows machine and UTF-8 on the CI runner. Source files carry
// Turkish text, so leaving this unset would make the same file compile into
// two different string constants.
// A whitespace gate, not a formatter. A full reformatter (google-java-format,
// palantir) would rewrite every file in the repository in one commit and take
// `git blame` with it -- and the thing being bought is consistency the review
// already enforces. What is left is the class of diff nobody should have to
// mention in a review: a trailing space, a missing final newline, tabs, an
// import that no longer resolves to anything.
spotless {
    java {
        target("src/*/java/**/*.java")
        trimTrailingWhitespace()
        endWithNewline()
        indentWithSpaces(4)
        removeUnusedImports()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Bolum 52.6's budget file is read by tests and is not on any classpath,
    // so Gradle cannot see it. Without this a loosened budget leaves the task
    // UP-TO-DATE and the guard it governs never runs -- which is how "changing
    // a budget is a deliberate decision" quietly stops being one.
    inputs.file(rootProject.file("performance-budgets.yaml"))
        .withPropertyName("performanceBudgets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // Same reasoning for the same reason: EnvExampleTest is what keeps a knob
    // in the example wired to something, and a knob edited without the test
    // running is exactly how DAILY_BUDGET_USD came to point at nothing.
    inputs.file(rootProject.file(".env.example"))
        .withPropertyName("envExample")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// Deliberately not wired into `check`: integration tests need Docker, and
// `gradlew build` must stay runnable without it.
tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Runs integration tests against real infrastructure (Testcontainers)."
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
    // The LaTeX image is a couple of gigabytes and takes minutes to build.
    // Paying that on every run would push the suite from half a minute to
    // several, and the thing it guards changes rarely.
    useJUnitPlatform { excludeTags("latex", "llm-eval") }
}

// Bolum 53.4. Real calls to a real model, so it is not wired into anything --
// not `check`, not `integrationTest`, and deliberately not nightly: Bolum 53.7
// says production telemetry (`llm_invocations`) gives the same information for
// nothing. Run it when a prompt changes, which is about $0.30.
tasks.register<Test>("llmEval") {
    group = "verification"
    description = "Scores a prompt against Bolum 53.5's thresholds. COSTS MONEY; "
        .plus("needs a provider key and the local-record or local-real profile.")
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform { includeTags("llm-eval") }
    timeout.set(Duration.ofMinutes(30))
    // The suite prints its table through the report renderer, and a person
    // reading it is the point of running it at all.
    testLogging { showStandardStreams = true }
}

tasks.register<Test>("latexTest") {
    group = "verification"
    description = "Builds the LaTeX image and compiles through it. Slow; run it when "
        .plus("docker/latex changes.")
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform { includeTags("latex") }
    timeout.set(Duration.ofMinutes(20))
    // Lets `-Dgolden.record=true` reach the test JVM, which is how the golden
    // set's measured costs are re-recorded after a fixture changes.
    systemProperty("golden.record", System.getProperty("golden.record", "false"))
}
