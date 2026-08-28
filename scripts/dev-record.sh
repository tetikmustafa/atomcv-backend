#!/usr/bin/env bash
# Drives every prompt once so `make record` has something to record (Bolum 54.2).
#
#   Kabuk 1:  make dev-full          # containers
#   Kabuk 2:  make record            # backend, local+local-record -- REAL calls
#   Kabuk 3:  ./scripts/dev-record.sh /c/Users/tetik/Desktop/cv.pdf
#
# COSTS MONEY. Every phase below is a real provider call at the configured
# model's price. It is meant to be run once, deliberately, and then never again
# until a prompt changes.
#
# Afterwards `local-fake` replays these answers, `latexTest` stops failing on a
# synthetic job_analysis, and Faz D stops producing sentences the validator
# throws away.
set -euo pipefail

API=${API:-http://localhost:8080}
FIXTURES=src/test/resources/fixtures/llm
CV=${1:-}
JAR=.dev-jar.txt

say() { printf '\n\033[1m%s\033[0m\n' "$1"; }
warn() { printf '\033[33m%s\033[0m\n' "$1"; }
die() { printf '\n\033[31m%s\033[0m\n' "$1" >&2; exit 1; }
json() { python -c "import sys,json;d=json.load(sys.stdin);print($1)"; }

# The directory does not exist until the first recording, and `pipefail` turns
# a failing `find` at the head of a pipe into a silent exit -- which is how this
# script first ran and printed nothing at all.
count_fixtures() {
    [ -d "$FIXTURES" ] || { echo 0; return 0; }
    find "$FIXTURES" -name '*.json' | wc -l | tr -d ' '
}

[ -n "$CV" ] || die "Kullanim: ./scripts/dev-record.sh <cv-dosyasi> [ilan.txt]
Ornek: ./scripts/dev-record.sh /c/Users/tetik/Desktop/cv.pdf
       ./scripts/dev-record.sh ~/Desktop/cv.pdf ~/Desktop/ilan.txt"
[ -f "$CV" ] || die "Dosya yok: $CV"
POSTING_FILE=${2:-}
[ -z "$POSTING_FILE" ] || [ -f "$POSTING_FILE" ] || die "Ilan dosyasi yok: $POSTING_FILE"

curl -sf --max-time 3 "$API/actuator/health" > /dev/null \
    || die "Backend cevap vermiyor. Baska bir kabukta: make record"

BEFORE=$(count_fixtures)
warn "Backend'in local-record profilinde oldugu VARSAYILIYOR."
warn "make dev ile calisiyorsa hicbir sey kaydedilmez -- sonunda soylenecek."
warn "Su an kayitli fixture sayisi: $BEFORE"

# Bolum 18.1's preflight refuses anything that does not read like a posting:
# 150 characters, 40 words, two signal words. This one is real enough to pass
# and short enough to paste again later -- which matters, because the fixture
# key is a digest of this exact text.
POSTING='We are seeking a senior backend engineer to join our payments team.
Responsibilities: design and operate distributed services in Java and Go, own the
reliability of a high throughput ledger, and mentor other engineers as the team
grows. Requirements: several years of production experience with PostgreSQL,
comfort with observability tooling, and a track record of shipping. Preferred
qualifications include Kubernetes and Terraform. Apply with a short note about
the systems you have run.'

# A real advertisement beats this one. Bolum 18.4's gate reads the model's own
# confidence, and a short synthetic posting is exactly what a model is least
# sure about -- refused at the gate, no Faz D, and the three prompts behind it
# never run. Pass a file with a posting you actually applied to.
if [ -n "$POSTING_FILE" ]; then
    # The `x` is not decoration. Command substitution strips *every* trailing
    # newline, and the fixture key is a digest of the exact bytes -- a file
    # ending in a newline would be recorded under one key and looked up under
    # another, missing silently and falling back to a synthetic answer. This
    # is the whole reason JobSpecificCvIT reads its posting from a file: one
    # text, one digest.
    POSTING=$(cat "$POSTING_FILE"; printf 'x')
    POSTING=${POSTING%x}
    echo "ilan dosyadan okundu: $POSTING_FILE ($(wc -c < "$POSTING_FILE") bayt)"
fi

say "0. Giris"
if [ -f "$JAR" ] && curl -s -b "$JAR" "$API/api/v1/auth/session" | grep -q '"authenticated":true'; then
    echo "mevcut oturum kullaniliyor ($JAR)"
else
    ./scripts/dev-signin.sh > /dev/null || die "Giris basarisiz -- once dev-signin.sh'i tek basina calistir"
    echo "giris yapildi"
fi
XSRF=$(awk '/XSRF-TOKEN/ {print $7}' "$JAR")

post() { curl -s -b "$JAR" -H "X-XSRF-TOKEN: $XSRF" "$@"; }

# The job is asynchronous, so nothing is recorded until the worker has actually
# run the phases. Waiting is the whole point of this loop.
await() {
    local job=$1 label=$2 status=''
    for _ in $(seq 1 90); do
        status=$(curl -s -b "$JAR" "$API/api/v1/jobs/$job" | json "d.get('status','?')")
        case "$status" in
            completed) echo "  $label: completed"; return 0 ;;
            failed)    curl -s -b "$JAR" "$API/api/v1/jobs/$job" | json "json.dumps(d.get('error'), ensure_ascii=False)"
                       warn "  $label: failed -- yukaridaki hataya bak, ama kayit yine de olmus olabilir"
                       return 0 ;;
        esac
        sleep 2
    done
    warn "  $label: hala $status -- beklemeyi biraktim"
}

# The body is read before the id is taken out of it: Bolum 31.6.2's five
# synchronous rejections all arrive this way, and "not accepted" on its own
# says nothing about which one it was.
jobIdIn() { printf '%s' "$1" | json "d.get('jobId','')" 2>/dev/null || true; }

# Skipped once it has been recorded, and for two reasons rather than thrift.
# A second import *appends* to the profile it already wrote -- the sections
# arrive twice, which is the open PROFILE_ALREADY_EXISTS decision -- and the
# call is paid for again to produce a file that already exists.
say "1. profile_extraction -- gercek CV"
if [ -d "$FIXTURES/profile_extraction" ]; then
    echo "  zaten kayitli, atlaniyor (ikinci import profili ikiye katlardi)"
    echo "  yeniden kaydetmek icin: rm -r $FIXTURES/profile_extraction"
    JOB=''
else
    BODY=$(post -X POST "$API/api/v1/profile/import" -F "file=@$CV")
    JOB=$(jobIdIn "$BODY")
    if [ -z "$JOB" ]; then
        warn "Yukleme kabul edilmedi (Bolum 31.6.2):"
        echo "  $BODY"
        exit 1
    fi
    await "$JOB" "cikarim"
fi

# The key is per-run and not a constant, which cost a recording run to learn.
# Bolum 30.7 replays the *same job* for a repeated key, so a run that had
# already failed under local-fake came straight back on the next attempt --
# no Faz A, no provider call, no fixture, and a stale error on screen that
# looked like the model had just refused.
say "2. job_analysis + bullet_rewrite + about_synthesis + cover_letter"
echo "  (Faz D ve mektup ayni uretimin icinde kosuyor)"
BODY=$(python -c "
import json,sys
print(json.dumps({'jobDescription': sys.argv[1], 'coverLetter': True}))
" "$POSTING" | post -X POST "$API/api/v1/generations" \
    -H 'Content-Type: application/json' -H "Idempotency-Key: record-$(date +%s)" -d @-)
JOB=$(jobIdIn "$BODY")
if [ -z "$JOB" ]; then
    warn "Uretim kabul edilmedi:"
    echo "  $BODY"
    warn "INSUFFICIENT_PROFILE ise sebep 1. adimdir: cikarim basarisiz oldugu"
    warn "icin profil bos kaldi. local-fake'te bu beklenen -- gercek kayit"
    warn "icin backend 'make record' ile kalkmali."
    exit 1
fi
await "$JOB" "uretim"

AFTER=$(count_fixtures)
say "Sonuc"
echo "fixture: $BEFORE -> $AFTER"
if [ "$AFTER" -gt "$BEFORE" ]; then
    find "$FIXTURES" -name '*.json' | sed 's|^|  |'

    # Faz A gates everything after it, so a refused analysis leaves the three
    # prompts behind it unrecorded -- and the run still looks like a success
    # because one file appeared. Name what is missing instead.
    MISSING=''
    for prompt in job_analysis profile_extraction bullet_rewrite about_synthesis cover_letter; do
        [ -d "$FIXTURES/$prompt" ] || MISSING="$MISSING $prompt"
    done
    if [ -n "$MISSING" ]; then
        echo
        warn "Kaydedilmeyenler:$MISSING"
        warn "job_analysis eksikse Faz A kapida reddedilmistir (LOW_CONFIDENCE) ya da"
        warn "istek eski bir ise dusmustur. Gercek bir ilan dosyasiyla tekrar dene:"
        warn "  ./scripts/dev-record.sh $CV ~/Desktop/ilan.txt"
    else
        echo
        echo "Bes promptun besi de kayitli."
    fi
    echo
    echo "Simdi: sh ./gradlew latexTest   (48/48 bekleniyor)"
    echo "Ve kullandigin ilani sakla -- fixture anahtari tam o metnin ozetinden turuyor."
else
    warn "Hicbir sey kaydedilmedi. Neredeyse kesin sebep: backend local-record"
    warn "profilinde degil. Ctrl-C ile durdur ve 'make record' ile baslat"
    warn "('make dev' local-fake'tir ve gercek cagri yapmaz)."
fi
