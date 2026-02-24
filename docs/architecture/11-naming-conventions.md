# 11 — Naming Conventions

> **Family Hobbies Manager** — Java 17 / Spring Boot 3.2 / Angular 17+ / PostgreSQL 16 / Kafka

This document defines the authoritative naming conventions for every layer of the
Family Hobbies Manager platform. All contributors **must** follow these conventions
to keep the codebase consistent, searchable, and maintainable.

---

## Table of Contents

1. [Database Naming (PostgreSQL)](#1-database-naming-postgresql)
2. [Liquibase Naming](#2-liquibase-naming)
3. [Java Backend Naming](#3-java-backend-naming)
4. [Kafka Naming](#4-kafka-naming)
5. [REST API Naming](#5-rest-api-naming)
6. [Angular Frontend Naming](#6-angular-frontend-naming)
7. [Docker / Infrastructure Naming](#7-docker--infrastructure-naming)
8. [Test Naming](#8-test-naming)
9. [Git Naming](#9-git-naming)
10. [Quick-Reference Cheat Sheet](#10-quick-reference-cheat-sheet)

---

## 1. Database Naming (PostgreSQL)

### Database Names

One dedicated database per microservice. Pattern: `familyhobbies_{service_domain}`.

| Service              | Database Name                  |
|----------------------|--------------------------------|
| user-service         | `familyhobbies_users`          |
| association-service  | `familyhobbies_associations`   |
| payment-service      | `familyhobbies_payments`       |
| notification-service | `familyhobbies_notifications`  |

### Table Names

All tables carry the `t_` prefix followed by a singular snake_case noun.

```
t_user
t_family
t_family_member
t_association
t_activity
t_session
t_subscription
t_attendance_record
t_payment
t_invoice
t_notification
t_email_template
```

### Column Names

Always **snake_case**. Never camelCase, never UPPER_CASE.

```sql
first_name
last_name
date_of_birth
email
phone_number
postal_code
helloasso_slug
created_at
updated_at
```

### Primary Keys

Every table uses a column named `id` of type `BIGINT GENERATED ALWAYS AS IDENTITY`.

```sql
CREATE TABLE t_user (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
```

### Foreign Keys

Column name: `{referenced_table}_id` (without the `t_` prefix).

```sql
-- In t_family_member
family_id   BIGINT NOT NULL,   -- references t_family(id)
user_id     BIGINT NOT NULL    -- references t_user(id)
```

### Constraint Names

| Constraint Type | Pattern                                  | Example                                          |
|-----------------|------------------------------------------|--------------------------------------------------|
| Foreign key     | `fk_{table}_{referenced_table}`          | `fk_family_member_family`, `fk_family_member_user` |
| Unique          | `uq_{table}_{column}`                    | `uq_user_email`, `uq_association_slug`           |
| Index           | `idx_{table}_{column(s)}`                | `idx_user_email`, `idx_association_city_category` |
| Check           | `ck_{table}_{column}`                    | `ck_payment_amount_positive`                     |

```sql
ALTER TABLE t_family_member
    ADD CONSTRAINT fk_family_member_family
    FOREIGN KEY (family_id) REFERENCES t_family(id);

ALTER TABLE t_family_member
    ADD CONSTRAINT fk_family_member_user
    FOREIGN KEY (user_id) REFERENCES t_user(id);

CREATE UNIQUE INDEX uq_user_email ON t_user(email);

CREATE INDEX idx_association_city_category ON t_association(city, category);
```

### Enum Columns

Store as `VARCHAR`. Map with JPA `@Enumerated(EnumType.STRING)`. Never use ordinal mapping.

```sql
-- In t_subscription
status VARCHAR(30) NOT NULL   -- PENDING, ACTIVE, CANCELLED, EXPIRED
```

```java
@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false, length = 30)
private SubscriptionStatus status;
```

### Timestamp Columns

Always use `TIMESTAMP WITH TIME ZONE`. Common column names:

```
created_at
updated_at
paid_at
marked_at
cancelled_at
expires_at
sent_at
```

### Boolean Columns

Do **not** use the `is_` prefix. Use the plain adjective or past participle.

```sql
active    BOOLEAN NOT NULL DEFAULT TRUE
read      BOOLEAN NOT NULL DEFAULT FALSE
granted   BOOLEAN NOT NULL DEFAULT FALSE
verified  BOOLEAN NOT NULL DEFAULT FALSE
```

JPA maps these correctly without the `is_` prefix:

```java
@Column(name = "active", nullable = false)
private boolean active;
```

---

## 2. Liquibase Naming

### Directory Structure

```
src/main/resources/
└── db/
    └── changelog/
        ├── db.changelog-master.yaml          -- master changelog
        └── changes/
            ├── 001-create-user-table.yaml
            ├── 002-create-family-table.yaml
            ├── 003-create-family-member-table.yaml
            ├── 004-create-association-table.yaml
            ├── 005-create-activity-table.yaml
            ├── 006-create-session-table.yaml
            ├── 007-create-attendance-record-table.yaml
            └── ...
```

### Changelog Master

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-create-user-table.yaml
  - include:
      file: db/changelog/changes/002-create-family-table.yaml
  - include:
      file: db/changelog/changes/003-create-family-member-table.yaml
```

### Changeset IDs

Pattern: `{NNN}-{description}` where `{NNN}` is a zero-padded sequence number.

| ID                                 | Description                       |
|------------------------------------|-----------------------------------|
| `001-create-user-table`            | Initial user table                |
| `002-create-family-table`          | Family table                      |
| `003-create-family-member-table`   | Family-member join table          |
| `004-add-phone-to-user`           | Add phone_number column to t_user |
| `005-create-subscription-table`    | Subscription table                |

### Author

Always `family-hobbies-team`.

### Example: Table Creation

```yaml
databaseChangeLog:
  - changeSet:
      id: 001-create-user-table
      author: family-hobbies-team
      changes:
        - createTable:
            tableName: t_user
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: email
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
                    unique: true
                    uniqueConstraintName: uq_user_email
              - column:
                  name: first_name
                  type: VARCHAR(100)
                  constraints:
                    nullable: false
              - column:
                  name: last_name
                  type: VARCHAR(100)
                  constraints:
                    nullable: false
              - column:
                  name: password_hash
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: role
                  type: VARCHAR(30)
                  constraints:
                    nullable: false
              - column:
                  name: active
                  type: BOOLEAN
                  defaultValueBoolean: true
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: TIMESTAMP WITH TIME ZONE
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: TIMESTAMP WITH TIME ZONE
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
```

### Example: Add Column

```yaml
databaseChangeLog:
  - changeSet:
      id: 004-add-phone-to-user
      author: family-hobbies-team
      changes:
        - addColumn:
            tableName: t_user
            columns:
              - column:
                  name: phone_number
                  type: VARCHAR(20)
                  constraints:
                    nullable: true
```

### Example: Add Foreign Key

```yaml
databaseChangeLog:
  - changeSet:
      id: 003-create-family-member-table
      author: family-hobbies-team
      changes:
        - createTable:
            tableName: t_family_member
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: family_id
                  type: BIGINT
                  constraints:
                    nullable: false
              - column:
                  name: user_id
                  type: BIGINT
                  constraints:
                    nullable: false
              - column:
                  name: role_in_family
                  type: VARCHAR(30)
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: TIMESTAMP WITH TIME ZONE
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
        - addForeignKeyConstraint:
            baseTableName: t_family_member
            baseColumnNames: family_id
            referencedTableName: t_family
            referencedColumnNames: id
            constraintName: fk_family_member_family
            onDelete: CASCADE
        - addForeignKeyConstraint:
            baseTableName: t_family_member
            baseColumnNames: user_id
            referencedTableName: t_user
            referencedColumnNames: id
            constraintName: fk_family_member_user
            onDelete: CASCADE
```

---

## 3. Java Backend Naming

### Package Structure

```
com.familyhobbies.{servicename}/
|-- config/           -- XxxConfig.java
|-- controller/       -- XxxController.java
|-- dto/
|   |-- request/      -- CreateXxxRequest.java, UpdateXxxRequest.java
|   +-- response/     -- XxxResponse.java, XxxSummaryResponse.java
|-- entity/           -- Xxx.java (JPA entity, no suffix)
|-- enums/            -- XxxStatus.java, XxxCategory.java
|-- exception/        -- XxxNotFoundException.java, XxxAlreadyExistsException.java
|-- mapper/           -- XxxMapper.java
|-- repository/       -- XxxRepository.java
|-- service/          -- XxxService.java (interface)
|-- service/impl/     -- XxxServiceImpl.java
|-- event/
|   |-- publisher/    -- XxxEventPublisher.java
|   +-- listener/     -- XxxEventListener.java
|-- adapter/          -- HelloAssoClient.java, HelloAssoTokenManager.java
|-- batch/            -- XxxJobConfig.java, XxxItemReader.java, XxxItemProcessor.java
+-- security/         -- JwtTokenProvider.java, JwtAuthenticationFilter.java
```

Service name in the package uses a flat lowercase name with no hyphens:

```
com.familyhobbies.userservice
com.familyhobbies.associationservice
com.familyhobbies.paymentservice
com.familyhobbies.notificationservice
com.familyhobbies.apigateway
com.familyhobbies.common
```

### Class Naming

| Category          | Pattern                                       | Examples                                                                             |
|-------------------|-----------------------------------------------|--------------------------------------------------------------------------------------|
| Entity            | `PascalCase` noun (no suffix)                 | `User`, `Family`, `FamilyMember`, `Association`, `Activity`, `Session`, `Subscription`, `AttendanceRecord`, `Payment`, `Invoice`, `Notification` |
| Controller        | `{Entity}Controller`                          | `UserController`, `FamilyController`, `AssociationController`, `AttendanceController` |
| Service interface | `{Entity}Service`                             | `UserService`, `FamilyService`, `SubscriptionService`                                |
| Service impl      | `{Entity}ServiceImpl`                         | `UserServiceImpl`, `FamilyServiceImpl`, `SubscriptionServiceImpl`                    |
| Repository        | `{Entity}Repository`                          | `UserRepository`, `FamilyRepository`, `AssociationRepository`                        |
| DTO request       | `Create{Entity}Request`, `Update{Entity}Request` | `CreateFamilyRequest`, `UpdateUserRequest`, `CreateSubscriptionRequest`           |
| DTO response      | `{Entity}Response`, `{Entity}SummaryResponse` | `FamilyResponse`, `AssociationSummaryResponse`, `PaymentResponse`                    |
| Mapper            | `{Entity}Mapper`                              | `UserMapper`, `FamilyMapper`, `AssociationMapper`                                    |
| Exception         | `{Entity}{Problem}Exception`                  | `UserNotFoundException`, `SubscriptionAlreadyExistsException`, `PaymentFailedException` |
| Config            | `{Feature}Config`                             | `SecurityConfig`, `KafkaConfig`, `WebClientConfig`, `CorsConfig`                     |
| Event             | `{Entity}{Action}Event`                       | `UserRegisteredEvent`, `PaymentCompletedEvent`, `SubscriptionCreatedEvent`           |
| Event publisher   | `{Entity}EventPublisher`                      | `UserEventPublisher`, `PaymentEventPublisher`                                        |
| Event listener    | `{Entity}EventListener`                       | `NotificationEventListener`, `SubscriptionEventListener`                             |
| Adapter           | `HelloAsso{Feature}Client`                    | `HelloAssoClient`, `HelloAssoCheckoutClient`, `HelloAssoTokenManager`                |
| Enum              | `{Entity}{Attribute}`                         | `SubscriptionStatus`, `PaymentStatus`, `UserRole`, `ActivityCategory`                |

### Method Naming

| Operation         | Pattern                           | Examples                                                           |
|-------------------|-----------------------------------|--------------------------------------------------------------------|
| Create            | `create{Entity}`                  | `createFamily()`, `createSubscription()`, `createUser()`           |
| Read single       | `find{Entity}ById`               | `findUserById()`, `findAssociationById()`, `findPaymentById()`    |
| Read list         | `find{Entities}By{Criteria}`     | `findFamilyMembersByFamilyId()`, `findAssociationsByCity()`       |
| Read all          | `findAll{Entities}`              | `findAllActivities()`, `findAllNotifications()`                   |
| Update            | `update{Entity}`                 | `updateUser()`, `updateSubscription()`, `updateAssociation()`     |
| Delete            | `delete{Entity}`                 | `deleteFamilyMember()`, `deleteNotification()`                    |
| Domain action     | `verb{Noun}`                     | `markAttendance()`, `cancelSubscription()`, `initiateCheckout()`, `processWebhook()`, `syncAssociations()` |
| Boolean query     | `is{Condition}`, `has{Condition}` | `isSubscriptionActive()`, `hasValidToken()`, `isEmailVerified()`  |
| Repository query  | `findBy{Field}`, `existsBy{Field}` | `findByEmail()`, `existsBySlug()`, `findByFamilyIdAndSeason()`  |

### Variable and Constant Naming

```java
// Local variables and parameters: camelCase
String firstName;
Long familyId;
List<Association> associations;

// Constants: UPPER_SNAKE_CASE
public static final String BEARER_PREFIX = "Bearer ";
public static final int MAX_FAMILY_MEMBERS = 10;
public static final String USER_REGISTERED_TOPIC = "family-hobbies.user.registered";

// Entity fields: camelCase, matching snake_case column via @Column
@Column(name = "first_name", nullable = false)
private String firstName;

@Column(name = "date_of_birth")
private LocalDate dateOfBirth;

@Column(name = "helloasso_slug")
private String helloassoSlug;
```

### Annotation Ordering Convention

```java
@Entity
@Table(name = "t_user")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class User extends AuditableEntity {
    // ...
}
```

---

## 4. Kafka Naming

### Topic Names

Pattern: `family-hobbies.{service-domain}.{event-name}`

All segments are lowercase kebab-case separated by dots.

| Topic                                        | Producer             | Consumers                        |
|----------------------------------------------|----------------------|----------------------------------|
| `family-hobbies.user.registered`             | user-service         | notification-service             |
| `family-hobbies.user.deleted`                | user-service         | notification-service, association-service |
| `family-hobbies.subscription.created`        | association-service  | notification-service, payment-service |
| `family-hobbies.subscription.cancelled`      | association-service  | notification-service, payment-service |
| `family-hobbies.payment.completed`           | payment-service      | notification-service, association-service |
| `family-hobbies.payment.failed`              | payment-service      | notification-service             |
| `family-hobbies.attendance.marked`           | association-service  | notification-service             |

### Event Class Names

Pattern: `{Entity}{Action}Event` in PascalCase. Events live in `backend/common/` so they
are shared across services.

```java
// com.familyhobbies.common.event
public record UserRegisteredEvent(
    Long userId,
    String email,
    String firstName,
    String lastName,
    Instant registeredAt
) {}

public record PaymentCompletedEvent(
    Long paymentId,
    Long subscriptionId,
    Long userId,
    BigDecimal amount,
    String currency,
    Instant paidAt
) {}

public record SubscriptionCreatedEvent(
    Long subscriptionId,
    Long userId,
    Long associationId,
    Long activityId,
    String season,
    Instant createdAt
) {}
```

### Consumer Group IDs

Pattern: `{service-name}-group`

```
notification-service-group
association-service-group
payment-service-group
user-service-group
```

### Kafka Configuration Keys

```yaml
spring:
  kafka:
    consumer:
      group-id: notification-service-group
      auto-offset-reset: earliest
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

---

## 5. REST API Naming

### Base Path

All services expose endpoints under `/api/v1`.

### Resource Naming

Use **plural nouns** in **kebab-case**. Never use verbs as resource names (except for non-CRUD actions).

| Resource           | Path                   |
|--------------------|------------------------|
| Users              | `/api/v1/users`        |
| Families           | `/api/v1/families`     |
| Family members     | `/api/v1/family-members` |
| Associations       | `/api/v1/associations` |
| Activities         | `/api/v1/activities`   |
| Sessions           | `/api/v1/sessions`     |
| Subscriptions      | `/api/v1/subscriptions`|
| Attendance records | `/api/v1/attendance`   |
| Payments           | `/api/v1/payments`     |
| Invoices           | `/api/v1/invoices`     |
| Notifications      | `/api/v1/notifications`|

### Nested Resources

Use nesting when there is a clear parent-child ownership relationship.

```
GET    /api/v1/families/{familyId}/members
POST   /api/v1/families/{familyId}/members
GET    /api/v1/associations/{id}/activities
GET    /api/v1/activities/{id}/sessions
GET    /api/v1/users/{userId}/subscriptions
GET    /api/v1/users/{userId}/notifications
```

### HTTP Methods

| Action         | Method   | Example                                         |
|----------------|----------|-------------------------------------------------|
| Create         | `POST`   | `POST /api/v1/families`                         |
| Read one       | `GET`    | `GET /api/v1/families/{id}`                     |
| Read list      | `GET`    | `GET /api/v1/associations?city=Paris`            |
| Full update    | `PUT`    | `PUT /api/v1/users/{id}`                        |
| Partial update | `PATCH`  | `PATCH /api/v1/users/{id}`                      |
| Delete         | `DELETE` | `DELETE /api/v1/family-members/{id}`            |

### Non-CRUD Actions

When an operation does not map cleanly to CRUD, use a verb sub-resource with `POST`.

```
POST /api/v1/subscriptions/{id}/cancel
POST /api/v1/attendance/mark
POST /api/v1/notifications/read-all
POST /api/v1/payments/{id}/refund
POST /api/v1/associations/sync
```

### Query Parameters

Use **camelCase** for query parameter names.

```
GET /api/v1/associations?city=Paris&category=SPORT&page=0&size=20&sort=name,asc
GET /api/v1/sessions?activityId=5&fromDate=2026-01-01&toDate=2026-06-30
GET /api/v1/users?search=dupont&role=FAMILY
```

### Response Format

No wrapping envelope. Return the entity directly for single-resource endpoints. Use Spring `Page<T>` for paginated collections.

```json
// Single resource: GET /api/v1/users/1
{
  "id": 1,
  "email": "marie.dupont@example.com",
  "firstName": "Marie",
  "lastName": "Dupont",
  "role": "FAMILY",
  "active": true,
  "createdAt": "2026-01-15T10:30:00Z"
}

// Paginated collection: GET /api/v1/associations?page=0&size=20
{
  "content": [ ... ],
  "totalElements": 142,
  "totalPages": 8,
  "number": 0,
  "size": 20
}
```

### Error Response Format

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "User with id 99 not found",
  "timestamp": "2026-02-23T14:05:00Z",
  "path": "/api/v1/users/99"
}
```

---

## 6. Angular Frontend Naming

### Directory Structure

```
src/app/
|-- core/
|   |-- interceptors/
|   |   |-- jwt.interceptor.ts
|   |   |-- error.interceptor.ts
|   |   +-- loading.interceptor.ts
|   |-- guards/
|   |   |-- auth.guard.ts
|   |   +-- role.guard.ts
|   +-- services/
|       +-- api.service.ts
|-- shared/
|   |-- components/
|   |   |-- confirm-dialog/
|   |   +-- loading-spinner/
|   |-- pipes/
|   |   |-- date-format.pipe.ts
|   |   +-- truncate.pipe.ts
|   +-- models/
|       |-- user.model.ts
|       |-- association.model.ts
|       |-- family.model.ts
|       +-- payment.model.ts
+-- features/
    |-- auth/
    |   |-- login/
    |   |   |-- login.component.ts
    |   |   |-- login.component.html
    |   |   |-- login.component.scss
    |   |   +-- login.component.spec.ts
    |   +-- register/
    |-- dashboard/
    |-- associations/
    |   |-- association-list/
    |   |   |-- association-list.component.ts
    |   |   |-- association-list.component.html
    |   |   |-- association-list.component.scss
    |   |   +-- association-list.component.spec.ts
    |   |-- association-detail/
    |   +-- association-search/
    |-- family/
    |   |-- family-overview/
    |   |-- family-member-form/
    |   +-- family-member-list/
    |-- payments/
    |-- notifications/
    +-- attendance/
```

### File Naming Patterns

| Category     | Pattern                                    | Examples                                                          |
|--------------|--------------------------------------------|-------------------------------------------------------------------|
| Component    | `{feature}-{purpose}.component.ts`         | `association-list.component.ts`, `family-member-form.component.ts` |
| Template     | `{feature}-{purpose}.component.html`       | `association-list.component.html`                                 |
| Styles       | `{feature}-{purpose}.component.scss`       | `association-list.component.scss`                                 |
| Service      | `{feature}.service.ts`                     | `auth.service.ts`, `association.service.ts`, `payment.service.ts` |
| Model        | `{entity}.model.ts`                        | `user.model.ts`, `association.model.ts`, `family.model.ts`       |
| Guard        | `{purpose}.guard.ts`                       | `auth.guard.ts`, `role.guard.ts`                                 |
| Interceptor  | `{purpose}.interceptor.ts`                 | `jwt.interceptor.ts`, `error.interceptor.ts`, `loading.interceptor.ts` |
| Pipe         | `{purpose}.pipe.ts`                        | `date-format.pipe.ts`, `truncate.pipe.ts`                        |
| Directive    | `{purpose}.directive.ts`                   | `click-outside.directive.ts`, `autofocus.directive.ts`           |
| Test         | `{name}.spec.ts`                           | `association-list.component.spec.ts`, `auth.service.spec.ts`     |

### NgRx Store Files

Pattern: `{feature}.{store-concept}.ts`

```
src/app/features/auth/store/
|-- auth.actions.ts
|-- auth.reducer.ts
|-- auth.effects.ts
|-- auth.selectors.ts
+-- auth.state.ts
```

### TypeScript Interface / Model Naming

```typescript
// user.model.ts
export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  active: boolean;
  createdAt: string;
}

export enum UserRole {
  FAMILY = 'FAMILY',
  ASSOCIATION = 'ASSOCIATION',
  ADMIN = 'ADMIN',
}

// association.model.ts
export interface Association {
  id: number;
  name: string;
  slug: string;
  city: string;
  postalCode: string;
  category: ActivityCategory;
  description: string;
}

export interface AssociationSummary {
  id: number;
  name: string;
  city: string;
  category: ActivityCategory;
}
```

### SCSS — BEM Methodology

Use Block-Element-Modifier naming for all CSS classes.

```scss
// Block
.association-card { ... }

// Element
.association-card__title { ... }
.association-card__description { ... }
.association-card__actions { ... }

// Modifier
.association-card--featured { ... }
.association-card--compact { ... }

// More examples
.family-member-list { ... }
.family-member-list__item { ... }
.family-member-list__item--active { ... }

.dashboard-stat { ... }
.dashboard-stat__value { ... }
.dashboard-stat__label { ... }
.dashboard-stat--highlight { ... }
```

### NgRx Action Naming

Use the `[Source] Description` pattern.

```typescript
// auth.actions.ts
export const login = createAction(
  '[Auth] Login',
  props<{ email: string; password: string }>()
);

export const loginSuccess = createAction(
  '[Auth] Login Success',
  props<{ user: User; token: string }>()
);

export const loginFailure = createAction(
  '[Auth] Login Failure',
  props<{ error: string }>()
);

export const logout = createAction('[Auth] Logout');
```

---

## 7. Docker / Infrastructure Naming

### Docker Images

Pattern: `family-hobbies/{service-name}:{tag}`

```
family-hobbies/user-service:latest
family-hobbies/association-service:latest
family-hobbies/payment-service:latest
family-hobbies/notification-service:latest
family-hobbies/api-gateway:latest
family-hobbies/discovery-service:latest
family-hobbies/frontend:latest
```

### Container Names

Pattern: `fhm-{service-name}`

```
fhm-user-service
fhm-association-service
fhm-payment-service
fhm-notification-service
fhm-api-gateway
fhm-discovery-service
fhm-frontend
fhm-postgres
fhm-kafka
fhm-zookeeper
```

### Docker Networks

```
fhm-network
```

### Volume Names

Pattern: `fhm-{service}-data`

```
fhm-postgres-data
fhm-kafka-data
fhm-zookeeper-data
```

### Environment Files

```
.env.local      -- local development (gitignored)
.env.docker     -- Docker Compose overrides
.env.ci         -- CI/CD pipeline variables
```

### Docker Compose Service Names

Use kebab-case matching the microservice names.

```yaml
services:
  user-service:
    image: family-hobbies/user-service:latest
    container_name: fhm-user-service
    networks:
      - fhm-network
    volumes:
      - fhm-user-data:/data

  postgres:
    image: postgres:16-alpine
    container_name: fhm-postgres
    volumes:
      - fhm-postgres-data:/var/lib/postgresql/data
```

---

## 8. Test Naming

### Java — JUnit 5

**Test class names:**

| Type        | Pattern                          | Example                                     |
|-------------|----------------------------------|---------------------------------------------|
| Unit test   | `{Class}Test.java`               | `UserServiceImplTest.java`, `UserMapperTest.java` |
| Integration | `{Class}IntegrationTest.java`    | `UserControllerIntegrationTest.java`        |

**Test method names:**

Pattern: `should_{expectedResult}_when_{condition}`

```java
@Test
void should_returnFamily_when_validFamilyId() { ... }

@Test
void should_throwNotFoundException_when_userDoesNotExist() { ... }

@Test
void should_publishEvent_when_subscriptionCreated() { ... }

@Test
void should_returnPagedAssociations_when_searchByCity() { ... }

@Test
void should_rejectRequest_when_emailAlreadyExists() { ... }

@Test
void should_markAttendance_when_sessionIsActive() { ... }

@Test
void should_returnUnauthorized_when_tokenExpired() { ... }
```

**Test structure — Arrange / Act / Assert:**

```java
@Test
void should_createFamily_when_validRequest() {
    // Arrange
    CreateFamilyRequest request = new CreateFamilyRequest("Dupont", 1L);
    Family savedFamily = Family.builder().id(1L).name("Dupont").build();
    when(familyRepository.save(any(Family.class))).thenReturn(savedFamily);

    // Act
    FamilyResponse response = familyService.createFamily(request);

    // Assert
    assertThat(response.id()).isEqualTo(1L);
    assertThat(response.name()).isEqualTo("Dupont");
    verify(familyRepository).save(any(Family.class));
}
```

### Angular — Jest

**Test file names:** `{name}.spec.ts` (co-located with the source file).

```
association-list.component.spec.ts
auth.service.spec.ts
user.mapper.spec.ts
date-format.pipe.spec.ts
```

**Describe blocks:** Use the class or component name.

```typescript
describe('AssociationListComponent', () => {
  // ...

  it('should display associations when loaded', () => { ... });

  it('should show empty state when no associations found', () => { ... });

  it('should call service on search input change', () => { ... });
});

describe('AuthService', () => {
  it('should store token on successful login', () => { ... });

  it('should clear token on logout', () => { ... });

  it('should return false for isAuthenticated when no token', () => { ... });
});
```

### Playwright — E2E

**Test file names:** `{feature}.spec.ts` in the `e2e/` directory.

```
e2e/
|-- auth.spec.ts
|-- association-search.spec.ts
|-- family-management.spec.ts
|-- subscription-flow.spec.ts
|-- attendance-tracking.spec.ts
+-- payment-checkout.spec.ts
```

**Test names:** Describe what the user can do.

```typescript
import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('user can register and login', async ({ page }) => {
    // ...
  });

  test('user sees error with invalid credentials', async ({ page }) => {
    // ...
  });

  test('user is redirected to login when not authenticated', async ({ page }) => {
    // ...
  });
});

test.describe('Association Search', () => {
  test('user can search associations by city', async ({ page }) => {
    // ...
  });

  test('user can filter associations by category', async ({ page }) => {
    // ...
  });
});
```

---

## 9. Git Naming

### Branch Names

Pattern: `{type}/{ticket-id}-{short-description}`

```
feat/42-attendance-tracking
feat/55-association-search
fix/78-jwt-expiry-500
fix/91-duplicate-subscription
refactor/102-extract-helloasso-adapter
chore/110-upgrade-spring-boot
test/120-payment-service-unit-tests
docs/130-api-documentation
migration/140-add-attendance-table
```

When no ticket exists, omit the ID:

```
chore/upgrade-angular-17
docs/naming-conventions
```

### Commit Message Types

| Prefix       | Use When                                           |
|--------------|----------------------------------------------------|
| `feat`       | New feature or capability                          |
| `fix`        | Bug fix                                            |
| `refactor`   | Code restructure with no behavior change           |
| `test`       | Adding or updating tests only                      |
| `docs`       | Documentation only                                 |
| `chore`      | Build, config, dependencies, CI/CD                 |
| `migration`  | Database schema changes (Liquibase)                |
| `style`      | Formatting, whitespace, SCSS, no logic change      |
| `perf`       | Performance improvement                            |
| `security`   | Security fix or hardening                          |

### Commit Scopes

Use the service or module name:

```
feat(user-service): add email verification flow
fix(api-gateway): resolve JWT expiry returning 500
refactor(association-service): extract HelloAsso adapter
chore(docker): add health checks to compose
test(payment-service): add checkout integration tests
migration(association-service): add attendance_record table
```

For cross-cutting changes:

```
chore(multi): upgrade Spring Boot to 3.2.5
refactor(infra): standardize logging configuration
```

### Tags

Pattern: `v{major}.{minor}.{patch}`

```
v1.0.0
v1.1.0
v1.1.1
v2.0.0
```

---

## 10. Quick-Reference Cheat Sheet

| Layer              | Convention          | Pattern                                              | Example                                       |
|--------------------|---------------------|------------------------------------------------------|-----------------------------------------------|
| **Database**       | Database name       | `familyhobbies_{domain}`                             | `familyhobbies_users`                         |
|                    | Table name          | `t_{entity}`                                         | `t_family_member`                             |
|                    | Column name         | `snake_case`                                         | `first_name`, `created_at`                    |
|                    | Primary key         | `id`                                                 | `id BIGINT GENERATED ALWAYS AS IDENTITY`      |
|                    | Foreign key column  | `{table}_id`                                         | `family_id`, `user_id`                        |
|                    | FK constraint       | `fk_{table}_{ref}`                                   | `fk_family_member_family`                     |
|                    | Index               | `idx_{table}_{cols}`                                 | `idx_association_city_category`                |
|                    | Unique constraint   | `uq_{table}_{col}`                                   | `uq_user_email`                               |
| **Liquibase**      | Changeset file      | `{NNN}-{description}.yaml`                           | `001-create-user-table.yaml`                  |
|                    | Changeset ID        | `{NNN}-{description}`                                | `001-create-user-table`                       |
|                    | Author              | `family-hobbies-team`                                | `family-hobbies-team`                         |
| **Java**           | Package             | `com.familyhobbies.{servicename}`                    | `com.familyhobbies.userservice`               |
|                    | Entity              | `PascalCase` noun                                    | `AttendanceRecord`                            |
|                    | Controller          | `{Entity}Controller`                                 | `UserController`                              |
|                    | Service             | `{Entity}Service` / `{Entity}ServiceImpl`            | `UserService` / `UserServiceImpl`             |
|                    | Repository          | `{Entity}Repository`                                 | `UserRepository`                              |
|                    | DTO request         | `Create{Entity}Request`                              | `CreateFamilyRequest`                         |
|                    | DTO response        | `{Entity}Response`                                   | `FamilyResponse`                              |
|                    | Exception           | `{Entity}{Problem}Exception`                         | `UserNotFoundException`                       |
|                    | Method (create)     | `create{Entity}`                                     | `createFamily()`                              |
|                    | Method (read)       | `find{Entity}ById`                                   | `findUserById()`                              |
|                    | Method (action)     | `verb{Noun}`                                         | `markAttendance()`                            |
|                    | Test method         | `should_{result}_when_{condition}`                   | `should_returnFamily_when_validFamilyId()`    |
|                    | Constant            | `UPPER_SNAKE_CASE`                                   | `MAX_FAMILY_MEMBERS`                          |
| **Kafka**          | Topic               | `family-hobbies.{domain}.{event}`                    | `family-hobbies.user.registered`              |
|                    | Event class         | `{Entity}{Action}Event`                              | `UserRegisteredEvent`                         |
|                    | Consumer group      | `{service-name}-group`                               | `notification-service-group`                  |
| **REST API**       | Base path           | `/api/v1`                                            | `/api/v1/users`                               |
|                    | Resource            | plural kebab-case nouns                              | `/family-members`                             |
|                    | Nested resource     | `/{parent}/{id}/{child}`                             | `/families/{id}/members`                      |
|                    | Non-CRUD action     | `POST /{resource}/{id}/{verb}`                       | `POST /subscriptions/{id}/cancel`             |
|                    | Query params        | `camelCase`                                          | `?postalCode=75001&page=0`                    |
| **Angular**        | Component           | `{feature}-{purpose}.component.ts`                   | `association-list.component.ts`               |
|                    | Service             | `{feature}.service.ts`                               | `auth.service.ts`                             |
|                    | Model               | `{entity}.model.ts`                                  | `user.model.ts`                               |
|                    | Guard               | `{purpose}.guard.ts`                                 | `auth.guard.ts`                               |
|                    | Interceptor         | `{purpose}.interceptor.ts`                           | `jwt.interceptor.ts`                          |
|                    | NgRx files          | `{feature}.{actions\|reducer\|effects\|selectors}.ts` | `auth.actions.ts`                            |
|                    | SCSS classes        | BEM: `.block__element--modifier`                     | `.association-card__title--featured`           |
|                    | Test                | `{name}.spec.ts`                                     | `auth.service.spec.ts`                        |
| **Docker**         | Image               | `family-hobbies/{service}:{tag}`                     | `family-hobbies/user-service:latest`          |
|                    | Container           | `fhm-{service}`                                      | `fhm-user-service`                            |
|                    | Network             | `fhm-network`                                       | `fhm-network`                                |
|                    | Volume              | `fhm-{service}-data`                                 | `fhm-postgres-data`                           |
|                    | Env file            | `.env.{environment}`                                 | `.env.local`, `.env.docker`                   |
| **Git**            | Branch              | `{type}/{ticket}-{desc}`                             | `feat/42-attendance-tracking`                 |
|                    | Commit scope        | `{type}({service}): ...`                             | `feat(user-service): add email verification`  |
|                    | Tag                 | `v{major}.{minor}.{patch}`                           | `v1.0.0`                                      |
| **E2E (Playwright)** | Test file          | `{feature}.spec.ts`                                  | `association-search.spec.ts`                  |

---

> **Rule of thumb**: when in doubt, check this document. Consistency across the entire stack
> is more important than any individual preference.
