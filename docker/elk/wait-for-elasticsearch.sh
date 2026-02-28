#!/usr/bin/env bash
# ============================================================
# Wait for Elasticsearch to be ready
# Usage: ./wait-for-elasticsearch.sh [host] [port] [timeout_seconds]
# ============================================================

set -euo pipefail

ES_HOST="${1:-localhost}"
ES_PORT="${2:-9200}"
TIMEOUT="${3:-120}"
INTERVAL=5

echo "Waiting for Elasticsearch at ${ES_HOST}:${ES_PORT} (timeout: ${TIMEOUT}s)..."

elapsed=0
while [ "$elapsed" -lt "$TIMEOUT" ]; do
    if curl -sf "http://${ES_HOST}:${ES_PORT}/_cluster/health" \
        -o /dev/null 2>/dev/null; then
        echo "Elasticsearch is ready (${elapsed}s elapsed)."

        # Check cluster health status
        STATUS=$(curl -sf "http://${ES_HOST}:${ES_PORT}/_cluster/health" \
            | grep -o '"status":"[^"]*"' \
            | cut -d'"' -f4)
        echo "Cluster health: ${STATUS}"
        exit 0
    fi

    echo "  ... not ready yet (${elapsed}s / ${TIMEOUT}s)"
    sleep "$INTERVAL"
    elapsed=$((elapsed + INTERVAL))
done

echo "ERROR: Elasticsearch not ready after ${TIMEOUT}s"
exit 1
