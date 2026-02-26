# Codebase Conventions — Family Hobbies Manager

> **Purpose**: Single reference for all coding agents. These conventions are established in the implemented codebase (Sprint 0 + Sprint 1) and MUST be followed in all future sprints.

---

## Package & Class Conventions

| Aspect | Convention | Example |
|--------|-----------|---------|
| Entity package | `entity/` (NOT `model/`) | `com.familyhobbies.userservice.entity.User` |
| DTO package | `dto/request/` + `dto/response/` (split, NOT flat `dto/`) | `dto/request/RegisterRequest.java`, `dto/response/AuthResponse.java` |
| DTO style | Java records with validation annotations (NOT Lombok `@Data` classes) | `public record RegisterRequest(@NotBlank String email, ...)` |
| Service impl package | `service/impl/` | `com.familyhobbies.userservice.service.impl.AuthServiceImpl` |
| Auth service class | `AuthServiceImpl` (NOT `UserServiceImpl`) | — |
| Password field | `passwordHash` (NOT `password`) | `User.builder().passwordHash(encoded).build()` |

## Database & Migration Conventions

| Aspect | Convention |
|--------|-----------|
| Liquibase format | XML (NOT YAML) |
| Changeset location | `src/main/resources/db/changelog/changesets/` |
| Master changelog | `src/main/resources/db/changelog/db.changelog-master.xml` |
| Schema init | `spring.batch.jdbc.initialize-schema: never` (managed by Liquibase) |

## Event Conventions

| Aspect | Convention |
|--------|-----------|
| Base class | All events extend `DomainEvent` |
| Construction | Use constructor (NOT builder pattern) |
| Timestamp | Use `getOccurredAt()` from `DomainEvent` base class (NOT custom timestamp fields) |
| Topic naming | `family-hobbies.<domain>.<event>` pattern |

### Established Event Fields

| Event | Fields | NOT present |
|-------|--------|-------------|
| `UserRegisteredEvent` | `userId`, `email`, `firstName`, `lastName` | ~~`role`~~, ~~`registeredAt`~~ |
| `UserDeletedEvent` | `userId`, `deletionType` | ~~`email`~~, ~~`deletedAt`~~, ~~`reason`~~ |
| `PaymentCompletedEvent` | `paymentId`, `userId`, `amount`, `currency`, `helloAssoCheckoutId` | — |
| `PaymentFailedEvent` | `paymentId`, `userId`, `failureReason`, `helloAssoCheckoutId` | — |

## Error Handling Conventions

| Aspect | Convention |
|--------|-----------|
| `ExternalApiException` | 3-arg constructor `(message, apiName, upstreamStatus)` or static factory `forApi()` |
| Error module | Dedicated `backend/error-handling/` module (NOT in `common`) |
| Dependency chain | `error-handling` ← `common` ← each service |

## JJWT & Security

| Aspect | Convention |
|--------|-----------|
| JJWT version | 0.13.0 with fluent API |
| Gateway headers | `X-User-Id`, `X-User-Roles` forwarded by API Gateway after JWT validation |
| Test auth | Controller tests must include `X-User-Id` and `X-User-Roles` headers |

## Spring Boot & Batch

| Aspect | Convention |
|--------|-----------|
| Spring Boot | 3.2.5 |
| Spring Cloud | 2023.0.3 |
| Spring Batch | 5.x — do NOT use `@EnableBatchProcessing` (disables auto-config) |
| Batch timestamps | `Instant` (NOT `LocalDateTime`) for `getStartTime()`, `getEndTime()` |
| Batch schema | Managed by Liquibase — `spring.batch.jdbc.initialize-schema: never` |

## Build & Versioning

| Aspect | Convention |
|--------|-----------|
| Parent POM artifactId | `family-hobbies-manager-backend` |
| Parent POM version | `0.1.0-SNAPSHOT` |
| Java version | 17 (enforced by Maven enforcer plugin) |

## Frontend Conventions

| Aspect | Convention |
|--------|-----------|
| ID types | `number` (matching backend `Long`) — NOT `string` |
| Framework | Angular 17+ with standalone components |
| Styling | Angular Material + SCSS |
| State management | NgRx |

## Logging

| Aspect | Convention |
|--------|-----------|
| MDC key naming | camelCase: `traceId`, `spanId`, `userId`, `serviceName` |
| Format | Structured JSON logging |
