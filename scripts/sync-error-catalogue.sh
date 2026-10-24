#!/usr/bin/env bash
# Backend reposunda çalıştırılır. Üretilmiş hata kataloğunu frontend reposuna
# kopyalar — frontend'in bir testi `params`'ı buradan okuyor.
#
# Eskiden `sync-spec.sh` idi ve spec'i de taşıyordu; spec 2026-10-24'te
# emekliye ayrıldı, taşınacak tek şey bu dosya kaldı.
set -euo pipefail

FRONTEND="${1:-../atomcv-frontend}"

[ -d "$FRONTEND/.git" ] || { echo "Frontend repo bulunamadı: $FRONTEND"; exit 1; }
[ -f error-catalogue.md ] || { echo "error-catalogue.md yok — betiği repo kökünden çalıştır."; exit 1; }

mkdir -p "$FRONTEND/docs"
cp error-catalogue.md "$FRONTEND/docs/error-catalogue.md"

echo "✓ error-catalogue senkronlandı → $FRONTEND"
echo
echo "Sıradaki adım (frontend reposunda):"
echo "  git add docs/error-catalogue.md && git commit -m 'docs: sync the error catalogue'"
