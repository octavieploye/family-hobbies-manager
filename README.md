# Family Hobbies Manager

Multi-association management application (sport, dance, music, theater) across 3 locations. Families register, manage subscriptions, track events/courses/attendance.

## Tech Stack

- **Backend**: Java 17 / Spring Boot 3 / Spring Cloud
- **Frontend**: Angular 17+ / Angular Material
- **Database**: PostgreSQL 16
- **Messaging**: Apache Kafka
- **Infrastructure**: Docker / Docker Compose
- **CI/CD**: GitHub Actions

## Project Structure

```
family-hobbies-manager/
├── backend/          # Spring Boot microservices
├── frontend/         # Angular application
├── docker/           # Docker Compose & configs
├── docs/             # Architecture & API documentation
└── e2e/              # End-to-end tests (Playwright)
```

## Microservices

| Service              | Port | Description                        |
|----------------------|------|------------------------------------|
| discovery-service    | 8761 | Eureka service registry            |
| api-gateway          | 8080 | Spring Cloud Gateway               |
| user-service         | 8081 | Authentication, users, families    |
| association-service  | 8082 | Associations, activities, sessions |
| payment-service      | 8083 | Payments, invoices                 |
| notification-service | 8084 | Notifications, email templates     |

## Getting Started

_Coming soon — see `docs/` for architecture details._
