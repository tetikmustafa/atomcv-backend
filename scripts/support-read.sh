#!/usr/bin/env bash
# Reads one generation's content under the permission its owner gave (Bolum 48.4).
#
#   ./scripts/support-read.sh <generation-id>
#
# A command and not an endpoint, deliberately: absolute rule 3 leaves no way for
# one user's request to read another's rows, and adding one would mean a support
# role and a permanent exception to the guard ArchUnit enforces — for something
# that happens by hand a few times a year. The grant is the credential: without
# an open one this prints why and reads nothing.
#
# It stamps `support_grants.accessed_at`, which the person is shown. That is the
# point of it — a consent nobody can check the use of is not a consent.
#
# The content goes to this terminal and to no log (absolute rule 4). Do not pipe
# it into a file that outlives the question you are answering.
#
#   ./scripts/support-read.sh <generation-id> --export=export.json
#
# writes Bolum 48.5's replay file instead of printing, under the same grant and
# the same stamp -- it is the same content. A file is the more dangerous of the
# two, because a printout dies with the terminal and a file does not: delete it
# with the question you opened it for.
set -euo pipefail

GENERATION=${1:-}
PROFILES=${PROFILES:-local,support}
EXPORT=""

if [ -z "$GENERATION" ]; then
  echo "usage: ./scripts/support-read.sh <generation-id> [--export=<path>]" >&2
  echo "       PROFILES=prod,support ./scripts/support-read.sh <generation-id>" >&2
  exit 2
fi
shift

while [ $# -gt 0 ]; do
  case "$1" in
    --export=*) EXPORT="--support.export=${1#--export=}"; shift ;;
    --export)   EXPORT="--support.export=$2"; shift 2 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

# `sh ./gradlew` because ./gradlew is not a Windows executable, and Git Bash is
# the shell this repository's scripts are written for.
exec sh ./gradlew bootRun --quiet \
  --args="--spring.profiles.active=$PROFILES --support.generation=$GENERATION $EXPORT"
