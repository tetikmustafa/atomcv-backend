#!/usr/bin/env bash
# Adim V.7. Runs on the server, called over SSH by the deploy workflow.
#
#   ./scripts/deploy.sh backend  <sha>
#   ./scripts/deploy.sh frontend <sha>
#
# The component is the first argument because the two repositories deploy
# independently: there is no single commit that describes what is running
# (Bolum 47.3), so each carries its own tag and a deploy of one must leave the
# other exactly where it was.
set -euo pipefail

cd "$(dirname "$0")/.."

COMPOSE="docker compose --env-file .env.deploy -f docker-compose.prod.yml"
HEALTH_URL="http://localhost:8080/actuator/health"
HEALTH_ATTEMPTS=45          # 90 seconds; a cold JVM with Flyway takes ~40

COMPONENT=${1:-}
NEW_SHA=${2:-}

case "$COMPONENT" in
    backend|frontend) ;;
    *) echo "usage: $0 <backend|frontend> <sha>" >&2; exit 2 ;;
esac
[ -n "$NEW_SHA" ] || { echo "usage: $0 $COMPONENT <sha>" >&2; exit 2; }

VAR="$(echo "$COMPONENT" | tr '[:lower:]' '[:upper:]')_SHA"

touch .env.deploy
PREVIOUS=$(grep "^$VAR=" .env.deploy | cut -d= -f2- || true)

write_tag() {
    local sha=$1 tmp
    tmp=$(mktemp)
    grep -v "^$VAR=" .env.deploy > "$tmp" || true
    echo "$VAR=$sha" >> "$tmp"
    mv "$tmp" .env.deploy
}

healthy() {
    for _ in $(seq 1 "$HEALTH_ATTEMPTS"); do
        if curl -sf --max-time 3 "$HEALTH_URL" > /dev/null; then
            return 0
        fi
        sleep 2
    done
    return 1
}

echo "Deploying $COMPONENT $NEW_SHA (was ${PREVIOUS:-none})"
write_tag "$NEW_SHA"

$COMPOSE pull "$COMPONENT"

# Migrations run at start-up, inside the application, and this is the decision
# that makes that safe: one instance at a time. Bolum 47's snippet reached for
# `--spring.flyway.migrate-only=true`, which is not a Spring Boot property at
# all -- see the Duzeltme in spec/11-operations.md § 47. Flyway takes its own
# lock, so the risk is not two migrators but two application versions against
# one schema, and a single replica is what rules that out.
$COMPOSE up -d --no-deps "$COMPONENT"

if healthy; then
    echo "Healthy. Deployed $COMPONENT $NEW_SHA"
    docker image prune -f > /dev/null
    exit 0
fi

# Only this component goes back. The other one is somebody else's deploy and
# may be minutes old; rolling it back would turn one failure into two.
echo "Health check failed after $((HEALTH_ATTEMPTS * 2))s" >&2

if [ -z "$PREVIOUS" ]; then
    echo "No previous tag for $COMPONENT -- nothing to roll back to." >&2
    echo "The failed version is still up; look at it with:" >&2
    echo "  $COMPOSE logs --tail=100 $COMPONENT" >&2
    exit 1
fi

echo "Rolling $COMPONENT back to $PREVIOUS" >&2
write_tag "$PREVIOUS"
$COMPOSE up -d --no-deps "$COMPONENT"

if healthy; then
    echo "Rolled back to $PREVIOUS and healthy." >&2
else
    # The rollback did not fix it, so the fault is very unlikely to be the
    # image. Say so rather than leaving somebody re-deploying an old tag at a
    # database that is down.
    echo "Still unhealthy after rollback -- the fault is probably not $COMPONENT." >&2
    echo "  $COMPOSE ps" >&2
fi
exit 1
