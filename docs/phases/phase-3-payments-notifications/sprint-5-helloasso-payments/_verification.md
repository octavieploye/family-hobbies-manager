# Sprint 5 Verification Checklist

> Run these commands IN ORDER after all stories are implemented.
> Every command must produce the expected output.
> Sprint 5 covers: S5-001 through S5-006 across association-service, payment-service, and frontend.

---

## Prerequisites Check

### 1. Infrastructure is running

```bash
docker compose -f docker/docker-compose.yml ps
```

Expected: `discovery-service`, `postgresql`, `kafka`, `zookeeper` containers are **Up**.

### 2. Databases exist

```bash
docker exec -it postgresql psql -U postgres -c "\l" | grep familyhobbies
```

Expected: `familyhobbies_associations` and `familyhobbies_payments` databases listed.

### 3. Environment variables are set

```bash
echo "CLIENT_ID=$HELLOASSO_CLIENT_ID" && echo "SECRET=$HELLOASSO_CLIENT_SECRET" && echo "WEBHOOK_SECRET=$HELLOASSO_WEBHOOK_SECRET"
```

Expected: All three variables are non-empty. If using sandbox, values should correspond to HelloAsso sandbox credentials.

### 4. Kafka topics exist

```bash
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list | grep -E "payment|notification"
```

Expected: `payment-events` and `notification-events` topics listed (or auto-created on first publish).

### 5. Eureka registry is accessible

```bash
curl -s http://localhost:8761/eureka/apps | head -5
```

Expected: XML response with `<applications>` root element.

---

## Build Verification

### 6. error-handling module compiles

```bash
cd backend && mvn clean compile -pl error-handling -q
```

Expected: `BUILD SUCCESS`

### 7. common module compiles

```bash
cd backend && mvn clean compile -pl common -q
```

Expected: `BUILD SUCCESS`

### 8. association-service compiles (S5-001, S5-002, S5-003)

```bash
cd backend && mvn clean compile -pl association-service -q
```

Expected: `BUILD SUCCESS` -- confirms HelloAssoTokenManager, HelloAssoClient, Resilience4jConfig, AssociationSyncService, AdminSyncController all compile.

### 9. payment-service compiles (S5-004, S5-005)

```bash
cd backend && mvn clean compile -pl payment-service -q
```

Expected: `BUILD SUCCESS` -- confirms Payment/Invoice entities, PaymentController, WebhookController, HelloAssoCheckoutClient, HelloAssoWebhookHandler, WebhookSignatureValidator, PaymentEventPublisher all compile.

### 10. Frontend compiles (S5-006)

```bash
cd frontend && npx ng build --configuration=production
```

Expected: Build succeeds with no errors. Bundle includes lazy-loaded payments chunk.

---

## Database Migration Verification

### 11. association-service Liquibase migrations run clean

```bash
cd backend && mvn liquibase:update -pl association-service \
  -Dliquibase.url=jdbc:postgresql://localhost:5432/familyhobbies_associations \
  -Dliquibase.username=postgres \
  -Dliquibase.password=postgres
```

Expected: All changesets applied, no errors. Verify `t_association` table exists with `helloasso_slug` and `last_synced_at` columns from S5-003.

### 12. payment-service Liquibase migrations run clean

```bash
cd backend && mvn liquibase:update -pl payment-service \
  -Dliquibase.url=jdbc:postgresql://localhost:5432/familyhobbies_payments \
  -Dliquibase.username=postgres \
  -Dliquibase.password=postgres
```

Expected: All three changesets applied:
- `001-create-payment-table.yaml` -- `t_payment` table with indexes
- `002-create-invoice-table.yaml` -- `t_invoice` table with FK to `t_payment`
- `003-create-payment-webhook-log-table.yaml` -- `t_payment_webhook_log` table

### 13. Verify payment tables exist

```bash
docker exec -it postgresql psql -U postgres -d familyhobbies_payments \
  -c "\dt" | grep -E "t_payment|t_invoice|t_payment_webhook_log"
```

Expected: All three tables listed.

---

## Test Verification

### 14. association-service unit tests pass (S5-001, S5-002, S5-003)

```bash
cd backend && mvn test -pl association-service -q
```

Expected: All tests pass. Key test classes:
- `HelloAssoTokenManagerTest` (S5-001) -- 8 tests: token acquisition, refresh, caching, error handling
- `HelloAssoClientTest` (S5-002) -- 10 tests: directory search, org details, circuit breaker, retry, WireMock integration
- `AssociationSyncServiceImplTest` (S5-003) -- 8 tests: full sync, delta sync, error recovery, admin endpoint

### 15. payment-service unit tests pass (S5-004, S5-005)

```bash
cd backend && mvn test -pl payment-service -q
```

Expected: All tests pass. Key test classes:
- `PaymentServiceImplTest` (S5-004) -- 12 tests: checkout initiation, duplicate detection, payment query, family listing
- `HelloAssoCheckoutClientTest` (S5-004) -- 6 tests: checkout session creation, error handling, timeout
- `PaymentMapperTest` (S5-004) -- 5 tests: entity-to-DTO mapping, null handling
- `WebhookSignatureValidatorTest` (S5-005) -- 7 tests: HMAC validation, missing signature, dev mode
- `HelloAssoWebhookHandlerTest` (S5-005) -- 9 tests: payment completion, failure, idempotency, unknown events
- `PaymentEventPublisherTest` (S5-005) -- 4 tests: PaymentCompletedEvent, PaymentFailedEvent, Kafka send failure

### 16. Frontend Jest tests pass (S5-006)

```bash
cd frontend && npx jest --ci --coverage
```

Expected: All 23 tests pass:
- `payment-list.component.spec.ts` -- 6 tests
- `checkout-redirect.component.spec.ts` -- 5 tests
- `payment.service.spec.ts` -- 4 tests
- `payment.reducer.spec.ts` -- 8 tests

### 17. Frontend specific test files run individually

```bash
cd frontend && npx jest --testPathPattern="payment-list.component.spec"
cd frontend && npx jest --testPathPattern="checkout-redirect.component.spec"
cd frontend && npx jest --testPathPattern="payment.service.spec"
cd frontend && npx jest --testPathPattern="payment.reducer.spec"
```

Expected: Each command passes with 0 failures.

---

## Integration Verification

### 18. HelloAsso OAuth2 token acquisition (S5-001)

```bash
# Start association-service, then check Actuator health
curl -s http://localhost:8082/actuator/health | jq '.components.helloAsso'
```

Expected: `"status": "UP"` -- confirms HelloAssoTokenManager successfully acquired a sandbox token.

### 19. HelloAsso directory search (S5-002)

```bash
# Search for associations via association-service API (proxied through HelloAsso sandbox)
curl -s http://localhost:8082/api/v1/associations/search?query=judo&city=Lyon | jq '.totalCount'
```

Expected: Numeric response (may be 0 on sandbox, but no 401/500 error). Confirms HelloAssoClient circuit breaker is CLOSED and API calls pass through.

### 20. Association sync (S5-003)

```bash
# Trigger manual sync via admin endpoint
curl -s -X POST http://localhost:8082/api/v1/admin/sync \
  -H "Content-Type: application/json" \
  -H "X-User-Roles: ADMIN" | jq '.'
```

Expected: JSON response with `synced`, `created`, `updated`, `failed` counts. No 500 error.

### 21. Circuit breaker health (S5-002)

```bash
curl -s http://localhost:8082/actuator/health | jq '.components.circuitBreakers'
```

Expected: `helloAssoClient` circuit breaker in `CLOSED` state.

### 22. Kafka connectivity (S5-005)

```bash
# Verify payment-service can reach Kafka
curl -s http://localhost:8083/actuator/health | jq '.components.kafka'
```

Expected: `"status": "UP"`.

### 23. Payment checkout initiation (S5-004)

```bash
curl -s -X POST http://localhost:8083/api/v1/payments/checkout \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <valid-jwt-token>" \
  -H "X-User-Id: user-001" \
  -d '{
    "subscriptionId": "sub-001",
    "amount": 15000,
    "description": "Adhésion Judo Club Lyon - Sophie Martin",
    "paymentType": "ADHESION",
    "returnUrl": "http://localhost:4200/payments/checkout/redirect?status=success",
    "cancelUrl": "http://localhost:4200/payments/checkout/redirect?status=cancelled"
  }' | jq '.'
```

Expected: JSON with `paymentId`, `checkoutUrl` (pointing to HelloAsso sandbox), `status: "PENDING"`.

### 24. Payment query (S5-004)

```bash
curl -s http://localhost:8083/api/v1/payments/family/famille-dupont-001 \
  -H "Authorization: Bearer <valid-jwt-token>" \
  -H "X-User-Id: user-001" | jq '.content | length'
```

Expected: Numeric count of payments for the family (>= 1 after checkout test).

### 25. Webhook signature validation (S5-005)

```bash
# Send a test webhook with invalid signature — should return 200 (always ack) but log as invalid
curl -s -X POST http://localhost:8083/api/v1/payments/webhook/helloasso \
  -H "Content-Type: application/json" \
  -H "X-HelloAsso-Signature: sha256=invalid" \
  -d '{"eventType":"Payment","data":{"id":99999}}' \
  -w "\nHTTP_CODE: %{http_code}\n"
```

Expected: `HTTP_CODE: 200` (webhook endpoint always returns 200). Check `t_payment_webhook_log` for `processed=false` entry.

### 26. Webhook full flow (S5-005)

```bash
# Simulate a valid webhook (generate HMAC with webhook secret)
BODY='{"eventType":"Payment","data":{"id":12345,"amount":15000,"payer":{"email":"sophie@famille-dupont.fr"},"metadata":{"checkoutId":"ha-checkout-abc123"}}}'
SIGNATURE=$(echo -n "$BODY" | openssl dgst -sha256 -hmac "$HELLOASSO_WEBHOOK_SECRET" | awk '{print $2}')

curl -s -X POST http://localhost:8083/api/v1/payments/webhook/helloasso \
  -H "Content-Type: application/json" \
  -H "X-HelloAsso-Signature: sha256=$SIGNATURE" \
  -d "$BODY" | jq '.'
```

Expected: `{"status": "received", "eventId": "..."}`. Verify:
1. `t_payment_webhook_log` has a `processed=true` entry
2. Kafka `payment-events` topic received a `PaymentCompletedEvent`

### 27. Kafka event verification (S5-005)

```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment-events \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 10000
```

Expected: JSON message with `eventType: "PaymentCompleted"`, `paymentId`, `amount`, `familyId`.

---

## Frontend Integration Verification

### 28. Frontend serves and routes resolve

```bash
cd frontend && npx ng serve &
sleep 10
curl -s -o /dev/null -w "%{http_code}" http://localhost:4200/payments
```

Expected: `200` -- confirms the `/payments` route resolves and the lazy-loaded chunk loads.

### 29. Payment list renders (manual)

Open `http://localhost:4200/payments` in a browser.

Expected:
- Page title: "Mes paiements"
- Status filter dropdown is visible
- Table headers: Date, Membre, Association, Activité, Montant, Statut, Actions
- If no payments exist, empty state message: "Aucun paiement trouvé."

### 30. Checkout redirect handles success (manual)

Open `http://localhost:4200/payments/checkout/redirect?status=success&paymentId=pay-001` in a browser.

Expected:
- Brief loading spinner with "Traitement du paiement en cours..."
- Then success card with green check icon
- Title: "Paiement réussi"
- "Voir le détail du paiement" button present

### 31. Checkout redirect handles error (manual)

Open `http://localhost:4200/payments/checkout/redirect?status=error` in a browser.

Expected:
- Brief loading spinner, then error card with red icon
- Title: "Paiement échoué"
- "Retour aux paiements" button present

---

## End-to-End Smoke Test

### 32. Full checkout flow (manual walkthrough)

This test validates the complete user journey from payment initiation to return handling:

**Steps:**

1. **Start all services**:
   ```bash
   docker compose -f docker/docker-compose.yml up -d
   cd backend && mvn spring-boot:run -pl discovery-service &
   cd backend && mvn spring-boot:run -pl api-gateway &
   cd backend && mvn spring-boot:run -pl association-service &
   cd backend && mvn spring-boot:run -pl payment-service &
   cd frontend && npx ng serve &
   ```

2. **Verify Eureka registrations**:
   ```bash
   curl -s http://localhost:8761/eureka/apps | grep -c "<app>"
   ```
   Expected: At least 4 (gateway, association, payment, user).

3. **Initiate checkout via API gateway**:
   ```bash
   curl -s -X POST http://localhost:8080/api/v1/payments/checkout \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <valid-jwt>" \
     -d '{
       "subscriptionId": "sub-001",
       "amount": 15000,
       "description": "Adhésion Judo Club Lyon - Sophie Martin",
       "paymentType": "ADHESION",
       "returnUrl": "http://localhost:4200/payments/checkout/redirect?status=success",
       "cancelUrl": "http://localhost:4200/payments/checkout/redirect?status=cancelled"
     }'
   ```
   Expected: JSON with `checkoutUrl` pointing to `helloasso-sandbox.com`.

4. **Open checkoutUrl in browser** -- HelloAsso sandbox checkout page loads.

5. **Complete payment on sandbox** -- Use HelloAsso sandbox test card.

6. **HelloAsso redirects to return URL** -- Browser navigates to:
   `http://localhost:4200/payments/checkout/redirect?status=success&paymentId=<id>`

7. **Verify redirect component**:
   - Loading spinner appears briefly
   - Success card displays with "Paiement réussi"
   - "Voir le détail du paiement" button works

8. **Verify webhook received** (HelloAsso sandbox sends webhook):
   ```bash
   docker exec -it postgresql psql -U postgres -d familyhobbies_payments \
     -c "SELECT id, event_type, processed FROM t_payment_webhook_log ORDER BY created_at DESC LIMIT 1;"
   ```
   Expected: Row with `processed=true`.

9. **Verify payment status updated**:
   ```bash
   docker exec -it postgresql psql -U postgres -d familyhobbies_payments \
     -c "SELECT id, status, helloasso_payment_id FROM t_payment WHERE id='<payment-id>';"
   ```
   Expected: `status=COMPLETED`, `helloasso_payment_id` populated.

10. **Verify Kafka event published**:
    ```bash
    docker exec -it kafka kafka-console-consumer \
      --bootstrap-server localhost:9092 \
      --topic payment-events \
      --from-beginning --max-messages 1 --timeout-ms 5000
    ```
    Expected: `PaymentCompletedEvent` JSON message.

11. **Verify payment list** -- Open `http://localhost:4200/payments`:
    - Payment appears in the table
    - Status chip is green ("Terminé")
    - Click "Voir le détail" -- detail page shows full payment info with HelloAsso references

12. **Verify status filter** -- Select "Terminé" from the filter dropdown:
    - Only completed payments shown
    - Select "Tous les statuts" to reset

---

## Story Completion Matrix

| Story | Build | Tests | Integration | Status |
|-------|-------|-------|-------------|--------|
| S5-001: HelloAssoTokenManager | Check #8 | Check #14 | Check #18 | [ ] |
| S5-002: HelloAssoClient | Check #8 | Check #14 | Checks #19, #21 | [ ] |
| S5-003: AssociationSyncService | Check #8 | Check #14 | Check #20 | [ ] |
| S5-004: Payment Entity + Checkout | Check #9, #12 | Check #15 | Checks #23, #24 | [ ] |
| S5-005: Webhook Handler | Check #9 | Check #15 | Checks #25, #26, #27 | [ ] |
| S5-006: Angular Payment Feature | Check #10 | Checks #16, #17 | Checks #28-31 | [ ] |

---

**Sprint 5 is DONE when all 32 checks above pass and all 6 stories are marked complete in the matrix.**
