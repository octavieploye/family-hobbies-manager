# Sprint 7 Verification Checklist

> Run these commands IN ORDER after all stories are implemented.
> Every command must produce the expected output.
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Prerequisites Check

```bash
# 1. Docker is running
docker info > /dev/null 2>&1 && echo "OK: Docker running" || echo "FAIL: Docker not running"

# 2. Docker Compose services are up (Kafka, PostgreSQL, Eureka)
docker compose up -d
docker compose ps
# Expected: postgres, kafka, zookeeper, discovery-service all "Up"

# 3. PostgreSQL databases exist
docker exec fhm-postgres psql -U postgres -c "\l" | grep familyhobbies
# Expected: familyhobbies_users, familyhobbies_associations,
#           familyhobbies_payments, familyhobbies_notifications

# 4. Kafka broker is reachable
docker exec fhm-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
# Expected: no error, lists existing topics (may be empty)

# 5. HelloAsso sandbox credentials are set
echo "HELLOASSO_CLIENT_ID=${HELLOASSO_CLIENT_ID:-(NOT SET)}"
echo "HELLOASSO_CLIENT_SECRET=${HELLOASSO_CLIENT_SECRET:+(SET)}"
# Expected: both variables are set (not empty)

# 6. All services compile
mvn clean compile -f backend/pom.xml
# Expected: BUILD SUCCESS
```

---

## Story S7-001: HelloAsso Sync Batch

### Build and Test

```bash
# Compile association-service
mvn compile -pl backend/association-service -f backend/pom.xml
# Expected: BUILD SUCCESS

# Run all S7-001 tests
mvn test -pl backend/association-service -f backend/pom.xml \
  -Dtest="HelloAssoSkipPolicyTest,HelloAssoItemReaderTest,HelloAssoItemProcessorTest,HelloAssoItemWriterTest,HelloAssoSyncJobConfigTest"
# Expected: Tests run: 12, Failures: 0, Errors: 0
```

### Verify Batch Beans

```bash
# Start association-service and verify batch beans are loaded
# (look for job registration in startup logs)
mvn spring-boot:run -pl backend/association-service -f backend/pom.xml \
  -Dspring-boot.run.profiles=local 2>&1 | grep -i "helloAssoSyncJob"
# Expected: "Job: [SimpleJob: [name=helloAssoSyncJob]]" or similar registration log
```

### Verify CRON Schedule

```bash
# Verify CRON expression is configured (application.yml or BatchSchedulerConfig)
grep -r "0 0 2 \* \* \*" backend/association-service/src/
# Expected: matches in BatchSchedulerConfig.java or application.yml
```

### Verify Admin Endpoint

```bash
# Trigger batch manually via REST (requires ADMIN JWT)
curl -s -X POST http://localhost:8082/admin/batch/helloasso-sync \
  -H "Authorization: Bearer ${ADMIN_JWT}" \
  -H "Content-Type: application/json" | jq .
# Expected: HTTP 202 with {"jobName":"helloAssoSyncJob","jobExecutionId":...,"status":"STARTING"}
```

### Verify DB Sync

```bash
# After batch completes, check associations in DB
docker exec fhm-postgres psql -U postgres -d familyhobbies_associations \
  -c "SELECT count(*) FROM associations WHERE last_synced_at > NOW() - INTERVAL '1 hour';"
# Expected: count > 0
```

### Verify Spring Batch Metadata

```bash
# Check batch execution history
docker exec fhm-postgres psql -U postgres -d familyhobbies_associations \
  -c "SELECT job_execution_id, status, start_time, end_time FROM batch_job_execution ORDER BY start_time DESC LIMIT 5;"
# Expected: at least 1 row with status COMPLETED
```

---

## Story S7-002: Subscription Expiry Batch

### Build and Test

```bash
# Compile common module (SubscriptionExpiredEvent)
mvn compile -pl backend/common -f backend/pom.xml
# Expected: BUILD SUCCESS

# Compile association-service
mvn compile -pl backend/association-service -f backend/pom.xml
# Expected: BUILD SUCCESS

# Run all S7-002 tests
mvn test -pl backend/association-service -f backend/pom.xml \
  -Dtest="SubscriptionExpiryProcessorTest,SubscriptionExpiryJobListenerTest,SubscriptionExpiryJobConfigTest"
# Expected: Tests run: 7, Failures: 0, Errors: 0
```

### Verify Expired Subscriptions Detected

```bash
# Insert a test subscription that expired yesterday
docker exec fhm-postgres psql -U postgres -d familyhobbies_associations \
  -c "INSERT INTO subscriptions (id, user_id, family_member_id, association_id, activity_id, status, amount, start_date, expires_at, created_at, updated_at)
      VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
              'ACTIVE', 50.00, NOW() - INTERVAL '1 year', NOW() - INTERVAL '1 day', NOW(), NOW());"

# Trigger the batch
curl -s -X POST http://localhost:8082/admin/batch/subscription-expiry \
  -H "Authorization: Bearer ${ADMIN_JWT}" | jq .
# Expected: HTTP 202 with jobExecutionId

# Wait for completion, then verify status updated
sleep 5
docker exec fhm-postgres psql -U postgres -d familyhobbies_associations \
  -c "SELECT id, status, expired_at FROM subscriptions WHERE status = 'EXPIRED' ORDER BY expired_at DESC LIMIT 5;"
# Expected: the inserted subscription now has status=EXPIRED and expired_at set
```

### Verify Kafka Events Published

```bash
# Check Kafka topic for SubscriptionExpiredEvent
docker exec fhm-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic subscription.expired \
  --from-beginning --timeout-ms 5000 2>/dev/null
# Expected: JSON events with subscriptionId, userId, associationId, expiredAt fields
```

### Verify Non-Expired Untouched

```bash
# Count active subscriptions with future expiry dates
docker exec fhm-postgres psql -U postgres -d familyhobbies_associations \
  -c "SELECT count(*) FROM subscriptions WHERE status = 'ACTIVE' AND expires_at > NOW();"
# Expected: count matches what was there before the batch ran (unchanged)
```

### Verify CRON Schedule

```bash
# Verify CRON expression for subscription expiry
grep -r "0 0 6 \* \* \*" backend/association-service/src/
# Expected: matches in BatchSchedulerConfig.java or application.yml
```

---

## Story S7-003: Payment Reconciliation Batch

### Build and Test

```bash
# Compile payment-service
mvn compile -pl backend/payment-service -f backend/pom.xml
# Expected: BUILD SUCCESS

# Run all S7-003 tests
mvn test -pl backend/payment-service -f backend/pom.xml \
  -Dtest="StalePaymentItemReaderTest,PaymentReconciliationProcessorTest,PaymentReconciliationWriterTest,HelloAssoApiSkipPolicyTest,PaymentReconciliationJobConfigTest,AdminBatchControllerTest"
# Expected: Tests run: ~30, Failures: 0, Errors: 0
```

### Verify Stale Payments Reconciled

```bash
# Insert a stale payment (INITIATED > 24h ago)
docker exec fhm-postgres psql -U postgres -d familyhobbies_payments \
  -c "INSERT INTO payments (id, user_id, amount, status, helloasso_checkout_id, initiated_at, created_at, updated_at)
      VALUES (gen_random_uuid(), gen_random_uuid(), 50.00, 'INITIATED',
              'test-checkout-id', NOW() - INTERVAL '2 days', NOW(), NOW());"

# Trigger reconciliation
curl -s -X POST http://localhost:8083/admin/batch/payment-reconciliation \
  -H "Authorization: Bearer ${ADMIN_JWT}" | jq .
# Expected: HTTP 200 with jobExecutionId

# Verify payment status updated
sleep 10
docker exec fhm-postgres psql -U postgres -d familyhobbies_payments \
  -c "SELECT id, status, reconciled_at FROM payments WHERE helloasso_checkout_id = 'test-checkout-id';"
# Expected: status is COMPLETED or FAILED (depending on HelloAsso sandbox response)
```

### Verify Admin Endpoint

```bash
curl -s -X POST http://localhost:8083/admin/batch/payment-reconciliation \
  -H "Authorization: Bearer ${ADMIN_JWT}" | jq .
# Expected: HTTP 200 with {"jobName":"paymentReconciliationJob","jobExecutionId":...}
```

### Verify CRON Schedule

```bash
grep -r "0 0 8 \* \* \*" backend/payment-service/src/
# Expected: matches in BatchSchedulerConfig.java or application.yml
```

---

## Story S7-004: RGPD Data Cleanup Batch

### Build and Test

```bash
# Compile user-service
mvn compile -pl backend/user-service -f backend/pom.xml
# Expected: BUILD SUCCESS

# Run all S7-004 tests
mvn test -pl backend/user-service -f backend/pom.xml \
  -Dtest="DeletionRequestReaderTest,RgpdCleanupProcessorTest,RgpdCleanupWriterTest,RgpdCleanupJobConfigTest"
# Expected: Tests run: ~20, Failures: 0, Errors: 0
```

### Verify Anonymization

```bash
# Insert a deletion request that matured (retention period expired)
docker exec fhm-postgres psql -U postgres -d familyhobbies_users \
  -c "INSERT INTO deletion_requests (id, user_id, requested_at, retention_expires_at, status, created_at, updated_at)
      VALUES (gen_random_uuid(), gen_random_uuid(),
              NOW() - INTERVAL '31 days', NOW() - INTERVAL '1 day', 'PENDING', NOW(), NOW());"

# Trigger RGPD cleanup
curl -s -X POST http://localhost:8081/admin/batch/rgpd-cleanup \
  -H "Authorization: Bearer ${ADMIN_JWT}" | jq .
# Expected: HTTP 202 with jobExecutionId

# Verify user data anonymized
sleep 5
docker exec fhm-postgres psql -U postgres -d familyhobbies_users \
  -c "SELECT id, email, first_name, last_name FROM users WHERE id IN (SELECT user_id FROM deletion_requests WHERE status = 'COMPLETED') LIMIT 5;"
# Expected: email/first_name/last_name are anonymized (hashed or replaced with 'ANONYMIZED')
```

### Verify Audit Log

```bash
docker exec fhm-postgres psql -U postgres -d familyhobbies_users \
  -c "SELECT * FROM rgpd_audit_log ORDER BY created_at DESC LIMIT 5;"
# Expected: audit entries for each anonymized user with action='DATA_ANONYMIZED'
```

### Verify Cross-Service Cleanup via Kafka

```bash
# Check Kafka topic for RGPD cleanup events
docker exec fhm-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic user.data.deleted \
  --from-beginning --timeout-ms 5000 2>/dev/null
# Expected: JSON events with userId and deletedAt fields
```

### Verify CRON Schedule

```bash
grep -r "0 0 3 \* \* \*" backend/user-service/src/
# Expected: matches in BatchSchedulerConfig.java or application.yml
```

---

## Story S7-005: Structured JSON Logging

### Build and Test

```bash
# Compile common module (MdcLoggingFilter, LoggingConstants)
mvn compile -pl backend/common -f backend/pom.xml
# Expected: BUILD SUCCESS

# Run S7-005 tests
mvn test -pl backend/common -f backend/pom.xml \
  -Dtest="MdcLoggingFilterTest,LoggingConstantsTest"
# Expected: Tests run: ~8, Failures: 0, Errors: 0
```

### Verify JSON Output in Docker Profile

```bash
# Start any service with docker profile and check log output
docker compose up -d association-service
docker logs fhm-association-service 2>&1 | head -5
# Expected: each line is a valid JSON object with fields:
# @timestamp, level, logger_name, message, thread_name,
# service_name (if MDC populated)

# Validate JSON format
docker logs fhm-association-service 2>&1 | head -1 | jq .
# Expected: jq parses successfully, shows structured JSON
```

### Verify MDC Fields Present

```bash
# Make a request through the gateway and check logs
curl -s http://localhost:8080/api/associations -H "X-Request-Id: test-trace-123"
docker logs fhm-association-service 2>&1 | grep "test-trace-123"
# Expected: log line contains traceId=test-trace-123 (or "traceId":"test-trace-123" in JSON)
```

### Verify Console Output in Local Profile

```bash
# Start service with local profile
mvn spring-boot:run -pl backend/association-service -f backend/pom.xml \
  -Dspring-boot.run.profiles=local 2>&1 | head -5
# Expected: human-readable log format (not JSON), like:
# 2026-02-24 10:00:00.000  INFO [association-service] --- ...
```

### Verify logback-spring.xml Exists in All Services

```bash
for service in discovery-service api-gateway user-service association-service payment-service notification-service; do
  if [ -f "backend/${service}/src/main/resources/logback-spring.xml" ]; then
    echo "OK: ${service} has logback-spring.xml"
  else
    echo "FAIL: ${service} missing logback-spring.xml"
  fi
done
# Expected: all 6 services report OK
```

---

## Story S7-006: Spring Actuator Metrics

### Build and Test

```bash
# Compile common module (health indicators, metrics)
mvn compile -pl backend/common -f backend/pom.xml
# Expected: BUILD SUCCESS

# Run S7-006 tests
mvn test -pl backend/common -f backend/pom.xml \
  -Dtest="KafkaHealthIndicatorTest,DatabaseHealthIndicatorTest,ApiMetricsFilterTest"
mvn test -pl backend/association-service -f backend/pom.xml \
  -Dtest="HelloAssoHealthIndicatorTest"
# Expected: all tests pass
```

### Verify /actuator/health

```bash
# Check health endpoint for each service
for port in 8081 8082 8083 8084; do
  echo "--- Port ${port} ---"
  curl -s "http://localhost:${port}/actuator/health" | jq .
done
# Expected per service:
# {
#   "status": "UP",
#   "components": {
#     "db": {"status": "UP", ...},
#     "kafka": {"status": "UP", ...},
#     "diskSpace": {"status": "UP", ...}
#   }
# }

# Check association-service has HelloAsso health
curl -s http://localhost:8082/actuator/health | jq '.components.helloasso'
# Expected: {"status":"UP"} (or "DOWN" if sandbox is unreachable)
```

### Verify /actuator/prometheus

```bash
# Check Prometheus scrape endpoint
for port in 8081 8082 8083 8084; do
  echo "--- Port ${port}: $(curl -sf http://localhost:${port}/actuator/prometheus | wc -l) metrics ---"
done
# Expected: each service returns hundreds of metric lines

# Check for custom counters
curl -s http://localhost:8082/actuator/prometheus | grep "api_requests_total"
# Expected: lines like api_requests_total{method="GET",uri="/api/associations",...} 42.0
```

### Verify Health Endpoint is Public

```bash
# Health should be accessible WITHOUT authentication
curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/actuator/health
# Expected: 200

# Other actuator endpoints should require auth
curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/actuator/env
# Expected: 401 or 403
```

---

## Story S7-007: ELK Stack

### Start ELK

```bash
# Start monitoring profile
docker compose --profile monitoring up -d
# Expected: elasticsearch, logstash, kibana containers start

# Wait for Elasticsearch
./docker/elk/wait-for-elasticsearch.sh localhost 9200 120
# Expected: "Elasticsearch is ready"

# Verify all 3 ELK containers are running
docker compose --profile monitoring ps | grep -E "elasticsearch|logstash|kibana"
# Expected: all 3 containers show "Up" or "running"
```

### Verify Elasticsearch

```bash
# Cluster health
curl -s http://localhost:9200/_cluster/health | jq '{status, number_of_nodes}'
# Expected: {"status":"yellow","number_of_nodes":1}
# (yellow is expected for single-node: no replica allocation)

# Check indices exist (after services have sent logs)
curl -s http://localhost:9200/_cat/indices?v | grep family-hobbies
# Expected: family-hobbies-YYYY.MM.dd with doc count > 0
```

### Verify Logstash

```bash
# Logstash pipeline stats
curl -s http://localhost:9600/_node/stats/pipelines | jq '.pipelines.main.events'
# Expected: {"in": N, "out": N, "filtered": N} where N > 0

# Verify TCP port is listening
nc -z localhost 5044 && echo "OK: Logstash TCP 5044 open" || echo "FAIL"
# Expected: OK
```

### Verify Kibana

```bash
# Kibana status
curl -s http://localhost:5601/api/status | jq '.status.overall.state'
# Expected: "available"

# Import saved objects
curl -s -X POST "http://localhost:5601/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file=@docker/elk/kibana/export.ndjson | jq .
# Expected: {"success":true, "successCount":6, ...}
```

### Search Logs in Kibana

```bash
# Search by service name via Elasticsearch API
curl -s "http://localhost:9200/family-hobbies-*/_search" \
  -H "Content-Type: application/json" \
  -d '{"query":{"match":{"serviceName":"association-service"}},"size":1}' | jq '.hits.total.value'
# Expected: > 0

# Search by trace ID
curl -s "http://localhost:9200/family-hobbies-*/_search" \
  -H "Content-Type: application/json" \
  -d '{"query":{"match":{"traceId":"test-trace-123"}},"size":1}' | jq '.hits.total.value'
# Expected: > 0 (if test-trace-123 was used in S7-005 verification)

# Filter by log level
curl -s "http://localhost:9200/family-hobbies-*/_search" \
  -H "Content-Type: application/json" \
  -d '{"query":{"match":{"log_level":"ERROR"}},"size":1}' | jq '.hits.total.value'
# Expected: >= 0 (may be 0 if no errors have occurred)

# Search by log level INFO (should have many results)
curl -s "http://localhost:9200/family-hobbies-*/_search" \
  -H "Content-Type: application/json" \
  -d '{"query":{"match":{"log_level":"INFO"}},"size":0}' | jq '.hits.total.value'
# Expected: > 0
```

### Verify Dashboard

```bash
# Dashboard exists
curl -s "http://localhost:5601/api/saved_objects/dashboard/fhm-dashboard-overview" \
  -H "kbn-xsrf: true" | jq '.attributes.title'
# Expected: "Family Hobbies Overview"

# All visualizations exist
for vis_id in fhm-vis-log-volume fhm-vis-error-by-service fhm-vis-top-errors; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    "http://localhost:5601/api/saved_objects/visualization/${vis_id}" \
    -H "kbn-xsrf: true")
  echo "${vis_id}: HTTP ${STATUS}"
done
# Expected: all return HTTP 200
```

### Verify Profile Gating

```bash
# Stop monitoring
docker compose --profile monitoring down

# Start without monitoring profile
docker compose up -d

# Verify ELK is NOT running
docker ps | grep -E "elasticsearch|logstash|kibana"
# Expected: no output (ELK containers are not running)
```

---

## End-to-End Smoke Test

This test verifies the full observability pipeline: batch job -> structured logs -> Logstash -> Elasticsearch -> Kibana.

### Step 1: Start Full Stack

```bash
# Start all services with monitoring
docker compose --profile monitoring up -d

# Wait for everything to be ready
./docker/elk/wait-for-elasticsearch.sh localhost 9200 120
sleep 30  # Allow services to fully start and send initial logs
```

### Step 2: Verify All Services Are Healthy

```bash
for port in 8761 8080 8081 8082 8083 8084; do
  STATUS=$(curl -sf "http://localhost:${port}/actuator/health" | jq -r '.status' 2>/dev/null || echo "UNREACHABLE")
  echo "Port ${port}: ${STATUS}"
done
# Expected:
# Port 8761: UP (discovery-service)
# Port 8080: UP (api-gateway)
# Port 8081: UP (user-service)
# Port 8082: UP (association-service)
# Port 8083: UP (payment-service)
# Port 8084: UP (notification-service)
```

### Step 3: Trigger All Batch Jobs

```bash
# Trigger HelloAsso sync (S7-001)
curl -s -X POST http://localhost:8082/admin/batch/helloasso-sync \
  -H "Authorization: Bearer ${ADMIN_JWT}" | jq .jobExecutionId
# Expected: numeric job execution ID

# Trigger subscription expiry (S7-002)
curl -s -X POST http://localhost:8082/admin/batch/subscription-expiry \
  -H "Authorization: Bearer ${ADMIN_JWT}" | jq .jobExecutionId
# Expected: numeric job execution ID

# Trigger payment reconciliation (S7-003)
curl -s -X POST http://localhost:8083/admin/batch/payment-reconciliation \
  -H "Authorization: Bearer ${ADMIN_JWT}" | jq .jobExecutionId
# Expected: numeric job execution ID

# Trigger RGPD cleanup (S7-004)
curl -s -X POST http://localhost:8081/admin/batch/rgpd-cleanup \
  -H "Authorization: Bearer ${ADMIN_JWT}" | jq .jobExecutionId
# Expected: numeric job execution ID

# Wait for batch jobs to complete
sleep 15
```

### Step 4: Verify Batch Logs in Kibana

```bash
# Search for batch job logs in Elasticsearch
curl -s "http://localhost:9200/family-hobbies-*/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          {"wildcard": {"logger_name": "*batch*"}},
          {"range": {"@timestamp": {"gte": "now-5m"}}}
        ]
      }
    },
    "size": 5
  }' | jq '.hits.hits[]._source | {serviceName, message, log_level}'
# Expected: batch job log entries from association-service, payment-service, user-service
```

### Step 5: Verify Kafka Events in Elasticsearch

```bash
# Search for Kafka-related logs
curl -s "http://localhost:9200/family-hobbies-*/_search" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "bool": {
        "must": [
          {"match_phrase": {"message": "Kafka"}},
          {"range": {"@timestamp": {"gte": "now-5m"}}}
        ]
      }
    },
    "size": 3
  }' | jq '.hits.total.value'
# Expected: > 0
```

### Step 6: Verify Prometheus Metrics After Batch

```bash
# Check batch-related metrics
curl -s http://localhost:8082/actuator/prometheus | grep -i batch
# Expected: Spring Batch metrics (spring.batch.job.*, spring.batch.step.*)

# Check API request counters
curl -s http://localhost:8082/actuator/prometheus | grep "api_requests_total"
# Expected: counter values reflecting the admin batch trigger requests
```

### Step 7: Verify Dashboard Data

```bash
# Open Kibana in browser
echo "Open http://localhost:5601 and navigate to Dashboard > Family Hobbies Overview"
echo "Set time range to 'Last 15 minutes'"
echo "Verify:"
echo "  - Log Volume Over Time chart shows recent activity"
echo "  - Error Rate by Service pie chart renders (may be empty if no errors)"
echo "  - Top 10 Error Messages table renders"
```

### Step 8: Cleanup

```bash
# Stop everything
docker compose --profile monitoring down

# Optional: remove volumes
docker compose --profile monitoring down -v
```

---

## Quick Reference: Service Ports

| Service              | Port | Health Check URL |
|----------------------|------|------------------|
| discovery-service    | 8761 | http://localhost:8761/actuator/health |
| api-gateway          | 8080 | http://localhost:8080/actuator/health |
| user-service         | 8081 | http://localhost:8081/actuator/health |
| association-service  | 8082 | http://localhost:8082/actuator/health |
| payment-service      | 8083 | http://localhost:8083/actuator/health |
| notification-service | 8084 | http://localhost:8084/actuator/health |
| Elasticsearch        | 9200 | http://localhost:9200/_cluster/health |
| Logstash TCP         | 5044 | `nc -z localhost 5044` |
| Logstash API         | 9600 | http://localhost:9600/ |
| Kibana               | 5601 | http://localhost:5601/api/status |

## Quick Reference: Kafka Topics (Sprint 7)

| Topic | Publisher | Consumer | Story |
|-------|-----------|----------|-------|
| `family-hobbies.association.synced` | association-service | notification-service | S7-001 |
| `subscription.expired` | association-service | notification-service | S7-002 |
| `payment.completed` | payment-service | notification-service | S7-003 |
| `payment.failed` | payment-service | notification-service | S7-003 |
| `user.data.deleted` | user-service | association-service, payment-service, notification-service | S7-004 |

## Quick Reference: CRON Schedules

| Job | CRON | Time | Service | Story |
|-----|------|------|---------|-------|
| helloAssoSyncJob | `0 0 2 * * *` | 02:00 daily | association-service | S7-001 |
| subscriptionExpiryJob | `0 0 6 * * *` | 06:00 daily | association-service | S7-002 |
| paymentReconciliationJob | `0 0 8 * * *` | 08:00 daily | payment-service | S7-003 |
| rgpdCleanupJob | `0 0 3 * * *` | 03:00 daily | user-service | S7-004 |

---

**Sprint 7 is DONE when all checks above pass.**
