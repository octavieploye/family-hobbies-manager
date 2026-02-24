# Story S8-006: Final Documentation

> 3 points | Priority: P1 | Service: all (documentation)
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The final documentation story ensures the Family Hobbies Manager project is presentation-ready for a portfolio and job application. This includes a professional README with clear quick-start instructions, OpenAPI/Swagger annotations on all REST controllers, a Postman collection with every API endpoint pre-configured, and a review of all Architecture Decision Records (ADRs) for accuracy. The README is the first thing a recruiter or hiring manager sees -- it must convey professionalism, technical depth, and completeness in under 2 minutes of reading. Swagger UI must be accessible per microservice so reviewers can explore the API interactively. The Postman collection provides a hands-on way to test every endpoint without writing code.

## Cross-References

- **S8-001** (Playwright E2E Suite) validates all user flows end-to-end
- **S8-002** (RGAA Accessibility) ensures frontend compliance
- **S8-004** (Multi-stage Dockerfiles) provides the Docker setup described in the README
- **S8-005** (OpenShift Manifests) provides the deployment manifests referenced in the README
- All architecture docs in `docs/architecture/` from Sprints 0-7

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | README.md (complete rewrite) | `README.md` | Portfolio-grade project README with overview, tech stack, architecture, quick start, development setup, API docs link | README renders correctly on GitHub, quick start instructions work |
| 2 | Springdoc OpenAPI configuration | Per-service `application.yml` additions | `springdoc-openapi-starter-webmvc-ui` dependency + config per service | `/swagger-ui.html` accessible per service |
| 3 | Swagger annotations on controllers | All REST controllers | `@Tag`, `@Operation`, `@ApiResponse` annotations | Swagger UI shows all endpoints with descriptions |
| 4 | Postman collection | `docs/api/postman/family-hobbies-manager.postman_collection.json` | Complete Postman collection with all endpoints | Import into Postman, all requests visible |
| 5 | Postman environment (local) | `docs/api/postman/local.postman_environment.json` | Environment variables for local development | Import into Postman, variables resolve |
| 6 | Postman environment (docker) | `docs/api/postman/docker.postman_environment.json` | Environment variables for Docker Compose setup | Import into Postman, variables resolve |
| 7 | ADR review and updates | `docs/architecture/*.md` | Review all ADRs for accuracy against implementation | All ADRs match current codebase |

---

## Task 1 Detail: README.md (Complete Rewrite)

- **What**: A portfolio-grade README that gives a hiring manager a complete picture of the project in under 2 minutes. Includes: project badge area, concise description, architecture diagram link, tech stack table, quick start with Docker Compose, development setup, API documentation links, project structure, and contributing/license sections.
- **Where**: `README.md` (project root)
- **Why**: The README is the most visible file in any GitHub repository. For a portfolio project targeting a job application, it must demonstrate professionalism, clear communication, and technical depth.

```markdown
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
+-- k8s/                        # OpenShift/Kubernetes manifests (Kustomize)
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
```

- **Verify**: Push to GitHub, verify README renders with badges, tables, and code blocks. Quick start instructions match actual Docker Compose setup.

---

## Task 2 Detail: Springdoc OpenAPI Configuration

- **What**: Add `springdoc-openapi-starter-webmvc-ui` dependency to each service POM and configure OpenAPI metadata in `application.yml`.
- **Where**: Each service's `pom.xml` and `src/main/resources/application.yml`
- **Why**: Springdoc auto-generates OpenAPI 3.0 specs from Spring MVC controllers and serves an interactive Swagger UI. No manual spec writing needed.

### Maven Dependency (add to each service pom.xml)

```xml
<!-- pom.xml: add to <dependencies> section of user-service, association-service,
     payment-service, notification-service -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

### application.yml additions (example: user-service)

```yaml
# src/main/resources/application.yml -- add to each service
springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
    tags-sorter: alpha
    operations-sorter: alpha
  info:
    title: "User Service API"
    description: "Authentication, user management, family management, and RGPD compliance"
    version: "1.0.0"
    contact:
      name: "Family Hobbies Manager"
      url: "https://github.com/octavieploye/family-hobbies-manager"
```

Replace `title` and `description` per service:

| Service | Title | Description |
|---------|-------|-------------|
| user-service | User Service API | Authentication, user management, family management, and RGPD compliance |
| association-service | Association Service API | HelloAsso directory proxy, activities, sessions, subscriptions, and attendance |
| payment-service | Payment Service API | HelloAsso Checkout integration, payment webhooks, and invoices |
| notification-service | Notification Service API | In-app and email notifications, notification preferences |

- **Verify**: Start each service, open `http://localhost:{port}/swagger-ui.html` -> Swagger UI loads with API info header

---

## Task 3 Detail: Swagger Annotations on Controllers

- **What**: Add `@Tag`, `@Operation`, and `@ApiResponse` annotations to all REST controller methods across all four services.
- **Where**: All `@RestController` classes in user-service, association-service, payment-service, notification-service
- **Why**: Swagger annotations enrich the auto-generated OpenAPI spec with human-readable descriptions, expected response codes, and parameter documentation. This makes the API self-documenting.

### Example: AuthController (user-service)

```java
package com.familyhobbies.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
// ... other imports

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "User registration, login, and token management")
public class AuthController {

    @PostMapping("/register")
    @Operation(summary = "Register a new user",
               description = "Creates a new user account and returns JWT tokens")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid registration data"),
        @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        // ...
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user",
               description = "Validates credentials and returns JWT access + refresh tokens")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // ...
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token",
               description = "Exchanges a valid refresh token for new access + refresh tokens")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token refreshed"),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        // ...
    }
}
```

### Annotation Pattern for All Controllers

| Service | Controller | Tag Name | Endpoints |
|---------|-----------|----------|-----------|
| user-service | AuthController | Authentication | POST /auth/register, POST /auth/login, POST /auth/refresh |
| user-service | UserController | Users | GET /users/me, PUT /users/me |
| user-service | FamilyController | Families | GET/POST /families, GET/PUT/DELETE /families/{id} |
| user-service | FamilyMemberController | Family Members | GET/POST /families/{id}/members, PUT/DELETE /families/{id}/members/{memberId} |
| user-service | RgpdController | RGPD | GET /rgpd/consent, PUT /rgpd/consent, POST /rgpd/export, DELETE /rgpd/account |
| association-service | AssociationController | Associations | POST /associations/search, GET /associations/{id} |
| association-service | ActivityController | Activities | GET /activities, GET /activities/{id} |
| association-service | SessionController | Sessions | GET /sessions, GET /sessions/{id} |
| association-service | SubscriptionController | Subscriptions | GET/POST /subscriptions, GET/DELETE /subscriptions/{id} |
| association-service | AttendanceController | Attendance | GET/POST /attendance, GET /attendance/history |
| payment-service | PaymentController | Payments | POST /payments/checkout, GET /payments/{id}, GET /payments/family/{familyId} |
| payment-service | WebhookController | Webhooks | POST /webhooks/helloasso |
| payment-service | InvoiceController | Invoices | GET /invoices, GET /invoices/{id} |
| notification-service | NotificationController | Notifications | GET /notifications/me, GET /notifications/me/unread-count, PATCH /notifications/{id}/read |
| notification-service | PreferenceController | Preferences | GET /notifications/preferences, PUT /notifications/preferences |

- **Verify**: Open Swagger UI per service -> all endpoints listed with descriptions, response codes, and parameter types

---

## Task 4 Detail: Postman Collection

- **What**: Complete Postman collection with all API endpoints organized by service, including example request bodies, headers, and pre-request scripts for JWT authentication.
- **Where**: `docs/api/postman/family-hobbies-manager.postman_collection.json`
- **Why**: Postman provides a zero-code way for reviewers to explore and test the API. The collection includes pre-request scripts that auto-refresh JWT tokens.

```json
{
  "info": {
    "name": "Family Hobbies Manager",
    "description": "Complete API collection for the Family Hobbies Manager platform.\n\nSetup:\n1. Import this collection\n2. Import the matching environment (local or docker)\n3. Run 'Auth > Register' then 'Auth > Login' to get tokens\n4. All subsequent requests auto-attach the JWT token",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    "_exporter_id": "family-hobbies-manager"
  },
  "auth": {
    "type": "bearer",
    "bearer": [
      {
        "key": "token",
        "value": "{{access_token}}",
        "type": "string"
      }
    ]
  },
  "event": [
    {
      "listen": "prerequest",
      "script": {
        "type": "text/javascript",
        "exec": [
          "// Auto-refresh token if expired",
          "const tokenExpiry = pm.environment.get('token_expiry');",
          "if (tokenExpiry && Date.now() > parseInt(tokenExpiry)) {",
          "    const refreshToken = pm.environment.get('refresh_token');",
          "    if (refreshToken) {",
          "        pm.sendRequest({",
          "            url: pm.environment.get('base_url') + '/auth/refresh',",
          "            method: 'POST',",
          "            header: { 'Content-Type': 'application/json' },",
          "            body: { mode: 'raw', raw: JSON.stringify({ refreshToken: refreshToken }) }",
          "        }, function (err, response) {",
          "            if (!err && response.code === 200) {",
          "                const body = response.json();",
          "                pm.environment.set('access_token', body.accessToken);",
          "                pm.environment.set('refresh_token', body.refreshToken);",
          "                pm.environment.set('token_expiry', Date.now() + 14 * 60 * 1000);",
          "            }",
          "        });",
          "    }",
          "}"
        ]
      }
    }
  ],
  "item": [
    {
      "name": "Auth",
      "description": "Authentication endpoints (user-service:8081)",
      "item": [
        {
          "name": "Register",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"email\": \"marie.dupont@example.com\",\n  \"password\": \"SecureP@ss123\",\n  \"firstName\": \"Marie\",\n  \"lastName\": \"Dupont\",\n  \"phone\": \"+33612345678\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/auth/register",
              "host": ["{{base_url}}"],
              "path": ["auth", "register"]
            },
            "auth": { "type": "noauth" }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "if (pm.response.code === 201) {",
                  "    const body = pm.response.json();",
                  "    pm.environment.set('access_token', body.accessToken);",
                  "    pm.environment.set('refresh_token', body.refreshToken);",
                  "    pm.environment.set('token_expiry', Date.now() + 14 * 60 * 1000);",
                  "    pm.environment.set('user_id', body.userId);",
                  "}"
                ]
              }
            }
          ]
        },
        {
          "name": "Login",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"email\": \"marie.dupont@example.com\",\n  \"password\": \"SecureP@ss123\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/auth/login",
              "host": ["{{base_url}}"],
              "path": ["auth", "login"]
            },
            "auth": { "type": "noauth" }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "if (pm.response.code === 200) {",
                  "    const body = pm.response.json();",
                  "    pm.environment.set('access_token', body.accessToken);",
                  "    pm.environment.set('refresh_token', body.refreshToken);",
                  "    pm.environment.set('token_expiry', Date.now() + 14 * 60 * 1000);",
                  "}"
                ]
              }
            }
          ]
        },
        {
          "name": "Refresh Token",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"refreshToken\": \"{{refresh_token}}\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/auth/refresh",
              "host": ["{{base_url}}"],
              "path": ["auth", "refresh"]
            },
            "auth": { "type": "noauth" }
          }
        }
      ]
    },
    {
      "name": "Users",
      "description": "User profile endpoints (user-service:8081)",
      "item": [
        {
          "name": "Get My Profile",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/users/me",
              "host": ["{{base_url}}"],
              "path": ["users", "me"]
            }
          }
        },
        {
          "name": "Update My Profile",
          "request": {
            "method": "PUT",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"firstName\": \"Marie\",\n  \"lastName\": \"Dupont-Martin\",\n  \"phone\": \"+33612345678\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/users/me",
              "host": ["{{base_url}}"],
              "path": ["users", "me"]
            }
          }
        }
      ]
    },
    {
      "name": "Families",
      "description": "Family management endpoints (user-service:8081)",
      "item": [
        {
          "name": "List My Families",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/families",
              "host": ["{{base_url}}"],
              "path": ["families"]
            }
          }
        },
        {
          "name": "Create Family",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"name\": \"Famille Dupont\",\n  \"address\": \"12 Rue de la Paix, 75002 Paris\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/families",
              "host": ["{{base_url}}"],
              "path": ["families"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "if (pm.response.code === 201) {",
                  "    pm.environment.set('family_id', pm.response.json().id);",
                  "}"
                ]
              }
            }
          ]
        },
        {
          "name": "Get Family by ID",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/families/{{family_id}}",
              "host": ["{{base_url}}"],
              "path": ["families", "{{family_id}}"]
            }
          }
        },
        {
          "name": "Update Family",
          "request": {
            "method": "PUT",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"name\": \"Famille Dupont-Martin\",\n  \"address\": \"15 Avenue des Champs-Elysees, 75008 Paris\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/families/{{family_id}}",
              "host": ["{{base_url}}"],
              "path": ["families", "{{family_id}}"]
            }
          }
        },
        {
          "name": "Delete Family",
          "request": {
            "method": "DELETE",
            "url": {
              "raw": "{{base_url}}/families/{{family_id}}",
              "host": ["{{base_url}}"],
              "path": ["families", "{{family_id}}"]
            }
          }
        },
        {
          "name": "List Family Members",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/families/{{family_id}}/members",
              "host": ["{{base_url}}"],
              "path": ["families", "{{family_id}}", "members"]
            }
          }
        },
        {
          "name": "Add Family Member",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"firstName\": \"Lucas\",\n  \"lastName\": \"Dupont\",\n  \"dateOfBirth\": \"2015-06-20\",\n  \"role\": \"CHILD\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/families/{{family_id}}/members",
              "host": ["{{base_url}}"],
              "path": ["families", "{{family_id}}", "members"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "if (pm.response.code === 201) {",
                  "    pm.environment.set('member_id', pm.response.json().id);",
                  "}"
                ]
              }
            }
          ]
        },
        {
          "name": "Update Family Member",
          "request": {
            "method": "PUT",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"firstName\": \"Lucas\",\n  \"lastName\": \"Dupont\",\n  \"dateOfBirth\": \"2015-06-20\",\n  \"role\": \"CHILD\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/families/{{family_id}}/members/{{member_id}}",
              "host": ["{{base_url}}"],
              "path": ["families", "{{family_id}}", "members", "{{member_id}}"]
            }
          }
        },
        {
          "name": "Remove Family Member",
          "request": {
            "method": "DELETE",
            "url": {
              "raw": "{{base_url}}/families/{{family_id}}/members/{{member_id}}",
              "host": ["{{base_url}}"],
              "path": ["families", "{{family_id}}", "members", "{{member_id}}"]
            }
          }
        }
      ]
    },
    {
      "name": "RGPD",
      "description": "RGPD compliance endpoints (user-service:8081)",
      "item": [
        {
          "name": "Get Consent Status",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/rgpd/consent",
              "host": ["{{base_url}}"],
              "path": ["rgpd", "consent"]
            }
          }
        },
        {
          "name": "Update Consent",
          "request": {
            "method": "PUT",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"dataProcessingConsent\": true,\n  \"marketingConsent\": false,\n  \"thirdPartyConsent\": false\n}"
            },
            "url": {
              "raw": "{{base_url}}/rgpd/consent",
              "host": ["{{base_url}}"],
              "path": ["rgpd", "consent"]
            }
          }
        },
        {
          "name": "Export My Data",
          "request": {
            "method": "POST",
            "url": {
              "raw": "{{base_url}}/rgpd/export",
              "host": ["{{base_url}}"],
              "path": ["rgpd", "export"]
            }
          }
        },
        {
          "name": "Delete My Account",
          "request": {
            "method": "DELETE",
            "url": {
              "raw": "{{base_url}}/rgpd/account",
              "host": ["{{base_url}}"],
              "path": ["rgpd", "account"]
            }
          }
        }
      ]
    },
    {
      "name": "Associations",
      "description": "Association directory endpoints (association-service:8082)",
      "item": [
        {
          "name": "Search Associations",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"keyword\": \"danse\",\n  \"city\": \"Paris\",\n  \"category\": \"SPORT\",\n  \"page\": 0,\n  \"size\": 20\n}"
            },
            "url": {
              "raw": "{{base_url}}/associations/search",
              "host": ["{{base_url}}"],
              "path": ["associations", "search"]
            }
          }
        },
        {
          "name": "Get Association by ID",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/associations/{{association_id}}",
              "host": ["{{base_url}}"],
              "path": ["associations", "{{association_id}}"]
            }
          }
        }
      ]
    },
    {
      "name": "Activities",
      "description": "Activity endpoints (association-service:8082)",
      "item": [
        {
          "name": "List Activities",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/activities?associationId={{association_id}}",
              "host": ["{{base_url}}"],
              "path": ["activities"],
              "query": [
                { "key": "associationId", "value": "{{association_id}}" }
              ]
            }
          }
        },
        {
          "name": "Get Activity by ID",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/activities/{{activity_id}}",
              "host": ["{{base_url}}"],
              "path": ["activities", "{{activity_id}}"]
            }
          }
        }
      ]
    },
    {
      "name": "Sessions",
      "description": "Session/course endpoints (association-service:8082)",
      "item": [
        {
          "name": "List Sessions",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/sessions?activityId={{activity_id}}",
              "host": ["{{base_url}}"],
              "path": ["sessions"],
              "query": [
                { "key": "activityId", "value": "{{activity_id}}" }
              ]
            }
          }
        },
        {
          "name": "Get Session by ID",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/sessions/{{session_id}}",
              "host": ["{{base_url}}"],
              "path": ["sessions", "{{session_id}}"]
            }
          }
        }
      ]
    },
    {
      "name": "Subscriptions",
      "description": "Subscription management endpoints (association-service:8082)",
      "item": [
        {
          "name": "List My Subscriptions",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/subscriptions",
              "host": ["{{base_url}}"],
              "path": ["subscriptions"]
            }
          }
        },
        {
          "name": "Create Subscription",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"memberId\": \"{{member_id}}\",\n  \"activityId\": \"{{activity_id}}\",\n  \"season\": \"2025-2026\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/subscriptions",
              "host": ["{{base_url}}"],
              "path": ["subscriptions"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "if (pm.response.code === 201) {",
                  "    pm.environment.set('subscription_id', pm.response.json().id);",
                  "}"
                ]
              }
            }
          ]
        },
        {
          "name": "Get Subscription by ID",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/subscriptions/{{subscription_id}}",
              "host": ["{{base_url}}"],
              "path": ["subscriptions", "{{subscription_id}}"]
            }
          }
        },
        {
          "name": "Cancel Subscription",
          "request": {
            "method": "DELETE",
            "url": {
              "raw": "{{base_url}}/subscriptions/{{subscription_id}}",
              "host": ["{{base_url}}"],
              "path": ["subscriptions", "{{subscription_id}}"]
            }
          }
        }
      ]
    },
    {
      "name": "Attendance",
      "description": "Attendance tracking endpoints (association-service:8082)",
      "item": [
        {
          "name": "Get Attendance for Session",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/attendance?sessionId={{session_id}}",
              "host": ["{{base_url}}"],
              "path": ["attendance"],
              "query": [
                { "key": "sessionId", "value": "{{session_id}}" }
              ]
            }
          }
        },
        {
          "name": "Mark Attendance",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"sessionId\": \"{{session_id}}\",\n  \"memberId\": \"{{member_id}}\",\n  \"status\": \"PRESENT\",\n  \"date\": \"2025-09-15\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/attendance",
              "host": ["{{base_url}}"],
              "path": ["attendance"]
            }
          }
        },
        {
          "name": "Get Attendance History",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/attendance/history?memberId={{member_id}}&from=2025-09-01&to=2025-12-31",
              "host": ["{{base_url}}"],
              "path": ["attendance", "history"],
              "query": [
                { "key": "memberId", "value": "{{member_id}}" },
                { "key": "from", "value": "2025-09-01" },
                { "key": "to", "value": "2025-12-31" }
              ]
            }
          }
        }
      ]
    },
    {
      "name": "Payments",
      "description": "Payment endpoints (payment-service:8083)",
      "item": [
        {
          "name": "Initiate Checkout",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"subscriptionId\": \"{{subscription_id}}\",\n  \"amount\": 15000,\n  \"description\": \"Adhesion Danse Classique - Saison 2025-2026\",\n  \"payerEmail\": \"marie.dupont@example.com\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/payments/checkout",
              "host": ["{{base_url}}"],
              "path": ["payments", "checkout"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "if (pm.response.code === 201) {",
                  "    pm.environment.set('payment_id', pm.response.json().paymentId);",
                  "    pm.environment.set('checkout_url', pm.response.json().checkoutUrl);",
                  "}"
                ]
              }
            }
          ]
        },
        {
          "name": "Get Payment by ID",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/payments/{{payment_id}}",
              "host": ["{{base_url}}"],
              "path": ["payments", "{{payment_id}}"]
            }
          }
        },
        {
          "name": "Get Payments by Family",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/payments/family/{{family_id}}",
              "host": ["{{base_url}}"],
              "path": ["payments", "family", "{{family_id}}"]
            }
          }
        },
        {
          "name": "HelloAsso Webhook (test)",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"eventType\": \"Payment\",\n  \"data\": {\n    \"id\": 12345,\n    \"amount\": 15000,\n    \"state\": \"Authorized\",\n    \"payer\": {\n      \"email\": \"marie.dupont@example.com\"\n    },\n    \"order\": {\n      \"id\": 67890\n    }\n  }\n}"
            },
            "url": {
              "raw": "{{base_url}}/webhooks/helloasso",
              "host": ["{{base_url}}"],
              "path": ["webhooks", "helloasso"]
            },
            "auth": { "type": "noauth" }
          }
        },
        {
          "name": "Get Invoices",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/invoices",
              "host": ["{{base_url}}"],
              "path": ["invoices"]
            }
          }
        }
      ]
    },
    {
      "name": "Notifications",
      "description": "Notification endpoints (notification-service:8084)",
      "item": [
        {
          "name": "Get My Notifications",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/notifications/me?page=0&size=20",
              "host": ["{{base_url}}"],
              "path": ["notifications", "me"],
              "query": [
                { "key": "page", "value": "0" },
                { "key": "size", "value": "20" }
              ]
            }
          }
        },
        {
          "name": "Get Unread Count",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/notifications/me/unread-count",
              "host": ["{{base_url}}"],
              "path": ["notifications", "me", "unread-count"]
            }
          }
        },
        {
          "name": "Mark as Read",
          "request": {
            "method": "PATCH",
            "url": {
              "raw": "{{base_url}}/notifications/{{notification_id}}/read",
              "host": ["{{base_url}}"],
              "path": ["notifications", "{{notification_id}}", "read"]
            }
          }
        },
        {
          "name": "Get Notification Preferences",
          "request": {
            "method": "GET",
            "url": {
              "raw": "{{base_url}}/notifications/preferences",
              "host": ["{{base_url}}"],
              "path": ["notifications", "preferences"]
            }
          }
        },
        {
          "name": "Update Notification Preferences",
          "request": {
            "method": "PUT",
            "header": [
              { "key": "Content-Type", "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"emailEnabled\": true,\n  \"inAppEnabled\": true,\n  \"paymentAlerts\": true,\n  \"attendanceReminders\": true,\n  \"subscriptionAlerts\": true\n}"
            },
            "url": {
              "raw": "{{base_url}}/notifications/preferences",
              "host": ["{{base_url}}"],
              "path": ["notifications", "preferences"]
            }
          }
        }
      ]
    }
  ]
}
```

- **Verify**: Open Postman -> Import -> select `family-hobbies-manager.postman_collection.json` -> all 11 folders with 34 requests visible, no import errors

---

## Task 5 Detail: Postman Environment (Local)

- **What**: Postman environment file with variables configured for local development (services running via `mvn spring-boot:run` or `npm start`).
- **Where**: `docs/api/postman/local.postman_environment.json`
- **Why**: Local development routes requests directly to each service port. The `base_url` points to the API gateway on `localhost:8080`.

```json
{
  "id": "local-env",
  "name": "Family Hobbies - Local",
  "values": [
    {
      "key": "base_url",
      "value": "http://localhost:8080",
      "type": "default",
      "enabled": true
    },
    {
      "key": "user_service_url",
      "value": "http://localhost:8081",
      "type": "default",
      "enabled": true
    },
    {
      "key": "association_service_url",
      "value": "http://localhost:8082",
      "type": "default",
      "enabled": true
    },
    {
      "key": "payment_service_url",
      "value": "http://localhost:8083",
      "type": "default",
      "enabled": true
    },
    {
      "key": "notification_service_url",
      "value": "http://localhost:8084",
      "type": "default",
      "enabled": true
    },
    {
      "key": "access_token",
      "value": "",
      "type": "secret",
      "enabled": true
    },
    {
      "key": "refresh_token",
      "value": "",
      "type": "secret",
      "enabled": true
    },
    {
      "key": "token_expiry",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "user_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "family_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "member_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "association_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "activity_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "session_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "subscription_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "payment_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "notification_id",
      "value": "",
      "type": "default",
      "enabled": true
    }
  ],
  "_postman_variable_scope": "environment"
}
```

- **Verify**: Import into Postman -> all variables visible in environment manager, `base_url` = `http://localhost:8080`

---

## Task 6 Detail: Postman Environment (Docker)

- **What**: Postman environment file with variables configured for Docker Compose deployment (all services behind the gateway).
- **Where**: `docs/api/postman/docker.postman_environment.json`
- **Why**: When running via Docker Compose, all traffic goes through the API gateway on the same port. The environment is identical to local except the base URL may differ if Docker runs on a different host.

```json
{
  "id": "docker-env",
  "name": "Family Hobbies - Docker",
  "values": [
    {
      "key": "base_url",
      "value": "http://localhost:8080",
      "type": "default",
      "enabled": true
    },
    {
      "key": "user_service_url",
      "value": "http://localhost:8080",
      "type": "default",
      "enabled": true
    },
    {
      "key": "association_service_url",
      "value": "http://localhost:8080",
      "type": "default",
      "enabled": true
    },
    {
      "key": "payment_service_url",
      "value": "http://localhost:8080",
      "type": "default",
      "enabled": true
    },
    {
      "key": "notification_service_url",
      "value": "http://localhost:8080",
      "type": "default",
      "enabled": true
    },
    {
      "key": "access_token",
      "value": "",
      "type": "secret",
      "enabled": true
    },
    {
      "key": "refresh_token",
      "value": "",
      "type": "secret",
      "enabled": true
    },
    {
      "key": "token_expiry",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "user_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "family_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "member_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "association_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "activity_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "session_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "subscription_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "payment_id",
      "value": "",
      "type": "default",
      "enabled": true
    },
    {
      "key": "notification_id",
      "value": "",
      "type": "default",
      "enabled": true
    }
  ],
  "_postman_variable_scope": "environment"
}
```

- **Verify**: Import into Postman -> all service URLs point to `http://localhost:8080` (gateway handles routing)

---

## Task 7 Detail: ADR Review and Updates

- **What**: Review all Architecture Decision Records in `docs/architecture/` to verify they match the current implementation. Update any that have drifted.
- **Where**: `docs/architecture/*.md`
- **Why**: Stale documentation is worse than no documentation. ADRs must reflect the actual architecture for portfolio credibility.

### Review Checklist

| ADR Topic | Verify |
|-----------|--------|
| Microservice architecture | Service list, ports, and DB names match implementation |
| Error handling module | Package structure matches `backend/error-handling/` |
| HelloAsso integration | OAuth2 flow, sandbox URL, webhook handling accurate |
| Kafka event model | Event names and payloads match `backend/common/` DTOs |
| JWT security | Token structure, role names (FAMILY/ASSOCIATION/ADMIN) accurate |
| Database per service | DB names and Liquibase migrations match |
| Frontend architecture | Standalone components, NgRx, Angular Material confirmed |
| RGPD compliance | Consent model, export, deletion flow documented |

### Process

1. Open each ADR document in `docs/architecture/`
2. Cross-reference with the actual implementation in `backend/` and `frontend/`
3. Update any discrepancies (e.g., changed class names, new endpoints, modified flows)
4. Add a "Last reviewed" date header to each ADR

- **Verify**: Read each ADR after update -> all technical details match the codebase. No references to removed or renamed components.

---

## File Structure Summary

```
README.md                                        (S8-006, complete rewrite)
docs/
  +-- api/
  |   +-- postman/
  |       +-- family-hobbies-manager.postman_collection.json  (S8-006)
  |       +-- local.postman_environment.json                 (S8-006)
  |       +-- docker.postman_environment.json                (S8-006)
docs/
  +-- architecture/
      +-- *.md                                    (S8-006, reviewed and updated)
```

---

## Acceptance Criteria Checklist

- [ ] README.md renders correctly on GitHub with badges, tables, and code blocks
- [ ] README quick start instructions match actual Docker Compose setup
- [ ] README contains: overview, architecture table, tech stack, quick start, dev setup, API docs links, project structure, features, license
- [ ] `springdoc-openapi-starter-webmvc-ui` dependency added to all 4 service POMs
- [ ] Swagger UI loads at `http://localhost:{port}/swagger-ui.html` for all 4 services
- [ ] All controller methods have `@Tag`, `@Operation`, and `@ApiResponse` annotations
- [ ] Postman collection imports without errors (34 requests across 11 folders)
- [ ] Postman collection pre-request scripts auto-refresh JWT tokens
- [ ] Local Postman environment has correct port-per-service URLs
- [ ] Docker Postman environment routes all traffic through gateway (port 8080)
- [ ] All ADRs in `docs/architecture/` reviewed and updated to match implementation
- [ ] No broken links in README or documentation files
