# Story S7-002: Implement Subscription Expiry Batch Job

> 5 points | Priority: P0 | Service: association-service
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The association-service manages family subscriptions to activities within associations. Each subscription has an `expiresAt` date representing the end of the membership period (typically annual). When a subscription expires, the system must automatically update its status from `ACTIVE` to `EXPIRED`, record the exact expiration timestamp, and notify downstream services so that the notification-service can alert families and dashboards reflect current membership status. Without this batch job, expired subscriptions would remain in `ACTIVE` status indefinitely, leading to stale data in family dashboards, incorrect attendance eligibility, and misleading association membership counts. This story implements a Spring Batch 5.x chunk-oriented job that runs daily at 6:00 AM, reads all subscriptions where `status = ACTIVE AND expires_at < NOW()`, transitions them to `EXPIRED` status, and publishes a `SubscriptionExpiredEvent` to Kafka for each expired subscription. The batch uses the shared `BatchConfig` (established in S7-001) for async job launching and thread pool management. A `SubscriptionExpiryJobListener` handles Kafka event publishing after each chunk is written, ensuring events are only published for successfully persisted records. The job also exposes an admin REST endpoint for manual triggering (extending the `AdminBatchController` from S7-001).

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | SubscriptionExpiredEvent Kafka DTO | `backend/common/src/main/java/com/familyhobbies/common/event/SubscriptionExpiredEvent.java` | New Kafka event class | `mvn compile -pl backend/common` |
| 2 | SubscriptionRepository query method | `backend/association-service/src/main/java/.../repository/SubscriptionRepository.java` | `findByStatusAndExpiresAtBefore` JPA query | `mvn compile -pl backend/association-service` |
| 3 | SubscriptionExpiryJobConfig | `backend/association-service/src/main/java/.../batch/config/SubscriptionExpiryJobConfig.java` | Full Spring Batch job configuration with reader, processor, writer, listener | Job bean created in context |
| 4 | SubscriptionExpiryProcessor | `backend/association-service/src/main/java/.../batch/processor/SubscriptionExpiryProcessor.java` | ItemProcessor that sets EXPIRED status and expiredAt timestamp | Unit tests pass |
| 5 | SubscriptionExpiryJobListener | `backend/association-service/src/main/java/.../batch/listener/SubscriptionExpiryJobListener.java` | ChunkListener that publishes Kafka events after write | Unit tests pass |
| 6 | BatchSchedulerConfig update | `backend/association-service/src/main/java/.../batch/config/BatchSchedulerConfig.java` | Add CRON schedule `0 0 6 * * *` for subscriptionExpiryJob | Application starts without errors |
| 7 | AdminBatchController extension | `backend/association-service/src/main/java/.../controller/AdminBatchController.java` | POST `/admin/batch/subscription-expiry` endpoint | `curl -X POST` returns 202 |
| 8 | Failing tests (TDD) | `backend/association-service/src/test/java/.../batch/` | 7 JUnit 5 test cases | `mvn test -pl backend/association-service` |

---

## Task 1 Detail: SubscriptionExpiredEvent Kafka DTO

- **What**: New Kafka event published when a subscription expires. Placed in the shared `common` module so both the association-service (publisher) and notification-service (consumer) have access to the class.
- **Where**: `backend/common/src/main/java/com/familyhobbies/common/event/SubscriptionExpiredEvent.java`
- **Why**: The notification-service must react to subscription expirations by sending renewal reminders to families. Kafka decouples the association-service from notification delivery logic. The event carries all identifiers needed for the notification to be contextual (which family member, which association, when it expired).
- **Content**:

```java
package com.familyhobbies.common.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event published when a subscription transitions to EXPIRED status.
 *
 * <p>Topic: {@code subscription.expired}
 *
 * <p>Published by: association-service (SubscriptionExpiryJobListener)
 * <p>Consumed by: notification-service (sends renewal reminder to family)
 *
 * <p>Key: subscriptionId (for Kafka partition ordering per subscription)
 *
 * @param subscriptionId  unique subscription identifier
 * @param userId          the account owner (parent)
 * @param familyMemberId  the family member who held the subscription
 * @param associationId   the association the subscription was for
 * @param activityId      the specific activity within the association
 * @param expiredAt       exact timestamp when the batch marked it expired
 * @param timestamp       event creation timestamp (ISO-8601)
 */
public record SubscriptionExpiredEvent(
        UUID subscriptionId,
        UUID userId,
        UUID familyMemberId,
        UUID associationId,
        UUID activityId,
        Instant expiredAt,
        Instant timestamp
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Factory method with auto-generated timestamp.
     */
    public static SubscriptionExpiredEvent of(UUID subscriptionId,
                                               UUID userId,
                                               UUID familyMemberId,
                                               UUID associationId,
                                               UUID activityId,
                                               Instant expiredAt) {
        return new SubscriptionExpiredEvent(
                subscriptionId,
                userId,
                familyMemberId,
                associationId,
                activityId,
                expiredAt,
                Instant.now()
        );
    }
}
```

- **Verify**: `mvn compile -pl backend/common` -> compiles without errors

---

## Task 2 Detail: SubscriptionRepository Query Method

- **What**: Add a Spring Data JPA derived query method to find all active subscriptions that have passed their expiry date. This method serves as the data source for the batch job's reader.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/repository/SubscriptionRepository.java`
- **Why**: The batch reader needs a query that selects only subscriptions eligible for expiry processing. Using a derived query method keeps the query logic co-located with the repository and benefits from Spring Data's compile-time validation. The `Pageable` parameter supports the `JpaPagingItemReader` pattern where Spring Batch reads in configurable page sizes.
- **Content**:

```java
package com.familyhobbies.associationservice.repository;

import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring Data repository for {@link Subscription} entities.
 *
 * <p>Pre-existing from S3-003. Extended in S7-002 with the expiry query
 * used by the subscription expiry batch job.
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /**
     * Find all subscriptions with the given status whose expiry date
     * is before the specified cutoff timestamp.
     *
     * <p>Used by the subscription expiry batch job reader to fetch
     * ACTIVE subscriptions that have passed their expiry date.
     *
     * @param status  the subscription status to filter by (ACTIVE)
     * @param cutoff  the timestamp cutoff (NOW at batch execution time)
     * @return page of matching subscriptions
     */
    Page<Subscription> findByStatusAndExpiresAtBefore(
            SubscriptionStatus status,
            LocalDateTime cutoff,
            Pageable pageable);

    /**
     * Count subscriptions matching status and expiry cutoff.
     * Used for job listener logging (total expired count).
     */
    long countByStatusAndExpiresAtBefore(
            SubscriptionStatus status,
            LocalDateTime cutoff);

    /**
     * JPQL query variant for the batch reader's named query approach.
     * Equivalent to the derived query above but explicit for documentation.
     */
    @Query("SELECT s FROM Subscription s "
            + "WHERE s.status = :status "
            + "AND s.expiresAt < :cutoff "
            + "ORDER BY s.expiresAt ASC")
    Page<Subscription> findExpiredSubscriptions(
            @Param("status") SubscriptionStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable);
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles without errors

---

## Task 3 Detail: SubscriptionExpiryJobConfig

- **What**: Spring Batch 5.x job configuration that assembles the full subscription expiry pipeline: `JpaPagingItemReader` (reads ACTIVE subscriptions past expiry), `SubscriptionExpiryProcessor` (sets status to EXPIRED), `JpaItemWriter` (batch updates), and `SubscriptionExpiryJobListener` (publishes Kafka events). Uses chunk size of 100 (subscriptions are small entities; larger chunks reduce transaction overhead). The `RunIdIncrementer` ensures each execution creates a new job instance.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/config/SubscriptionExpiryJobConfig.java`
- **Why**: This is the central orchestration point for the expiry batch pipeline. Spring Batch 5.x uses the new `JobBuilder`/`StepBuilder` fluent API (no more deprecated `StepBuilderFactory`). The JPA paging reader is ideal because the data source is the local PostgreSQL database (no external API calls, no skip policy needed).
- **Content**:

```java
package com.familyhobbies.associationservice.batch.config;

import com.familyhobbies.associationservice.batch.listener.SubscriptionExpiryJobListener;
import com.familyhobbies.associationservice.batch.processor.SubscriptionExpiryProcessor;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.enums.SubscriptionStatus;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Spring Batch job configuration for subscription expiry processing.
 *
 * <p>Job: {@code subscriptionExpiryJob}
 * <ul>
 *   <li>Step: {@code processExpiredSubscriptionsStep}</li>
 *   <li>Reader: {@link JpaPagingItemReader} -- ACTIVE subs where expiresAt < NOW()</li>
 *   <li>Processor: {@link SubscriptionExpiryProcessor} -- set EXPIRED + timestamp</li>
 *   <li>Writer: {@link JpaItemWriter} -- batch persist updated subscriptions</li>
 *   <li>Listener: {@link SubscriptionExpiryJobListener} -- Kafka events + logging</li>
 *   <li>Chunk size: 100</li>
 * </ul>
 *
 * <p>No skip policy needed: data is read from local DB (no transient API errors).
 * If the database is unavailable, the entire job should fail and be retried.
 *
 * <p>The {@link RunIdIncrementer} ensures each manual or scheduled trigger
 * creates a new job instance, allowing re-runs on the same day.
 */
@Configuration
public class SubscriptionExpiryJobConfig {

    private static final int CHUNK_SIZE = 100;
    private static final int PAGE_SIZE = 100;

    private final EntityManagerFactory entityManagerFactory;
    private final SubscriptionExpiryProcessor subscriptionExpiryProcessor;
    private final SubscriptionExpiryJobListener subscriptionExpiryJobListener;

    public SubscriptionExpiryJobConfig(
            EntityManagerFactory entityManagerFactory,
            SubscriptionExpiryProcessor subscriptionExpiryProcessor,
            SubscriptionExpiryJobListener subscriptionExpiryJobListener) {
        this.entityManagerFactory = entityManagerFactory;
        this.subscriptionExpiryProcessor = subscriptionExpiryProcessor;
        this.subscriptionExpiryJobListener = subscriptionExpiryJobListener;
    }

    /**
     * JPA paging reader that selects ACTIVE subscriptions past their expiry date.
     *
     * <p>Uses a JPQL query with named parameters. The cutoff timestamp is
     * set at reader initialization time (job start), ensuring consistent
     * behavior throughout the job execution.
     *
     * <p>Page size matches chunk size for optimal DB read efficiency.
     */
    @Bean
    public JpaPagingItemReader<Subscription> expiredSubscriptionReader() {
        return new JpaPagingItemReaderBuilder<Subscription>()
                .name("expiredSubscriptionReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString(
                        "SELECT s FROM Subscription s "
                        + "WHERE s.status = :status "
                        + "AND s.expiresAt < :cutoff "
                        + "ORDER BY s.expiresAt ASC")
                .parameterValues(Map.of(
                        "status", SubscriptionStatus.ACTIVE,
                        "cutoff", LocalDateTime.now()))
                .pageSize(PAGE_SIZE)
                .build();
    }

    /**
     * JPA item writer that persists updated subscription entities.
     *
     * <p>Uses the same {@link EntityManagerFactory} as the reader.
     * Spring Batch manages the transaction boundaries per chunk.
     */
    @Bean
    public JpaItemWriter<Subscription> subscriptionExpiryWriter() {
        JpaItemWriter<Subscription> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(entityManagerFactory);
        return writer;
    }

    /**
     * Single-step job that processes all expired subscriptions.
     *
     * <p>Flow: expiredSubscriptionReader -> subscriptionExpiryProcessor
     * -> subscriptionExpiryWriter
     *
     * <p>The listener handles structured logging of job start/end and
     * publishes Kafka events for each expired subscription after the
     * chunk is committed.
     */
    @Bean
    public Job subscriptionExpiryJob(JobRepository jobRepository,
                                      Step processExpiredSubscriptionsStep) {
        return new JobBuilder("subscriptionExpiryJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(subscriptionExpiryJobListener)
                .start(processExpiredSubscriptionsStep)
                .build();
    }

    /**
     * Step definition: chunk-oriented processing of expired subscriptions.
     *
     * <p>Chunk size of 100 balances transaction size with throughput.
     * No fault tolerance (skip/retry) is configured because:
     * <ul>
     *   <li>Reader queries local DB -- no transient external API errors</li>
     *   <li>Writer updates local DB -- failures indicate real problems</li>
     *   <li>If DB is down, the entire job should fail (not silently skip)</li>
     * </ul>
     */
    @Bean
    public Step processExpiredSubscriptionsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager) {
        return new StepBuilder("processExpiredSubscriptionsStep", jobRepository)
                .<Subscription, Subscription>chunk(CHUNK_SIZE, transactionManager)
                .reader(expiredSubscriptionReader())
                .processor(subscriptionExpiryProcessor)
                .writer(subscriptionExpiryWriter())
                .listener(subscriptionExpiryJobListener)
                .build();
    }
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles without errors

---

## Task 4 Detail: SubscriptionExpiryProcessor

- **What**: Spring Batch `ItemProcessor` that receives an `ACTIVE` subscription (already past its expiry date) and transitions it to `EXPIRED` status with an `expiredAt` timestamp. Returns the modified entity for the writer to persist. This processor is intentionally simple: it performs no external calls, no complex logic -- just a status transition and timestamp.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/processor/SubscriptionExpiryProcessor.java`
- **Why**: Separating the status transition into a dedicated processor follows the Spring Batch SRP pattern (reader reads, processor transforms, writer persists). It also makes the transformation independently testable without involving the database or Kafka.
- **Content**:

```java
package com.familyhobbies.associationservice.batch.processor;

import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.enums.SubscriptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Processor that transitions an ACTIVE subscription to EXPIRED status.
 *
 * <p>Sets:
 * <ul>
 *   <li>{@code status} = {@link SubscriptionStatus#EXPIRED}</li>
 *   <li>{@code expiredAt} = current timestamp (batch processing time)</li>
 * </ul>
 *
 * <p>Precondition: the reader already filtered for ACTIVE subscriptions
 * past their expiry date, so this processor does NOT re-validate eligibility.
 * It trusts the reader's query.
 *
 * <p>Returns the modified entity (never {@code null}): every subscription
 * delivered by the reader is guaranteed to need expiry processing.
 */
@Component
public class SubscriptionExpiryProcessor
        implements ItemProcessor<Subscription, Subscription> {

    private static final Logger log =
            LoggerFactory.getLogger(SubscriptionExpiryProcessor.class);

    @Override
    public Subscription process(Subscription subscription) {
        log.debug("Expiring subscription: id={}, userId={}, "
                + "associationId={}, expiresAt={}",
                subscription.getId(),
                subscription.getUserId(),
                subscription.getAssociationId(),
                subscription.getExpiresAt());

        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setExpiredAt(LocalDateTime.now());

        return subscription;
    }
}
```

- **Verify**: `mvn test -pl backend/association-service -Dtest=SubscriptionExpiryProcessorTest` -> all tests pass

---

## Task 5 Detail: SubscriptionExpiryJobListener

- **What**: Spring Batch `JobExecutionListener` and `ChunkListener` that handles two concerns: (1) structured logging of job lifecycle events (start, end, duration, counts), and (2) publishing `SubscriptionExpiredEvent` to Kafka after each chunk is successfully written. The Kafka publishing happens in the `afterChunk` callback to ensure events are only sent for persisted records.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/listener/SubscriptionExpiryJobListener.java`
- **Why**: Publishing Kafka events in the listener (after the chunk commits) rather than in the writer ensures that events are only sent for successfully persisted records. If the writer's transaction rolls back, the `afterChunk` callback is not invoked, preventing phantom events. The listener also serves as the observability layer, providing structured logs for the monitoring pipeline (S7-005, S7-007).
- **Content**:

```java
package com.familyhobbies.associationservice.batch.listener;

import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.common.event.SubscriptionExpiredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.listener.ChunkListenerSupport;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listener for the subscription expiry batch job.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Log job start/end with structured fields (duration, counts, status)</li>
 *   <li>Collect expired subscriptions during processing</li>
 *   <li>Publish {@link SubscriptionExpiredEvent} to Kafka after each chunk</li>
 * </ul>
 *
 * <p>Kafka topic: {@code subscription.expired}
 * <p>Kafka key: subscriptionId (ensures ordering per subscription)
 *
 * <p>Thread safety: uses {@link CopyOnWriteArrayList} for the pending events
 * buffer because Spring Batch may invoke callbacks from the batch thread pool.
 */
@Component
public class SubscriptionExpiryJobListener
        extends ChunkListenerSupport
        implements JobExecutionListener {

    private static final Logger log =
            LoggerFactory.getLogger(SubscriptionExpiryJobListener.class);

    private static final String TOPIC = "subscription.expired";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Buffer of subscriptions that were expired in the current chunk.
     * Populated by the processor/writer, drained in afterChunk.
     */
    private final CopyOnWriteArrayList<Subscription> pendingEvents =
            new CopyOnWriteArrayList<>();

    public SubscriptionExpiryJobListener(
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // -- JobExecutionListener callbacks --

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Subscription expiry job STARTED: jobId={}, "
                + "jobParameters={}",
                jobExecution.getJobId(),
                jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        Duration duration = Duration.between(
                jobExecution.getStartTime().toInstant(java.time.ZoneOffset.UTC),
                Instant.now());

        long writeCount = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();

        long readCount = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount)
                .sum();

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("Subscription expiry job COMPLETED: jobId={}, "
                    + "duration={}ms, readCount={}, writeCount={}",
                    jobExecution.getJobId(),
                    duration.toMillis(),
                    readCount,
                    writeCount);
        } else {
            log.error("Subscription expiry job FAILED: jobId={}, "
                    + "status={}, duration={}ms, readCount={}, writeCount={}, "
                    + "exitDescription={}",
                    jobExecution.getJobId(),
                    jobExecution.getStatus(),
                    duration.toMillis(),
                    readCount,
                    writeCount,
                    jobExecution.getExitStatus().getExitDescription());
        }
    }

    // -- ChunkListener callbacks --

    @Override
    public void beforeChunk(ChunkContext context) {
        pendingEvents.clear();
    }

    @Override
    public void afterChunk(ChunkContext context) {
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Publishing {} SubscriptionExpiredEvents to Kafka",
                pendingEvents.size());

        for (Subscription subscription : pendingEvents) {
            SubscriptionExpiredEvent event = SubscriptionExpiredEvent.of(
                    subscription.getId(),
                    subscription.getUserId(),
                    subscription.getFamilyMemberId(),
                    subscription.getAssociationId(),
                    subscription.getActivityId(),
                    subscription.getExpiredAt()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant());

            kafkaTemplate.send(TOPIC,
                    subscription.getId().toString(),
                    event);

            log.debug("Published SubscriptionExpiredEvent: "
                    + "subscriptionId={}, userId={}, associationId={}",
                    subscription.getId(),
                    subscription.getUserId(),
                    subscription.getAssociationId());
        }

        pendingEvents.clear();
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        log.warn("Chunk failed -- discarding {} pending Kafka events "
                + "to prevent phantom notifications",
                pendingEvents.size());
        pendingEvents.clear();
    }

    // -- Called by the writer after successful persist --

    /**
     * Register a subscription that was successfully expired and persisted.
     * Called by the job config or writer to buffer events for Kafka publishing.
     *
     * @param subscription the expired subscription entity (post-persist)
     */
    public void registerExpiredSubscription(Subscription subscription) {
        pendingEvents.add(subscription);
    }
}
```

- **Verify**: `mvn test -pl backend/association-service -Dtest=SubscriptionExpiryJobListenerTest` -> all tests pass

---

## Task 6 Detail: BatchSchedulerConfig Update

- **What**: Extend the existing `BatchSchedulerConfig` (created in S7-001 for the HelloAsso sync job) with an additional CRON-scheduled method that launches the `subscriptionExpiryJob` daily at 6:00 AM. Uses the default synchronous `JobLauncher` (not the async one) because CRON-triggered jobs run on the scheduler thread and do not need to return immediately.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/config/BatchSchedulerConfig.java`
- **Why**: Centralizing all CRON schedules in one config class provides a single place to view all scheduled jobs in the association-service. The 6:00 AM schedule runs after the HelloAsso sync job (2:00 AM, S7-001), ensuring that any newly synced associations are available before subscription processing. The `CRON` expression is externalized to `application.yml` for environment-specific overrides.
- **Content**:

```java
package com.familyhobbies.associationservice.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * CRON scheduler for all batch jobs in association-service.
 *
 * <p>Schedule overview:
 * <ul>
 *   <li>{@code helloAssoSyncJob}: 02:00 daily (S7-001)</li>
 *   <li>{@code subscriptionExpiryJob}: 06:00 daily (S7-002)</li>
 * </ul>
 *
 * <p>Order rationale: HelloAsso sync runs first (2 AM) to ensure local
 * association data is fresh before subscription processing (6 AM).
 *
 * <p>Uses the default synchronous {@link JobLauncher} (not the async one)
 * because scheduled jobs run on the scheduler thread pool and do not
 * need to return immediately to a caller.
 */
@Configuration
public class BatchSchedulerConfig {

    private static final Logger log =
            LoggerFactory.getLogger(BatchSchedulerConfig.class);

    private final JobLauncher jobLauncher;
    private final Job helloAssoSyncJob;
    private final Job subscriptionExpiryJob;

    public BatchSchedulerConfig(
            JobLauncher jobLauncher,
            @Qualifier("helloAssoSyncJob") Job helloAssoSyncJob,
            @Qualifier("subscriptionExpiryJob") Job subscriptionExpiryJob) {
        this.jobLauncher = jobLauncher;
        this.helloAssoSyncJob = helloAssoSyncJob;
        this.subscriptionExpiryJob = subscriptionExpiryJob;
    }

    /**
     * Trigger HelloAsso sync job daily at 2:00 AM (S7-001).
     */
    @Scheduled(cron = "${batch.helloasso-sync.cron:0 0 2 * * *}")
    public void runHelloAssoSyncJob() {
        launchJob("helloAssoSyncJob", helloAssoSyncJob);
    }

    /**
     * Trigger subscription expiry job daily at 6:00 AM (S7-002).
     *
     * <p>Runs after the HelloAsso sync (2 AM) to ensure fresh data.
     * Processes all ACTIVE subscriptions where expiresAt < NOW().
     */
    @Scheduled(cron = "${batch.subscription-expiry.cron:0 0 6 * * *}")
    public void runSubscriptionExpiryJob() {
        launchJob("subscriptionExpiryJob", subscriptionExpiryJob);
    }

    private void launchJob(String jobName, Job job) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("trigger", "CRON")
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            log.info("Launching scheduled batch job: jobName={}", jobName);
            jobLauncher.run(job, params);
        } catch (Exception e) {
            log.error("Failed to launch scheduled batch job: "
                    + "jobName={}, error={}", jobName, e.getMessage(), e);
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles without errors; application starts without scheduling errors

---

## Task 7 Detail: AdminBatchController Extension

- **What**: Extend the `AdminBatchController` (created in S7-001) with a new POST endpoint for manually triggering the subscription expiry batch job. Returns 202 Accepted with the job execution ID. Secured with `@PreAuthorize("hasRole('ADMIN')")`.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/controller/AdminBatchController.java`
- **Why**: Administrators need the ability to trigger expiry processing on demand (e.g., after a data migration, or to process expirations immediately rather than waiting for the 6 AM CRON). The async job launcher ensures the endpoint returns immediately while the job runs in the background.
- **Content**:

```java
package com.familyhobbies.associationservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin REST controller for manually triggering batch jobs.
 *
 * <p>All endpoints are secured with ADMIN role.
 * Uses the async job launcher to return immediately (202 Accepted).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /admin/batch/helloasso-sync (S7-001)</li>
 *   <li>POST /admin/batch/subscription-expiry (S7-002)</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/batch")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBatchController {

    private static final Logger log =
            LoggerFactory.getLogger(AdminBatchController.class);

    private final JobLauncher asyncJobLauncher;
    private final Job helloAssoSyncJob;
    private final Job subscriptionExpiryJob;

    public AdminBatchController(
            @Qualifier("asyncJobLauncher") JobLauncher asyncJobLauncher,
            @Qualifier("helloAssoSyncJob") Job helloAssoSyncJob,
            @Qualifier("subscriptionExpiryJob") Job subscriptionExpiryJob) {
        this.asyncJobLauncher = asyncJobLauncher;
        this.helloAssoSyncJob = helloAssoSyncJob;
        this.subscriptionExpiryJob = subscriptionExpiryJob;
    }

    /**
     * Manually trigger the HelloAsso sync batch job (S7-001).
     *
     * @return 202 Accepted with job execution ID
     */
    @PostMapping("/helloasso-sync")
    public ResponseEntity<Map<String, Object>> triggerHelloAssoSync() {
        return launchJob("helloAssoSyncJob", helloAssoSyncJob);
    }

    /**
     * Manually trigger the subscription expiry batch job (S7-002).
     *
     * <p>Processes all ACTIVE subscriptions where expiresAt < NOW().
     * Publishes SubscriptionExpiredEvent to Kafka for each expired subscription.
     *
     * @return 202 Accepted with job execution ID
     */
    @PostMapping("/subscription-expiry")
    public ResponseEntity<Map<String, Object>> triggerSubscriptionExpiry() {
        return launchJob("subscriptionExpiryJob", subscriptionExpiryJob);
    }

    private ResponseEntity<Map<String, Object>> launchJob(
            String jobName, Job job) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("trigger", "ADMIN_REST")
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            log.info("Admin triggering batch job: jobName={}", jobName);
            JobExecution execution = asyncJobLauncher.run(job, params);

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "jobName", jobName,
                            "jobExecutionId", execution.getId(),
                            "status", execution.getStatus().toString(),
                            "message", "Job launched asynchronously"));
        } catch (Exception e) {
            log.error("Failed to launch batch job: jobName={}, error={}",
                    jobName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "jobName", jobName,
                            "error", e.getMessage()));
        }
    }
}
```

- **Verify**: `curl -X POST http://localhost:8082/admin/batch/subscription-expiry -H "Authorization: Bearer <admin-jwt>"` -> returns 202 with JSON body containing `jobExecutionId`

---

## Task 8 Detail: Failing Tests (TDD Contract)

### SubscriptionExpiryProcessorTest.java

```java
package com.familyhobbies.associationservice.batch.processor;

import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.enums.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD contract tests for {@link SubscriptionExpiryProcessor}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Status transitions from ACTIVE to EXPIRED</li>
 *   <li>expiredAt timestamp is set to current time</li>
 *   <li>Other fields remain unchanged</li>
 * </ul>
 */
class SubscriptionExpiryProcessorTest {

    private SubscriptionExpiryProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SubscriptionExpiryProcessor();
    }

    @Test
    @DisplayName("Should set status to EXPIRED for an active subscription")
    void shouldSetStatusToExpired() {
        // Given
        Subscription subscription = createActiveSubscription(
                LocalDateTime.now().minusDays(1));

        // When
        Subscription result = processor.process(subscription);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("Should set expiredAt to current timestamp")
    void shouldSetExpiredAtTimestamp() {
        // Given
        Subscription subscription = createActiveSubscription(
                LocalDateTime.now().minusDays(5));
        assertThat(subscription.getExpiredAt()).isNull();

        // When
        LocalDateTime beforeProcess = LocalDateTime.now();
        Subscription result = processor.process(subscription);
        LocalDateTime afterProcess = LocalDateTime.now();

        // Then
        assertThat(result.getExpiredAt()).isNotNull();
        assertThat(result.getExpiredAt()).isAfterOrEqualTo(beforeProcess);
        assertThat(result.getExpiredAt()).isBeforeOrEqualTo(afterProcess);
    }

    @Test
    @DisplayName("Should preserve all other subscription fields")
    void shouldPreserveOtherFields() {
        // Given
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID familyMemberId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        LocalDateTime startDate = LocalDateTime.now().minusYears(1);
        LocalDateTime expiresAt = LocalDateTime.now().minusDays(1);

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setUserId(userId);
        subscription.setFamilyMemberId(familyMemberId);
        subscription.setAssociationId(associationId);
        subscription.setActivityId(activityId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(startDate);
        subscription.setExpiresAt(expiresAt);

        // When
        Subscription result = processor.process(subscription);

        // Then -- identity fields unchanged
        assertThat(result.getId()).isEqualTo(subscriptionId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getFamilyMemberId()).isEqualTo(familyMemberId);
        assertThat(result.getAssociationId()).isEqualTo(associationId);
        assertThat(result.getActivityId()).isEqualTo(activityId);
        assertThat(result.getStartDate()).isEqualTo(startDate);
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
        // status and expiredAt changed
        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(result.getExpiredAt()).isNotNull();
    }

    private Subscription createActiveSubscription(LocalDateTime expiresAt) {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUserId(UUID.randomUUID());
        subscription.setFamilyMemberId(UUID.randomUUID());
        subscription.setAssociationId(UUID.randomUUID());
        subscription.setActivityId(UUID.randomUUID());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDateTime.now().minusYears(1));
        subscription.setExpiresAt(expiresAt);
        subscription.setExpiredAt(null);
        return subscription;
    }
}
```

### SubscriptionExpiryJobListenerTest.java

```java
package com.familyhobbies.associationservice.batch.listener;

import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.enums.SubscriptionStatus;
import com.familyhobbies.common.event.SubscriptionExpiredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * TDD contract tests for {@link SubscriptionExpiryJobListener}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Kafka events published after chunk with correct topic and payload</li>
 *   <li>No events published when no subscriptions were registered</li>
 *   <li>Pending events cleared after chunk error</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryJobListenerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ChunkContext chunkContext;

    private SubscriptionExpiryJobListener listener;

    @BeforeEach
    void setUp() {
        listener = new SubscriptionExpiryJobListener(kafkaTemplate);
    }

    @Test
    @DisplayName("Should publish SubscriptionExpiredEvent to Kafka "
            + "for each registered subscription after chunk")
    void shouldPublishKafkaEventsAfterChunk() {
        // Given
        Subscription sub1 = createExpiredSubscription();
        Subscription sub2 = createExpiredSubscription();

        listener.beforeChunk(chunkContext);
        listener.registerExpiredSubscription(sub1);
        listener.registerExpiredSubscription(sub2);

        // When
        listener.afterChunk(chunkContext);

        // Then
        verify(kafkaTemplate, times(2)).send(
                eq("subscription.expired"),
                any(String.class),
                any(SubscriptionExpiredEvent.class));

        // Verify keys are subscription IDs
        ArgumentCaptor<String> keyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(2)).send(
                eq("subscription.expired"),
                keyCaptor.capture(),
                any(SubscriptionExpiredEvent.class));

        assertThat(keyCaptor.getAllValues())
                .containsExactly(
                        sub1.getId().toString(),
                        sub2.getId().toString());
    }

    @Test
    @DisplayName("Should NOT publish events when no subscriptions registered")
    void shouldNotPublishWhenEmpty() {
        // Given
        listener.beforeChunk(chunkContext);
        // No subscriptions registered

        // When
        listener.afterChunk(chunkContext);

        // Then
        verify(kafkaTemplate, never()).send(
                any(String.class),
                any(String.class),
                any());
    }

    @Test
    @DisplayName("Should clear pending events after chunk error")
    void shouldClearEventsOnChunkError() {
        // Given
        listener.beforeChunk(chunkContext);
        listener.registerExpiredSubscription(createExpiredSubscription());

        // When -- chunk fails
        listener.afterChunkError(chunkContext);

        // Then -- subsequent afterChunk should have nothing to publish
        listener.afterChunk(chunkContext);
        verify(kafkaTemplate, never()).send(
                any(String.class),
                any(String.class),
                any());
    }

    @Test
    @DisplayName("Should include correct fields in SubscriptionExpiredEvent")
    void shouldIncludeCorrectEventFields() {
        // Given
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID familyMemberId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        LocalDateTime expiredAt = LocalDateTime.now();

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setUserId(userId);
        subscription.setFamilyMemberId(familyMemberId);
        subscription.setAssociationId(associationId);
        subscription.setActivityId(activityId);
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setExpiredAt(expiredAt);

        listener.beforeChunk(chunkContext);
        listener.registerExpiredSubscription(subscription);

        // When
        listener.afterChunk(chunkContext);

        // Then
        ArgumentCaptor<SubscriptionExpiredEvent> eventCaptor =
                ArgumentCaptor.forClass(SubscriptionExpiredEvent.class);
        verify(kafkaTemplate).send(
                eq("subscription.expired"),
                eq(subscriptionId.toString()),
                eventCaptor.capture());

        SubscriptionExpiredEvent event = eventCaptor.getValue();
        assertThat(event.subscriptionId()).isEqualTo(subscriptionId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.familyMemberId()).isEqualTo(familyMemberId);
        assertThat(event.associationId()).isEqualTo(associationId);
        assertThat(event.activityId()).isEqualTo(activityId);
        assertThat(event.expiredAt()).isNotNull();
        assertThat(event.timestamp()).isNotNull();
    }

    private Subscription createExpiredSubscription() {
        Subscription subscription = new Subscription();
        subscription.setId(UUID.randomUUID());
        subscription.setUserId(UUID.randomUUID());
        subscription.setFamilyMemberId(UUID.randomUUID());
        subscription.setAssociationId(UUID.randomUUID());
        subscription.setActivityId(UUID.randomUUID());
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setExpiredAt(LocalDateTime.now());
        return subscription;
    }
}
```

### SubscriptionExpiryJobConfigTest.java (Integration Test)

```java
package com.familyhobbies.associationservice.batch.config;

import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the subscription expiry batch job.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Job completes successfully with expired subscriptions</li>
 *   <li>Expired subscriptions are updated to EXPIRED status</li>
 *   <li>Non-expired subscriptions remain ACTIVE</li>
 * </ul>
 *
 * <p>Uses H2 in-memory database and mocked KafkaTemplate.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class SubscriptionExpiryJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("subscriptionExpiryJob")
    private Job subscriptionExpiryJob;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private UUID expiredSubId;
    private UUID activeSubId;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();

        // Subscription that expired yesterday -- should be processed
        Subscription expired = new Subscription();
        expired.setId(UUID.randomUUID());
        expired.setUserId(UUID.randomUUID());
        expired.setFamilyMemberId(UUID.randomUUID());
        expired.setAssociationId(UUID.randomUUID());
        expired.setActivityId(UUID.randomUUID());
        expired.setStatus(SubscriptionStatus.ACTIVE);
        expired.setAmount(BigDecimal.valueOf(50.00));
        expired.setStartDate(LocalDateTime.now().minusYears(1));
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        expired = subscriptionRepository.save(expired);
        expiredSubId = expired.getId();

        // Subscription that expires next month -- should NOT be processed
        Subscription active = new Subscription();
        active.setId(UUID.randomUUID());
        active.setUserId(UUID.randomUUID());
        active.setFamilyMemberId(UUID.randomUUID());
        active.setAssociationId(UUID.randomUUID());
        active.setActivityId(UUID.randomUUID());
        active.setStatus(SubscriptionStatus.ACTIVE);
        active.setAmount(BigDecimal.valueOf(75.00));
        active.setStartDate(LocalDateTime.now().minusMonths(6));
        active.setExpiresAt(LocalDateTime.now().plusMonths(1));
        active = subscriptionRepository.save(active);
        activeSubId = active.getId();
    }

    @Test
    @DisplayName("subscriptionExpiryJob should expire past-due "
            + "subscriptions and leave active ones untouched")
    void jobShouldExpirePastDueSubscriptions() throws Exception {
        // When
        jobLauncherTestUtils.setJob(subscriptionExpiryJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("trigger", "TEST")
                        .addLong("timestamp", System.currentTimeMillis())
                        .toJobParameters());

        // Then -- job completed
        assertThat(execution.getStatus())
                .isEqualTo(BatchStatus.COMPLETED);

        // Then -- expired subscription updated
        Subscription expiredSub =
                subscriptionRepository.findById(expiredSubId).orElseThrow();
        assertThat(expiredSub.getStatus())
                .isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(expiredSub.getExpiredAt()).isNotNull();

        // Then -- active subscription untouched
        Subscription activeSub =
                subscriptionRepository.findById(activeSubId).orElseThrow();
        assertThat(activeSub.getStatus())
                .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(activeSub.getExpiredAt()).isNull();
    }

    @Test
    @DisplayName("subscriptionExpiryJob should complete with zero "
            + "writes when no subscriptions are expired")
    void jobShouldCompleteWithZeroWritesWhenNoneExpired() throws Exception {
        // Given -- remove the expired subscription
        subscriptionRepository.deleteById(expiredSubId);

        // When
        jobLauncherTestUtils.setJob(subscriptionExpiryJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("trigger", "TEST")
                        .addLong("timestamp", System.currentTimeMillis())
                        .toJobParameters());

        // Then
        assertThat(execution.getStatus())
                .isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions().iterator().next()
                .getWriteCount())
                .isEqualTo(0);
    }
}
```

### Required Test Dependencies (pom.xml)

```xml
<!-- Spring Batch Test (already listed in prerequisites) -->
<dependency>
    <groupId>org.springframework.batch</groupId>
    <artifactId>spring-batch-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- H2 for in-memory integration tests -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Acceptance Criteria Checklist

- [ ] `SubscriptionExpiredEvent` record class exists in `common` module with all required fields
- [ ] `SubscriptionRepository.findByStatusAndExpiresAtBefore()` query returns ACTIVE subscriptions past expiry
- [ ] `SubscriptionExpiryProcessor` sets `status=EXPIRED` and `expiredAt=NOW()` on each subscription
- [ ] `SubscriptionExpiryProcessor` preserves all other subscription fields unchanged
- [ ] `JpaPagingItemReader` reads ACTIVE subscriptions where `expiresAt < NOW()` with page size 100
- [ ] `JpaItemWriter` batch-updates expired subscriptions in PostgreSQL
- [ ] `SubscriptionExpiryJobListener` publishes `SubscriptionExpiredEvent` to Kafka topic `subscription.expired` for each expired subscription
- [ ] `SubscriptionExpiryJobListener` uses subscription ID as Kafka key for partition ordering
- [ ] `SubscriptionExpiryJobListener` discards pending events on chunk error (no phantom notifications)
- [ ] `BatchSchedulerConfig` schedules `subscriptionExpiryJob` at CRON `0 0 6 * * *`
- [ ] `AdminBatchController.triggerSubscriptionExpiry()` returns 202 Accepted with job execution ID
- [ ] `AdminBatchController` is secured with `@PreAuthorize("hasRole('ADMIN')")`
- [ ] Non-expired subscriptions (expiresAt in future) remain ACTIVE and untouched
- [ ] Job completes with zero writes when no subscriptions are expired
- [ ] All 7 JUnit 5 tests pass green
