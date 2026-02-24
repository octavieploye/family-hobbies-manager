# Story S6-001: Implement Notification Entities

> 5 points | Priority: P0 | Service: notification-service
> Sprint file: [Back to Sprint Index](./_index.md)
> Tests: [S6-001 Tests Companion](./S6-001-notification-entities-tests.md)

---

## Context

The notification-service needs a persistent data model to store in-app notifications, email templates, and per-user notification preferences. This story creates the three foundational database tables via Liquibase YAML migrations, maps them to JPA entities with Lombok, defines the enums for notification type and category, and implements Spring Data JPA repositories with custom query methods. Every other story in Sprint 6 depends on these entities and repositories -- S6-002 (Kafka consumers) writes notifications, S6-003 (API) reads them, and S6-005 (seed templates) populates the email template table. This is the first code written for the notification-service and must be implemented before any other Sprint 6 work begins.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Liquibase master changelog | `backend/notification-service/src/main/resources/db/changelog/db.changelog-master.yaml` | Master changelog including all changesets | `mvn liquibase:validate -pl backend/notification-service` |
| 2 | Create t_notification table | `backend/notification-service/src/main/resources/db/changelog/changesets/001-create-notification-table.yaml` | Liquibase changeset with table + 3 indexes | Table exists in DB with correct columns and indexes |
| 3 | Create t_email_template table | `backend/notification-service/src/main/resources/db/changelog/changesets/002-create-email-template-table.yaml` | Liquibase changeset with table + unique + 2 indexes | Table exists in DB with correct columns, unique constraint, and indexes |
| 4 | Create t_notification_preference table | `backend/notification-service/src/main/resources/db/changelog/changesets/003-create-notification-preference-table.yaml` | Liquibase changeset with table + unique + 1 index | Table exists in DB with correct columns, unique constraint, and index |
| 5 | NotificationType enum | `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/enums/NotificationType.java` | Enum: EMAIL, IN_APP, SMS | Compiles, used by Notification entity |
| 6 | NotificationCategory enum | `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/enums/NotificationCategory.java` | Enum: WELCOME, PAYMENT_SUCCESS, PAYMENT_FAILED, SUBSCRIPTION_CONFIRMED, SUBSCRIPTION_CANCELLED, ATTENDANCE_REMINDER, SYSTEM | Compiles, used by Notification + NotificationPreference entities |
| 7 | Notification entity | `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/entity/Notification.java` | JPA entity mapping t_notification | Compiles, Hibernate validates schema match |
| 8 | EmailTemplate entity | `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/entity/EmailTemplate.java` | JPA entity mapping t_email_template | Compiles, Hibernate validates schema match |
| 9 | NotificationPreference entity | `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/entity/NotificationPreference.java` | JPA entity mapping t_notification_preference | Compiles, Hibernate validates schema match |
| 10 | NotificationRepository | `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/repository/NotificationRepository.java` | Spring Data JPA repository with custom queries | Compiles, queries return expected results |
| 11 | EmailTemplateRepository | `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/repository/EmailTemplateRepository.java` | Spring Data JPA repository with findByCodeAndActiveTrue | Compiles, query returns expected results |
| 12 | NotificationPreferenceRepository | `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/repository/NotificationPreferenceRepository.java` | Spring Data JPA repository with user/category queries | Compiles, queries return expected results |
| 13 | Failing tests (TDD contract) | `backend/notification-service/src/test/java/...` | JUnit 5 test classes for entities and repositories | Tests compile, define contract |

---

## Task 1 Detail: Liquibase Master Changelog

- **What**: Master changelog file that includes all notification-service changesets in order
- **Where**: `backend/notification-service/src/main/resources/db/changelog/db.changelog-master.yaml`
- **Why**: Liquibase requires a master changelog as the entry point. All individual changesets are included from here.
- **Content**:

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changesets/001-create-notification-table.yaml
  - include:
      file: db/changelog/changesets/002-create-email-template-table.yaml
  - include:
      file: db/changelog/changesets/003-create-notification-preference-table.yaml
  - include:
      file: db/changelog/changesets/004-seed-email-templates.yaml
```

- **Verify**: `mvn liquibase:validate -pl backend/notification-service` -> BUILD SUCCESS

---

## Task 2 Detail: Create t_notification Table

- **What**: Liquibase YAML changeset creating the `t_notification` table with all columns and three indexes
- **Where**: `backend/notification-service/src/main/resources/db/changelog/changesets/001-create-notification-table.yaml`
- **Why**: Core table storing all in-app and email notifications per user. Indexed on user_id for fast lookups, on (user_id, read) for unread count queries, and on category for filtering.
- **Content**:

```yaml
databaseChangeLog:
  - changeSet:
      id: 001-create-notification-table
      author: family-hobbies-team
      changes:
        - createTable:
            tableName: t_notification
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    primaryKeyName: pk_notification
                    nullable: false
              - column:
                  name: user_id
                  type: BIGINT
                  constraints:
                    nullable: false
              - column:
                  name: type
                  type: VARCHAR(20)
                  constraints:
                    nullable: false
              - column:
                  name: category
                  type: VARCHAR(30)
                  constraints:
                    nullable: false
              - column:
                  name: title
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: message
                  type: TEXT
                  constraints:
                    nullable: false
              - column:
                  name: read
                  type: BOOLEAN
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
              - column:
                  name: read_at
                  type: TIMESTAMPTZ
              - column:
                  name: email_sent
                  type: BOOLEAN
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
              - column:
                  name: email_sent_at
                  type: TIMESTAMPTZ
              - column:
                  name: created_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
                  constraints:
                    nullable: false

        - createIndex:
            indexName: idx_notification_user_id
            tableName: t_notification
            columns:
              - column:
                  name: user_id

        - createIndex:
            indexName: idx_notification_user_read
            tableName: t_notification
            columns:
              - column:
                  name: user_id
              - column:
                  name: read

        - createIndex:
            indexName: idx_notification_category
            tableName: t_notification
            columns:
              - column:
                  name: category
```

- **Verify**: Start notification-service -> Liquibase applies changeset -> `\d t_notification` in psql shows all columns and indexes

---

## Task 3 Detail: Create t_email_template Table

- **What**: Liquibase YAML changeset creating the `t_email_template` table with unique constraint on code and two indexes
- **Where**: `backend/notification-service/src/main/resources/db/changelog/changesets/002-create-email-template-table.yaml`
- **Why**: Stores Thymeleaf email templates that the EmailService resolves by category code. The unique constraint ensures no duplicate template codes. Active flag allows soft-disable without deletion.
- **Content**:

```yaml
databaseChangeLog:
  - changeSet:
      id: 002-create-email-template-table
      author: family-hobbies-team
      changes:
        - createTable:
            tableName: t_email_template
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    primaryKeyName: pk_email_template
                    nullable: false
              - column:
                  name: code
                  type: VARCHAR(50)
                  constraints:
                    nullable: false
              - column:
                  name: subject_template
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: body_template
                  type: TEXT
                  constraints:
                    nullable: false
              - column:
                  name: variables
                  type: VARCHAR(500)
              - column:
                  name: active
                  type: BOOLEAN
                  defaultValueBoolean: true
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
                  constraints:
                    nullable: false

        - addUniqueConstraint:
            constraintName: uq_template_code
            tableName: t_email_template
            columnNames: code

        - createIndex:
            indexName: idx_email_template_code
            tableName: t_email_template
            columns:
              - column:
                  name: code

        - createIndex:
            indexName: idx_email_template_active
            tableName: t_email_template
            columns:
              - column:
                  name: active
```

- **Verify**: Start notification-service -> Liquibase applies changeset -> `\d t_email_template` in psql shows all columns, unique constraint, and indexes

---

## Task 4 Detail: Create t_notification_preference Table

- **What**: Liquibase YAML changeset creating the `t_notification_preference` table with unique constraint on (user_id, category) and one index
- **Where**: `backend/notification-service/src/main/resources/db/changelog/changesets/003-create-notification-preference-table.yaml`
- **Why**: Stores per-user, per-category notification preferences. The unique constraint prevents duplicate preferences. Consumers check this table before creating notifications or sending emails.
- **Content**:

```yaml
databaseChangeLog:
  - changeSet:
      id: 003-create-notification-preference-table
      author: family-hobbies-team
      changes:
        - createTable:
            tableName: t_notification_preference
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    primaryKeyName: pk_notification_preference
                    nullable: false
              - column:
                  name: user_id
                  type: BIGINT
                  constraints:
                    nullable: false
              - column:
                  name: category
                  type: VARCHAR(30)
                  constraints:
                    nullable: false
              - column:
                  name: email_enabled
                  type: BOOLEAN
                  defaultValueBoolean: true
                  constraints:
                    nullable: false
              - column:
                  name: in_app_enabled
                  type: BOOLEAN
                  defaultValueBoolean: true
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
                  constraints:
                    nullable: false

        - addUniqueConstraint:
            constraintName: uq_notification_pref_user_category
            tableName: t_notification_preference
            columnNames: user_id, category

        - createIndex:
            indexName: idx_notification_pref_user_id
            tableName: t_notification_preference
            columns:
              - column:
                  name: user_id
```

- **Verify**: Start notification-service -> Liquibase applies changeset -> `\d t_notification_preference` in psql shows all columns, unique constraint, and index

---

## Task 5 Detail: NotificationType Enum

- **What**: Java enum defining the notification delivery channel types
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/enums/NotificationType.java`
- **Why**: Used by the Notification entity to classify how the notification is delivered. Consumers decide which type to create based on user preferences.
- **Content**:

```java
package com.familyhobbies.notificationservice.enums;

/**
 * Defines the delivery channel for a notification.
 *
 * <ul>
 *   <li>{@code EMAIL} -- delivered via SMTP email</li>
 *   <li>{@code IN_APP} -- displayed in the user's notification center</li>
 *   <li>{@code SMS} -- delivered via SMS (future)</li>
 * </ul>
 */
public enum NotificationType {
    EMAIL,
    IN_APP,
    SMS
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles without error

---

## Task 6 Detail: NotificationCategory Enum

- **What**: Java enum defining the business categories of notifications
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/enums/NotificationCategory.java`
- **Why**: Used by both Notification and NotificationPreference entities. Consumers use the category to resolve the correct email template from the EmailTemplateRepository and to check user preferences.
- **Content**:

```java
package com.familyhobbies.notificationservice.enums;

/**
 * Business categories for notifications. Each category maps to:
 * <ul>
 *   <li>An email template code in {@code t_email_template}</li>
 *   <li>A user preference row in {@code t_notification_preference}</li>
 * </ul>
 *
 * <p>Categories align with Kafka event types:
 * <ul>
 *   <li>{@code WELCOME} -- UserRegisteredEvent</li>
 *   <li>{@code PAYMENT_SUCCESS} -- PaymentCompletedEvent</li>
 *   <li>{@code PAYMENT_FAILED} -- PaymentFailedEvent</li>
 *   <li>{@code SUBSCRIPTION_CONFIRMED} -- SubscriptionCreatedEvent</li>
 *   <li>{@code SUBSCRIPTION_CANCELLED} -- SubscriptionCancelledEvent</li>
 *   <li>{@code ATTENDANCE_REMINDER} -- AttendanceMarkedEvent (future threshold alerts)</li>
 *   <li>{@code SYSTEM} -- platform-wide announcements</li>
 * </ul>
 */
public enum NotificationCategory {
    WELCOME,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    SUBSCRIPTION_CONFIRMED,
    SUBSCRIPTION_CANCELLED,
    ATTENDANCE_REMINDER,
    SYSTEM
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles without error

---

## Task 7 Detail: Notification Entity

- **What**: JPA entity mapping the `t_notification` table with all columns, enums stored as strings, and audit timestamps
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/entity/Notification.java`
- **Why**: Core entity that all notification operations read from and write to. The Kafka consumers create Notification records; the REST API reads them.
- **Content**:

```java
package com.familyhobbies.notificationservice.entity;

import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Represents a notification sent to a user, either in-app or via email.
 * Maps to the {@code t_notification} table.
 *
 * <p>Created by Kafka consumers when events are received.
 * Read by the NotificationController for the user's notification center.
 */
@Entity
@Table(name = "t_notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private NotificationCategory category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Builder.Default
    @Column(name = "read", nullable = false)
    private Boolean read = false;

    @Column(name = "read_at")
    private Instant readAt;

    @Builder.Default
    @Column(name = "email_sent", nullable = false)
    private Boolean emailSent = false;

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles; Hibernate schema validation passes on startup

---

## Task 8 Detail: EmailTemplate Entity

- **What**: JPA entity mapping the `t_email_template` table with Thymeleaf template content fields
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/entity/EmailTemplate.java`
- **Why**: Stores email templates that the EmailService resolves by category code. The subject_template and body_template fields contain Thymeleaf expressions. The variables field documents which placeholders are available.
- **Content**:

```java
package com.familyhobbies.notificationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Stores email templates with Thymeleaf syntax for dynamic content rendering.
 * Maps to the {@code t_email_template} table.
 *
 * <p>Each template has a unique {@code code} that corresponds to a
 * {@link com.familyhobbies.notificationservice.enums.NotificationCategory}.
 * The {@code body_template} field contains the full Thymeleaf HTML template.
 * The {@code variables} field documents available placeholders (e.g. "firstName,lastName,email").
 *
 * <p>Templates are seeded via Liquibase changeset 004 (S6-005).
 */
@Entity
@Table(name = "t_email_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "subject_template", nullable = false)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    @Column(name = "variables", length = 500)
    private String variables;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles; Hibernate schema validation passes on startup

---

## Task 9 Detail: NotificationPreference Entity

- **What**: JPA entity mapping the `t_notification_preference` table with per-user, per-category preference flags
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/entity/NotificationPreference.java`
- **Why**: Controls whether a user receives email and/or in-app notifications for each category. Kafka consumers check this before creating notifications. The unique constraint on (user_id, category) ensures one preference row per user per category.
- **Content**:

```java
package com.familyhobbies.notificationservice.entity;

import com.familyhobbies.notificationservice.enums.NotificationCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Per-user, per-category notification preference.
 * Maps to the {@code t_notification_preference} table.
 *
 * <p>Controls two delivery channels:
 * <ul>
 *   <li>{@code emailEnabled} -- whether to send emails for this category</li>
 *   <li>{@code inAppEnabled} -- whether to create in-app notifications for this category</li>
 * </ul>
 *
 * <p>When no preference row exists for a user+category combination, the system
 * defaults to both channels enabled (opt-out model).
 */
@Entity
@Table(name = "t_notification_preference", uniqueConstraints = {
        @UniqueConstraint(name = "uq_notification_pref_user_category",
                columnNames = {"user_id", "category"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private NotificationCategory category;

    @Builder.Default
    @Column(name = "email_enabled", nullable = false)
    private Boolean emailEnabled = true;

    @Builder.Default
    @Column(name = "in_app_enabled", nullable = false)
    private Boolean inAppEnabled = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles; Hibernate schema validation passes on startup

---

## Task 10 Detail: NotificationRepository

- **What**: Spring Data JPA repository for the Notification entity with paginated user queries and unread count
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/repository/NotificationRepository.java`
- **Why**: Provides paginated notification lists for the user's notification center (S6-003) and unread count badge. Also used by RGPD cleanup to delete all notifications for a user.
- **Content**:

```java
package com.familyhobbies.notificationservice.repository;

import com.familyhobbies.notificationservice.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Notification} entities.
 *
 * <p>Custom queries:
 * <ul>
 *   <li>{@link #findByUserIdOrderByCreatedAtDesc} -- paginated notification list for a user</li>
 *   <li>{@link #countByUserIdAndReadFalse} -- unread notification count for badge display</li>
 *   <li>{@link #deleteByUserId} -- RGPD cleanup on user deletion</li>
 * </ul>
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Returns all notifications for a user, newest first, with pagination.
     *
     * @param userId the user's ID
     * @param pageable pagination parameters (page, size)
     * @return a page of notifications ordered by created_at DESC
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * Counts unread notifications for a user. Used for the notification badge.
     *
     * @param userId the user's ID
     * @return number of unread notifications
     */
    long countByUserIdAndReadFalse(Long userId);

    /**
     * Deletes all notifications for a user. Called during RGPD user deletion.
     *
     * @param userId the user's ID
     */
    void deleteByUserId(Long userId);
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles without error

---

## Task 11 Detail: EmailTemplateRepository

- **What**: Spring Data JPA repository for the EmailTemplate entity with active template lookup by code
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/repository/EmailTemplateRepository.java`
- **Why**: Used by the EmailService (S6-002) to resolve the correct template when sending an email. Only active templates are returned.
- **Content**:

```java
package com.familyhobbies.notificationservice.repository;

import com.familyhobbies.notificationservice.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link EmailTemplate} entities.
 *
 * <p>Primary query:
 * <ul>
 *   <li>{@link #findByCodeAndActiveTrue} -- resolves the active template for a given
 *       notification category code (e.g. "WELCOME", "PAYMENT_SUCCESS")</li>
 * </ul>
 */
@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    /**
     * Finds the active email template by its unique code.
     * Returns empty if no template exists or the template is inactive.
     *
     * @param code the template code (matches {@code NotificationCategory.name()})
     * @return the active template, or empty
     */
    Optional<EmailTemplate> findByCodeAndActiveTrue(String code);
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles without error

---

## Task 12 Detail: NotificationPreferenceRepository

- **What**: Spring Data JPA repository for the NotificationPreference entity with user and user+category lookups
- **Where**: `backend/notification-service/src/main/java/com/familyhobbies/notificationservice/repository/NotificationPreferenceRepository.java`
- **Why**: Used by Kafka consumers (S6-002) to check if a user has opted out of email or in-app notifications for a given category. Also used by the preferences API (S6-003) to list and update preferences. RGPD cleanup deletes all preferences for a user.
- **Content**:

```java
package com.familyhobbies.notificationservice.repository;

import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link NotificationPreference} entities.
 *
 * <p>Custom queries:
 * <ul>
 *   <li>{@link #findByUserId} -- all preferences for a user (preferences API)</li>
 *   <li>{@link #findByUserIdAndCategory} -- specific preference check (Kafka consumers)</li>
 *   <li>{@link #deleteByUserId} -- RGPD cleanup on user deletion</li>
 * </ul>
 */
@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    /**
     * Returns all notification preferences for a user across all categories.
     *
     * @param userId the user's ID
     * @return list of preferences (may be empty if user has not customized any)
     */
    List<NotificationPreference> findByUserId(Long userId);

    /**
     * Returns the notification preference for a specific user and category.
     * Used by Kafka consumers to check email_enabled / in_app_enabled before
     * creating notifications.
     *
     * @param userId   the user's ID
     * @param category the notification category
     * @return the preference, or empty (defaults to both channels enabled)
     */
    Optional<NotificationPreference> findByUserIdAndCategory(Long userId, NotificationCategory category);

    /**
     * Deletes all notification preferences for a user. Called during RGPD user deletion.
     *
     * @param userId the user's ID
     */
    void deleteByUserId(Long userId);
}
```

- **Verify**: `mvn compile -pl backend/notification-service` -> compiles without error

---

## Failing Tests (TDD Contract)

Tests are in the companion file: **[S6-001 Notification Entities Tests](./S6-001-notification-entities-tests.md)**

The companion file contains:
- `NotificationEntityTest` -- 8 tests (entity builders, defaults, override, no-args constructor)
- `NotificationEnumTest` -- 7 tests (enum values count, contents, uniqueness, valueOf)
- `NotificationRepositoryTest` -- 7 tests (paginated queries, unread count, delete by user, user isolation)
- `EmailTemplateRepositoryTest` -- 3 tests (active template lookup, inactive filtering, non-existent code)
- `NotificationPreferenceRepositoryTest` -- 5 tests (user preferences, user+category lookup, delete by user)

---

## Acceptance Criteria Checklist

- [ ] All 3 Liquibase changesets apply without error
- [ ] `t_notification` table has 11 columns, 3 indexes
- [ ] `t_email_template` table has 8 columns, 1 unique constraint, 2 indexes
- [ ] `t_notification_preference` table has 7 columns, 1 unique constraint, 1 index
- [ ] `NotificationType` enum has 3 values: EMAIL, IN_APP, SMS
- [ ] `NotificationCategory` enum has 7 values matching Kafka event types
- [ ] All 3 JPA entities compile and map to their respective tables
- [ ] Entity builder defaults are correct (read=false, emailSent=false, active=true, etc.)
- [ ] `NotificationRepository` custom queries work (paginated user lookup, unread count, delete by user)
- [ ] `EmailTemplateRepository.findByCodeAndActiveTrue` returns only active templates
- [ ] `NotificationPreferenceRepository` queries work (by user, by user+category, delete by user)
- [ ] All 30 JUnit 5 tests pass green
