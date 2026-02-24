# Delivery Phases — Execution Blueprints

> **Family Hobbies Manager** -- Multi-Association Management Platform
> Sprint-level execution guides with file paths, commands, and TDD contracts

---

## Reading Order

Read phases in order. Each phase depends on the previous phase being complete.

| Phase | Directory | Sprints | Theme | Key Deliverables |
|-------|-----------|---------|-------|------------------|
| **1** | `phase-1-foundation/` | Sprint 0, Sprint 1 | Infrastructure + Security | Parent POM, error-handling, common, Docker, Eureka, Gateway, JWT auth, Angular auth |
| **2** | `phase-2-core-domain/` | Sprint 2, 3, 4 | Core Business Domain | Family CRUD, associations, activities, sessions, subscriptions, attendance, RGPD |
| **3** | `phase-3-payments-notifications/` | Sprint 5, 6 | Payments + Notifications | HelloAsso Checkout, webhooks, invoices, Kafka consumers, email dispatch |
| **4** | `phase-4-polish/` | Sprint 7, 8 | Dashboards + Hardening | Dashboard widgets, batch jobs, E2E tests, RGAA audit, performance |

---

## Incremental Build Strategy

Services, databases, and infrastructure are created **only when a sprint story requires them**.
Nothing is scaffolded "just in case."

### Service Scaffold Timeline

| Service | Scaffolded In | First Story | Database Created In |
|---------|--------------|-------------|---------------------|
| discovery-service | Sprint 0 (S0-005) | S0-005 | -- (no DB) |
| api-gateway | Sprint 0 (S0-006) | S0-006 | -- (no DB) |
| user-service | Sprint 0 (S0-007) | S1-002 | Sprint 0 (S0-002) |
| association-service | Sprint 2 (S2-001) | S2-001 | Sprint 2 (S2-001) |
| payment-service | Sprint 5 (S5-001) | S5-001 | Sprint 5 (S5-001) |
| notification-service | Sprint 6 (S6-001) | S6-001 | Sprint 6 (S6-001) |

### Docker Compose Growth

| Sprint | What Gets Added | docker-compose.yml Services |
|--------|----------------|----------------------------|
| Sprint 0 | PostgreSQL 16 | `postgres` |
| Sprint 1 | -- | `postgres` |
| Sprint 2 | -- (second DB in same PostgreSQL) | `postgres` |
| Sprint 5 | Apache Kafka + Zookeeper | `postgres`, `zookeeper`, `kafka` |

### Shared Module Timeline

| Module | Created In | First Consumer | Tests |
|--------|-----------|----------------|-------|
| error-handling | Sprint 0 (S0-003) | common (S0-004) | 44 unit tests |
| common | Sprint 0 (S0-004) | user-service (Sprint 1) | Compiles, audit entity test |

---

## Document Format Standard

Every sprint document follows this exact structure:

```
# Phase X / Sprint Y: [Title]

## Sprint Goal           -- one sentence, measurable
## Prerequisites         -- what must exist before starting
## Dependency Map        -- mermaid diagram
## Stories
  ### Story [ID]: [Title]
    #### Context         -- WHY this story exists
    #### Tasks Table     -- # | Task | File Path | What To Create | How To Verify
    #### Task [N] Detail
      - What             -- precise deliverable
      - Where            -- absolute file path
      - Why              -- dependency reason
      - Content          -- exact file contents (class skeleton, YAML, SQL)
      - Verify           -- command + expected output
    #### Failing Tests   -- TDD contract (full test source code)
## Sprint Verification   -- commands to validate entire sprint is done
```

---

## Cross-References

These phase documents are the execution layer of the architecture. They reference:

| Document | What It Provides |
|----------|-----------------|
| `00-system-overview.md` | C4 diagrams, architecture principles |
| `01-service-catalog.md` | Service details, ports, communication matrix |
| `02-data-model.md` | Entity relationships, column definitions |
| `03-api-contracts.md` | REST endpoints, request/response shapes |
| `11-naming-conventions.md` | File naming, package naming, DB naming rules |
| `12-delivery-roadmap.md` | Story IDs, point estimates, sprint capacity |
| `error-handling/13-error-handling.md` | Error module specification |
