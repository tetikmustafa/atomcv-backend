#!/bin/sh
# Per-compilation ceilings (Bolum 29.4).
#
# These belong to each xelatex, not to the service: setting them in the
# entrypoint applied them to the JVM as well, which then could not reserve its
# heap — and a CPU-second limit there would have killed the server itself after
# the first twenty seconds of work.
#
# A pathological document now fails on its own instead of taking the compiler
# down with it.
set -eu

ulimit -t "${LATEX_ULIMIT_CPU:-20}"             # CPU seconds
ulimit -v "${LATEX_ULIMIT_MEMORY_KB:-524288}"   # virtual memory, 512 MB
ulimit -f "${LATEX_ULIMIT_FILE_BLOCKS:-20480}"  # file size, 10 MB in 512b blocks

exec xelatex "$@"
