# The image Adim V.7's workflow builds and Bolum 11.1 runs.
#
# It did not exist. The deploy pipeline in the build guide ends with
# `docker build -t ghcr.io/.../atomcv-backend:$SHA .` and there was nothing at
# the root to build -- the whole deployment path was written down and had no
# artefact to deploy.

# ── build ────────────────────────────────────────────────────────────────
# The wrapper rather than a gradle image: the wrapper pins the Gradle version
# the repository actually builds with, and a base image's version drifts on
# somebody else's release schedule.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src

# Wrapper and build files first, so a source-only change reuses the layer that
# downloaded the dependencies. gradlew is mode 100755 in the repository and has
# to stay that way (CLAUDE.md) -- a copied file keeps its mode.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN sh ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY src ./src
# Tests do not run here. CI runs them against real infrastructure the build
# container does not have -- Testcontainers needs a Docker daemon, and a build
# that quietly skipped them would look like it had run them.
RUN sh ./gradlew --no-daemon bootJar -x test

# ── run ──────────────────────────────────────────────────────────────────
# JRE, not JDK: the compiler is build-time only and shipping it is attack
# surface for nothing. Jammy rather than Alpine because the JDK's fontconfig
# and locale handling are glibc's, and PDFBox reads text out of PDFs here.
FROM eclipse-temurin:21-jre-jammy

# curl is the health check. Bolum 11's compose asks the container whether it is
# well, and a health check that cannot run reports unhealthy forever.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Never root. Nothing in this image needs to write outside /tmp, and the
# LaTeX container next door already runs as 1000:1000 for the same reason.
RUN useradd --system --uid 1000 --create-home atomcv
USER atomcv
WORKDIR /app

COPY --from=build --chown=atomcv:atomcv /src/build/libs/*.jar app.jar

EXPOSE 8080

# Compose overrides this with the memory percentage and the locale (Bolum
# 11.1); repeated here so the image is correct when run on its own.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Duser.language=en -Duser.country=US"

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
