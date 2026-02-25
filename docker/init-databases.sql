-- =============================================================================
-- Family Hobbies Manager - Database Initialization
-- Creates service-specific databases on first PostgreSQL startup.
-- Add new databases here as services are scaffolded in later sprints.
-- =============================================================================

-- Sprint 0: user-service database
CREATE DATABASE familyhobbies_users;
GRANT ALL PRIVILEGES ON DATABASE familyhobbies_users TO fhm_admin;
