#!/usr/bin/env bash
# Bolum 49.2, run from cron at 03:00 (Adim V.8).
#
#   0 3 * * * /opt/atomcv/scripts/backup.sh >> /var/log/atomcv-backup.log 2>&1
#
# Encrypted before it leaves the machine: the dump is every CV in the product,
# and an object store credential is not a reason to trust the object store with
# plaintext. The private half of the age key is NOT on this server -- if it
# were, whoever took the server would have both halves and the encryption would
# be decoration.
set -euo pipefail

cd "$(dirname "$0")/.."

COMPOSE="docker compose -f docker-compose.prod.yml"
REMOTE=${BACKUP_REMOTE:-r2:atomcv-backups}
KEEP_DAILY=${BACKUP_KEEP_DAILY:-7d}

# From .env, which is chmod 600 and holds the deployment's secrets. This one is
# a public key and is not secret; it lives there because that is where the
# deployment's configuration lives.
set -a
[ -f .env ] && . ./.env
set +a

: "${AGE_PUBLIC_KEY:?AGE_PUBLIC_KEY is not set -- see Adim V.8}"
: "${POSTGRES_USER:?POSTGRES_USER is not set}"
: "${POSTGRES_DB:?POSTGRES_DB is not set}"

STAMP=$(date +%Y%m%d-%H%M)
ARCHIVE="/tmp/atomcv-$STAMP.sql.gz.age"

# `set -o pipefail` is why this is one line and not four: a pg_dump that failed
# halfway would otherwise gzip and encrypt a truncated dump, upload it, and
# report success -- a backup that exists and cannot restore, which is worse
# than no backup because nobody looks for it.
trap 'rm -f "$ARCHIVE"' EXIT

$COMPOSE exec -T postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" \
    | gzip \
    | age -r "$AGE_PUBLIC_KEY" \
    > "$ARCHIVE"

SIZE=$(wc -c < "$ARCHIVE")
# An empty or near-empty archive means the dump failed in a way the pipeline
# did not report. A real one is megabytes.
if [ "$SIZE" -lt 4096 ]; then
    echo "$(date -Is) FAILED: the archive is only $SIZE bytes" >&2
    exit 1
fi

rclone copy "$ARCHIVE" "$REMOTE/daily/"

# Bolum 49.2's retention. The credential this runs under must be write-only
# (Adim V.8) -- so if `rclone delete` is refused, that is the credential doing
# its job and not an error worth failing the backup over.
rclone delete --min-age "$KEEP_DAILY" "$REMOTE/daily/" \
    || echo "$(date -Is) retention skipped: the remote refused a delete" >&2

echo "$(date -Is) OK: $STAMP, $SIZE bytes"
