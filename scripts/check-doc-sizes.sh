#!/usr/bin/env bash
# Rolling dosyaların sınırı aşıp aşmadığını kontrol eder.
# CI'a ya da pre-commit hook'a bağlanabilir.
set -euo pipefail

fail=0
check() {
  local file=$1 limit=$2
  [ -f "$file" ] || return 0
  local n; n=$(wc -l < "$file")
  if [ "$n" -gt "$limit" ]; then
    echo "⚠  $file: $n satır (sınır $limit) — arşivleme zamanı"
    fail=1
  fi
}

check docs/notes/current.md        200
check docs/handoff/to-frontend.md  100
check docs/handoff/to-backend.md   100
check docs/STATUS.md                60
check CLAUDE.md                    280

exit $fail
