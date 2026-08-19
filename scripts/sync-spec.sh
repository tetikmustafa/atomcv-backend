#!/usr/bin/env bash
# Backend reposunda çalıştırılır. Spec'i ve indeksi frontend reposuna kopyalar.
# notes/ ve handoff/ KOPYALANMAZ — handoff ayrı akışta, notes repo-yerel.
#
# rsync KULLANMA: Windows'ta Git Bash rsync ile gelmiyor, betik ilk satırda
# çakılır. `rm -rf` + `cp -r` aynı işi yapar; hedefi önce silmek `--delete`
# karşılığıdır — spec'ten kaldırılan bir dosya frontend'de bayat kalmasın.
set -euo pipefail

FRONTEND="${1:-../atomcv-frontend}"

[ -d "$FRONTEND/.git" ] || { echo "Frontend repo bulunamadı: $FRONTEND"; exit 1; }
[ -d docs/spec ] || { echo "docs/spec yok — betiği repo kökünden çalıştır."; exit 1; }

mkdir -p "$FRONTEND/docs"
rm -rf "$FRONTEND/docs/spec"
cp -r docs/spec "$FRONTEND/docs/spec"
cp docs/INDEX.md "$FRONTEND/docs/INDEX.md"

echo "✓ spec + INDEX senkronlandı → $FRONTEND"
echo
echo "Sıradaki adımlar (frontend reposunda):"
echo "  git add docs/ && git commit -m 'docs: sync spec from backend'"
