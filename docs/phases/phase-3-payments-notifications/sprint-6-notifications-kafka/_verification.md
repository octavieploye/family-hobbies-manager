# Sprint 6 Verification Checklist

> Run these commands IN ORDER after all stories are implemented.
> Every command must produce the expected output.
> Sprint file: [Back to Sprint Index](./_index.md)

---

## 1. Prerequisites Check

### 1.1 Infrastructure Services

```bash
# Verify Docker Compose is running with all required services
docker compose ps
```

Expected: All containers running:
- `postgres` (port 5432)
- `kafka` (port 9092)
- `zookeeper` (port 2181)
- `mailhog` (ports 1025 SMTP, 8025 web UI)
- `eureka` / discovery-service (port 8761)

### 1.2 Databases Exist

```bash
# Verify notification-service database exists
docker exec -it postgres psql -U postgres -c "\l" | grep familyhobbies_notifications
```

Expected: `familyhobbies_notifications` database listed.

```bash
# Verify payment-service database exists
docker exec -it postgres psql -U postgres -c "\l" | grep familyhobbies_payments
```

Expected: `familyhobbies_payments` database listed.

### 1.3 Kafka Broker Reachable

```bash
# Verify Kafka is running and topics can be listed
docker exec -it kafka kafka-topics.sh --list --bootstrap-server localhost:9092
```

Expected: Output includes topics (or empty list if first run). No connection errors.

### 1.4 Kafka Topics Exist

```bash
# Verify required Kafka topics exist (created by producers in previous sprints)
docker exec -it kafka kafka-topics.sh --describe --bootstrap-server localhost:9092 --topic user-events
docker exec -it kafka kafka-topics.sh --describe --bootstrap-server localhost:9092 --topic subscription-events
docker exec -it kafka kafka-topics.sh --describe --bootstrap-server localhost:9092 --topic payment-events
docker exec -it kafka kafka-topics.sh --describe --bootstrap-server localhost:9092 --topic attendance-events
```

Expected: Each topic described with partition and replication info. No errors.

### 1.5 SMTP Server (MailHog)

```bash
# Verify MailHog is accessible
curl -s http://localhost:8025/api/v2/messages | head -c 100
```

Expected: JSON response (even if empty messages array).

### 1.6 Environment Variables

```bash
# Verify notification-service env vars are set
env | grep -E "(SPRING_MAIL|KAFKA_BOOTSTRAP|NOTIFICATION_DB)" || echo "Check .env file"
```

Expected: Required env vars present or documented in `.env` / `docker-compose.yml`.

### 1.7 Common + Error-Handling Modules

```bash
# Verify common and error-handling are installed in local Maven repo
cd backend/error-handling && mvn verify -q
cd backend/common && mvn verify -q
```

Expected: Both `BUILD SUCCESS`.

---

## 2. Build Verification

### 2.1 Notification Service Build

```bash
cd backend/notification-service
mvn clean compile -q
```

Expected: `BUILD SUCCESS`. No compilation errors.

### 2.2 Payment Service Build (Invoice additions)

```bash
cd backend/payment-service
mvn clean compile -q
```

Expected: `BUILD SUCCESS`. No compilation errors.

### 2.3 Frontend Build

```bash
cd frontend
npm install
npx ng build --configuration=development
```

Expected: Build succeeds with no errors. Warnings about unused imports acceptable but no type errors.

---

## 3. Database Migration Verification

### 3.1 Notification Service Migrations (S6-001)

```bash
cd backend/notification-service
mvn liquibase:update -q
```

Expected: `BUILD SUCCESS`. Then verify tables:

```bash
docker exec -it postgres psql -U postgres -d familyhobbies_notifications -c "\dt"
```

Expected tables:
- `notification` -- S6-001, changeset 001
- `email_template` -- S6-001, changeset 002
- `notification_preference` -- S6-001, changeset 003

### 3.2 Verify Notification Table Columns

```bash
docker exec -it postgres psql -U postgres -d familyhobbies_notifications -c "\d notification"
```

Expected columns: `id`, `user_id`, `type`, `category`, `title`, `message`, `action_url`, `read`, `read_at`, `created_at`, `updated_at`.

### 3.3 Verify Email Template Table Columns

```bash
docker exec -it postgres psql -U postgres -d familyhobbies_notifications -c "\d email_template"
```

Expected columns: `id`, `name`, `subject_template`, `body_template`, `category`, `active`, `created_at`, `updated_at`.

### 3.4 Verify Notification Preference Table Columns

```bash
docker exec -it postgres psql -U postgres -d familyhobbies_notifications -c "\d notification_preference"
```

Expected columns: `id`, `user_id`, `category`, `email_enabled`, `in_app_enabled`, `created_at`, `updated_at`.

### 3.5 Verify Email Template Seed Data (S6-005)

```bash
docker exec -it postgres psql -U postgres -d familyhobbies_notifications \
  -c "SELECT name, category, active FROM email_template ORDER BY id;"
```

Expected: 5 rows with templates for WELCOME, PAYMENT_SUCCESS, PAYMENT_FAILED, SUBSCRIPTION_CONFIRMED, SUBSCRIPTION_CANCELLED. All `active = true`.

### 3.6 Payment Service Migrations (S6-006)

```bash
cd backend/payment-service
mvn liquibase:update -q
```

Expected: `BUILD SUCCESS`. Then verify:

```bash
docker exec -it postgres psql -U postgres -d familyhobbies_payments -c "\d invoice"
```

Expected: Invoice table has additional columns from changeset 004: `invoice_number`, `family_name`, `association_name`, `activity_name`, `family_member_name`, `season`, `subtotal`, `tax`, `total`, `currency`, `payer_email`, `payer_name`, `issued_at`, `paid_at`.

### 3.7 Verify Invoice Number Sequence (S6-006)

```bash
docker exec -it postgres psql -U postgres -d familyhobbies_payments \
  -c "SELECT * FROM information_schema.sequences WHERE sequence_name = 'invoice_number_seq';"
```

Expected: Sequence `invoice_number_seq` exists.

---

## 4. Test Verification

### 4.1 Notification Service Unit Tests (S6-001, S6-002, S6-003, S6-005)

```bash
cd backend/notification-service
mvn test -q
```

Expected: `BUILD SUCCESS`. All tests pass. Expected test classes:
- `NotificationEntityTest` -- entity mapping (S6-001)
- `EmailTemplateEntityTest` -- entity mapping (S6-001)
- `NotificationPreferenceEntityTest` -- entity mapping (S6-001)
- `NotificationCategoryTest` -- enum values (S6-001)
- `NotificationTypeTest` -- enum values (S6-001)
- `NotificationRepositoryTest` -- repository queries (S6-001)
- `UserEventConsumerTest` -- Kafka consumer (S6-002)
- `SubscriptionEventConsumerTest` -- Kafka consumer (S6-002)
- `PaymentEventConsumerTest` -- Kafka consumer (S6-002)
- `AttendanceEventConsumerTest` -- Kafka consumer (S6-002)
- `EmailServiceImplTest` -- email sending (S6-002)
- `NotificationCreationServiceImplTest` -- notification creation (S6-002)
- `NotificationMapperTest` -- DTO mapping (S6-003)
- `NotificationServiceImplTest` -- service logic (S6-003)
- `NotificationControllerTest` -- API endpoints (S6-003)

### 4.2 Payment Service Unit Tests (S6-006)

```bash
cd backend/payment-service
mvn test -q
```

Expected: `BUILD SUCCESS`. Tests include:
- `InvoiceMapperTest` -- DTO mapping (S6-006)
- `InvoiceNumberGeneratorTest` -- sequential numbering (S6-006)
- `InvoiceServiceImplTest` -- service logic (S6-006)
- `PaymentCompletedEventConsumerTest` -- Kafka consumer (S6-006)
- `InvoiceControllerTest` -- API endpoints (S6-006)

### 4.3 Frontend Notification Tests (S6-004)

```bash
cd frontend
npx jest --testPathPattern="features/notifications" --verbose
```

Expected: All 36 tests pass:
- `notification.service.spec.ts` -- 6 tests
- `notification.reducer.spec.ts` -- 12 tests
- `notification-bell.component.spec.ts` -- 4 tests
- `notification-dropdown.component.spec.ts` -- 5 tests
- `notification-list.component.spec.ts` -- 5 tests
- `notification-preferences.component.spec.ts` -- 4 tests

### 4.4 Frontend Invoice Tests (S6-007)

```bash
cd frontend
npx jest --testPathPattern="features/invoices" --verbose
```

Expected: All 14 tests pass:
- `invoice.service.spec.ts` -- 4 tests
- `invoice-list.component.spec.ts` -- 5 tests
- `invoice-section.component.spec.ts` -- 5 tests

### 4.5 Full Frontend Test Suite

```bash
cd frontend
npx jest --verbose
```

Expected: All tests pass (including tests from previous sprints). No regressions.

---

## 5. Integration Verification

### 5.1 Start All Services

```bash
# Start all backend services (in separate terminals or via Docker Compose)
cd backend/discovery-service && mvn spring-boot:run &
cd backend/api-gateway && mvn spring-boot:run &
cd backend/user-service && mvn spring-boot:run &
cd backend/association-service && mvn spring-boot:run &
cd backend/payment-service && mvn spring-boot:run &
cd backend/notification-service && mvn spring-boot:run &
```

Expected: All services start without errors and register with Eureka.

### 5.2 Verify Eureka Registration

```bash
curl -s http://localhost:8761/eureka/apps | grep -o '<app>[^<]*</app>'
```

Expected: All 5 services listed: `API-GATEWAY`, `USER-SERVICE`, `ASSOCIATION-SERVICE`, `PAYMENT-SERVICE`, `NOTIFICATION-SERVICE`.

### 5.3 Kafka Consumer Groups (S6-002, S6-006)

```bash
docker exec -it kafka kafka-consumer-groups.sh \
  --list --bootstrap-server localhost:9092
```

Expected: Consumer groups for notification-service and payment-service present (e.g., `notification-service-group`, `payment-service-group`).

### 5.4 Notification API -- Unread Count (S6-003)

```bash
# Get a valid JWT token first (adjust based on your auth flow)
TOKEN="<valid-jwt-token>"

curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/notifications/me/unread-count
```

Expected: `{"count":0}` (or the actual unread count).

### 5.5 Notification API -- List Notifications (S6-003)

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/notifications/me?page=0&size=10"
```

Expected: JSON with `content` array, `totalElements`, `totalPages`, etc.

### 5.6 Notification API -- Preferences (S6-003)

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/notifications/preferences
```

Expected: JSON with `userId`, `categories` map containing all 7 categories with `emailEnabled` and `inAppEnabled` booleans.

### 5.7 Invoice API -- List Invoices (S6-006)

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/invoices/family/1?page=0&size=10"
```

Expected: JSON with paginated invoice list (may be empty if no payments completed yet).

### 5.8 Invoice API -- Download PDF (S6-006)

```bash
# Only if an invoice exists
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/invoices/1/download \
  -o test-invoice.pdf

file test-invoice.pdf
```

Expected: File downloaded. `file` command reports `PDF document`.

### 5.9 Kafka End-to-End -- User Registration Flow (S6-002)

```bash
# Register a new user via user-service (produces UserRegisteredEvent to Kafka)
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test-kafka@example.com",
    "password": "Test1234!",
    "firstName": "Test",
    "lastName": "Kafka"
  }'
```

Then verify notification was created:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/notifications/me?category=WELCOME"
```

Expected: At least one WELCOME notification in the list.

### 5.10 Email Delivery via MailHog (S6-002, S6-005)

```bash
# Check MailHog for received emails
curl -s http://localhost:8025/api/v2/messages | python3 -m json.tool | head -40
```

Expected: Email(s) present in MailHog with subjects matching email templates (e.g., "Bienvenue chez Family Hobbies Manager").

### 5.11 Invoice Generation on Payment (S6-006)

Trigger a payment completed event (via webhook or direct Kafka publish):

```bash
# Publish a test PaymentCompletedEvent to Kafka
docker exec -it kafka kafka-console-producer.sh \
  --broker-list localhost:9092 \
  --topic payment-events << 'EOF'
{"eventType":"PAYMENT_COMPLETED","paymentId":1,"subscriptionId":1,"familyId":1,"amount":150.00,"currency":"EUR","timestamp":"2026-02-24T10:00:00Z"}
EOF
```

Then verify invoice was generated:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/invoices/family/1"
```

Expected: Invoice list contains the newly generated invoice with sequential `invoiceNumber`.

---

## 6. Frontend Integration

### 6.1 Start Frontend Dev Server

```bash
cd frontend
npx ng serve --open
```

Expected: Browser opens at `http://localhost:4200`. No console errors.

### 6.2 Notification Routes

Navigate to `http://localhost:4200/notifications`.

Expected: Notification list page renders with:
- Page title "Notifications"
- Category filter dropdown
- Read/unread filter dropdown
- Table (or empty state "Aucune notification")
- Pagination controls

### 6.3 Notification Bell in Toolbar

Expected: Bell icon visible in the application toolbar.

- When no unread notifications: no badge visible
- When unread notifications exist: red badge with count
- Click on bell: dropdown opens showing recent notifications

### 6.4 Notification Dropdown

Click bell icon.

Expected:
- Header shows "Notifications"
- "Tout marquer comme lu" button visible when unread exist
- Up to 5 recent notifications listed with category chips
- "Voir tout" link at bottom navigates to `/notifications`
- Unread items have blue left border and dot indicator

### 6.5 Notification Preferences

Navigate to settings page or `/notifications/preferences` (if separate route).

Expected:
- Card titled "Preferences de notification"
- Table with 7 rows (one per category)
- Two toggle columns: "Email" and "In-app"
- Toggling a switch immediately dispatches update to API

### 6.6 Invoice Routes

Navigate to `http://localhost:4200/invoices`.

Expected: Invoice list page renders with:
- Page title "Factures"
- Status filter dropdown
- Table with columns: Numero de facture, Association, Activite, Membre, Saison, Montant, Statut, Date d'emission
- Download button per row
- Pagination controls

### 6.7 Invoice Download

Click a download button in the invoice list.

Expected:
- Spinner replaces icon during download
- Browser save dialog opens with `facture-{id}.pdf`
- PDF file is valid

### 6.8 Invoice Section in Payment Detail

Navigate to a payment detail page (e.g., `/payments/1`).

Expected:
- If payment has an associated invoice: "Facture" card displayed
- Card shows invoice number, date, amount, status
- "Telecharger" button downloads the PDF
- If no invoice: card is not shown

### 6.9 Polling Verification

Open browser DevTools Network tab while on any page with the bell component.

Expected: `GET /api/v1/notifications/me/unread-count` request every 30 seconds.

---

## 7. End-to-End Smoke Test

### Full Flow: Registration to Notification

1. **Register a new user** via the frontend registration form or API
2. **Verify** Kafka produces `UserRegisteredEvent`
3. **Verify** notification-service consumes the event (check service logs)
4. **Verify** welcome notification created in database
5. **Verify** welcome email sent (check MailHog at `http://localhost:8025`)
6. **Verify** bell icon shows unread count = 1
7. **Click** bell icon -- dropdown shows welcome notification
8. **Click** "Tout marquer comme lu" -- badge disappears
9. **Navigate** to `/notifications` -- notification listed with category chip "Bienvenue"
10. **Filter** by category "Bienvenue" -- only welcome notification shown

### Full Flow: Payment to Invoice

1. **Create a subscription** for a family member to an association activity
2. **Complete a payment** (via HelloAsso sandbox or mock webhook)
3. **Verify** Kafka produces `PaymentCompletedEvent`
4. **Verify** payment-service generates invoice (check logs + database)
5. **Verify** notification-service creates payment success notification
6. **Verify** email sent for payment confirmation (check MailHog)
7. **Navigate** to `/invoices` -- new invoice listed
8. **Click** download -- PDF downloaded with correct invoice number
9. **Navigate** to payment detail page -- invoice section visible
10. **Verify** invoice number matches between list and detail

### Full Flow: Notification Preferences

1. **Navigate** to notification preferences
2. **Disable** email for ATTENDANCE_REMINDER category
3. **Trigger** an attendance reminder event
4. **Verify** in-app notification created but NO email sent (check MailHog)
5. **Re-enable** email for ATTENDANCE_REMINDER
6. **Trigger** another attendance reminder event
7. **Verify** both in-app notification AND email sent

---

## Story Coverage Matrix

| Story | Description | Verification Sections |
|-------|-------------|-----------------------|
| S6-001 | Notification entities + migrations | 3.1-3.4, 4.1 |
| S6-002 | Kafka consumers + email sending | 4.1, 5.3, 5.9, 5.10, 7 |
| S6-003 | Notification REST API | 4.1, 5.4-5.6, 7 |
| S6-004 | Angular notification feature | 4.3, 4.5, 6.1-6.5, 6.9, 7 |
| S6-005 | Seed email templates | 3.5, 4.1, 5.10, 7 |
| S6-006 | Invoice generation | 3.6-3.7, 4.2, 5.7-5.8, 5.11, 7 |
| S6-007 | Angular invoice download | 4.4, 4.5, 6.6-6.8, 7 |

---

**Sprint 6 is DONE when all checks above pass.**
