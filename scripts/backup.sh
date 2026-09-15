#!/usr/bin/env bash
# The nightly backup, run from cron at 03:00.
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

# The third leg: the weekly and monthly copies go to a SECOND
# provider. Same-provider redundancy is not what 3-2-1 asks for -- an account
# suspension, a billing lapse or a console mistake takes every copy in one
# place at once, and those are the scenarios a second copy exists for.
ARCHIVE_REMOTE=${BACKUP_ARCHIVE_REMOTE:-b2:atomcv-archive}

# The retention, and the three numbers the privacy text is written
# against: an anonymous row caught in a backup can live at most six months.
KEEP_DAILY=${BACKUP_KEEP_DAILY:-7d}
KEEP_WEEKLY=${BACKUP_KEEP_WEEKLY:-28d}
KEEP_MONTHLY=${BACKUP_KEEP_MONTHLY:-180d}

# From .env, which is chmod 600 and holds the deployment's secrets. This one is
# a public key and is not secret; it lives there because that is where the
# deployment's configuration lives.
set -a
[ -f .env ] && . ./.env
set +a

: "${AGE_PUBLIC_KEY:?AGE_PUBLIC_KEY is not set -- see .env.example}"
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

# The retention. The credential this runs under must be write-only
# -- so if `rclone delete` is refused, that is the credential doing
# its job and not an error worth failing the backup over.
rclone delete --min-age "$KEEP_DAILY" "$REMOTE/daily/" \
    || echo "$(date -Is) retention skipped: the remote refused a delete" >&2

# ── the second provider ──────────────────────────────────────────
#
# Sunday writes the weekly copy, the first of the month writes the monthly one.
# Both are the archive that already passed the size check above -- re-dumping
# would take a second, different snapshot and call it the same backup.
TIERS=""
[ "$(date +%u)" = "7" ] && TIERS="$TIERS weekly"
[ "$(date +%d)" = "01" ] && TIERS="$TIERS monthly"

if [ -n "$TIERS" ]; then
    archive_host=${ARCHIVE_REMOTE%%:*}
    # ── The base backup that makes the WAL replayable ────────────
    #
    # This is a PHYSICAL backup and the dump above is a LOGICAL one, and the
    # difference is the whole reason this block exists: WAL segments replay
    # onto a physical base and onto nothing else. Archiving WAL beside a
    # pg_dump-only strategy ships segments that no procedure can ever apply --
    # a recovery window that exists in the log and not in fact.
    #
    # Weekly against WAL kept for seven days is not a coincidence: it is the
    # constraint. Sunday's base plus every segment since covers any Saturday,
    # and lengthening the base interval past the WAL retention silently opens a
    # hole at the far end.
    if echo "$TIERS" | grep -q weekly; then
        BASE="/tmp/atomcv-base-$STAMP.tar.gz.age"
        # -X fetch collects the WAL written during the backup itself, so the
        # tar is consistent on its own before any archived segment is applied.
        if $COMPOSE exec -T postgres pg_basebackup -U "$POSTGRES_USER" \
                -D - -Ft -z -X fetch 2>/dev/null | age -r "$AGE_PUBLIC_KEY" > "$BASE"; then
            rclone copy "$BASE" "$REMOTE/base/"
            rclone delete --min-age "$KEEP_WEEKLY" "$REMOTE/base/" \
                || echo "$(date -Is) base retention skipped: the remote refused a delete" >&2
            echo "$(date -Is) OK: base backup, $(wc -c < "$BASE") bytes"
        else
            # Not fatal: the dump already succeeded and is the copy that gets
            # restored on an ordinary bad day. Losing the base costs the
            # five-minute window, not the backup.
            echo "$(date -Is) WARNING: pg_basebackup failed -- WAL replay has no base this week" >&2
        fi
        rm -f "$BASE"
    fi

    if rclone listremotes 2>/dev/null | grep -qx "$archive_host:"; then
        for tier in $TIERS; do
            case "$tier" in
                weekly)  keep=$KEEP_WEEKLY ;;
                monthly) keep=$KEEP_MONTHLY ;;
            esac
            rclone copy "$ARCHIVE" "$ARCHIVE_REMOTE/$tier/"
            rclone delete --min-age "$keep" "$ARCHIVE_REMOTE/$tier/" \
                || echo "$(date -Is) $tier retention skipped: the remote refused a delete" >&2
            echo "$(date -Is) OK: $tier copy on $archive_host"
        done
    else
        # Loud rather than silent, and not fatal: a daily backup that exists is
        # worth more than a cron job that failed over the second copy. But a
        # deployment running on one provider is not running 3-2-1, and the only
        # place that can be said is this log.
        echo "$(date -Is) WARNING: no '$archive_host' remote -- the" \
             "second provider is not configured and$TIERS copies were skipped" >&2
    fi
fi

echo "$(date -Is) OK: $STAMP, $SIZE bytes"
