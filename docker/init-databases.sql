-- =============================================================================
-- Family Hobbies Manager - Database Initialization
-- Creates service-specific databases on first PostgreSQL startup.
-- Add new databases here as services are scaffolded in later sprints.
-- =============================================================================

-- Sprint 0: user-service database
CREATE DATABASE familyhobbies_users;
GRANT ALL PRIVILEGES ON DATABASE familyhobbies_users TO fhm_admin;

-- Sprint 2: association-service database
CREATE DATABASE familyhobbies_associations;
GRANT ALL PRIVILEGES ON DATABASE familyhobbies_associations TO fhm_admin;

-- Sprint 5: payment-service database
CREATE DATABASE familyhobbies_payments;
GRANT ALL PRIVILEGES ON DATABASE familyhobbies_payments TO fhm_admin;

-- Sprint 6: notification-service database
CREATE DATABASE familyhobbies_notifications;
GRANT ALL PRIVILEGES ON DATABASE familyhobbies_notifications TO fhm_admin;
