# Phase 1: Foundation

> Sprints 0-1 | Weeks 1-4

---

## Phase Goal

All shared infrastructure running, error handling module tested and green, JWT authentication
working end-to-end from Angular through API Gateway to user-service.

## Phase Exit Criteria

- [ ] Docker Compose starts PostgreSQL without errors
- [ ] Parent POM builds all modules: `mvn clean package -DskipTests`
- [ ] Error-handling module: 44 tests pass (`cd backend/error-handling && mvn test`)
- [ ] Common library compiles and depends on error-handling
- [ ] Discovery-service: Eureka dashboard accessible at `http://localhost:8761`
- [ ] API Gateway: routes resolve to downstream services
- [ ] User-service: register, login, refresh endpoints return correct responses
- [ ] Angular: login/register forms work, JWT interceptor attaches token
- [ ] CI pipeline: GitHub Actions green on push to main

## Services Created in This Phase

| Service | Sprint | Port | Database |
|---------|--------|------|----------|
| discovery-service | Sprint 0 | 8761 | -- |
| api-gateway | Sprint 0 | 8080 | -- |
| user-service | Sprint 0 | 8081 | `familyhobbies_users` |

## Shared Modules Created in This Phase

| Module | Sprint | Depends On |
|--------|--------|------------|
| error-handling | Sprint 0 | -- (standalone) |
| common | Sprint 0 | error-handling |

## Sprint Documents

| Sprint | File | Stories | Points |
|--------|------|---------|--------|
| Sprint 0 | `sprint-0-infrastructure.md` | S0-001 through S0-008 | ~30 |
| Sprint 1 | `sprint-1-security.md` | S1-001 through S1-008 | ~39 |
