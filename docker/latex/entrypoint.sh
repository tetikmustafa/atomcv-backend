#!/bin/sh
# The service itself runs unrestricted by rlimits; each compilation is limited
# on its own (see run-xelatex.sh). The container's cpu and memory limits are
# what bound the process as a whole.
set -eu

exec java -XX:MaxRAMPercentage=50 -jar /opt/server.jar
