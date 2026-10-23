#!/usr/bin/env bash
# Run this YOURSELF — not through Claude Code. Pure shell, zero LLM tokens.
#
# Prints how many OPEN items are waiting in each handoff file, so you know
# whether it's even worth asking Claude to look before starting a session.
#
# Usage: ./scripts/handoff-status.sh   (from either repo root)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

any=0
for f in docs/handoff/to-backend.md docs/handoff/to-frontend.md; do
  [ -f "$f" ] || continue
  count=$(awk '/^## OPEN/{f=1;next}/^## ACK/{f=0}f' "$f" | grep -c '^### ' || true)
  printf '%-32s %s open item(s)\n' "$(basename "$f")" "$count"
  [ "$count" -gt 0 ] && any=1
done

[ "$any" -eq 0 ] && echo "(nothing open — safe to skip asking Claude to check)"
exit 0
