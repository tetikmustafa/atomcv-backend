#!/usr/bin/env bash
# The other half of Bolum 49, and the half the release checklist marks with a
# warning: "⚠️ Gercek restore testi yapildi".
#
#   ./scripts/restore.sh                       # newest backup -> scratch database
#   ./scripts/restore.sh atomcv-20260828-0300.sql.gz.age
#   ./scripts/restore.sh <archive> --into-production
#
# **It restores into a scratch database by default, and that is the point.**
# A restore script that can only overwrite production is a script nobody runs
# until the day they have to, which is the day they find out it does not work.
# This one can be run on an ordinary Tuesday against the real backups, and it
# prints row counts so the answer is "yes, and there are 412 profiles in it"
# rather than "it did not error".
#
# The age private key is not on the server (Adim V.8). Run this from the
# machine that has it, or copy the key in for the length of the test and
# remove it afterwards.
set -euo pipefail

cd "$(dirname "$0")/.."

COMPOSE="docker compose -f docker-compose.prod.yml"
REMOTE=${BACKUP_REMOTE:-r2:atomcv-backups}
AGE_KEY=${AGE_KEY_FILE:-$HOME/.age-key.txt}
SCRATCH=${RESTORE_SCRATCH_DB:-atomcv_restore_test}

set -a
[ -f .env ] && . ./.env
set +a

: "${POSTGRES_USER:?POSTGRES_USER is not set}"
: "${POSTGRES_DB:?POSTGRES_DB is not set}"
[ -f "$AGE_KEY" ] || { echo "No age private key at $AGE_KEY -- see Adim V.8" >&2; exit 2; }

ARCHIVE=${1:-}
TARGET_IS_PRODUCTION=false
[ "${2:-}" = "--into-production" ] && TARGET_IS_PRODUCTION=true

if [ -z "$ARCHIVE" ]; then
    ARCHIVE=$(rclone lsf "$REMOTE/daily/" | sort | tail -1)
    [ -n "$ARCHIVE" ] || { echo "No backups in $REMOTE/daily/" >&2; exit 1; }
    echo "Newest backup: $ARCHIVE"
fi

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

rclone copy "$REMOTE/daily/$ARCHIVE" "$WORK/"
age -d -i "$AGE_KEY" "$WORK/$ARCHIVE" | gunzip > "$WORK/dump.sql"
echo "Decrypted: $(wc -c < "$WORK/dump.sql") bytes of SQL"

if $TARGET_IS_PRODUCTION; then
    # Deliberately awkward. This is the real thing, it is not reversible, and
    # the only time it is right is when production is already lost.
    echo
    echo "This will DROP and rebuild $POSTGRES_DB -- the live database."
    read -r -p "Type the database name to confirm: " typed
    [ "$typed" = "$POSTGRES_DB" ] || { echo "Not confirmed." >&2; exit 1; }
    TARGET=$POSTGRES_DB
    $COMPOSE stop backend
else
    TARGET=$SCRATCH
    echo "Restoring into the scratch database $TARGET (production untouched)"
fi

psql_root() {
    $COMPOSE exec -T postgres psql -U "$POSTGRES_USER" -d postgres "$@"
}

psql_root -c "DROP DATABASE IF EXISTS \"$TARGET\";"
psql_root -c "CREATE DATABASE \"$TARGET\";"
$COMPOSE exec -T postgres psql -U "$POSTGRES_USER" -d "$TARGET" < "$WORK/dump.sql" > /dev/null

# The assertion, and the reason this script prints anything at all. "It ran
# without an error" is not a restore test; a schema with no rows in it would
# also run without an error.
echo
echo "Restored into $TARGET:"
$COMPOSE exec -T postgres psql -U "$POSTGRES_USER" -d "$TARGET" -t -c "
    SELECT format('  %-16s %s', table_name, n_live_tup)
    FROM (
        SELECT relname AS table_name, n_live_tup
        FROM pg_stat_user_tables
        WHERE relname IN ('users', 'profiles', 'sections', 'entries', 'atoms',
                          'atom_variants', 'generations', 'jobs')
        ORDER BY relname
    ) counted;"

if $TARGET_IS_PRODUCTION; then
    $COMPOSE start backend
    echo "Backend restarted."
else
    echo
    echo "Drop it when you are done looking:"
    echo "  $COMPOSE exec -T postgres psql -U $POSTGRES_USER -d postgres -c 'DROP DATABASE \"$TARGET\";'"
fi
