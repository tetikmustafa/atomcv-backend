#!/usr/bin/env bash
# İki yönlü kanal senkronu. Her iki repodan da çalıştırılabilir.
#   ./sync-handoff.sh push   → bu repodaki değişiklikleri karşıya taşı
#   ./sync-handoff.sh pull   → karşıdakileri buraya al
set -euo pipefail

OTHER="${2:-../atomcv-frontend}"
[ -d "$OTHER/.git" ] || { echo "Karşı repo bulunamadı: $OTHER"; exit 1; }

case "${1:-}" in
  push) cp docs/handoff/*.md "$OTHER/docs/handoff/"; cp docs/STATUS.md "$OTHER/docs/"
        echo "✓ handoff + STATUS → $OTHER" ;;
  pull) cp "$OTHER"/docs/handoff/*.md docs/handoff/; cp "$OTHER/docs/STATUS.md" docs/
        echo "✓ handoff + STATUS ← $OTHER" ;;
  *)    echo "kullanım: $0 {push|pull} [karşı-repo-yolu]"; exit 1 ;;
esac
