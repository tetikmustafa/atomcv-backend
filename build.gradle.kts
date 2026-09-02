import java.time.Duration

plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    // Bolum 47.1 runs `spotlessCheck` and nothing configured a formatter, so
    // there was no format gate at all. Deliberately narrow -- see the
    // `spotless` block below.
    id("com.diffplug.spotless") version "7.0.2"
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

// Two libraries Spring Boot's BOM pins one patch behind a fix, and the image
// scan on the Deploy workflow is where that shows up: Trivy fails the job on
// HIGH, so main has been red on every push since these two were published.
// Overriding the BOM's property is the documented way to take a security patch
// before the next Boot release carries it — and it is a property rather than a
// dependency line because both arrive transitively, netty through the mail and
// Redis clients and pgjdbc through the driver.
//
// **Both go when Boot's BOM catches up.** An override that outlives its reason
// is a pin that quietly holds a library back, which is the same failure in the
// other direction.
extra["postgresql.version"] = "42.7.12"   // CVE-2026-54291, SCRAM downgrade
extra["netty.version"] = "4.1.136.Final"  // CVE-2026-59901, decoder loop

// Integration tests live in their own source set so that `gradlew test` stays
// fast and free of Docker. CI runs `test` and `integrationTest` as separate
// steps (Bolum 47.1).
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
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
    implementation("org.hibernate.orm:hibernate-vector:6.6.53.Final")
    // JSON merge-patch needs three states — absent, null, value — and Java has
    // no tri-state Optional: Jackson deserializes an absent Optional field as
    // Optional.empty(), the same as an explicit null. This library owns that
    // distinction. Springdoc does not render it as a nullable field on its own:
    // each field needs @Schema(implementation = ..., types = {..., "null"}) or
    // the wrapper leaks into the published schema. OpenApiSchemaIT holds it.
    implementation("org.openapitools:jackson-databind-nullable:0.2.6")

    // The published schema is the API contract: the frontend generates its
    // types from it, so enums and headers have to reach it, not only payloads.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")
    // Bolum 31.3. PDFBox reads the text out of a PDF and, unlike the
    // alternatives, executes nothing while doing it (Bolum 42.1) -- no
    // JavaScript, no embedded action. Versions pinned because Spring Boot's
    // BOM manages neither: an unpinned coordinate does not resolve.
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
    // Likewise for DOCX, and for the same reason: POI's text API reads
    // document parts and never runs a macro.
    implementation("org.apache.poi:poi-ooxml:5.4.1")
    // EK C.1 asks that errors reach somewhere a person looks. Axiom takes the
    // logs and the metrics; a stack trace with the request that produced it is
    // a different question and this answers it. Inert with no DSN, which is
    // every profile but prod -- so nothing is shipped from a developer's
    // machine. Absolute rule 4 still holds: send-default-pii stays off, so no
    // request body, no headers, no address reaches the vendor.
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:8.9.0")
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
    useJUnitPlatform { excludeTags("latex") }
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
