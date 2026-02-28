# Family Hobbies Manager

[![CI/CD](https://github.com/octavieploye/family-hobbies-manager/actions/workflows/ci.yml/badge.svg)](https://github.com/octavieploye/family-hobbies-manager/actions)
[![E2E Tests](https://github.com/octavieploye/family-hobbies-manager/actions/workflows/e2e-tests.yml/badge.svg)](https://github.com/octavieploye/family-hobbies-manager/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A fullstack multi-association management platform integrating with the [HelloAsso](https://www.helloasso.com/) API. Families discover associations (sport, dance, music, theatre, etc.) across France, register members, manage subscriptions, track attendance, and process payments -- all from a unified dashboard.

**Value proposition**: HelloAsso handles association directories and payments. This app adds family grouping, multi-association dashboards, attendance tracking, course scheduling, and notification orchestration -- features HelloAsso does not offer.

---

## Architecture

| Service | Port | Role |
|---------|------|------|
| **discovery-service** | 8761 | Eureka service registry |
| **api-gateway** | 8080 | Spring Cloud Gateway, JWT validation |
| **user-service** | 8081 | Auth, users, families, RGPD compliance |
| **association-service** | 8082 | HelloAsso directory proxy, activities, sessions, subscriptions, attendance |
| **payment-service** | 8083 | HelloAsso Checkout, payment webhooks, invoices |
| **notification-service** | 8084 | Email + in-app notifications, Kafka consumers |
| **frontend** | 4200 | Angular 17+ SPA |

> Architecture diagram: [docs/architecture/architecture-overview.md](docs/architecture/architecture-overview.md)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.2, Spring Cloud 2023.0 |
| Frontend | Angular 17+ (standalone components, NgRx, Angular Material, SCSS) |
| Database | PostgreSQL 16 (one DB per service, Liquibase migrations) |
| Messaging | Apache Kafka (inter-service events) |
| External API | HelloAsso API v5 (OAuth2, REST/JSON) |
| Testing | JUnit 5, Mockito, Playwright (E2E), axe-core (a11y) |
| Infrastructure | Docker, Docker Compose, Kustomize (OpenShift-ready) |
| CI/CD | GitHub Actions |
| Accessibility | RGAA / WCAG 2.1 AA compliant |

## Quick Start

### Prerequisites

- Docker 24+ and Docker Compose v2
- 8 GB RAM available for Docker

### Run with Docker Compose

```bash
# Clone the repository
git clone https://github.com/octavieploye/family-hobbies-manager.git
cd family-hobbies-manager

# Copy environment template
cp docker/.env.example docker/.env

# Start all services
docker compose -f docker/docker-compose.yml up -d

# Wait for all services to be healthy (~90 seconds)
docker compose -f docker/docker-compose.yml ps
```

Once all services are healthy:

| URL | Description |
|-----|-------------|
| http://localhost:4200 | Angular frontend |
| http://localhost:8080 | API Gateway |
| http://localhost:8761 | Eureka dashboard |
| http://localhost:8081/swagger-ui.html | User Service API docs |
| http://localhost:8082/swagger-ui.html | Association Service API docs |
| http://localhost:8083/swagger-ui.html | Payment Service API docs |
| http://localhost:8084/swagger-ui.html | Notification Service API docs |

### Stop

```bash
docker compose -f docker/docker-compose.yml down -v
```

## Development Setup

### Backend

```bash
# Prerequisites: Java 17, Maven 3.9+

# Build all modules
cd backend
mvn clean install

# Run a specific service (example: user-service)
mvn spring-boot:run -pl user-service
```

### Frontend

```bash
# Prerequisites: Node.js 18+, npm 9+

cd frontend
npm install
npm start
# Open http://localhost:4200
```

### Running Tests

```bash
# Backend unit tests
cd backend && mvn test

# Frontend unit tests
cd frontend && npm test

# E2E tests (requires Docker Compose running)
cd e2e && npm install && npx playwright install
npx playwright test

# Accessibility tests
npx playwright test specs/a11y/
```

## API Documentation

Interactive API documentation is available via Swagger UI when each service is running:

- **User Service**: `http://localhost:8081/swagger-ui.html`
- **Association Service**: `http://localhost:8082/swagger-ui.html`
- **Payment Service**: `http://localhost:8083/swagger-ui.html`
- **Notification Service**: `http://localhost:8084/swagger-ui.html`

A Postman collection with all endpoints is available in [`docs/api/postman/`](docs/api/postman/).

## Project Structure

```
family-hobbies-manager/
+-- backend/
|   +-- error-handling/         # Shared error handling module
|   +-- common/                 # Shared DTOs, events, security, audit
|   +-- discovery-service/      # Eureka registry
|   +-- api-gateway/            # Spring Cloud Gateway
|   +-- user-service/           # Auth, users, families
|   +-- association-service/    # Associations, activities, attendance
|   +-- payment-service/        # Payments, invoices
|   +-- notification-service/   # Notifications, emails
+-- frontend/                   # Angular 17+ SPA
+-- e2e/                        # Playwright E2E tests
+-- docker/                     # Docker Compose, init scripts
+-- docs/                       # Architecture docs, ADRs, API specs
+-- .github/workflows/          # CI/CD pipelines
```

## Key Features

- **Family Management**: Create family profiles, add/remove members, assign roles
- **Association Discovery**: Search French associations by keyword, city, category via HelloAsso directory
- **Subscription Management**: Subscribe family members to association activities
- **Attendance Tracking**: Calendar view, mark present/absent, attendance history
- **Payment Processing**: HelloAsso Checkout integration, payment status, invoices
- **Notifications**: Real-time in-app notifications, email alerts via Kafka events
- **RGPD Compliance**: Consent management, data export, account deletion
- **Accessibility**: RGAA / WCAG 2.1 AA compliant with axe-core validation

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture Overview](docs/architecture/architecture-overview.md) | System architecture and service interactions |
| [Error Handling](docs/architecture/error-handling/13-error-handling.md) | Error handling module specification |
| [HelloAsso Integration](docs/architecture/) | HelloAsso API integration strategy |
| [Deployment Guide](docs/phases/phase-4-polish/sprint-8-e2e-rgaa-production/S8-005-openshift-manifests.md) | OpenShift deployment instructions |

## License

This project is licensed under the MIT License -- see the [LICENSE](LICENSE) file for details.

---

Built with Java 17, Spring Boot, Angular 17, PostgreSQL, Kafka, and HelloAsso API.
