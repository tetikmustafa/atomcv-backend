#!/usr/bin/env bash
# Signs in through the magic link, with no frontend and no browser.
#
# Adim 3.3's sign-in is three requests and one email, and doing it by hand on
# Windows is where it goes wrong: PowerShell's `curl` is Invoke-WebRequest and
# rejects every flag below, cmd.exe has no `$(...)`, and `jq` is not installed
# on this machine. So the whole tour is here instead.
#
#   ./scripts/dev-signin.sh [email]
#
# Leaves the cookie jar in .dev-jar.txt (git-ignored). Everything after this --
# uploading a CV, running a generation -- reuses it:
#
#   XSRF=$(awk '/XSRF-TOKEN/ {print $7}' .dev-jar.txt)
#   curl -b .dev-jar.txt -H "X-XSRF-TOKEN: $XSRF" -X POST localhost:8080/...
set -euo pipefail

API=${API:-http://localhost:8080}
MAILPIT=${MAILPIT:-http://localhost:8025}
EMAIL=${1:-ben@example.com}
JAR=.dev-jar.txt

say() { printf '\n\033[1m%s\033[0m\n' "$1"; }
die() { printf '\n\033[31m%s\033[0m\n' "$1" >&2; exit 1; }

# `python` rather than `jq`: one is on this machine and the other is not.
json() { python -c "import sys,json;d=json.load(sys.stdin);print($1)"; }

say "0. Ayakta mi"
curl -sf --max-time 3 "$API/actuator/health" > /dev/null \
    || die "Backend cevap vermiyor. Baska bir kabukta: make dev"
curl -sf --max-time 3 "$MAILPIT/api/v1/messages?limit=1" > /dev/null \
    || die "Mailpit cevap vermiyor. Baska bir kabukta: make dev-full"
echo "backend ve mailpit hazir"

# Every response carries the cookie (SecurityConfig's eager tokens), so any
# request will do to collect it. The token is not a credential on its own --
# it proves nothing without the session cookie beside it.
say "1. CSRF tokeni"
rm -f "$JAR"
curl -s -c "$JAR" "$API/actuator/health" > /dev/null
XSRF=$(awk '/XSRF-TOKEN/ {print $7}' "$JAR")
[ -n "$XSRF" ] || die "Cerezde XSRF-TOKEN yok"
echo "alindi"

say "2. Giris baglantisi isteniyor: $EMAIL"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -H "X-XSRF-TOKEN: $XSRF" \
    -X POST "$API/api/v1/auth/magic-link" \
    -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\"}")
case "$CODE" in
    202) echo "202 -- ve adresin hesabi olsun olmasin hep 202 (Bolum 40.4)" ;;
    403) die "403 CHALLENGE_FAILED. TURNSTILE_SECRET_KEY yerelde okunuyor demek;
application-local.yml LOCAL_TURNSTILE_SECRET_KEY bekliyor -- backend'i yeniden baslat." ;;
    429) die "429 RATE_LIMITED. Uc istek / 15dk. Temizlemek icin:
  docker compose exec redis redis-cli --scan --pattern 'ratelimit:*' |
    xargs -r docker compose exec -T redis redis-cli del" ;;
    *)   die "$CODE bekleniyordu degil" ;;
esac

say "3. Baglanti Mailpit'ten aliniyor"
LATEST=$(curl -s "$MAILPIT/api/v1/message/latest")
LINK=$(printf '%s' "$LATEST" | json "__import__('re').search(r'/verify\?s=\S+', d['Text']).group(0)") \
    || die "Son e-postada giris baglantisi yok"
SELECTOR=${LINK#*s=}; SELECTOR=${SELECTOR%%&*}
VERIFIER=${LINK#*v=}
echo "selector=$SELECTOR"

# The link in the email points at a page, and this is the POST that page makes.
# Bolum 40.3: mail scanners follow links, and a one-shot token spent by a
# scanner is a sign-in the person never got.
say "4. Giris yapiliyor"
BODY=$(curl -s -b "$JAR" -c "$JAR" -H "X-XSRF-TOKEN: $XSRF" \
    -X POST "$API/api/v1/auth/verify" -H 'Content-Type: application/json' \
    -d "{\"selector\":\"$SELECTOR\",\"verifier\":\"$VERIFIER\"}")
echo "$BODY"
grep -q '"sid"\|sid' "$JAR" || die "Oturum cerezi gelmedi"
echo "profileUpgrade yukarida: none = tasinacak anonim profil yoktu"

say "5. Kimim"
curl -s -b "$JAR" "$API/api/v1/auth/session" \
    | json "json.dumps(d, indent=2, ensure_ascii=False)"

# Bolum 40.3: expired, already used, wrong verifier and never existed are one
# answer, because telling them apart tells an attacker which half of a guess
# was right.
say "6. Ayni baglanti ikinci kez"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -H "X-XSRF-TOKEN: $XSRF" \
    -X POST "$API/api/v1/auth/verify" -H 'Content-Type: application/json' \
    -d "{\"selector\":\"$SELECTOR\",\"verifier\":\"$VERIFIER\"}")
[ "$CODE" = "400" ] && echo "400 MAGIC_LINK_INVALID -- tek kullanimlik, dogru" \
    || echo "beklenen 400 idi, gelen $CODE"

say "Bitti. Cerez kavanozu: $JAR"
