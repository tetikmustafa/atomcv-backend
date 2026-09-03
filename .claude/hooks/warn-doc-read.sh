#!/usr/bin/env bash
# PreToolUse hook for the Read tool.
#
# Hooks see the tool call, not the conversation — they cannot tell whether
# you actually asked for a doc read this turn. So this does NOT fully enforce
# "manual only" (that's CLAUDE.md's job, backed by your own spot-checking).
# What it does do:
#   1. Log every docs/ read to a local file so you can audit drift later.
#   2. Hard-block the one file that should never be read at all.
#
# Exit code contract (verify against current Claude Code hook docs):
#   0  → allow
#   2  → block; stderr shown to Claude as the reason
set -euo pipefail

INPUT=$(cat)
FILE_PATH=$(python3 -c "
import json, sys
try:
    print(json.load(sys.stdin).get('tool_input', {}).get('file_path', ''))
except Exception:
    print('')
" <<< "$INPUT" 2>/dev/null || echo "")

[ -z "$FILE_PATH" ] && exit 0

case "$FILE_PATH" in
  */docs/_archive-monolith.md)
    echo "BLOCKED: _archive-monolith.md is retired. If you need something from it, ask the user which docs/spec/ file replaced that section." >&2
    exit 2
    ;;
  */docs/spec/*|*/docs/notes/*|*/docs/handoff/*|*/docs/INDEX.md|*/docs/STATUS.md)
    LOGDIR="${CLAUDE_PROJECT_DIR:-.}/.claude"
    mkdir -p "$LOGDIR" 2>/dev/null || true
    echo "$(date -u +%FT%TZ)  READ  $FILE_PATH" >> "$LOGDIR/doc-read-log.txt" 2>/dev/null || true
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
