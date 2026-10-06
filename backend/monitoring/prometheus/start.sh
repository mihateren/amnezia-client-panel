#!/bin/sh
set -eu
umask 077
: "${METRICS_TOKEN:?METRICS_TOKEN must be configured}"
printf '%s' "$METRICS_TOKEN" > /tmp/metrics-token
unset METRICS_TOKEN
exec /bin/prometheus --config.file=/etc/prometheus/prometheus.yml --storage.tsdb.path=/prometheus --storage.tsdb.retention.time=15d
