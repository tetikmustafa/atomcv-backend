#!/usr/bin/env bash
# Measures a template's fixed costs against the real compiler (Bolum 26.4,
# EK C.2's "Kapasite olculdu" and "Sabit maliyetler olculdu" boxes).
#
#   ./scripts/measure-template.sh classic
#   ./scripts/measure-template.sh compact --font sans --size 9.5 --margin 0.45
#
# Runs `latexTest`'s calibration against a real XeLaTeX image and prints the
# seventeen numbers the selection budget is built from. Nothing is written to
# the database: this is the check a person runs after changing a preamble, and
# the numbers it prints are what belong in TemplateRegistry.
#
# **It needs Docker** and builds the LaTeX image, which takes minutes the first
# time. That is not incidental -- Bolum 26 exists because a measured height and
# an estimated one are different things, and there is no way to measure one
# without the compiler that produces it.
set -euo pipefail

cd "$(dirname "$0")/.."

TEMPLATE=${1:-}
[ -n "$TEMPLATE" ] || {
    echo "usage: $0 <classic|compact|modern> [--font F] [--size N] [--margin N] [--spacing N]" >&2
    exit 2
}
shift

FONT=""
SIZE=""
MARGIN=""
SPACING=""
while [ $# -gt 0 ]; do
    case "$1" in
        --font)    FONT=$2; shift 2 ;;
        --size)    SIZE=$2; shift 2 ;;
        --margin)  MARGIN=$2; shift 2 ;;
        --spacing) SPACING=$2; shift 2 ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
done

# Passed through as system properties rather than as arguments: the measurement
# runs inside a test JVM, and `-D` is the one channel Gradle already forwards
# (the same mechanism `-Dgolden.record=true` uses).
PROPS="-Dmeasure.template=$TEMPLATE"
[ -n "$FONT" ]    && PROPS="$PROPS -Dmeasure.font=$FONT"
[ -n "$SIZE" ]    && PROPS="$PROPS -Dmeasure.size=$SIZE"
[ -n "$MARGIN" ]  && PROPS="$PROPS -Dmeasure.margin=$MARGIN"
[ -n "$SPACING" ] && PROPS="$PROPS -Dmeasure.spacing=$SPACING"

printf '\n\033[1mMeasuring %s against the real compiler. The first run builds the image.\033[0m\n\n' \
    "$TEMPLATE"

# shellcheck disable=SC2086
exec sh ./gradlew latexTest --tests '*TemplateMeasurementRunIT' $PROPS
