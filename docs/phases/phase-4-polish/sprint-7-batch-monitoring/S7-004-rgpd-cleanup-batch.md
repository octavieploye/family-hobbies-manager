# Story S7-004: Implement RGPD Data Cleanup Batch Job

> 5 points | Priority: P1 | Service: user-service
> Sprint file: [Back to Sprint Index](./_index.md)
> Tests: [S7-004 Tests Companion](./S7-004-rgpd-cleanup-batch-tests.md)

---

## Context

The Family Hobbies Manager handles personal data of French families and their members -- subject to the RGPD (General Data Protection Regulation). When a user requests account deletion, their status is set to `DELETED` but personal data is retained for 30 days to allow for account recovery or dispute resolution. After 30 days, all PII (Personally Identifiable Information) must be irreversibly anonymized: first name, last name, email, phone number, date of birth, address, city, and postal code are replaced with hashed values. This story implements a Spring Batch job running weekly on Sundays at 3 AM that identifies users whose data is eligible for anonymization (`status=DELETED`, `updated_at` older than 30 days, `anonymized=false`), hard-anonymizes all PII fields using SHA-256 hashing, triggers cross-service cleanup on `association-service` and `payment-service`, and creates an audit trail in the `t_rgpd_cleanup_log` table. The audit log records the number of users processed, anonymization outcomes, and cross-service cleanup status. A `RgpdCleanupJobListener` captures job-level metrics after execution. An admin endpoint allows manual triggering. This story establishes the project's compliance with RGPD Article 17 (Right to Erasure) and demonstrates production-grade data lifecycle management.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Spring Batch Maven dependency | `backend/user-service/pom.xml` | Add spring-boot-starter-batch dependency | `mvn compile` |
| 2 | Liquibase 007 -- t_rgpd_cleanup_log table | `backend/user-service/src/main/resources/db/changelog/changesets/007-create-rgpd-cleanup-log-table.xml` | Create audit log table | Migration runs |
| 3 | Liquibase 008 -- Spring Batch metadata | `backend/user-service/src/main/resources/db/changelog/changesets/008-create-batch-metadata-tables.xml` | Enable Spring Batch schema initialization | Migration runs |
| 4 | RgpdCleanupLog entity | `backend/user-service/src/main/java/.../entity/RgpdCleanupLog.java` | JPA entity for audit log | Compiles |
| 5 | RgpdCleanupLogRepository | `backend/user-service/src/main/java/.../repository/RgpdCleanupLogRepository.java` | Spring Data JPA repository | Compiles |
| 6 | CrossServiceCleanupStatus enum | `backend/user-service/src/main/java/.../enums/CrossServiceCleanupStatus.java` | Enum: SUCCESS, PARTIAL_FAILURE, FAILED | Compiles |
| 7 | UserRepository -- add eligible users query | `backend/user-service/src/main/java/.../repository/UserRepository.java` | `findEligibleForAnonymization` method | Compiles |
| 8 | RgpdAnonymizationProcessor | `backend/user-service/src/main/java/.../batch/processor/RgpdAnonymizationProcessor.java` | Anonymize PII fields with SHA-256 | Unit tests pass |
| 9 | AssociationServiceClient adapter | `backend/user-service/src/main/java/.../adapter/AssociationServiceClient.java` | WebClient calling DELETE on association-service | Unit tests pass |
| 10 | PaymentServiceClient adapter | `backend/user-service/src/main/java/.../adapter/PaymentServiceClient.java` | WebClient calling DELETE on payment-service | Unit tests pass |
| 11 | RgpdCleanupWriter | `backend/user-service/src/main/java/.../batch/writer/RgpdCleanupWriter.java` | Save anonymized users + trigger cross-service cleanup | Unit tests pass |
| 12 | RgpdCleanupJobListener | `backend/user-service/src/main/java/.../batch/listener/RgpdCleanupJobListener.java` | Job listener creating audit log entry | Unit tests pass |
| 13 | RgpdDataCleanupJobConfig | `backend/user-service/src/main/java/.../batch/config/RgpdDataCleanupJobConfig.java` | Full Spring Batch job configuration | Job bean created |
| 14 | BatchSchedulerConfig | `backend/user-service/src/main/java/.../batch/config/BatchSchedulerConfig.java` | CRON scheduling at `0 0 3 * * SUN` | Scheduled trigger fires |
| 15 | AdminBatchController | `backend/user-service/src/main/java/.../controller/AdminBatchController.java` | POST `/admin/batch/rgpd-cleanup` | Returns 200 with job execution ID |
| 16 | application.yml -- batch config | `backend/user-service/src/main/resources/application.yml` | Spring Batch + cross-service config | Service starts |
| 17 | Failing tests (TDD) | See companion file | 7 JUnit 5 test classes, ~34 test cases | Tests compile, fail (TDD) |

---

## Task 1 Detail: Spring Batch Maven Dependency

- **What**: Add `spring-boot-starter-batch` and `spring-batch-test` dependencies to the user-service
- **Where**: `backend/user-service/pom.xml`
- **Why**: Provides the Spring Batch 5.x framework for the RGPD cleanup job
- **Content** (add to `<dependencies>` section):

```xml
<!-- Spring Batch for RGPD cleanup -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-batch</artifactId>
</dependency>

<!-- Spring Batch test support -->
<dependency>
    <groupId>org.springframework.batch</groupId>
    <artifactId>spring-batch-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- WebClient for cross-service calls -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles

---

## Task 2 Detail: Liquibase 007 -- Create t_rgpd_cleanup_log Table

- **What**: Liquibase changeset creating the `t_rgpd_cleanup_log` table for RGPD audit trail
- **Where**: `backend/user-service/src/main/resources/db/changelog/changesets/007-create-rgpd-cleanup-log-table.xml`
- **Why**: RGPD compliance requires an audit trail of all anonymization operations. This table records each cleanup execution with user counts, status, and error details.
- **Content**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="007-create-rgpd-cleanup-log-table" author="family-hobbies-team">
        <comment>
            RGPD compliance audit table. Records each data cleanup batch execution
            with processed/anonymized counts, cross-service cleanup status, and errors.
        </comment>
        <preConditions onFail="MARK_RAN">
            <not>
                <tableExists tableName="t_rgpd_cleanup_log"/>
            </not>
        </preConditions>
        <createTable tableName="t_rgpd_cleanup_log">
            <column name="id" type="BIGINT" autoIncrement="true">
                <constraints primaryKey="true" primaryKeyName="pk_rgpd_cleanup_log" nullable="false"/>
            </column>
            <column name="execution_timestamp" type="TIMESTAMPTZ">
                <constraints nullable="false"/>
            </column>
            <column name="users_processed" type="INTEGER" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="users_anonymized" type="INTEGER" defaultValueNumeric="0">
                <constraints nullable="false"/>
            </column>
            <column name="cross_service_cleanup_status" type="VARCHAR(20)" defaultValue="SUCCESS">
                <constraints nullable="false"/>
            </column>
            <column name="error_details" type="TEXT"/>
            <column name="created_at" type="TIMESTAMPTZ" defaultValueComputed="CURRENT_TIMESTAMP">
                <constraints nullable="false"/>
            </column>
        </createTable>
        <sql>
            ALTER TABLE t_rgpd_cleanup_log
            ADD CONSTRAINT chk_cross_service_cleanup_status
            CHECK (cross_service_cleanup_status IN ('SUCCESS', 'PARTIAL_FAILURE', 'FAILED'));
        </sql>
        <createIndex tableName="t_rgpd_cleanup_log" indexName="idx_rgpd_cleanup_log_execution_timestamp">
            <column name="execution_timestamp" descending="true"/>
        </createIndex>
    </changeSet>

</databaseChangeLog>
```

- **Verify**: `mvn liquibase:update -pl backend/user-service` -> table `t_rgpd_cleanup_log` created with check constraint and index

---

## Task 3 Detail: Liquibase 008 -- Spring Batch Metadata Tables

- **What**: Liquibase documentation marker for Spring Batch metadata table auto-initialization
- **Where**: `backend/user-service/src/main/resources/db/changelog/changesets/008-create-batch-metadata-tables.xml`
- **Why**: Spring Batch requires metadata tables. Same pattern as S7-003 in payment-service.
- **Content**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="008-spring-batch-metadata-tables" author="family-hobbies-team">
        <comment>
            Spring Batch 5.x metadata tables are auto-created by Spring Boot
            via spring.batch.jdbc.initialize-schema=always.
            This changeset is a documentation marker only.
        </comment>
        <tagDatabase tag="spring-batch-metadata-initialized"/>
    </changeSet>

</databaseChangeLog>
```

- **Verify**: `mvn liquibase:update -pl backend/user-service` -> tag applied

---

## Task 4 Detail: RgpdCleanupLog Entity

- **What**: JPA entity mapping the `t_rgpd_cleanup_log` table
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/entity/RgpdCleanupLog.java`
- **Why**: Records each RGPD cleanup batch execution for audit trail
- **Content**:

```java
package com.familyhobbies.userservice.entity;

import com.familyhobbies.userservice.enums.CrossServiceCleanupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Audit entity recording each RGPD data cleanup batch execution.
 *
 * <p>Tracks:
 * <ul>
 *     <li>How many users were eligible for anonymization (usersProcessed)</li>
 *     <li>How many were successfully anonymized (usersAnonymized)</li>
 *     <li>Whether cross-service cleanup (association-service, payment-service) succeeded</li>
 *     <li>Error details if any failures occurred</li>
 * </ul>
 *
 * <p>Maps to table {@code t_rgpd_cleanup_log}.
 */
@Entity
@Table(name = "t_rgpd_cleanup_log")
public class RgpdCleanupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_timestamp", nullable = false)
    private Instant executionTimestamp;

    @Column(name = "users_processed", nullable = false)
    private int usersProcessed;

    @Column(name = "users_anonymized", nullable = false)
    private int usersAnonymized;

    @Enumerated(EnumType.STRING)
    @Column(name = "cross_service_cleanup_status", nullable = false, length = 20)
    private CrossServiceCleanupStatus crossServiceCleanupStatus = CrossServiceCleanupStatus.SUCCESS;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    // --- Constructors ---

    public RgpdCleanupLog() {
    }

    public RgpdCleanupLog(Instant executionTimestamp, int usersProcessed, int usersAnonymized,
                           CrossServiceCleanupStatus crossServiceCleanupStatus, String errorDetails) {
        this.executionTimestamp = executionTimestamp;
        this.usersProcessed = usersProcessed;
        this.usersAnonymized = usersAnonymized;
        this.crossServiceCleanupStatus = crossServiceCleanupStatus;
        this.errorDetails = errorDetails;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getExecutionTimestamp() {
        return executionTimestamp;
    }

    public void setExecutionTimestamp(Instant executionTimestamp) {
        this.executionTimestamp = executionTimestamp;
    }

    public int getUsersProcessed() {
        return usersProcessed;
    }

    public void setUsersProcessed(int usersProcessed) {
        this.usersProcessed = usersProcessed;
    }

    public int getUsersAnonymized() {
        return usersAnonymized;
    }

    public void setUsersAnonymized(int usersAnonymized) {
        this.usersAnonymized = usersAnonymized;
    }

    public CrossServiceCleanupStatus getCrossServiceCleanupStatus() {
        return crossServiceCleanupStatus;
    }

    public void setCrossServiceCleanupStatus(CrossServiceCleanupStatus crossServiceCleanupStatus) {
        this.crossServiceCleanupStatus = crossServiceCleanupStatus;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles, Hibernate validates schema

---

## Task 5 Detail: RgpdCleanupLogRepository

- **What**: Spring Data JPA repository for `RgpdCleanupLog`
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/repository/RgpdCleanupLogRepository.java`
- **Why**: Persistence layer for the RGPD audit log
- **Content**:

```java
package com.familyhobbies.userservice.repository;

import com.familyhobbies.userservice.entity.RgpdCleanupLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for RGPD cleanup audit logs.
 */
@Repository
public interface RgpdCleanupLogRepository extends JpaRepository<RgpdCleanupLog, Long> {

    /**
     * Find all cleanup logs ordered by execution timestamp descending (most recent first).
     */
    Page<RgpdCleanupLog> findAllByOrderByExecutionTimestampDesc(Pageable pageable);

    /**
     * Find cleanup logs within a date range (for audit reporting).
     */
    List<RgpdCleanupLog> findByExecutionTimestampBetweenOrderByExecutionTimestampDesc(
            Instant from, Instant to
    );
}
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles

---

## Task 6 Detail: CrossServiceCleanupStatus Enum

- **What**: Enum representing the outcome of cross-service data cleanup calls
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/enums/CrossServiceCleanupStatus.java`
- **Why**: Used by `RgpdCleanupLog` and the writer to track whether association-service and payment-service cleanup succeeded
- **Content**:

```java
package com.familyhobbies.userservice.enums;

/**
 * Outcome of cross-service data cleanup during RGPD anonymization.
 *
 * <p>After anonymizing a user's PII in user-service, the system calls
 * association-service and payment-service to delete or anonymize that
 * user's data in those services as well.
 */
public enum CrossServiceCleanupStatus {

    /** Both association-service and payment-service cleanup succeeded. */
    SUCCESS,

    /** One of the two services failed, but the other succeeded. */
    PARTIAL_FAILURE,

    /** Both cross-service cleanup calls failed. */
    FAILED
}
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles

---

## Task 7 Detail: UserRepository -- Add Eligible Users Query

- **What**: Add a query method to find users eligible for RGPD anonymization
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/repository/UserRepository.java`
- **Why**: The batch reader needs to find users with `status=DELETED`, `updatedAt` older than 30 days, and `anonymized=false`
- **Content** (add to existing repository):

```java
package com.familyhobbies.userservice.repository;

import com.familyhobbies.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // --- Existing methods from Sprint 1/4 ---

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // --- New method for S7-004: RGPD Data Cleanup Batch ---

    /**
     * Find users eligible for RGPD anonymization.
     *
     * <p>Criteria:
     * <ul>
     *     <li>{@code status = 'DELETED'} — user has requested account deletion</li>
     *     <li>{@code updated_at < cutoff} — at least 30 days have passed since deletion</li>
     *     <li>{@code anonymized = false} — data has not yet been anonymized</li>
     * </ul>
     *
     * @param cutoff users deleted before this timestamp are eligible
     * @return list of users whose PII must be anonymized
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.status = 'DELETED'
              AND u.updatedAt < :cutoff
              AND u.anonymized = false
            ORDER BY u.updatedAt ASC
            """)
    List<User> findEligibleForAnonymization(@Param("cutoff") Instant cutoff);
}
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles

---

## Task 8 Detail: RgpdAnonymizationProcessor

- **What**: Spring Batch `ItemProcessor<User, User>` that replaces all PII fields with SHA-256 hashed values
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/batch/processor/RgpdAnonymizationProcessor.java`
- **Why**: Core anonymization logic. PII is replaced with irreversible hashed values, making re-identification impossible while preserving data structure integrity.
- **Content**:

```java
package com.familyhobbies.userservice.batch.processor;

import com.familyhobbies.userservice.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

/**
 * Anonymizes all PII (Personally Identifiable Information) fields of a deleted user.
 *
 * <p>Replaces the following fields with SHA-256 hashed values:
 * <ul>
 *     <li>{@code firstName} -> {@code ANON-{hash8}}</li>
 *     <li>{@code lastName} -> {@code ANON-{hash8}}</li>
 *     <li>{@code email} -> {@code anon-{hash8}@anonymized.local}</li>
 *     <li>{@code phoneNumber} -> {@code 0000000000}</li>
 *     <li>{@code dateOfBirth} -> {@code 1970-01-01}</li>
 *     <li>{@code address} -> {@code ANONYMIZED}</li>
 *     <li>{@code city} -> {@code ANONYMIZED}</li>
 *     <li>{@code postalCode} -> {@code 00000}</li>
 * </ul>
 *
 * <p>After anonymization, sets {@code anonymized = true} to prevent re-processing.
 *
 * <p>The hash is computed from the original value concatenated with the user ID
 * to ensure uniqueness across users with identical PII values.
 */
public class RgpdAnonymizationProcessor implements ItemProcessor<User, User> {

    private static final Logger log = LoggerFactory.getLogger(RgpdAnonymizationProcessor.class);
    private static final LocalDate ANONYMIZED_DOB = LocalDate.of(1970, 1, 1);
    private static final String ANONYMIZED_PHONE = "0000000000";
    private static final String ANONYMIZED_ADDRESS = "ANONYMIZED";
    private static final String ANONYMIZED_CITY = "ANONYMIZED";
    private static final String ANONYMIZED_POSTAL_CODE = "00000";

    /**
     * Anonymize all PII fields of the given user.
     *
     * @param user the deleted user whose data must be anonymized
     * @return the user with all PII replaced by hashed/anonymized values
     */
    @Override
    public User process(User user) throws Exception {
        log.info("Anonymizing PII for user id={}", user.getId());

        String salt = String.valueOf(user.getId());

        // Anonymize name fields
        user.setFirstName("ANON-" + hashAndTruncate(user.getFirstName(), salt));
        user.setLastName("ANON-" + hashAndTruncate(user.getLastName(), salt));

        // Anonymize email -- must remain unique and valid format
        String emailHash = hashAndTruncate(user.getEmail(), salt);
        user.setEmail("anon-" + emailHash + "@anonymized.local");

        // Anonymize phone number
        user.setPhoneNumber(ANONYMIZED_PHONE);

        // Anonymize date of birth
        user.setDateOfBirth(ANONYMIZED_DOB);

        // Anonymize address fields
        user.setAddress(ANONYMIZED_ADDRESS);
        user.setCity(ANONYMIZED_CITY);
        user.setPostalCode(ANONYMIZED_POSTAL_CODE);

        // Mark as anonymized to prevent re-processing
        user.setAnonymized(true);

        log.info("Successfully anonymized PII for user id={}", user.getId());
        return user;
    }

    /**
     * Compute a SHA-256 hash of the input combined with a salt, and return
     * the first 8 hex characters. This produces a short, irreversible identifier.
     *
     * @param input the original PII value
     * @param salt  a per-user salt (typically the user ID) to ensure uniqueness
     * @return first 8 characters of the hex-encoded SHA-256 hash
     */
    String hashAndTruncate(String input, String salt) {
        if (input == null) {
            input = "null";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                    (input + "|" + salt).getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all JVM implementations
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles

---

## Task 9 Detail: AssociationServiceClient Adapter

- **What**: WebClient adapter to call the association-service's internal cleanup endpoint
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/adapter/AssociationServiceClient.java`
- **Why**: RGPD cleanup must propagate to all services holding user data. The association-service stores subscriptions, attendance records, and session registrations linked to user IDs.
- **Content**:

```java
package com.familyhobbies.userservice.adapter;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * WebClient adapter for calling association-service internal cleanup endpoint.
 *
 * <p>Calls {@code DELETE /api/v1/internal/users/{userId}/data} to trigger
 * anonymization/deletion of user-related data in the association-service
 * (subscriptions, attendance records, session registrations).
 *
 * <p>This is an internal service-to-service call, not exposed to external clients.
 */
@Component
public class AssociationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AssociationServiceClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;

    public AssociationServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${services.association-service.url:http://association-service:8082}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Trigger user data cleanup in association-service.
     *
     * @param userId the ID of the user whose data should be cleaned up
     * @throws ExternalApiException if the association-service is unreachable or returns an error
     */
    public void cleanupUserData(Long userId) {
        log.info("Calling association-service to cleanup data for userId={}", userId);

        try {
            webClient.delete()
                    .uri("/api/v1/internal/users/{userId}/data", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> {
                        log.error("association-service returned {} for user cleanup userId={}",
                                response.statusCode(), userId);
                        return response.bodyToMono(String.class)
                                .map(body -> new ExternalApiException(
                                        "association-service cleanup failed for userId=" + userId
                                                + ": " + response.statusCode() + " " + body));
                    })
                    .toBodilessEntity()
                    .timeout(TIMEOUT)
                    .block();

            log.info("association-service cleanup completed for userId={}", userId);

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to reach association-service for userId={}: {}", userId, e.getMessage());
            throw new ExternalApiException(
                    "association-service unreachable for user cleanup userId=" + userId, e);
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles

---

## Task 10 Detail: PaymentServiceClient Adapter

- **What**: WebClient adapter to call the payment-service's internal cleanup endpoint
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/adapter/PaymentServiceClient.java`
- **Why**: RGPD cleanup must propagate to payment-service, which holds payment records, invoices, and billing details linked to user IDs.
- **Content**:

```java
package com.familyhobbies.userservice.adapter;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * WebClient adapter for calling payment-service internal cleanup endpoint.
 *
 * <p>Calls {@code DELETE /api/v1/internal/users/{userId}/data} to trigger
 * anonymization/deletion of user-related data in the payment-service
 * (payment records, invoices, billing details).
 *
 * <p>This is an internal service-to-service call, not exposed to external clients.
 */
@Component
public class PaymentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;

    public PaymentServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${services.payment-service.url:http://payment-service:8083}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Trigger user data cleanup in payment-service.
     *
     * @param userId the ID of the user whose data should be cleaned up
     * @throws ExternalApiException if the payment-service is unreachable or returns an error
     */
    public void cleanupUserData(Long userId) {
        log.info("Calling payment-service to cleanup data for userId={}", userId);

        try {
            webClient.delete()
                    .uri("/api/v1/internal/users/{userId}/data", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> {
                        log.error("payment-service returned {} for user cleanup userId={}",
                                response.statusCode(), userId);
                        return response.bodyToMono(String.class)
                                .map(body -> new ExternalApiException(
                                        "payment-service cleanup failed for userId=" + userId
                                                + ": " + response.statusCode() + " " + body));
                    })
                    .toBodilessEntity()
                    .timeout(TIMEOUT)
                    .block();

            log.info("payment-service cleanup completed for userId={}", userId);

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to reach payment-service for userId={}: {}", userId, e.getMessage());
            throw new ExternalApiException(
                    "payment-service unreachable for user cleanup userId=" + userId, e);
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles

---

## Task 11 Detail: RgpdCleanupWriter

- **What**: Spring Batch `ItemWriter<User>` that persists anonymized users and triggers cross-service cleanup
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/batch/writer/RgpdCleanupWriter.java`
- **Why**: Handles the write phase: saves anonymized users, calls association-service and payment-service for data cleanup, and tracks cross-service cleanup outcomes
- **Content**:

```java
package com.familyhobbies.userservice.batch.writer;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.userservice.adapter.AssociationServiceClient;
import com.familyhobbies.userservice.adapter.PaymentServiceClient;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.enums.CrossServiceCleanupStatus;
import com.familyhobbies.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writes anonymized users and triggers cross-service data cleanup.
 *
 * <p>For each anonymized user in the chunk:
 * <ol>
 *     <li>Saves the anonymized user entity to the database</li>
 *     <li>Calls association-service to delete/anonymize user data</li>
 *     <li>Calls payment-service to delete/anonymize user data</li>
 * </ol>
 *
 * <p>Cross-service failures are logged but do not prevent the local anonymization
 * from being persisted. The cross-service cleanup status is tracked and reported
 * in the {@link com.familyhobbies.userservice.entity.RgpdCleanupLog}.
 *
 * <p>Uses Spring Batch 5.x {@link Chunk} API.
 */
public class RgpdCleanupWriter implements ItemWriter<User> {

    private static final Logger log = LoggerFactory.getLogger(RgpdCleanupWriter.class);

    private final UserRepository userRepository;
    private final AssociationServiceClient associationServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    // Tracks cross-service cleanup outcomes for the job listener
    private final AtomicInteger anonymizedCount = new AtomicInteger(0);
    private final AtomicReference<CrossServiceCleanupStatus> overallCleanupStatus =
            new AtomicReference<>(CrossServiceCleanupStatus.SUCCESS);
    private final List<String> errorMessages = new ArrayList<>();

    public RgpdCleanupWriter(UserRepository userRepository,
                              AssociationServiceClient associationServiceClient,
                              PaymentServiceClient paymentServiceClient) {
        this.userRepository = userRepository;
        this.associationServiceClient = associationServiceClient;
        this.paymentServiceClient = paymentServiceClient;
    }

    @Override
    public void write(Chunk<? extends User> chunk) throws Exception {
        log.info("Writing {} anonymized users", chunk.size());

        for (User user : chunk) {
            // 1. Persist the anonymized user locally
            userRepository.save(user);
            anonymizedCount.incrementAndGet();

            // 2. Trigger cross-service cleanup
            triggerCrossServiceCleanup(user.getId());
        }

        log.info("Successfully wrote {} anonymized users", chunk.size());
    }

    /**
     * Call association-service and payment-service to cleanup user data.
     * Failures are logged but do not abort the batch.
     */
    private void triggerCrossServiceCleanup(Long userId) {
        boolean associationSuccess = callServiceSafely(
                () -> associationServiceClient.cleanupUserData(userId),
                "association-service", userId);

        boolean paymentSuccess = callServiceSafely(
                () -> paymentServiceClient.cleanupUserData(userId),
                "payment-service", userId);

        if (!associationSuccess || !paymentSuccess) {
            if (!associationSuccess && !paymentSuccess) {
                overallCleanupStatus.set(CrossServiceCleanupStatus.FAILED);
            } else {
                // Only upgrade to PARTIAL_FAILURE if not already FAILED
                overallCleanupStatus.compareAndSet(
                        CrossServiceCleanupStatus.SUCCESS,
                        CrossServiceCleanupStatus.PARTIAL_FAILURE);
            }
        }
    }

    /**
     * Call a cross-service cleanup, catching and logging any errors.
     *
     * @return {@code true} if the call succeeded, {@code false} otherwise
     */
    private boolean callServiceSafely(Runnable serviceCall, String serviceName, Long userId) {
        try {
            serviceCall.run();
            return true;
        } catch (ExternalApiException e) {
            String error = String.format("%s cleanup failed for userId=%d: %s",
                    serviceName, userId, e.getMessage());
            log.error(error);
            errorMessages.add(error);
            return false;
        } catch (Exception e) {
            String error = String.format("Unexpected error calling %s for userId=%d: %s",
                    serviceName, userId, e.getMessage());
            log.error(error, e);
            errorMessages.add(error);
            return false;
        }
    }

    // --- Accessors for the job listener ---

    public int getAnonymizedCount() {
        return anonymizedCount.get();
    }

    public CrossServiceCleanupStatus getOverallCleanupStatus() {
        return overallCleanupStatus.get();
    }

    public String getErrorDetailsAsString() {
        return errorMessages.isEmpty() ? null : String.join("\n", errorMessages);
    }

    /**
     * Reset counters for a new job execution.
     */
    public void resetCounters() {
        anonymizedCount.set(0);
        overallCleanupStatus.set(CrossServiceCleanupStatus.SUCCESS);
        errorMessages.clear();
    }
}
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles

---

## Task 12 Detail: RgpdCleanupJobListener

- **What**: A Spring Batch `JobExecutionListener` that creates an audit log entry after each RGPD cleanup job execution
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/batch/listener/RgpdCleanupJobListener.java`
- **Why**: RGPD compliance requires an audit trail. The listener captures job-level metrics (users processed, anonymized count, cross-service status) and persists them to `t_rgpd_cleanup_log`.
- **Content**:

```java
package com.familyhobbies.userservice.batch.listener;

import com.familyhobbies.userservice.batch.writer.RgpdCleanupWriter;
import com.familyhobbies.userservice.entity.RgpdCleanupLog;
import com.familyhobbies.userservice.repository.RgpdCleanupLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.time.Instant;

/**
 * Job execution listener that records each RGPD cleanup job run in the audit log.
 *
 * <p>Before the job starts: resets the writer's counters.
 * <p>After the job completes (success or failure): creates a {@link RgpdCleanupLog}
 * entry with the execution metrics.
 */
public class RgpdCleanupJobListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(RgpdCleanupJobListener.class);

    private final RgpdCleanupLogRepository rgpdCleanupLogRepository;
    private final RgpdCleanupWriter rgpdCleanupWriter;

    public RgpdCleanupJobListener(RgpdCleanupLogRepository rgpdCleanupLogRepository,
                                   RgpdCleanupWriter rgpdCleanupWriter) {
        this.rgpdCleanupLogRepository = rgpdCleanupLogRepository;
        this.rgpdCleanupWriter = rgpdCleanupWriter;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("RGPD cleanup job starting: executionId={}", jobExecution.getId());
        rgpdCleanupWriter.resetCounters();
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        int usersProcessed = 0;

        // Extract the read count from step execution
        if (!jobExecution.getStepExecutions().isEmpty()) {
            usersProcessed = jobExecution.getStepExecutions().stream()
                    .mapToInt(step -> step.getReadCount())
                    .sum();
        }

        int usersAnonymized = rgpdCleanupWriter.getAnonymizedCount();

        String errorDetails = rgpdCleanupWriter.getErrorDetailsAsString();
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            String jobError = "Job failed: " + jobExecution.getAllFailureExceptions().toString();
            errorDetails = errorDetails != null
                    ? errorDetails + "\n" + jobError
                    : jobError;
        }

        RgpdCleanupLog auditLog = new RgpdCleanupLog(
                Instant.now(),
                usersProcessed,
                usersAnonymized,
                rgpdCleanupWriter.getOverallCleanupStatus(),
                errorDetails
        );

        rgpdCleanupLogRepository.save(auditLog);

        log.info("RGPD cleanup job completed: executionId={}, status={}, " +
                        "usersProcessed={}, usersAnonymized={}, crossServiceStatus={}",
                jobExecution.getId(),
                jobExecution.getStatus(),
                usersProcessed,
                usersAnonymized,
                rgpdCleanupWriter.getOverallCleanupStatus());
    }
}
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles

---

## Task 13 Detail: RgpdDataCleanupJobConfig

- **What**: Full Spring Batch job configuration defining the `rgpdDataCleanupJob` with reader, processor, writer, and job listener
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/batch/config/RgpdDataCleanupJobConfig.java`
- **Why**: Central wiring of all RGPD batch components into a Spring Batch 5.x job
- **Content**:

```java
package com.familyhobbies.userservice.batch.config;

import com.familyhobbies.userservice.adapter.AssociationServiceClient;
import com.familyhobbies.userservice.adapter.PaymentServiceClient;
import com.familyhobbies.userservice.batch.listener.RgpdCleanupJobListener;
import com.familyhobbies.userservice.batch.processor.RgpdAnonymizationProcessor;
import com.familyhobbies.userservice.batch.writer.RgpdCleanupWriter;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.repository.RgpdCleanupLogRepository;
import com.familyhobbies.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.IteratorItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Spring Batch configuration for the RGPD data cleanup job.
 *
 * <p>Defines:
 * <ul>
 *     <li>{@code rgpdDataCleanupJob} -- top-level job with audit listener</li>
 *     <li>{@code rgpdCleanupStep} -- single chunk step (read/process/write)</li>
 *     <li>All batch component beans: reader, processor, writer, listener</li>
 * </ul>
 *
 * <p>The reader queries users where {@code status=DELETED AND updated_at < NOW()-30days AND anonymized=false}.
 * The processor anonymizes PII. The writer persists and triggers cross-service cleanup.
 * The listener records the audit log entry.
 *
 * <p>Chunk size is configurable via {@code batch.rgpd-cleanup.chunk-size} (default 5).
 * Retention period is configurable via {@code batch.rgpd-cleanup.retention-days} (default 30).
 */
@Configuration
public class RgpdDataCleanupJobConfig {

    private static final Logger log = LoggerFactory.getLogger(RgpdDataCleanupJobConfig.class);

    @Value("${batch.rgpd-cleanup.chunk-size:5}")
    private int chunkSize;

    @Value("${batch.rgpd-cleanup.retention-days:30}")
    private int retentionDays;

    @Bean
    public Clock rgpdCleanupClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ItemReader<User> rgpdEligibleUserReader(
            UserRepository userRepository,
            Clock rgpdCleanupClock) {

        // Use a factory to create the reader at job launch time (not at bean creation time)
        return () -> {
            // This is a stateful reader that loads all eligible users once
            // and returns them one by one. Thread-safe is not required
            // since Spring Batch steps are single-threaded by default.
            return null; // Placeholder -- actual implementation below
        };
    }

    /**
     * Custom reader that queries eligible users at job start and iterates through them.
     * Uses the same pattern as StalePaymentItemReader from S7-003.
     */
    @Bean
    public RgpdEligibleUserItemReader rgpdEligibleUserItemReader(
            UserRepository userRepository,
            Clock rgpdCleanupClock) {
        return new RgpdEligibleUserItemReader(userRepository, rgpdCleanupClock, retentionDays);
    }

    @Bean
    public RgpdAnonymizationProcessor rgpdAnonymizationProcessor() {
        return new RgpdAnonymizationProcessor();
    }

    @Bean
    public RgpdCleanupWriter rgpdCleanupWriter(
            UserRepository userRepository,
            AssociationServiceClient associationServiceClient,
            PaymentServiceClient paymentServiceClient) {
        return new RgpdCleanupWriter(userRepository, associationServiceClient, paymentServiceClient);
    }

    @Bean
    public RgpdCleanupJobListener rgpdCleanupJobListener(
            RgpdCleanupLogRepository rgpdCleanupLogRepository,
            RgpdCleanupWriter rgpdCleanupWriter) {
        return new RgpdCleanupJobListener(rgpdCleanupLogRepository, rgpdCleanupWriter);
    }

    @Bean
    public Step rgpdCleanupStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RgpdEligibleUserItemReader rgpdEligibleUserItemReader,
            RgpdAnonymizationProcessor rgpdAnonymizationProcessor,
            RgpdCleanupWriter rgpdCleanupWriter) {

        return new StepBuilder("rgpdCleanupStep", jobRepository)
                .<User, User>chunk(chunkSize, transactionManager)
                .reader(rgpdEligibleUserItemReader)
                .processor(rgpdAnonymizationProcessor)
                .writer(rgpdCleanupWriter)
                .build();
    }

    @Bean
    public Job rgpdDataCleanupJob(
            JobRepository jobRepository,
            Step rgpdCleanupStep,
            RgpdCleanupJobListener rgpdCleanupJobListener) {

        return new JobBuilder("rgpdDataCleanupJob", jobRepository)
                .listener(rgpdCleanupJobListener)
                .start(rgpdCleanupStep)
                .build();
    }

    // --- Inner class: Eligible User Reader ---

    /**
     * ItemReader that loads all users eligible for RGPD anonymization at first read,
     * then iterates through them one by one.
     */
    public static class RgpdEligibleUserItemReader implements ItemReader<User> {

        private static final Logger log = LoggerFactory.getLogger(RgpdEligibleUserItemReader.class);

        private final UserRepository userRepository;
        private final Clock clock;
        private final int retentionDays;
        private java.util.Iterator<User> userIterator;
        private boolean initialized = false;

        public RgpdEligibleUserItemReader(UserRepository userRepository,
                                           Clock clock,
                                           int retentionDays) {
            this.userRepository = userRepository;
            this.clock = clock;
            this.retentionDays = retentionDays;
        }

        @Override
        public User read() throws Exception {
            if (!initialized) {
                Instant cutoff = Instant.now(clock).minus(Duration.ofDays(retentionDays));
                log.info("RGPD cleanup: querying DELETED users older than {} days (cutoff={})",
                        retentionDays, cutoff);

                List<User> eligibleUsers = userRepository.findEligibleForAnonymization(cutoff);
                log.info("RGPD cleanup: found {} users eligible for anonymization",
                        eligibleUsers.size());

                this.userIterator = eligibleUsers.iterator();
                this.initialized = true;
            }

            if (userIterator != null && userIterator.hasNext()) {
                User user = userIterator.next();
                log.debug("Reading eligible user: id={}, deletedAt={}", user.getId(), user.getUpdatedAt());
                return user;
            }

            return null; // signals end of data
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles; Spring context loads with batch beans

---

## Task 14 Detail: BatchSchedulerConfig

- **What**: Configuration class enabling scheduling and triggering the RGPD cleanup job weekly on Sundays at 3 AM
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/batch/config/BatchSchedulerConfig.java`
- **Why**: Automates RGPD cleanup. The CRON expression `0 0 3 * * SUN` fires at 03:00 UTC every Sunday.
- **Content**:

```java
package com.familyhobbies.userservice.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

/**
 * Scheduler configuration for user-service batch jobs.
 *
 * <p>Triggers the RGPD data cleanup job weekly on Sundays at 3:00 AM UTC.
 * Each execution receives a unique {@code runTimestamp} parameter.
 */
@Configuration
@EnableScheduling
public class BatchSchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchSchedulerConfig.class);

    private final JobLauncher jobLauncher;
    private final Job rgpdDataCleanupJob;

    public BatchSchedulerConfig(JobLauncher jobLauncher,
                                 Job rgpdDataCleanupJob) {
        this.jobLauncher = jobLauncher;
        this.rgpdDataCleanupJob = rgpdDataCleanupJob;
    }

    /**
     * Run RGPD data cleanup every Sunday at 3:00 AM UTC.
     *
     * <p>CRON expression: {@code 0 0 3 * * SUN}
     * <ul>
     *     <li>second: 0</li>
     *     <li>minute: 0</li>
     *     <li>hour: 3</li>
     *     <li>day of month: * (every day)</li>
     *     <li>month: * (every month)</li>
     *     <li>day of week: SUN (Sunday only)</li>
     * </ul>
     */
    @Scheduled(cron = "${batch.rgpd-cleanup.cron:0 0 3 * * SUN}")
    public void runRgpdDataCleanup() {
        log.info("Scheduled RGPD data cleanup job starting");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("runTimestamp", Instant.now().toString())
                    .toJobParameters();

            jobLauncher.run(rgpdDataCleanupJob, params);
            log.info("Scheduled RGPD data cleanup job completed");
        } catch (Exception e) {
            log.error("Scheduled RGPD data cleanup job failed: {}", e.getMessage(), e);
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/user-service` -> compiles

---

## Task 15 Detail: AdminBatchController

- **What**: REST controller providing a manual trigger endpoint for the RGPD cleanup batch job, restricted to `ADMIN` role
- **Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/controller/AdminBatchController.java`
- **Why**: Allows administrators to trigger RGPD cleanup on demand for compliance audits or post-incident cleanup
- **Content**:

```java
package com.familyhobbies.userservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Admin endpoints for manually triggering batch jobs in the user-service.
 *
 * <p>All endpoints require the {@code ADMIN} role.
 */
@RestController
@RequestMapping("/admin/batch")
public class AdminBatchController {

    private static final Logger log = LoggerFactory.getLogger(AdminBatchController.class);

    private final JobLauncher jobLauncher;
    private final Job rgpdDataCleanupJob;

    public AdminBatchController(JobLauncher jobLauncher,
                                 Job rgpdDataCleanupJob) {
        this.jobLauncher = jobLauncher;
        this.rgpdDataCleanupJob = rgpdDataCleanupJob;
    }

    /**
     * Manually trigger the RGPD data cleanup batch job.
     *
     * <p>POST /admin/batch/rgpd-cleanup
     *
     * @return 200 OK with the job execution ID and status
     */
    @PostMapping("/rgpd-cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerRgpdCleanup() {
        log.info("Admin triggered RGPD data cleanup job");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("runTimestamp", Instant.now().toString())
                    .addString("trigger", "ADMIN_MANUAL")
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(rgpdDataCleanupJob, params);

            Map<String, Object> response = Map.of(
                    "jobExecutionId", execution.getId(),
                    "jobName", "rgpdDataCleanupJob",
                    "status", execution.getStatus().toString(),
                    "startTime", execution.getStartTime() != null
                            ? execution.getStartTime().toString() : "pending",
                    "trigger", "ADMIN_MANUAL"
            );

            log.info("RGPD data cleanup job triggered: executionId={}", execution.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to trigger RGPD data cleanup job: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to trigger RGPD data cleanup job",
                    "message", e.getMessage()
            ));
        }
    }
}
```

- **Verify**: `curl -X POST http://localhost:8081/admin/batch/rgpd-cleanup -H "Authorization: Bearer {admin-jwt}"` -> 200 with job execution ID

---

## Task 16 Detail: application.yml -- Batch Configuration

- **What**: Add Spring Batch and cross-service configuration properties to the existing `application.yml`
- **Where**: `backend/user-service/src/main/resources/application.yml`
- **Why**: Configures Spring Batch schema initialization, RGPD cleanup parameters, and cross-service URLs
- **Content** (add to existing `application.yml`):

```yaml
# --- Spring Batch Configuration (S7-004) ---
spring:
  batch:
    jdbc:
      initialize-schema: always
    job:
      enabled: false  # Do not auto-run jobs on application startup

# --- RGPD Cleanup Batch Properties ---
batch:
  rgpd-cleanup:
    chunk-size: 5                        # Number of users per chunk
    retention-days: 30                   # Days after deletion before anonymization
    cron: "0 0 3 * * SUN"               # Weekly Sunday at 3:00 AM UTC
  scheduling:
    enabled: true                        # Set to false in test profiles

# --- Cross-Service URLs ---
services:
  association-service:
    url: http://association-service:8082
  payment-service:
    url: http://payment-service:8083
```

- **Verify**: `mvn spring-boot:run -pl backend/user-service` -> application starts, no auto-run batch

---

## Failing Tests (TDD Contract)

Full test source code is in the companion file: **[S7-004 Tests Companion](./S7-004-rgpd-cleanup-batch-tests.md)**

The companion file contains 7 test classes with ~34 test cases:

| Test Class | Tests | Covers |
|------------|-------|--------|
| `RgpdCleanupLogEntityTest` | 3 | Entity mapping, @PrePersist, constructor |
| `RgpdAnonymizationProcessorTest` | 6 | All PII fields anonymized, SHA-256 hash, idempotency, null safety |
| `RgpdCleanupWriterTest` | 5 | Saves users, triggers cross-service, tracks cleanup status |
| `AssociationServiceClientTest` | 3 | Successful call, error handling, timeout |
| `PaymentServiceClientTest` | 3 | Successful call, error handling, timeout |
| `RgpdCleanupJobListenerTest` | 4 | Audit log created, metrics captured, error details recorded |
| `RgpdDataCleanupJobConfigTest` | 5 | Full integration: eligible users anonymized, recent deletions untouched, audit log created, cross-service triggered |
| `AdminBatchControllerTest` | 5 | Admin endpoint triggers job, returns execution ID, enforces ADMIN role |
