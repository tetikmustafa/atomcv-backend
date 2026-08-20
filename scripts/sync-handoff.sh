#!/usr/bin/env bash
# İki yönlü kanal senkronu. Her iki repodan da çalıştırılabilir.
#   ./sync-handoff.sh push   → bu repodaki değişiklikleri karşıya taşı
#   ./sync-handoff.sh pull   → karşıdakileri buraya al
set -euo pipefail

OTHER="${2:-../atomcv-frontend}"
[ -d "$OTHER/.git" ] || { echo "Karşı repo bulunamadı: $OTHER"; exit 1; }

# resolved/ da taşınır: ACK maddeleri oraya iner ve karşı taraf `to-*.md`'den
# kaybolan bir maddenin nereye gittiğini ancak orada bulur.
copy_handoff() {  # $1 kaynak kök, $2 hedef kök
  mkdir -p "$2/docs/handoff/resolved"
  cp "$1"/docs/handoff/*.md "$2/docs/handoff/"
  # resolved/ boş olabilir — glob eşleşmezse sessizce geç
  for f in "$1"/docs/handoff/resolved/*.md; do [ -e "$f" ] && cp "$f" "$2/docs/handoff/resolved/"; done
  cp "$1/docs/STATUS.md" "$2/docs/"
}

case "${1:-}" in
  push) copy_handoff . "$OTHER"
        echo "✓ handoff (resolved dahil) + STATUS → $OTHER" ;;
  pull) copy_handoff "$OTHER" .
        echo "✓ handoff (resolved dahil) + STATUS ← $OTHER" ;;
  *)    echo "kullanım: $0 {push|pull} [karşı-repo-yolu]"; exit 1 ;;
esac
