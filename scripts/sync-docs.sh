#!/usr/bin/env bash
# Copy the architecture documents to the frontend repository.
#
# This repository is the single source: Claude Code can only read the folder it
# was started in, so both repos need their own copy under docs/ (Bolum XI-B.1.3).
#
# Usage: scripts/sync-docs.sh [path-to-frontend-repo]
#        defaults to ../atomcv-frontend relative to this repository.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_REPO="${1:-$(dirname "$REPO_ROOT")/atomcv-frontend}"
SOURCE_DOCS="$REPO_ROOT/docs"
TARGET_DOCS="$TARGET_REPO/docs"

if [ ! -d "$SOURCE_DOCS" ]; then
    echo "error: no docs/ directory in $REPO_ROOT" >&2
    exit 1
fi

if [ ! -d "$TARGET_REPO/.git" ]; then
    echo "error: $TARGET_REPO is not a git repository." >&2
    echo "       Pass the frontend repository path as an argument." >&2
    exit 1
fi

mkdir -p "$TARGET_DOCS"
cp "$SOURCE_DOCS"/*.md "$TARGET_DOCS/"

echo "Copied to $TARGET_DOCS:"
for f in "$SOURCE_DOCS"/*.md; do
    echo "  $(basename "$f")"
done

# Deliberately no commit. Committing into another repository from here would
# hide the change from whoever runs this; review it there instead.
if git -C "$TARGET_REPO" status --porcelain -- docs/ | grep -q .; then
    echo
    echo "The frontend repository now has uncommitted documentation changes:"
    git -C "$TARGET_REPO" status --short -- docs/
    echo
    echo "Commit them there with:"
    echo "  git -C \"$TARGET_REPO\" add docs/ && \\"
    echo "  git -C \"$TARGET_REPO\" commit -m 'docs: sync architecture docs from backend'"
else
    echo
    echo "Already up to date; nothing changed."
fi
