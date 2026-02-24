# Phase 2: Core Domain

> Sprints 2-4 | Weeks 5-10
> Write the sprints,stories and tasks for a senior engineer expert in code and stacks
>

---

## Phase Goal

Core business domain fully functional. A family can be created with members, search associations by
location/category, view activities and sessions, subscribe members to activities, and track attendance.
RGPD consent management and data export implemented.

## Phase Exit Criteria

- [ ] Family CRUD with member management working end-to-end
- [ ] Association search with filters (city, category, keyword) returning paginated results
- [ ] Activity and session browsing within associations
- [ ] Subscription lifecycle (create, view, cancel) with business rules enforced
- [ ] Attendance marking and tracking per session per member
- [ ] RGPD consent management, data export, and account deletion
- [ ] Dashboard with family overview, subscriptions, and upcoming sessions widgets
- [ ] All unit and integration tests passing

## Services Created in This Phase

| Service | Sprint | Port | Database |
|---------|--------|------|----------|
| association-service | Sprint 2 | 8082 | `familyhobbies_associations` |

## Shared Modules Created in This Phase

None — Phase 2 uses error-handling and common from Phase 1.

## Sprint Documents

| Sprint | File | Stories | Points |
|--------|------|---------|--------|
| Sprint 2 | `sprint-2-family-associations.md` | S2-001 through S2-006 | ~38 |
| Sprint 3 | `sprint-3-activities-subscriptions.md` | S3-001 through S3-006 | ~34 |
| Sprint 4 | `sprint-4-attendance-rgpd.md` | S4-001 through S4-006 | ~34 |
