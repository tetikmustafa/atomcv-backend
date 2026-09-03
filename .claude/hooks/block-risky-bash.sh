#!/usr/bin/env bash
# PreToolUse hook for the Bash tool.
#
# Why this exists alongside .claude/settings.json's deny list: Claude Code's
# permission-deny matching for Bash has had bugs where a deny rule silently
# doesn't fire (seen in the wild, filed upstream). This hook is a second,
# independent check on the actual command string, so a permission-layer bug
# doesn't leave you unprotected. If you're on a Claude Code version where the
# deny list is confirmed reliable again, you can drop this — but there's no
# harm in keeping both.
#
# Exit code contract (verify against current Claude Code hook docs if this
# stops working after an update — this has changed before):
#   0  → allow the tool call
#   2  → block the tool call; stderr is shown to Claude as the reason
set -euo pipefail

INPUT=$(cat)
CMD=$(python3 -c "
import json, sys
try:
    print(json.load(sys.stdin).get('tool_input', {}).get('command', ''))
except Exception:
    print('')
" <<< "$INPUT" 2>/dev/null || echo "")

[ -z "$CMD" ] && exit 0

# Keep this list in sync with .claude/settings.json's permissions.deny.
PATTERNS=(
  'git push'
  'git merge'
  'git rebase'
  'gh pr create'
  'gh pr merge'
  'gh pr review'
  'sync-spec\.sh'
  'sync-handoff\.sh'
  'docker-compose\.prod\.yml'
  'git clean -fdx'
  'docker volume rm'
  'flyway.*prod'
)

for p in "${PATTERNS[@]}"; do
  if echo "$CMD" | grep -qE "$p"; then
    echo "BLOCKED by PreToolUse hook: command matches restricted pattern '$p'." >&2
    echo "Per CLAUDE.md 'Propose, Don't Run': print this exact command in your reply for the user to run themselves. Do not retry it." >&2
    exit 2
  fi
done

exit 0
