# Story S7-007: Add ELK Stack to Docker Compose

> 5 points | Priority: P2 | Service: infrastructure (Docker)
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The monitoring chain for Family Hobbies Manager progresses from structured JSON logging (S7-005) through Spring Actuator metrics (S7-006) to centralized log aggregation. Without a log aggregation solution, debugging production issues across six microservices requires SSH-ing into individual containers and grepping through log files -- an approach that does not scale and makes cross-service correlation impossible. This story adds Elasticsearch, Logstash, and Kibana (the ELK stack) to the Docker Compose infrastructure, gated behind a `monitoring` profile so it is optional for development but available for integration testing and demo. Logstash receives structured JSON logs from all services via TCP (port 5044), parses and enriches them, and forwards to Elasticsearch (port 9200) for indexing. Kibana (port 5601) provides the visual interface for searching, filtering, and dashboarding. The Logstash pipeline is configured to parse the JSON log format established in S7-005 (logstash-logback-encoder), extract MDC fields (traceId, userId, serviceName), and create a daily index pattern (`family-hobbies-YYYY.MM.dd`). A pre-built Kibana saved objects export provides an index pattern, a saved search for errors, and a basic dashboard with log volume over time, error rate by service, and top 10 error messages. Each service's `logback-spring.xml` (from S7-005) is extended with a Logstash TCP appender active only in the `docker` profile. Elasticsearch runs in single-node mode with xpack security disabled for development simplicity.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Docker Compose ELK services | `docker/docker-compose.yml` | Elasticsearch, Logstash, Kibana services with `monitoring` profile | `docker compose --profile monitoring up -d` starts ELK |
| 2 | Logstash pipeline config | `docker/elk/logstash/pipeline/logstash.conf` | TCP input, JSON filter, Elasticsearch output | Logs appear in Elasticsearch |
| 3 | Kibana saved objects | `docker/elk/kibana/export.ndjson` | Index pattern, saved search, dashboard | Import via Kibana API |
| 4 | Service logback TCP appender | `backend/{service}/src/main/resources/logback-spring.xml` | Logstash TCP appender in docker profile | JSON logs sent to Logstash |
| 5 | Elasticsearch wait-for script | `docker/elk/wait-for-elasticsearch.sh` | Health check script for service startup ordering | Logstash waits for ES to be ready |
| 6 | Documentation -- usage instructions | Section in this file | How to start, verify, search, and use dashboards | Follow instructions successfully |

---

## Task 1 Detail: Docker Compose ELK Services

- **What**: Add Elasticsearch 8.x (single-node), Logstash, and Kibana to the existing `docker/docker-compose.yml` using the `monitoring` profile. This ensures ELK only starts when explicitly requested (`docker compose --profile monitoring up`), keeping the default developer experience lightweight. All three services share a dedicated `elk` network and a `monitoring` profile. Elasticsearch uses a named volume for index persistence across restarts.
- **Where**: `docker/docker-compose.yml`
- **Why**: The `monitoring` profile pattern avoids resource waste during normal development (ELK requires ~2 GB RAM). The single-node Elasticsearch configuration is appropriate for development and demo; production would use a multi-node cluster. xpack security is disabled because the ELK stack is only accessible on the internal Docker network (not exposed to the internet in development).
- **Content**:

```yaml
# ============================================================
# ELK Stack -- activated with: docker compose --profile monitoring up
# ============================================================

# Add to the 'services' section of docker/docker-compose.yml:

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.2
    container_name: fhm-elasticsearch
    profiles:
      - monitoring
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - xpack.security.enrollment.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
      - cluster.name=family-hobbies
      - bootstrap.memory_lock=true
    ulimits:
      memlock:
        soft: -1
        hard: -1
    volumes:
      - elasticsearch-data:/usr/share/elasticsearch/data
    ports:
      - "9200:9200"
    networks:
      - fhm-network
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:9200/_cluster/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 30
      start_period: 30s

  logstash:
    image: docker.elastic.co/logstash/logstash:8.12.2
    container_name: fhm-logstash
    profiles:
      - monitoring
    volumes:
      - ./elk/logstash/pipeline/logstash.conf:/usr/share/logstash/pipeline/logstash.conf:ro
    ports:
      - "5044:5044"
      - "9600:9600"
    environment:
      - LS_JAVA_OPTS=-Xms256m -Xmx256m
      - xpack.monitoring.enabled=false
    networks:
      - fhm-network
    depends_on:
      elasticsearch:
        condition: service_healthy

  kibana:
    image: docker.elastic.co/kibana/kibana:8.12.2
    container_name: fhm-kibana
    profiles:
      - monitoring
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
      - xpack.security.enabled=false
      - server.name=fhm-kibana
      - telemetry.optIn=false
    ports:
      - "5601:5601"
    networks:
      - fhm-network
    depends_on:
      elasticsearch:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:5601/api/status || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 30
      start_period: 60s

# Add to the 'volumes' section:
# volumes:
#   elasticsearch-data:
#     driver: local

# Add to the 'networks' section (if not already present):
# networks:
#   fhm-network:
#     driver: bridge
```

- **Verify**: `docker compose --profile monitoring up -d` -> all 3 containers start; `curl http://localhost:9200/_cluster/health` -> returns `{"status":"green"}` or `{"status":"yellow"}`

---

## Task 2 Detail: Logstash Pipeline Configuration

- **What**: Logstash pipeline configuration that receives structured JSON logs from all services via TCP on port 5044, parses the JSON payload, extracts key fields for Elasticsearch indexing, and forwards to Elasticsearch with daily index rotation. The pipeline handles the JSON format produced by `logstash-logback-encoder` (S7-005), which includes timestamp, level, logger_name, message, thread_name, and MDC fields (traceId, userId, serviceName).
- **Where**: `docker/elk/logstash/pipeline/logstash.conf`
- **Why**: Logstash serves as the log ingestion layer, normalizing and enriching log data before indexing. The TCP input is simpler than Beats for a development setup and works directly with Logback's TCP appender. The JSON filter parses the structured log format, and the mutate filter promotes MDC fields to top-level for efficient Elasticsearch querying. The daily index pattern enables time-based retention policies.
- **Content**:

```ruby
# ============================================================
# Logstash Pipeline: Family Hobbies Manager
# ============================================================
# Input:  TCP port 5044 (structured JSON from logstash-logback-encoder)
# Filter: JSON parse, field promotion, timestamp normalization
# Output: Elasticsearch with daily index rotation
# ============================================================

input {
  tcp {
    port => 5044
    codec => json_lines {
      charset => "UTF-8"
    }
    type => "application-log"
  }
}

filter {
  # --------------------------------------------------------
  # 1. Parse the JSON payload if not already parsed by codec
  # --------------------------------------------------------
  if [message] =~ /^\{/ {
    json {
      source => "message"
      target => "parsed"
      skip_on_invalid_json => true
    }

    if [parsed] {
      mutate {
        replace => {
          "message" => "%{[parsed][message]}"
        }
      }
    }
  }

  # --------------------------------------------------------
  # 2. Promote MDC fields to top-level for efficient queries
  # --------------------------------------------------------
  if [mdc] {
    mutate {
      rename => {
        "[mdc][traceId]"     => "traceId"
        "[mdc][userId]"      => "userId"
        "[mdc][serviceName]" => "serviceName"
        "[mdc][spanId]"      => "spanId"
        "[mdc][requestUri]"  => "requestUri"
        "[mdc][httpMethod]"  => "httpMethod"
      }
      remove_field => ["mdc"]
    }
  }

  # --------------------------------------------------------
  # 3. Normalize timestamp to ISO-8601
  # --------------------------------------------------------
  if [@timestamp] {
    date {
      match => ["@timestamp", "ISO8601"]
      target => "@timestamp"
    }
  }

  # --------------------------------------------------------
  # 4. Extract service name from logger if not in MDC
  # --------------------------------------------------------
  if ![serviceName] and [logger_name] {
    grok {
      match => {
        "logger_name" => "com\.familyhobbies\.(?<serviceName>[a-z]+service)\."
      }
      tag_on_failure => []
    }
  }

  # --------------------------------------------------------
  # 5. Add log level as keyword for aggregations
  # --------------------------------------------------------
  if [level] {
    mutate {
      add_field => { "log_level" => "%{level}" }
    }
  }

  # --------------------------------------------------------
  # 6. Parse stack trace for error logs
  # --------------------------------------------------------
  if [stack_trace] {
    mutate {
      add_field => { "has_exception" => "true" }
    }
  } else {
    mutate {
      add_field => { "has_exception" => "false" }
    }
  }

  # --------------------------------------------------------
  # 7. Clean up internal fields
  # --------------------------------------------------------
  mutate {
    remove_field => ["parsed", "host", "port", "type"]
  }
}

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    index => "family-hobbies-%{+YYYY.MM.dd}"
    document_type => "_doc"
  }

  # Debug: also output to Logstash stdout (remove in production)
  stdout {
    codec => rubydebug {
      metadata => false
    }
  }
}
```

- **Verify**: After services send logs, query `curl http://localhost:9200/family-hobbies-*/_count` -> returns count > 0

---

## Task 3 Detail: Kibana Saved Objects

- **What**: Pre-configured Kibana saved objects exported as NDJSON for automated import. Includes: (1) an index pattern `family-hobbies-*` with timestamp as the time field, (2) a saved search "All Errors" filtering `log_level:ERROR`, (3) a dashboard "Family Hobbies Overview" with three visualizations: log volume over time (date histogram), error rate by service (pie chart), and top 10 error messages (data table).
- **Where**: `docker/elk/kibana/export.ndjson`
- **Why**: Pre-built Kibana objects provide immediate value on first startup, eliminating manual configuration. The NDJSON format is Kibana's standard import/export format and can be loaded via the Saved Objects API (`POST /api/saved_objects/_import`). This ensures a repeatable, version-controlled dashboard setup.
- **Content**:

```json
{"id":"family-hobbies-*","type":"index-pattern","attributes":{"title":"family-hobbies-*","timeFieldName":"@timestamp","fields":"[]"},"references":[],"migrationVersion":{"index-pattern":"8.0.0"}}
{"id":"fhm-search-errors","type":"search","attributes":{"title":"All Errors","description":"Search for all ERROR-level log entries across services","kibanaSavedObjectMeta":{"searchSourceJSON":"{\"query\":{\"bool\":{\"must\":[{\"match\":{\"log_level\":\"ERROR\"}}]}},\"filter\":[],\"indexRefName\":\"kibanaSavedObjectMeta.searchSourceJSON.index\"}"},"sort":[["@timestamp","desc"]],"columns":["serviceName","message","traceId","stack_trace"]},"references":[{"id":"family-hobbies-*","name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern"}]}
{"id":"fhm-vis-log-volume","type":"visualization","attributes":{"title":"Log Volume Over Time","visState":"{\"title\":\"Log Volume Over Time\",\"type\":\"histogram\",\"aggs\":[{\"id\":\"1\",\"enabled\":true,\"type\":\"count\",\"params\":{}},{\"id\":\"2\",\"enabled\":true,\"type\":\"date_histogram\",\"params\":{\"field\":\"@timestamp\",\"calendar_interval\":\"1h\",\"min_doc_count\":0}}],\"params\":{\"type\":\"histogram\",\"grid\":{\"categoryLines\":false},\"categoryAxes\":[{\"id\":\"CategoryAxis-1\",\"type\":\"category\",\"position\":\"bottom\"}],\"valueAxes\":[{\"id\":\"ValueAxis-1\",\"name\":\"LeftAxis-1\",\"type\":\"value\",\"position\":\"left\"}]}}","kibanaSavedObjectMeta":{"searchSourceJSON":"{\"query\":{\"match_all\":{}},\"filter\":[],\"indexRefName\":\"kibanaSavedObjectMeta.searchSourceJSON.index\"}"}},"references":[{"id":"family-hobbies-*","name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern"}]}
{"id":"fhm-vis-error-by-service","type":"visualization","attributes":{"title":"Error Rate by Service","visState":"{\"title\":\"Error Rate by Service\",\"type\":\"pie\",\"aggs\":[{\"id\":\"1\",\"enabled\":true,\"type\":\"count\",\"params\":{}},{\"id\":\"2\",\"enabled\":true,\"type\":\"terms\",\"params\":{\"field\":\"serviceName.keyword\",\"size\":10,\"order\":\"desc\",\"orderBy\":\"1\"}}],\"params\":{\"type\":\"pie\",\"addTooltip\":true,\"addLegend\":true,\"legendPosition\":\"right\",\"isDonut\":false}}","kibanaSavedObjectMeta":{"searchSourceJSON":"{\"query\":{\"bool\":{\"must\":[{\"match\":{\"log_level\":\"ERROR\"}}]}},\"filter\":[],\"indexRefName\":\"kibanaSavedObjectMeta.searchSourceJSON.index\"}"}},"references":[{"id":"family-hobbies-*","name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern"}]}
{"id":"fhm-vis-top-errors","type":"visualization","attributes":{"title":"Top 10 Error Messages","visState":"{\"title\":\"Top 10 Error Messages\",\"type\":\"table\",\"aggs\":[{\"id\":\"1\",\"enabled\":true,\"type\":\"count\",\"params\":{}},{\"id\":\"2\",\"enabled\":true,\"type\":\"terms\",\"params\":{\"field\":\"message.keyword\",\"size\":10,\"order\":\"desc\",\"orderBy\":\"1\"}},{\"id\":\"3\",\"enabled\":true,\"type\":\"terms\",\"params\":{\"field\":\"serviceName.keyword\",\"size\":10,\"order\":\"desc\",\"orderBy\":\"1\"}}],\"params\":{\"perPage\":10,\"showPartialRows\":false,\"showMetricsAtAllLevels\":false,\"showTotal\":false,\"totalFunc\":\"sum\"}}","kibanaSavedObjectMeta":{"searchSourceJSON":"{\"query\":{\"bool\":{\"must\":[{\"match\":{\"log_level\":\"ERROR\"}}]}},\"filter\":[],\"indexRefName\":\"kibanaSavedObjectMeta.searchSourceJSON.index\"}"}},"references":[{"id":"family-hobbies-*","name":"kibanaSavedObjectMeta.searchSourceJSON.index","type":"index-pattern"}]}
{"id":"fhm-dashboard-overview","type":"dashboard","attributes":{"title":"Family Hobbies Overview","description":"Main monitoring dashboard for Family Hobbies Manager. Shows log volume, error rates, and top error messages.","panelsJSON":"[{\"version\":\"8.12.2\",\"gridData\":{\"x\":0,\"y\":0,\"w\":48,\"h\":15,\"i\":\"1\"},\"panelIndex\":\"1\",\"embeddableConfig\":{},\"panelRefName\":\"panel_0\"},{\"version\":\"8.12.2\",\"gridData\":{\"x\":0,\"y\":15,\"w\":24,\"h\":15,\"i\":\"2\"},\"panelIndex\":\"2\",\"embeddableConfig\":{},\"panelRefName\":\"panel_1\"},{\"version\":\"8.12.2\",\"gridData\":{\"x\":24,\"y\":15,\"w\":24,\"h\":15,\"i\":\"3\"},\"panelIndex\":\"3\",\"embeddableConfig\":{},\"panelRefName\":\"panel_2\"}]","optionsJSON":"{\"useMargins\":true,\"syncColors\":false,\"hidePanelTitles\":false}","timeRestore":true,"timeTo":"now","timeFrom":"now-24h","refreshInterval":{"pause":false,"value":30000},"kibanaSavedObjectMeta":{"searchSourceJSON":"{\"query\":{\"language\":\"kuery\",\"query\":\"\"},\"filter\":[]}"}},"references":[{"id":"fhm-vis-log-volume","name":"panel_0","type":"visualization"},{"id":"fhm-vis-error-by-service","name":"panel_1","type":"visualization"},{"id":"fhm-vis-top-errors","name":"panel_2","type":"visualization"}]}
```

- **Verify**: `curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" -H "kbn-xsrf: true" --form file=@docker/elk/kibana/export.ndjson` -> returns `{"success":true}`

---

## Task 4 Detail: Service Logback TCP Appender

- **What**: Extend each service's `logback-spring.xml` (created in S7-005) with a Logstash TCP appender that sends structured JSON logs to the Logstash TCP input on port 5044. The appender is active only in the `docker` profile, ensuring local development is unaffected. Uses the `LogstashTcpSocketAppender` from `logstash-logback-encoder` (already added as a dependency in S7-005).
- **Where**: `backend/{service}/src/main/resources/logback-spring.xml` (all 6 services)
- **Why**: Sending logs directly from the application to Logstash via TCP is the simplest integration path for a Docker development environment. No additional agents (Filebeat, Fluentd) are needed. The TCP appender is resilient to Logstash restarts (reconnects automatically with configurable backoff). In the `docker` profile, logs go to both stdout (for `docker logs`) and Logstash (for Kibana).
- **Content**:

Add the following block inside each service's `logback-spring.xml`, within the `<configuration>` element:

```xml
<!-- ============================================================ -->
<!-- Logstash TCP appender -- active only in docker profile       -->
<!-- Sends structured JSON to Logstash for ELK ingestion (S7-007) -->
<!-- ============================================================ -->
<springProfile name="docker">
    <appender name="LOGSTASH"
              class="net.logstash.logback.appender.LogstashTcpSocketAppender">
        <destination>${LOGSTASH_HOST:-logstash}:${LOGSTASH_PORT:-5044}</destination>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>serviceName</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>requestUri</includeMdcKeyName>
            <includeMdcKeyName>httpMethod</includeMdcKeyName>
            <customFields>
                {"application":"family-hobbies-manager","service":"${SERVICE_NAME:-unknown}"}
            </customFields>
            <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSS'Z'</timestampPattern>
        </encoder>
        <keepAliveDuration>5 minutes</keepAliveDuration>
        <reconnectionDelay>10 seconds</reconnectionDelay>
        <writeBufferSize>8192</writeBufferSize>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE_JSON" />
        <appender-ref ref="LOGSTASH" />
    </root>
</springProfile>
```

Additionally, each service's Docker Compose entry must include the `SERVICE_NAME` environment variable:

```yaml
  user-service:
    environment:
      - SERVICE_NAME=user-service
      - LOGSTASH_HOST=logstash
      - LOGSTASH_PORT=5044

  association-service:
    environment:
      - SERVICE_NAME=association-service
      - LOGSTASH_HOST=logstash
      - LOGSTASH_PORT=5044

  payment-service:
    environment:
      - SERVICE_NAME=payment-service
      - LOGSTASH_HOST=logstash
      - LOGSTASH_PORT=5044

  notification-service:
    environment:
      - SERVICE_NAME=notification-service
      - LOGSTASH_HOST=logstash
      - LOGSTASH_PORT=5044

  api-gateway:
    environment:
      - SERVICE_NAME=api-gateway
      - LOGSTASH_HOST=logstash
      - LOGSTASH_PORT=5044

  discovery-service:
    environment:
      - SERVICE_NAME=discovery-service
      - LOGSTASH_HOST=logstash
      - LOGSTASH_PORT=5044
```

- **Verify**: `docker compose --profile monitoring up -d` -> services start; `curl http://localhost:9200/family-hobbies-*/_count` -> count > 0

---

## Task 5 Detail: Elasticsearch Wait-For Script

- **What**: Shell script that waits for Elasticsearch to be ready before allowing dependent services to proceed. While Docker Compose `depends_on` with `condition: service_healthy` handles the Logstash/Kibana startup ordering, this script provides a manual verification tool for debugging and CI pipelines.
- **Where**: `docker/elk/wait-for-elasticsearch.sh`
- **Why**: Elasticsearch can take 30-60 seconds to fully initialize, especially on first startup when it creates indices. A wait script prevents race conditions in CI pipelines where services start before Elasticsearch is ready to accept connections.
- **Content**:

```bash
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
```

- **Verify**: `chmod +x docker/elk/wait-for-elasticsearch.sh && ./docker/elk/wait-for-elasticsearch.sh localhost 9200 60` -> prints "Elasticsearch is ready"

---

## Task 6 Detail: Usage Instructions

### Starting the ELK Stack

```bash
# Start all services WITH monitoring
docker compose --profile monitoring up -d

# Or start ONLY the monitoring stack (for testing)
docker compose --profile monitoring up -d elasticsearch logstash kibana
```

### Verifying Elasticsearch

```bash
# Cluster health
curl -s http://localhost:9200/_cluster/health | jq .

# Expected output:
# {
#   "cluster_name": "family-hobbies",
#   "status": "green",  (or "yellow" for single-node)
#   "number_of_nodes": 1,
#   ...
# }

# Check indices
curl -s http://localhost:9200/_cat/indices?v

# Expected: family-hobbies-YYYY.MM.dd indices listed
```

### Verifying Logstash

```bash
# Logstash API (pipeline stats)
curl -s http://localhost:9600/_node/stats/pipelines | jq .

# Expected: events.in > 0, events.out > 0
```

### Verifying Kibana

```bash
# Kibana status
curl -s http://localhost:5601/api/status | jq .status.overall.state

# Expected: "available"

# Import saved objects
curl -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@docker/elk/kibana/export.ndjson

# Expected: {"success":true, ...}
```

### Searching Logs in Kibana

1. Open `http://localhost:5601` in a browser
2. Navigate to **Discover** (left sidebar)
3. Select the `family-hobbies-*` index pattern
4. Use KQL queries:

```
# All errors
log_level: "ERROR"

# Errors from a specific service
serviceName: "association-service" AND log_level: "ERROR"

# Search by trace ID (cross-service correlation)
traceId: "abc-123-def-456"

# Search by user ID
userId: "user-uuid-here"

# Batch job logs
logger_name: "com.familyhobbies.associationservice.batch.*"
```

### Viewing the Dashboard

1. Navigate to **Dashboard** (left sidebar)
2. Select **Family Hobbies Overview**
3. Set time range to **Last 24 hours**
4. Dashboard shows:
   - **Log Volume Over Time**: hourly bar chart of total log events
   - **Error Rate by Service**: pie chart showing error distribution
   - **Top 10 Error Messages**: data table with most frequent errors

### Stopping the ELK Stack

```bash
# Stop only monitoring containers
docker compose --profile monitoring down

# Stop and remove volumes (clears all indexed data)
docker compose --profile monitoring down -v
```

### Ports Reference

| Service       | Port | Purpose |
|---------------|------|---------|
| Elasticsearch | 9200 | REST API, index management |
| Logstash TCP  | 5044 | Log ingestion from services |
| Logstash API  | 9600 | Monitoring Logstash itself |
| Kibana        | 5601 | Web UI for searching and dashboards |

### Memory Requirements

| Service       | Heap Size | Total RAM (approx.) |
|---------------|-----------|---------------------|
| Elasticsearch | 512 MB    | ~1 GB |
| Logstash      | 256 MB    | ~500 MB |
| Kibana        | default   | ~500 MB |
| **Total ELK** |           | **~2 GB** |

---

## Acceptance Criteria Checklist

- [ ] ELK stack starts with `docker compose --profile monitoring up -d`
- [ ] ELK stack does NOT start with default `docker compose up -d` (profile gating works)
- [ ] Elasticsearch is accessible at `http://localhost:9200` with cluster health green/yellow
- [ ] Elasticsearch runs in single-node mode with xpack security disabled
- [ ] Logstash receives TCP connections on port 5044
- [ ] Logstash pipeline parses JSON logs and forwards to Elasticsearch
- [ ] Logstash promotes MDC fields (traceId, userId, serviceName) to top-level
- [ ] Kibana is accessible at `http://localhost:5601`
- [ ] Kibana saved objects import successfully (index pattern, saved search, dashboard)
- [ ] Logs from all 6 services appear in Kibana Discover
- [ ] Search by service name works (`serviceName: "association-service"`)
- [ ] Search by trace ID works (`traceId: "abc-123"`)
- [ ] Filter by log level works (`log_level: "ERROR"`)
- [ ] Dashboard "Family Hobbies Overview" renders with 3 visualizations
- [ ] Daily index pattern `family-hobbies-YYYY.MM.dd` is created
- [ ] Each service's logback-spring.xml has Logstash TCP appender in docker profile
- [ ] Service environment variables (SERVICE_NAME, LOGSTASH_HOST, LOGSTASH_PORT) are configured in Docker Compose
