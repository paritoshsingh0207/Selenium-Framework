#!/usr/bin/env bash
set -euo pipefail

ENGINE="${ENGINE:-selenium}"
BROWSER="${BROWSER:-chrome}"
DATA_SOURCE="${DATA_SOURCE:-feature}"
HEADLESS="${HEADLESS:-false}"
THREADS="${THREADS:-1}"

if [ "$THREADS" -le 1 ]; then
  PARALLEL_MODE="none"
else
  PARALLEL_MODE="methods"
fi

mvn clean test \
  -Dengine="$ENGINE" \
  -Dbrowser="$BROWSER" \
  -Ddata.source="$DATA_SOURCE" \
  -Dheadless="$HEADLESS" \
  -Dparallel.mode="$PARALLEL_MODE" \
  -Dthread.count="$THREADS" \
  -Ddataprovider.thread.count="$THREADS"
