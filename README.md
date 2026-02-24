# Family Hobbies Manager

Multi-association management platform integrating with the [HelloAsso](https://www.helloasso.com) API. French families discover associations (sport, dance, music, theater, etc.) across any location in France, register, manage subscriptions, track events/courses/attendance.

**Value-add on top of HelloAsso**: family grouping, cross-association dashboards, attendance tracking, course scheduling, notification orchestration, and RGPD compliance.

## Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Java 17 / Spring Boot 3.2 / Spring Cloud 2023.0.x |
| **Frontend** | Angular 17+ / Angular Material / NgRx / SCSS |
| **Database** | PostgreSQL 16 (one DB per microservice, Liquibase migrations) |
| **Messaging** | Apache Kafka (inter-service events) |
| **External API** | HelloAsso API v5 (OAuth2, sandbox for dev) |
| **Infrastructure** | Docker / Docker Compose |
| **Testing** | JUnit 5 / Jest / Playwright |
| **CI/CD** | GitHub Actions |

## HelloAsso Integration

- **Development**: HelloAsso Sandbox (`api.helloasso-sandbox.com/v5`) — free, no approval needed
- **Demo**: Realistic French association data seeded via Liquibase
- **Production-ready**: switching to production = config change (URL + credentials), zero code changes

## Project Structure

```
family-hobbies-manager/
├── backend/
│   ├── error-handling/         # Dedicated error handling module
│   ├── common/                 # Shared DTOs, events, security, audit
│   ├── discovery-service/      # Eureka service registry
│   ├── api-gateway/            # Spring Cloud Gateway + JWT validation
│   ├── user-service/           # Auth, users, families, RGPD
│   ├── association-service/    # Associations, activities, sessions, subscriptions, attendance
│   ├── payment-service/        # HelloAsso Checkout, webhooks, invoices
│   └── notification-service/   # Notifications, email templates, Kafka listeners
├── frontend/                   # Angular 17+ SPA
│   └── src/app/core/
│       └── error-handling/     # Error models, handlers, interceptor (RGAA-compliant)
├── docker/                     # Docker Compose, init scripts, env
├── docs/architecture/          # 13 architecture documents
├── e2e/                        # Playwright E2E tests
└── .github/workflows/          # CI/CD pipeline
```

## Microservices

| Service | Port | Database | Description |
|---|---|---|---|
| discovery-service | 8761 | — | Eureka service registry |
| api-gateway | 8080 | — | Spring Cloud Gateway, JWT validation, rate limiting |
| user-service | 8081 | familyhobbies_users | Authentication, users, families, RGPD |
| association-service | 8082 | familyhobbies_associations | HelloAsso directory proxy + cache, activities, sessions, subscriptions, attendance |
| payment-service | 8083 | familyhobbies_payments | HelloAsso Checkout integration, payment webhooks, invoices |
| notification-service | 8084 | familyhobbies_notifications | Notifications, email templates, Kafka listeners |

## Shared Modules

| Module | Purpose | Dependency Chain |
|---|---|---|
| `error-handling` | Exceptions (web/server/container), GlobalExceptionHandler, ErrorResponse, ErrorCode | Standalone |
| `common` | Shared DTOs, Kafka events, JWT security, audit base entity | Depends on `error-handling` |

## Architecture Documentation

See [`docs/architecture/`](docs/architecture/) for the full 13-document architecture suite:

- System Overview, Service Catalog, Data Model, API Contracts
- Kafka Events, Security, HelloAsso Integration, Batch Processing
- Frontend Architecture, Infrastructure, Testing Strategy
- Naming Conventions, Delivery Roadmap, Error Handling

## Getting Started

### Prerequisites

- Java 17+
- Node.js 20+
- Docker & Docker Compose

### Quick Start

```bash
# Start infrastructure (PostgreSQL, Kafka, Zookeeper)
docker compose up -d postgres zookeeper kafka

# Start backend services (in order)
cd backend/discovery-service && ./mvnw spring-boot:run &
cd backend/api-gateway && ./mvnw spring-boot:run &
cd backend/user-service && ./mvnw spring-boot:run &
cd backend/association-service && ./mvnw spring-boot:run &
cd backend/payment-service && ./mvnw spring-boot:run &
cd backend/notification-service && ./mvnw spring-boot:run &

# Start frontend
cd frontend && npm install && ng serve
```

### Run Tests

```bash
# Backend unit tests
cd backend/error-handling && ./mvnw test
cd backend/user-service && ./mvnw test

# Frontend unit tests
cd frontend && npm test

# E2E tests
cd e2e && npx playwright test
```
