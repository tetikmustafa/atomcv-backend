# The only place xelatex runs (Bolum 29).
#
# LaTeX is a programming language and user content reaches it, so this image is
# built to be the blast radius: no credentials, no database, and — through the
# compose network — no route out. What is left is CPU and a temporary
# directory.

# ── build the wrapper ─────────────────────────────────────────────────────
FROM debian:bookworm-slim AS server-build

RUN apt-get update \
 && apt-get install -y --no-install-recommends default-jdk-headless \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /build
COPY server/CompileServer.java .
# -encoding UTF-8 for the same reason build.gradle.kts pins it: javac follows
# the platform charset, and the container's is US-ASCII, so a dash in a comment
# fails the build.
RUN javac -encoding UTF-8 -d classes CompileServer.java \
 && jar --create --file server.jar --main-class CompileServer -C classes .

# ── the image that runs ───────────────────────────────────────────────────
FROM debian:bookworm-slim

# texlive-xetex for the engine, the recommended sets for the packages a CV
# template needs, and TeX Gyre for a font family that exists on every machine
# this is measured on. No texlive-full: it is several gigabytes of packages
# that would never be loaded, and every one of them is attack surface.
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      texlive-xetex \
      texlive-latex-recommended \
      texlive-fonts-recommended \
      fonts-texgyre \
      default-jre-headless \
      fontconfig \
 && rm -rf /var/lib/apt/lists/* \
 && fc-cache -f

# Whitelisted fonts are copied in when a template needs one that Debian does
# not ship (Bolum 33). The directory is created either way so the COPY below
# has something to find.
RUN mkdir -p /usr/local/share/fonts
COPY fonts/ /usr/local/share/fonts/
RUN fc-cache -f

COPY --from=server-build /build/server.jar /opt/server.jar
COPY entrypoint.sh run-xelatex.sh /opt/
RUN chmod +x /opt/entrypoint.sh /opt/run-xelatex.sh

# An unprivileged user with no shell: nothing here should ever be logged into.
RUN useradd --create-home --uid 1000 --shell /usr/sbin/nologin texuser
USER texuser
WORKDIR /home/texuser

EXPOSE 8090

# bash, not sh: /dev/tcp is a bash feature, and dash answers "Directory
# nonexistent" — which reads like a broken container rather than a broken
# health check. No curl or wget is installed on purpose; the fewer tools that
# can reach the network from in here, the better.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8090 && printf 'GET /healthz HTTP/1.0\\r\\n\\r\\n' >&3 && head -1 <&3 | grep -q 200"]

ENTRYPOINT ["/opt/entrypoint.sh"]
