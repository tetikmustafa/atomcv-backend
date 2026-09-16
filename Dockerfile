# The image CI builds and the production compose file runs.
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

# curl is the health check. Compose asks the container whether it is well,
# and a health check that cannot run reports unhealthy forever.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Never root. Nothing in this image needs to write outside /tmp, and the
# LaTeX container next door already runs as 1000:1000 for the same reason.
RUN useradd --system --uid 1000 --create-home atomcv     && mkdir -p /var/cache/atomcv && chown atomcv:atomcv /var/cache/atomcv
USER atomcv
WORKDIR /app

COPY --from=build --chown=atomcv:atomcv /src/build/libs/*.jar app.jar

EXPOSE 8080

# The cold start. A class-data archive maps the loaded classes instead of
# parsing and verifying them again, and Spring loads a great many.
#
# `AutoCreateSharedArchive` rather than a training run at build time: the
# documented Spring CDS recipe starts the application to record what it loaded,
# which needs a database this build container does not have -- and a build that
# quietly skipped the training step would ship an image whose archive was
# empty. This writes the archive on the first start and maps it on every one
# after, and regenerates it by itself when the jar changes, so a stale archive
# from a previous image cannot be used against a new one.
#
# The path is a cache directory rather than /tmp, and compose mounts a volume
# over it: a container is recreated on every deploy, so an archive written
# inside the container's own layer would be written on every start and read on
# none. Losing it costs one slow start, never a wrong one.

# Compose overrides this with the memory percentage and the locale; repeated
# here so the image is correct when run on its own.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Duser.language=en -Duser.country=US"
ENV JAVA_CDS_OPTS="-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=/var/cache/atomcv/atomcv.jsa"

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1

# Shell form so that JAVA_CDS_OPTS expands. The archive flags are deliberately
# not in JAVA_TOOL_OPTIONS: compose overrides that variable wholesale, and a
# deployment that set it would silently drop the archive.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_CDS_OPTS -jar app.jar"]
