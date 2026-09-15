#!/usr/bin/env bash
# WAL archiving, the half that turns a nightly dump into
# point-in-time recovery. Run from cron every five minutes:
#
#   */5 * * * * /opt/atomcv/scripts/archive-wal.sh >> /var/log/atomcv-wal.log 2>&1
#
# Postgres copies each finished segment into the `walarchive` volume
# (docker-compose.prod.yml) and stops there; nothing in that container can
# reach an object store, and nothing in it holds the age key. This script is
# the other side: it encrypts each segment on the host and ships it.
#
# WITHOUT THIS RUNNING, THE ARCHIVE FILLS AND POSTGRES STOPS ACCEPTING WRITES.
# That is archive_mode's design, not a bug in it: a database told to archive
# and unable to would otherwise silently lose the recovery window. The failure
# is loud and the fix is to run this. Watch the volume.
set -euo pipefail

cd "$(dirname "$0")/.."

COMPOSE="docker compose -f docker-compose.prod.yml"
REMOTE=${BACKUP_REMOTE:-r2:atomcv-backups}
ARCHIVE_DIR=/wal-archive

# A segment is only useful with a base backup old enough to replay from, so it
# is kept exactly as long as the oldest daily dump. Outliving that is storage
# paid for a recovery that cannot be performed.
KEEP_WAL=${BACKUP_KEEP_DAILY:-7d}

set -a
[ -f .env ] && . ./.env
set +a

: "${AGE_PUBLIC_KEY:?AGE_PUBLIC_KEY is not set -- see .env.example}"

# Segments touched in the last minute may still be mid-copy: Postgres's
# archive_command is a plain `cp` and `cp` is not atomic. Shipping a truncated
# segment and then deleting it is the one way this script can lose data.
SEGMENTS=$($COMPOSE exec -T postgres \
    find "$ARCHIVE_DIR" -type f -mmin +1 -printf '%f\n' 2>/dev/null | sort || true)

if [ -z "$SEGMENTS" ]; then
    exit 0
fi

SHIPPED=0
for segment in $SEGMENTS; do
    local_file="/tmp/atomcv-wal-$segment.age"
    trap 'rm -f "$local_file"' EXIT

    # Encrypted before it leaves the machine, for the same reason the dump is:
    # a WAL segment holds the same rows, only newer.
    if ! $COMPOSE exec -T postgres cat "$ARCHIVE_DIR/$segment" \
            | age -r "$AGE_PUBLIC_KEY" > "$local_file"; then
        echo "$(date -Is) FAILED: could not read $segment" >&2
        exit 1
    fi

    if ! rclone copy "$local_file" "$REMOTE/wal/"; then
        echo "$(date -Is) FAILED: could not ship $segment" >&2
        exit 1
    fi

    # Only now. A segment deleted before it landed is a hole in the recovery
    # chain, and replay stops at a hole -- everything after it is unreachable
    # even though it shipped.
    $COMPOSE exec -T postgres rm -f "$ARCHIVE_DIR/$segment"
    rm -f "$local_file"
    SHIPPED=$((SHIPPED + 1))
done

# Same note as the retention: the credential this runs under should be
# write-only, so a refused delete is the credential working.
rclone delete --min-age "$KEEP_WAL" "$REMOTE/wal/" \
    || echo "$(date -Is) retention skipped: the remote refused a delete" >&2

echo "$(date -Is) OK: $SHIPPED segment(s)"
