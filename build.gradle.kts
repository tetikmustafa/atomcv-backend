import java.time.Duration

plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
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
    // JSON merge-patch needs three states — absent, null, value — and Java has
    // no tri-state Optional: Jackson deserializes an absent Optional field as
    // Optional.empty(), the same as an explicit null. This library owns that
    // distinction. Springdoc does not render it as a nullable field on its own:
    // each field needs @Schema(implementation = ..., types = {..., "null"}) or
    // the wrapper leaks into the published schema. OpenApiSchemaIT holds it.
    implementation("org.openapitools:jackson-databind-nullable:0.2.6")

    // The published schema is the API contract: the frontend generates its
    // types from it, so enums and headers have to reach it, not only payloads.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// javac still defaults to the platform charset, which is windows-1254 on a
// Turkish Windows machine and UTF-8 on the CI runner. Source files carry
// Turkish text, so leaving this unset would make the same file compile into
// two different string constants.
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
