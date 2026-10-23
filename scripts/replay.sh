#!/usr/bin/env bash
# Re-runs Faz E from an export, on this machine.
#
#   ./scripts/replay.sh export.json            # prints the LaTeX
#   ./scripts/replay.sh export.json out.tex    # writes it instead
#
# No database, no Spring, no compiler. That is the section's claim about the
# pure phases and this is what checks it: the renderer has no collaborators, so
# the same snapshot produces the same bytes and a diff against what shipped is
# the answer.
#
# **Faz E is what replays.** Faz B and Faz C are pure too, and their inputs --
# a scored tree, and a selection request carrying every atom's measured height
# -- are stored nowhere. Rebuilding them from today's profile would answer a
# question about last week with this week's text, which is the exact failure
# `content_snapshot` exists to prevent.
#
# The export comes from the support reader, under the grant its owner gave:
#
#   ./scripts/support-read.sh <generation-id> --export=export.json
#
# **It is the document somebody sent to an employer.** Absolute rule 4 is about
# logs and this is about disks: delete the file with the question you opened it
# for.
set -euo pipefail

cd "$(dirname "$0")/.."

EXPORT=${1:-}
OUT=${2:-}

if [ -z "$EXPORT" ]; then
    echo "usage: $0 <export.json> [out.tex]" >&2
    echo "       ./scripts/support-read.sh <generation-id> --export=export.json" >&2
    exit 2
fi

ARGS="-Preplay.file=$EXPORT"
[ -n "$OUT" ] && ARGS="$ARGS -Preplay.out=$OUT"

# `sh ./gradlew` because ./gradlew is not a Windows executable, and Git Bash is
# the shell this repository's scripts are written for.
# shellcheck disable=SC2086
exec sh ./gradlew replay --quiet --console=plain $ARGS
