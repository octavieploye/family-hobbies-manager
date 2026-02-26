# Story S7-001: Implement HelloAsso Sync Batch Job

> 8 points | Priority: P0 | Service: association-service
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The association-service already has an `AssociationSyncService` (S5-003) that performs on-demand synchronization of association data from the HelloAsso API. However, for production-grade data freshness, the platform needs an automated, scheduled batch job that runs daily to synchronize the full HelloAsso association directory into the local database. This batch job uses Spring Batch 5.x chunk-oriented processing with a paginated `ItemReader` that calls the HelloAsso API through the existing `HelloAssoClient` (S5-002), an `ItemProcessor` that maps HelloAsso DTOs to `Association` entities and skips unchanged records, and an `ItemWriter` that upserts to PostgreSQL. A custom `SkipPolicy` handles transient API errors gracefully (rate limits, timeouts) without aborting the entire job. The job runs on a daily CRON schedule at 2:00 AM and can also be triggered manually by administrators via a secured REST endpoint. Spring Batch metadata tables track execution history, step details, and job parameters for full auditability.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Shared batch config | `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/config/BatchConfig.java` | @EnableBatchProcessing, async job launcher, thread pool | `mvn compile -pl backend/association-service` |
| 2 | Skip policy | `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/policy/HelloAssoSkipPolicy.java` | Custom SkipPolicy for transient API errors | Unit test verifies skip decisions |
| 3 | Item reader | `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/reader/HelloAssoItemReader.java` | Paginated ItemReader using HelloAssoClient | Mock test returns paginated data |
| 4 | Item processor | `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/processor/HelloAssoItemProcessor.java` | Map DTO to entity, skip unchanged | Unit test verifies transformation + skip |
| 5 | Item writer | `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/writer/HelloAssoItemWriter.java` | Upsert to DB via AssociationRepository | Mock test verifies saveAll |
| 6 | Job config | `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/config/HelloAssoSyncJobConfig.java` | Job + Step beans with chunk=50, skip policy | Integration test: job completes |
| 7 | Job listener | `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/listener/SyncJobListener.java` | JobExecutionListener with structured logging | Logs emitted on job start/end |
| 8 | Batch scheduler | `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/config/BatchSchedulerConfig.java` | CRON scheduler for helloAssoSyncJob | Application starts without errors |
| 9 | Admin controller | `backend/association-service/src/main/java/com/familyhobbies/associationservice/controller/AdminBatchController.java` | POST /admin/batch/helloasso-sync endpoint | `curl -X POST` returns 202 |
| 10 | Liquibase migration | `backend/association-service/src/main/resources/db/changelog/changesets/006-spring-batch-metadata.xml` | Spring Batch metadata tables (or auto-init) | `spring.batch.jdbc.initialize-schema=always` |
| 11 | application.yml additions | `backend/association-service/src/main/resources/application.yml` | Batch config properties | Application starts with batch disabled on startup |
| 12 | Failing tests (TDD) | `backend/association-service/src/test/java/com/familyhobbies/associationservice/batch/` | 12 JUnit 5 test cases | `mvn test -pl backend/association-service` |

---

## Task 1 Detail: Shared Batch Configuration

- **What**: `@Configuration` class enabling Spring Batch processing and scheduling. Provides an async `TaskExecutor` for non-blocking job launches and a `JobLauncher` that uses it for REST-triggered executions.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/config/BatchConfig.java`
- **Why**: All batch jobs in association-service share this infrastructure. The async launcher allows the admin REST endpoint to return 202 Accepted immediately while the job runs in a background thread.
- **Content**:

```java
package com.familyhobbies.associationservice.batch.config;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Shared Spring Batch infrastructure for association-service.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@code batchTaskExecutor} -- thread pool for async job execution</li>
 *   <li>{@code asyncJobLauncher} -- non-blocking job launcher for REST triggers</li>
 * </ul>
 *
 * <p>All batch jobs in this service inherit these beans. CRON scheduling
 * is enabled via {@code @EnableScheduling}; individual schedules are declared
 * in {@link BatchSchedulerConfig}.
 */
@Configuration
@EnableBatchProcessing
@EnableScheduling
public class BatchConfig {

    /**
     * Thread pool for background batch execution.
     * Sized conservatively: 2 core, 4 max, 10 queue depth.
     * association-service runs at most 2 concurrent jobs (sync + expiry).
     */
    @Bean(name = "batchTaskExecutor")
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("batch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Async job launcher for REST-triggered batch executions.
     * Returns immediately after submitting the job to the thread pool.
     * The synchronous default {@code JobLauncher} provided by Spring Batch
     * is still available for CRON-triggered executions.
     */
    @Bean
    public JobLauncher asyncJobLauncher(JobRepository jobRepository,
                                        TaskExecutor batchTaskExecutor) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(batchTaskExecutor);
        launcher.afterPropertiesSet();
        return launcher;
    }
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles without errors

---

## Task 2 Detail: HelloAsso Skip Policy

- **What**: Custom `SkipPolicy` implementation that allows the batch step to skip transient API errors (rate limits, timeouts, connection errors, HelloAsso 5xx errors) without aborting the entire job. Limits total skips to a configurable maximum (default 10). Non-transient errors (4xx except 429) are never skipped.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/policy/HelloAssoSkipPolicy.java`
- **Why**: HelloAsso API may be temporarily unavailable or rate-limit our requests. The skip policy ensures the job processes as many associations as possible, logging skipped items for manual review, rather than failing entirely on a single transient error.
- **Content**:

```java
package com.familyhobbies.associationservice.batch.policy;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Skip policy for the HelloAsso sync batch step.
 *
 * <p>Skippable exceptions (transient):
 * <ul>
 *   <li>{@link ExternalApiException} with upstream status 429 (rate limit) or 5xx</li>
 *   <li>{@link WebClientRequestException} (connection refused, DNS failure)</li>
 *   <li>{@link TimeoutException} and {@link SocketTimeoutException}</li>
 *   <li>{@link ConnectException}</li>
 * </ul>
 *
 * <p>Non-skippable exceptions:
 * <ul>
 *   <li>{@link ExternalApiException} with upstream status 4xx (except 429)</li>
 *   <li>Any other unexpected exception (NullPointerException, etc.)</li>
 * </ul>
 *
 * <p>Maximum skip count defaults to 10. If exceeded, the step fails.
 */
public class HelloAssoSkipPolicy implements SkipPolicy {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoSkipPolicy.class);

    private final int maxSkipCount;

    public HelloAssoSkipPolicy() {
        this(10);
    }

    public HelloAssoSkipPolicy(int maxSkipCount) {
        this.maxSkipCount = maxSkipCount;
    }

    @Override
    public boolean shouldSkip(Throwable throwable, long skipCount) {
        if (skipCount >= maxSkipCount) {
            log.warn("HelloAsso sync skip limit reached: maxSkipCount={}, "
                    + "lastException={}", maxSkipCount, throwable.getMessage());
            return false;
        }

        if (throwable instanceof ExternalApiException apiEx) {
            int upstream = apiEx.getUpstreamStatus();
            // Skip rate limits (429) and server errors (5xx)
            if (upstream == 429 || upstream >= 500) {
                log.info("Skipping transient HelloAsso API error: "
                        + "upstream={}, message={}, skipCount={}",
                        upstream, apiEx.getMessage(), skipCount);
                return true;
            }
            // Do NOT skip client errors (400, 401, 403, 404, etc.)
            log.warn("Non-skippable HelloAsso client error: "
                    + "upstream={}, message={}",
                    upstream, apiEx.getMessage());
            return false;
        }

        if (throwable instanceof WebClientRequestException
                || throwable instanceof TimeoutException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof ConnectException) {
            log.info("Skipping transient connection error: type={}, "
                    + "message={}, skipCount={}",
                    throwable.getClass().getSimpleName(),
                    throwable.getMessage(), skipCount);
            return true;
        }

        // Unknown exceptions are NOT skippable
        log.warn("Non-skippable exception in HelloAsso sync: type={}, "
                + "message={}",
                throwable.getClass().getSimpleName(),
                throwable.getMessage());
        return false;
    }
}
```

- **Verify**: `mvn test -pl backend/association-service -Dtest=HelloAssoSkipPolicyTest` -> all skip policy tests pass

---

## Task 3 Detail: HelloAsso Item Reader

- **What**: Spring Batch `ItemReader` that fetches organizations from the HelloAsso API v5 in pages of 50. Uses the existing `HelloAssoClient` (S5-002) to call `POST /directory/organizations` with pagination. Buffers each page in an internal queue and returns items one at a time to Spring Batch. Returns `null` when all pages are exhausted (Spring Batch end-of-data signal). Implements `@StepScope` for safe reuse across job executions.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/reader/HelloAssoItemReader.java`
- **Why**: The batch reader decouples HelloAsso API pagination from the chunk-oriented processing model. Each chunk processes up to 50 items (one page), but the reader handles multi-page iteration transparently.
- **Content**:

```java
package com.familyhobbies.associationservice.batch.reader;

import com.familyhobbies.associationservice.adapter.HelloAssoClient;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Paginated reader for the HelloAsso organization directory.
 *
 * <p>Fetches pages of {@value #PAGE_SIZE} organizations via
 * {@link HelloAssoClient#searchOrganizations} and buffers them
 * in an internal queue. Returns {@code null} when all pages are
 * exhausted (signals end-of-data to Spring Batch).
 *
 * <p>Pagination uses {@code pageIndex} for the first request and
 * {@code continuationToken} for subsequent pages (HelloAsso API v5 pattern).
 *
 * <p>Thread safety: NOT thread-safe. Each step execution creates its own
 * instance via {@code @StepScope}.
 *
 * @see HelloAssoClient#searchOrganizations(HelloAssoDirectoryRequest)
 */
@Component
@StepScope
public class HelloAssoItemReader implements ItemReader<HelloAssoOrganization> {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoItemReader.class);
    private static final int PAGE_SIZE = 50;

    private final HelloAssoClient helloAssoClient;

    private int currentPageIndex = 1;
    private String continuationToken = null;
    private boolean exhausted = false;
    private final Queue<HelloAssoOrganization> buffer = new LinkedList<>();
    private int totalRead = 0;

    public HelloAssoItemReader(HelloAssoClient helloAssoClient) {
        this.helloAssoClient = helloAssoClient;
    }

    @Override
    public HelloAssoOrganization read() {
        // Return buffered item if available
        if (!buffer.isEmpty()) {
            totalRead++;
            return buffer.poll();
        }

        // All pages consumed
        if (exhausted) {
            log.info("HelloAsso reader exhausted: totalRead={}", totalRead);
            return null;
        }

        // Fetch next page from HelloAsso API
        fetchNextPage();

        if (buffer.isEmpty()) {
            log.info("HelloAsso reader exhausted (empty page): "
                    + "totalRead={}", totalRead);
            exhausted = true;
            return null;
        }

        totalRead++;
        return buffer.poll();
    }

    /**
     * Fetches the next page from HelloAsso and adds results to the buffer.
     * Uses continuationToken for pages after the first.
     */
    private void fetchNextPage() {
        HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
                .pageSize(PAGE_SIZE)
                .pageIndex(continuationToken == null ? currentPageIndex : null)
                .continuationToken(continuationToken)
                .build();

        log.debug("Fetching HelloAsso directory page: pageIndex={}, "
                + "continuationToken={}", currentPageIndex,
                continuationToken != null ? "present" : "null");

        HelloAssoDirectoryResponse response = helloAssoClient
                .searchOrganizations(request)
                .block();

        if (response == null || response.data() == null
                || response.data().isEmpty()) {
            exhausted = true;
            return;
        }

        buffer.addAll(response.data());
        currentPageIndex++;

        // Update pagination state
        if (response.pagination() != null
                && response.pagination().continuationToken() != null) {
            continuationToken = response.pagination().continuationToken();
        } else {
            // No more pages available
            exhausted = true;
        }

        log.debug("Fetched {} organizations from HelloAsso "
                + "(page {}), exhausted={}",
                response.data().size(), currentPageIndex - 1, exhausted);
    }
}
```

- **Verify**: `mvn test -pl backend/association-service -Dtest=HelloAssoItemReaderTest` -> reader returns paginated data from mock API

---

## Task 4 Detail: HelloAsso Item Processor

- **What**: Spring Batch `ItemProcessor` that maps `HelloAssoOrganization` DTOs to `Association` JPA entities. Detects new organizations (INSERT), updated organizations (UPDATE based on `updatedDate` comparison), and unchanged organizations (returns `null` to skip). Applies `@StepScope` for safe reuse across executions.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/processor/HelloAssoItemProcessor.java`
- **Why**: The processor is where the "smart" logic lives: by returning `null` for unchanged records, it prevents unnecessary database writes and reduces I/O during daily sync.
- **Content**:

```java
package com.familyhobbies.associationservice.batch.processor;

import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Maps HelloAsso organization DTOs to local Association entities.
 *
 * <p>Processing logic:
 * <ol>
 *   <li>Look up existing Association by {@code helloAssoSlug}</li>
 *   <li>If found and not modified since last sync: return {@code null} (skip)</li>
 *   <li>If found and modified: update fields, set {@code lastSyncedAt}</li>
 *   <li>If not found: create new Association entity</li>
 * </ol>
 *
 * <p>Spring Batch convention: returning {@code null} from a processor
 * means "skip this item -- do not pass to the writer." This prevents
 * unnecessary database writes for unchanged records.
 */
@Component
@StepScope
public class HelloAssoItemProcessor
        implements ItemProcessor<HelloAssoOrganization, Association> {

    private static final Logger log =
            LoggerFactory.getLogger(HelloAssoItemProcessor.class);

    private final AssociationRepository associationRepository;

    private int newCount = 0;
    private int updatedCount = 0;
    private int skippedCount = 0;

    public HelloAssoItemProcessor(AssociationRepository associationRepository) {
        this.associationRepository = associationRepository;
    }

    @Override
    public Association process(HelloAssoOrganization helloAssoOrg) {
        Optional<Association> existingOpt = associationRepository
                .findByHelloAssoSlug(helloAssoOrg.slug());

        if (existingOpt.isPresent()) {
            Association existing = existingOpt.get();

            // Skip if not modified since last sync
            if (isUnchanged(existing, helloAssoOrg)) {
                skippedCount++;
                log.trace("Skipping unchanged association: slug={}",
                        helloAssoOrg.slug());
                return null;
            }

            // Update existing entity with fresh data
            mapFields(helloAssoOrg, existing);
            existing.setLastSyncedAt(LocalDateTime.now());
            updatedCount++;
            log.debug("Updating association: slug={}, name={}",
                    helloAssoOrg.slug(), helloAssoOrg.name());
            return existing;
        }

        // New association discovered
        Association newAssociation = new Association();
        mapFields(helloAssoOrg, newAssociation);
        newAssociation.setHelloAssoSlug(helloAssoOrg.slug());
        newAssociation.setLastSyncedAt(LocalDateTime.now());
        newAssociation.setStatus("ACTIVE");
        newCount++;
        log.info("New association discovered: slug={}, name={}, city={}",
                helloAssoOrg.slug(), helloAssoOrg.name(),
                helloAssoOrg.city());
        return newAssociation;
    }

    /**
     * Checks whether the HelloAsso organization has changed since
     * the last sync by comparing {@code updatedDate} against
     * {@code lastSyncedAt}.
     */
    private boolean isUnchanged(Association existing,
                                 HelloAssoOrganization incoming) {
        if (existing.getLastSyncedAt() == null) {
            return false; // Never synced -- treat as changed
        }
        if (incoming.updatedDate() == null) {
            return true; // No update date from API -- assume unchanged
        }
        LocalDateTime incomingUpdate = incoming.updatedDate()
                .toLocalDateTime();
        return !incomingUpdate.isAfter(existing.getLastSyncedAt());
    }

    /**
     * Maps HelloAsso DTO fields to the Association entity.
     */
    private void mapFields(HelloAssoOrganization source,
                            Association target) {
        target.setName(source.name());
        target.setDescription(source.description());
        target.setCity(source.city());
        target.setZipCode(source.postalCode());
        target.setCategory(source.category());
        target.setLogoUrl(source.logo());
        target.setHelloAssoUrl(source.url());
    }

    public int getNewCount() {
        return newCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }
}
```

- **Verify**: `mvn test -pl backend/association-service -Dtest=HelloAssoItemProcessorTest` -> processor transforms correctly and returns null for unchanged

---

## Task 5 Detail: HelloAsso Item Writer

- **What**: Spring Batch `ItemWriter` that upserts `Association` entities to PostgreSQL via `AssociationRepository.saveAll()`. After successful persistence, publishes an `AssociationSyncedEvent` to Kafka for each saved entity.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/writer/HelloAssoItemWriter.java`
- **Why**: The writer is the final stage of the chunk pipeline. It handles both inserts (new associations) and updates (existing associations with changed data). The Kafka event allows downstream services (notification-service) to react to directory changes.
- **Content**:

```java
package com.familyhobbies.associationservice.batch.writer;

import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Batch writer that upserts Association entities to PostgreSQL
 * and publishes sync events to Kafka.
 *
 * <p>Uses {@link AssociationRepository#saveAll} for batch persistence.
 * JPA's merge semantics handle both INSERT (new, no ID) and UPDATE
 * (existing, has ID) transparently.
 *
 * <p>After a successful save, publishes an {@code AssociationSyncedEvent}
 * to the {@code family-hobbies.association.synced} Kafka topic for each
 * persisted entity. The Kafka publish is fire-and-forget within the
 * chunk transaction.
 */
@Component
public class HelloAssoItemWriter implements ItemWriter<Association> {

    private static final Logger log =
            LoggerFactory.getLogger(HelloAssoItemWriter.class);
    private static final String TOPIC = "family-hobbies.association.synced";

    private final AssociationRepository associationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public HelloAssoItemWriter(
            AssociationRepository associationRepository,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.associationRepository = associationRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void write(Chunk<? extends Association> chunk) {
        List<? extends Association> items = chunk.getItems();
        if (items.isEmpty()) {
            return;
        }

        // Batch upsert
        List<Association> saved = associationRepository.saveAll(
                (List<Association>) items);
        log.info("Batch wrote {} associations to database", saved.size());

        // Publish Kafka events for each persisted association
        for (Association association : saved) {
            AssociationSyncedEvent event = new AssociationSyncedEvent(
                    association.getId(),
                    association.getHelloAssoSlug(),
                    association.getName(),
                    association.getStatus()
            );
            kafkaTemplate.send(TOPIC,
                    association.getHelloAssoSlug(), event);
            log.debug("Published AssociationSyncedEvent: slug={}",
                    association.getHelloAssoSlug());
        }
    }

    /**
     * Lightweight event record for association sync notifications.
     * Published to Kafka after each batch write.
     */
    public record AssociationSyncedEvent(
            Long associationId,
            String helloAssoSlug,
            String name,
            String status
    ) {}
}
```

- **Verify**: `mvn test -pl backend/association-service -Dtest=HelloAssoItemWriterTest` -> writer calls saveAll and sends Kafka events

---

## Task 6 Detail: HelloAsso Sync Job Configuration

- **What**: Spring Batch `@Configuration` class that defines the `helloAssoSyncJob` Job bean and the `fetchOrganizationsStep` Step bean. The step uses chunk size 50, fault tolerance with the custom `HelloAssoSkipPolicy`, and a retry policy for `TooManyRequests` (HTTP 429). The `RunIdIncrementer` ensures each execution gets a unique job instance.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/config/HelloAssoSyncJobConfig.java`
- **Why**: This is the central orchestration class that wires together the reader, processor, writer, skip policy, and listener into a complete Spring Batch job.
- **Content**:

```java
package com.familyhobbies.associationservice.batch.config;

import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.batch.listener.SyncJobListener;
import com.familyhobbies.associationservice.batch.policy.HelloAssoSkipPolicy;
import com.familyhobbies.associationservice.batch.processor.HelloAssoItemProcessor;
import com.familyhobbies.associationservice.batch.reader.HelloAssoItemReader;
import com.familyhobbies.associationservice.batch.writer.HelloAssoItemWriter;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch job configuration for HelloAsso association directory sync.
 *
 * <p>Job: {@code helloAssoSyncJob}
 * <ul>
 *   <li>Step: {@code fetchOrganizationsStep}</li>
 *   <li>Reader: {@link HelloAssoItemReader} -- paginated API calls</li>
 *   <li>Processor: {@link HelloAssoItemProcessor} -- DTO to entity, skip unchanged</li>
 *   <li>Writer: {@link HelloAssoItemWriter} -- upsert to DB + Kafka events</li>
 *   <li>Chunk size: 50 (matches HelloAsso API page size)</li>
 *   <li>Skip policy: {@link HelloAssoSkipPolicy} -- skip transient API errors</li>
 *   <li>Retry: 3 attempts on {@link ExternalApiException} with 429 status</li>
 * </ul>
 *
 * <p>The {@link RunIdIncrementer} ensures each manual or scheduled trigger
 * creates a new job instance, allowing re-runs on the same day.
 */
@Configuration
public class HelloAssoSyncJobConfig {

    private static final int CHUNK_SIZE = 50;
    private static final int MAX_SKIP_COUNT = 10;
    private static final int RETRY_LIMIT = 3;

    private final HelloAssoItemReader helloAssoItemReader;
    private final HelloAssoItemProcessor helloAssoItemProcessor;
    private final HelloAssoItemWriter helloAssoItemWriter;
    private final SyncJobListener syncJobListener;

    public HelloAssoSyncJobConfig(
            HelloAssoItemReader helloAssoItemReader,
            HelloAssoItemProcessor helloAssoItemProcessor,
            HelloAssoItemWriter helloAssoItemWriter,
            SyncJobListener syncJobListener) {
        this.helloAssoItemReader = helloAssoItemReader;
        this.helloAssoItemProcessor = helloAssoItemProcessor;
        this.helloAssoItemWriter = helloAssoItemWriter;
        this.syncJobListener = syncJobListener;
    }

    @Bean
    public Job helloAssoSyncJob(JobRepository jobRepository,
                                 Step fetchOrganizationsStep) {
        return new JobBuilder("helloAssoSyncJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(fetchOrganizationsStep)
                .listener(syncJobListener)
                .build();
    }

    @Bean
    public Step fetchOrganizationsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager) {
        return new StepBuilder("fetchOrganizationsStep", jobRepository)
                .<HelloAssoOrganization, Association>chunk(
                        CHUNK_SIZE, transactionManager)
                .reader(helloAssoItemReader)
                .processor(helloAssoItemProcessor)
                .writer(helloAssoItemWriter)
                .faultTolerant()
                .skipPolicy(new HelloAssoSkipPolicy(MAX_SKIP_COUNT))
                .retry(ExternalApiException.class)
                .retryLimit(RETRY_LIMIT)
                .listener(syncJobListener)
                .build();
    }
}
```

- **Verify**: `mvn test -pl backend/association-service -Dtest=HelloAssoSyncJobConfigTest` -> job completes with COMPLETED status

---

## Task 7 Detail: Sync Job Listener

- **What**: `JobExecutionListener` and `StepExecutionListener` that logs structured information at job start, job end, and after each step. Calculates duration, items read/written/skipped, and reports failures.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/listener/SyncJobListener.java`
- **Why**: Observability for batch jobs is critical in production. Structured log output is consumed by the ELK stack (S7-007) and feeds Actuator metrics (S7-006).
- **Content**:

```java
package com.familyhobbies.associationservice.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.listener.StepExecutionListenerSupport;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Structured logging listener for the HelloAsso sync batch job.
 *
 * <p>Logs:
 * <ul>
 *   <li>Job start: job name, job ID, parameters</li>
 *   <li>Job end: status, duration, read/write/skip counts</li>
 *   <li>Failures: exception details for FAILED jobs</li>
 * </ul>
 *
 * <p>Output is JSON-structured (via logback-spring.xml) for ingestion
 * by ELK stack, Graylog, or any log aggregator.
 */
@Component
public class SyncJobListener extends StepExecutionListenerSupport
        implements JobExecutionListener {

    private static final Logger log =
            LoggerFactory.getLogger(SyncJobListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Batch job STARTED: jobName={}, jobId={}, parameters={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobId(),
                jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime() != null
                ? jobExecution.getEndTime()
                : LocalDateTime.now();
        Duration duration = Duration.between(startTime, endTime);

        long itemsRead = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount)
                .sum();
        long itemsWritten = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();
        long itemsSkipped = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getSkipCount)
                .sum();
        long itemsFiltered = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getFilterCount)
                .sum();

        log.info("Batch job {}: jobName={}, jobId={}, duration={}s, "
                + "itemsRead={}, itemsWritten={}, itemsFiltered={}, "
                + "itemsSkipped={}",
                jobExecution.getStatus(),
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobId(),
                duration.getSeconds(),
                itemsRead,
                itemsWritten,
                itemsFiltered,
                itemsSkipped);

        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            jobExecution.getAllFailureExceptions().forEach(ex ->
                    log.error("Batch job failure: jobName={}, "
                            + "exception={}",
                            jobExecution.getJobInstance().getJobName(),
                            ex.getMessage(), ex));
        }
    }
}
```

- **Verify**: Run the job and observe structured log output with job name, duration, and item counts.

---

## Task 8 Detail: Batch Scheduler Configuration

- **What**: `@Configuration` class with `@Scheduled` methods that trigger batch jobs via CRON expressions. The HelloAsso sync job runs daily at 2:00 AM. Uses the synchronous `JobLauncher` (default) since CRON-triggered jobs do not need to return immediately.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/batch/config/BatchSchedulerConfig.java`
- **Why**: Scheduled execution is the primary trigger mode. The daily sync at 2 AM minimizes API load during off-peak hours and ensures data is fresh by the start of each business day.
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

import java.time.LocalDateTime;

/**
 * CRON scheduler for all batch jobs in association-service.
 *
 * <p>Schedules:
 * <ul>
 *   <li>{@code helloAssoSyncJob}: daily at 2:00 AM ({@code 0 0 2 * * *})</li>
 *   <li>{@code subscriptionExpiryJob}: daily at 6:00 AM ({@code 0 0 6 * * *})
 *       -- added by S7-002</li>
 * </ul>
 *
 * <p>Each execution passes a unique {@code timestamp} job parameter
 * to ensure a new job instance is created (required by Spring Batch
 * for re-runs on the same day).
 */
@Configuration
public class BatchSchedulerConfig {

    private static final Logger log =
            LoggerFactory.getLogger(BatchSchedulerConfig.class);

    private final JobLauncher jobLauncher;
    private final Job helloAssoSyncJob;

    public BatchSchedulerConfig(
            JobLauncher jobLauncher,
            @Qualifier("helloAssoSyncJob") Job helloAssoSyncJob) {
        this.jobLauncher = jobLauncher;
        this.helloAssoSyncJob = helloAssoSyncJob;
    }

    /**
     * Runs the HelloAsso sync job daily at 2:00 AM.
     * The timestamp parameter ensures each run creates a unique job instance.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void runHelloAssoSync() {
        log.info("CRON trigger: launching helloAssoSyncJob at {}",
                LocalDateTime.now());
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("trigger", "CRON")
                    .addLocalDateTime("timestamp", LocalDateTime.now())
                    .toJobParameters();
            jobLauncher.run(helloAssoSyncJob, params);
        } catch (Exception e) {
            log.error("Failed to launch helloAssoSyncJob via CRON", e);
        }
    }
}
```

- **Verify**: Application starts without errors. CRON expression syntax is valid. Job launches at the scheduled time in integration tests.

---

## Task 9 Detail: Admin Batch Controller

- **What**: REST controller providing a manual trigger endpoint for the HelloAsso sync job. Secured with `ADMIN` role. Returns 202 Accepted with the job execution ID. Uses the async `JobLauncher` so the HTTP response is immediate.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/controller/AdminBatchController.java`
- **Why**: Administrators need the ability to trigger a sync on demand (e.g., after adding new associations in HelloAsso, or to recover from a failed nightly sync).
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

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Admin REST controller for manually triggering batch jobs.
 *
 * <p>All endpoints require the {@code ADMIN} role.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/admin/batch/helloasso-sync} -- trigger HelloAsso sync</li>
 *   <li>{@code POST /api/v1/admin/batch/subscription-expiry} -- trigger subscription expiry (S7-002)</li>
 * </ul>
 *
 * <p>Uses the {@code asyncJobLauncher} to return 202 Accepted immediately
 * while the job runs in a background thread.
 */
@RestController
@RequestMapping("/api/v1/admin/batch")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBatchController {

    private static final Logger log =
            LoggerFactory.getLogger(AdminBatchController.class);

    private final JobLauncher asyncJobLauncher;
    private final Job helloAssoSyncJob;

    public AdminBatchController(
            @Qualifier("asyncJobLauncher") JobLauncher asyncJobLauncher,
            @Qualifier("helloAssoSyncJob") Job helloAssoSyncJob) {
        this.asyncJobLauncher = asyncJobLauncher;
        this.helloAssoSyncJob = helloAssoSyncJob;
    }

    /**
     * Manually triggers the HelloAsso association sync batch job.
     *
     * @return 202 Accepted with job execution ID
     */
    @PostMapping("/helloasso-sync")
    public ResponseEntity<Map<String, Object>> triggerHelloAssoSync() {
        log.info("Admin trigger: launching helloAssoSyncJob");
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("trigger", "ADMIN")
                    .addLocalDateTime("timestamp", LocalDateTime.now())
                    .toJobParameters();

            JobExecution execution = asyncJobLauncher.run(
                    helloAssoSyncJob, params);

            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "jobName", "helloAssoSyncJob",
                            "jobExecutionId", execution.getId(),
                            "status", execution.getStatus().toString(),
                            "message", "Job launched successfully. "
                                    + "Check /api/v1/admin/batch/status/"
                                    + execution.getId()
                                    + " for progress."
                    ));
        } catch (Exception e) {
            log.error("Failed to launch helloAssoSyncJob via admin trigger",
                    e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "jobName", "helloAssoSyncJob",
                            "status", "LAUNCH_FAILED",
                            "message", e.getMessage()
                    ));
        }
    }
}
```

- **Verify**: `curl -X POST http://localhost:8082/api/v1/admin/batch/helloasso-sync -H "Authorization: Bearer {admin-jwt}"` -> returns 202 with job execution ID

---

## Task 10 Detail: Liquibase Migration for Spring Batch Metadata

- **What**: Configuration property to auto-initialize Spring Batch metadata tables. Spring Batch 5.x can create its own `BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`, etc. tables using `spring.batch.jdbc.initialize-schema=always`.
- **Where**: `backend/association-service/src/main/resources/application.yml` (merge)
- **Why**: Spring Batch requires metadata tables to track job executions, step details, and parameters. The auto-initialization approach is simpler than a Liquibase changeset and is recommended for single-database-per-service architectures.
- **Content** (alternative Liquibase changeset if explicit control is preferred):

```xml
<!-- File: backend/association-service/src/main/resources/db/changelog/changesets/006-spring-batch-metadata.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="006-spring-batch-metadata" author="familyhobbies">
        <comment>
            Spring Batch 5.x metadata tables.
            Alternative to spring.batch.jdbc.initialize-schema=always.
            These tables track job execution history, step details, and parameters.
        </comment>
        <sql>
            -- Spring Batch 5.x PostgreSQL schema
            -- Source: org/springframework/batch/core/schema-postgresql.sql

            CREATE TABLE IF NOT EXISTS BATCH_JOB_INSTANCE (
                JOB_INSTANCE_ID BIGINT NOT NULL PRIMARY KEY,
                VERSION BIGINT,
                JOB_NAME VARCHAR(100) NOT NULL,
                JOB_KEY VARCHAR(32) NOT NULL,
                CONSTRAINT JOB_INST_UN UNIQUE (JOB_NAME, JOB_KEY)
            );

            CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION (
                JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
                VERSION BIGINT,
                JOB_INSTANCE_ID BIGINT NOT NULL,
                CREATE_TIME TIMESTAMP NOT NULL,
                START_TIME TIMESTAMP DEFAULT NULL,
                END_TIME TIMESTAMP DEFAULT NULL,
                STATUS VARCHAR(10),
                EXIT_CODE VARCHAR(2500),
                EXIT_MESSAGE VARCHAR(2500),
                LAST_UPDATED TIMESTAMP,
                CONSTRAINT JOB_INST_EXEC_FK FOREIGN KEY (JOB_INSTANCE_ID)
                    REFERENCES BATCH_JOB_INSTANCE(JOB_INSTANCE_ID)
            );

            CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_PARAMS (
                JOB_EXECUTION_ID BIGINT NOT NULL,
                PARAMETER_NAME VARCHAR(100) NOT NULL,
                PARAMETER_TYPE VARCHAR(100) NOT NULL,
                PARAMETER_VALUE VARCHAR(2500),
                IDENTIFYING CHAR(1) NOT NULL,
                CONSTRAINT JOB_EXEC_PARAMS_FK FOREIGN KEY (JOB_EXECUTION_ID)
                    REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
            );

            CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION (
                STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
                VERSION BIGINT NOT NULL,
                STEP_NAME VARCHAR(100) NOT NULL,
                JOB_EXECUTION_ID BIGINT NOT NULL,
                CREATE_TIME TIMESTAMP NOT NULL,
                START_TIME TIMESTAMP DEFAULT NULL,
                END_TIME TIMESTAMP DEFAULT NULL,
                STATUS VARCHAR(10),
                COMMIT_COUNT BIGINT,
                READ_COUNT BIGINT,
                FILTER_COUNT BIGINT,
                WRITE_COUNT BIGINT,
                READ_SKIP_COUNT BIGINT,
                WRITE_SKIP_COUNT BIGINT,
                PROCESS_SKIP_COUNT BIGINT,
                ROLLBACK_COUNT BIGINT,
                EXIT_CODE VARCHAR(2500),
                EXIT_MESSAGE VARCHAR(2500),
                LAST_UPDATED TIMESTAMP,
                CONSTRAINT JOB_EXEC_STEP_FK FOREIGN KEY (JOB_EXECUTION_ID)
                    REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
            );

            CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION_CONTEXT (
                STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
                SHORT_CONTEXT VARCHAR(2500) NOT NULL,
                SERIALIZED_CONTEXT TEXT,
                CONSTRAINT STEP_EXEC_CTX_FK FOREIGN KEY (STEP_EXECUTION_ID)
                    REFERENCES BATCH_STEP_EXECUTION(STEP_EXECUTION_ID)
            );

            CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_CONTEXT (
                JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
                SHORT_CONTEXT VARCHAR(2500) NOT NULL,
                SERIALIZED_CONTEXT TEXT,
                CONSTRAINT JOB_EXEC_CTX_FK FOREIGN KEY (JOB_EXECUTION_ID)
                    REFERENCES BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
            );

            CREATE SEQUENCE IF NOT EXISTS BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
            CREATE SEQUENCE IF NOT EXISTS BATCH_JOB_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
            CREATE SEQUENCE IF NOT EXISTS BATCH_JOB_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
        </sql>
    </changeSet>

</databaseChangeLog>
```

- **Verify**: `spring.batch.jdbc.initialize-schema=always` or Liquibase migration creates all `BATCH_*` tables. `SELECT * FROM BATCH_JOB_INSTANCE;` returns empty result set.

---

## Task 11 Detail: Application YAML Additions

- **What**: Batch-related configuration properties for association-service.
- **Where**: `backend/association-service/src/main/resources/application.yml` (merge into existing)
- **Why**: Configures Spring Batch metadata initialization, disables auto-run on startup, and documents CRON schedules.
- **Content**:

```yaml
# ── Spring Batch Configuration ──────────────────────────────────────────
spring:
  batch:
    jdbc:
      initialize-schema: always   # Auto-create BATCH_* metadata tables
    job:
      enabled: false              # Do NOT auto-run jobs on startup

# ── Batch Job Schedules ─────────────────────────────────────────────────
# Documented here for visibility; actual CRON expressions are in
# BatchSchedulerConfig.java using @Scheduled annotations.
#
# helloAssoSyncJob:        0 0 2 * * *   (daily at 2:00 AM)
# subscriptionExpiryJob:   0 0 6 * * *   (daily at 6:00 AM, added by S7-002)
```

- **Verify**: Application starts without auto-running batch jobs. `BATCH_*` tables exist in PostgreSQL.

---

## Failing Tests (TDD Contract)

**Test file**: `backend/association-service/src/test/java/com/familyhobbies/associationservice/batch/`

**Test categories (12 tests total)**:

| Category | Test Class | Tests | What They Verify |
|----------|-----------|-------|------------------|
| Skip Policy | `HelloAssoSkipPolicyTest` | 4 | Transient errors skipped, client errors not skipped, skip limit enforced |
| Item Reader | `HelloAssoItemReaderTest` | 3 | Paginated reads, empty page returns null, multi-page iteration |
| Item Processor | `HelloAssoItemProcessorTest` | 3 | New org -> entity, updated org -> entity, unchanged -> null |
| Item Writer | `HelloAssoItemWriterTest` | 1 | saveAll called, Kafka events published |
| Job Integration | `HelloAssoSyncJobConfigTest` | 1 | Full job completes with COMPLETED status |

### HelloAssoSkipPolicyTest.java

```java
package com.familyhobbies.associationservice.batch.policy;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.ConnectException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD contract tests for {@link HelloAssoSkipPolicy}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Transient API errors (429, 5xx) are skippable</li>
 *   <li>Client errors (400, 401, 403, 404) are NOT skippable</li>
 *   <li>Connection/timeout errors are skippable</li>
 *   <li>Skip limit is enforced</li>
 * </ul>
 */
class HelloAssoSkipPolicyTest {

    private HelloAssoSkipPolicy skipPolicy;

    @BeforeEach
    void setUp() {
        skipPolicy = new HelloAssoSkipPolicy(10);
    }

    @Test
    @DisplayName("Should skip ExternalApiException with 429 (rate limit)")
    void shouldSkipRateLimitError() {
        // Given
        ExternalApiException exception = new ExternalApiException(
                "Rate limit exceeded", "HelloAsso", 429);

        // When
        boolean result = skipPolicy.shouldSkip(exception, 0);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should skip ExternalApiException with 503 (server unavailable)")
    void shouldSkipServerError() {
        // Given
        ExternalApiException exception = new ExternalApiException(
                "Service unavailable", "HelloAsso", 503);

        // When
        boolean result = skipPolicy.shouldSkip(exception, 0);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should NOT skip ExternalApiException with 400 (bad request)")
    void shouldNotSkipClientError() {
        // Given
        ExternalApiException exception = new ExternalApiException(
                "Bad request", "HelloAsso", 400);

        // When
        boolean result = skipPolicy.shouldSkip(exception, 0);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should NOT skip when skip count exceeds max")
    void shouldNotSkipWhenLimitReached() {
        // Given
        ExternalApiException exception = new ExternalApiException(
                "Rate limit exceeded", "HelloAsso", 429);

        // When -- skip count already at max
        boolean result = skipPolicy.shouldSkip(exception, 10);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should skip TimeoutException")
    void shouldSkipTimeoutException() {
        // Given
        TimeoutException exception = new TimeoutException("Request timed out");

        // When
        boolean result = skipPolicy.shouldSkip(exception, 0);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should skip ConnectException")
    void shouldSkipConnectException() {
        // Given
        ConnectException exception = new ConnectException("Connection refused");

        // When
        boolean result = skipPolicy.shouldSkip(exception, 0);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should NOT skip unknown exceptions (e.g., NullPointerException)")
    void shouldNotSkipUnknownException() {
        // Given
        NullPointerException exception = new NullPointerException("unexpected");

        // When
        boolean result = skipPolicy.shouldSkip(exception, 0);

        // Then
        assertThat(result).isFalse();
    }
}
```

### HelloAssoItemReaderTest.java

```java
package com.familyhobbies.associationservice.batch.reader;

import com.familyhobbies.associationservice.adapter.HelloAssoClient;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoPagination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TDD contract tests for {@link HelloAssoItemReader}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Reader returns items from the first page</li>
 *   <li>Reader iterates across multiple pages using continuationToken</li>
 *   <li>Reader returns null when all pages are exhausted</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class HelloAssoItemReaderTest {

    @Mock
    private HelloAssoClient helloAssoClient;

    private HelloAssoItemReader reader;

    @BeforeEach
    void setUp() {
        reader = new HelloAssoItemReader(helloAssoClient);
    }

    @Test
    @DisplayName("Should return organizations from first page")
    void shouldReturnOrganizationsFromFirstPage() throws Exception {
        // Given
        HelloAssoOrganization org1 = HelloAssoOrganization.builder()
                .name("Club de Danse Paris")
                .slug("club-danse-paris")
                .city("Paris")
                .postalCode("75001")
                .build();
        HelloAssoOrganization org2 = HelloAssoOrganization.builder()
                .name("Association Sport Lyon")
                .slug("asso-sport-lyon")
                .city("Lyon")
                .postalCode("69001")
                .build();

        HelloAssoPagination pagination = new HelloAssoPagination(
                50, 2, 1, 1, null); // No more pages
        HelloAssoDirectoryResponse response =
                new HelloAssoDirectoryResponse(List.of(org1, org2), pagination);

        when(helloAssoClient.searchOrganizations(any(HelloAssoDirectoryRequest.class)))
                .thenReturn(Mono.just(response));

        // When
        HelloAssoOrganization result1 = reader.read();
        HelloAssoOrganization result2 = reader.read();
        HelloAssoOrganization result3 = reader.read(); // Should be null

        // Then
        assertThat(result1).isNotNull();
        assertThat(result1.name()).isEqualTo("Club de Danse Paris");
        assertThat(result2).isNotNull();
        assertThat(result2.name()).isEqualTo("Association Sport Lyon");
        assertThat(result3).isNull(); // End of data
    }

    @Test
    @DisplayName("Should iterate across multiple pages using continuationToken")
    void shouldIterateMultiplePages() throws Exception {
        // Given -- page 1 with continuation token
        HelloAssoOrganization org1 = HelloAssoOrganization.builder()
                .name("Club de Danse Paris")
                .slug("club-danse-paris")
                .build();
        HelloAssoPagination page1Pagination = new HelloAssoPagination(
                50, 100, 1, 2, "token-page-2");
        HelloAssoDirectoryResponse page1Response =
                new HelloAssoDirectoryResponse(
                        List.of(org1), page1Pagination);

        // Given -- page 2 with no continuation token (last page)
        HelloAssoOrganization org2 = HelloAssoOrganization.builder()
                .name("Association Sport Lyon")
                .slug("asso-sport-lyon")
                .build();
        HelloAssoPagination page2Pagination = new HelloAssoPagination(
                50, 100, 2, 2, null);
        HelloAssoDirectoryResponse page2Response =
                new HelloAssoDirectoryResponse(
                        List.of(org2), page2Pagination);

        when(helloAssoClient.searchOrganizations(any(HelloAssoDirectoryRequest.class)))
                .thenReturn(Mono.just(page1Response))
                .thenReturn(Mono.just(page2Response));

        // When
        HelloAssoOrganization result1 = reader.read();
        HelloAssoOrganization result2 = reader.read();
        HelloAssoOrganization result3 = reader.read(); // null

        // Then
        assertThat(result1).isNotNull();
        assertThat(result1.slug()).isEqualTo("club-danse-paris");
        assertThat(result2).isNotNull();
        assertThat(result2.slug()).isEqualTo("asso-sport-lyon");
        assertThat(result3).isNull();
    }

    @Test
    @DisplayName("Should return null immediately for empty directory")
    void shouldReturnNullForEmptyDirectory() throws Exception {
        // Given
        HelloAssoPagination pagination = new HelloAssoPagination(
                50, 0, 1, 0, null);
        HelloAssoDirectoryResponse emptyResponse =
                new HelloAssoDirectoryResponse(List.of(), pagination);

        when(helloAssoClient.searchOrganizations(any(HelloAssoDirectoryRequest.class)))
                .thenReturn(Mono.just(emptyResponse));

        // When
        HelloAssoOrganization result = reader.read();

        // Then
        assertThat(result).isNull();
    }
}
```

### HelloAssoItemProcessorTest.java

```java
package com.familyhobbies.associationservice.batch.processor;

import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TDD contract tests for {@link HelloAssoItemProcessor}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>New organization -> creates Association entity with ACTIVE status</li>
 *   <li>Updated organization -> updates existing entity fields</li>
 *   <li>Unchanged organization -> returns null (skip)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class HelloAssoItemProcessorTest {

    @Mock
    private AssociationRepository associationRepository;

    private HelloAssoItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new HelloAssoItemProcessor(associationRepository);
    }

    @Test
    @DisplayName("Should create new Association for unknown slug")
    void shouldCreateNewAssociation() {
        // Given
        HelloAssoOrganization incoming = HelloAssoOrganization.builder()
                .name("Club de Danse Paris")
                .slug("club-danse-paris")
                .city("Paris")
                .postalCode("75001")
                .category("Danse")
                .description("Cours de danse pour tous")
                .build();

        when(associationRepository.findByHelloAssoSlug("club-danse-paris"))
                .thenReturn(Optional.empty());

        // When
        Association result = processor.process(incoming);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Club de Danse Paris");
        assertThat(result.getCity()).isEqualTo("Paris");
        assertThat(result.getZipCode()).isEqualTo("75001");
        assertThat(result.getHelloAssoSlug()).isEqualTo("club-danse-paris");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getLastSyncedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update existing Association when HelloAsso data changed")
    void shouldUpdateExistingAssociation() {
        // Given -- existing association synced yesterday
        Association existing = new Association();
        existing.setId(42L);
        existing.setHelloAssoSlug("club-danse-paris");
        existing.setName("Old Name");
        existing.setLastSyncedAt(LocalDateTime.now().minusDays(1));

        // Given -- incoming data updated today
        HelloAssoOrganization incoming = HelloAssoOrganization.builder()
                .name("Club de Danse Paris - Nouveau Nom")
                .slug("club-danse-paris")
                .city("Paris")
                .postalCode("75001")
                .updatedDate(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        when(associationRepository.findByHelloAssoSlug("club-danse-paris"))
                .thenReturn(Optional.of(existing));

        // When
        Association result = processor.process(incoming);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getName()).isEqualTo(
                "Club de Danse Paris - Nouveau Nom");
        assertThat(result.getLastSyncedAt()).isAfter(
                LocalDateTime.now().minusMinutes(1));
    }

    @Test
    @DisplayName("Should return null for unchanged Association (skip)")
    void shouldSkipUnchangedAssociation() {
        // Given -- existing association synced today
        Association existing = new Association();
        existing.setId(42L);
        existing.setHelloAssoSlug("club-danse-paris");
        existing.setLastSyncedAt(LocalDateTime.now());

        // Given -- incoming data updated yesterday (before last sync)
        HelloAssoOrganization incoming = HelloAssoOrganization.builder()
                .slug("club-danse-paris")
                .updatedDate(OffsetDateTime.now(ZoneOffset.UTC)
                        .minusDays(1))
                .build();

        when(associationRepository.findByHelloAssoSlug("club-danse-paris"))
                .thenReturn(Optional.of(existing));

        // When
        Association result = processor.process(incoming);

        // Then -- null means "skip this item"
        assertThat(result).isNull();
    }
}
```

### HelloAssoItemWriterTest.java

```java
package com.familyhobbies.associationservice.batch.writer;

import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TDD contract tests for {@link HelloAssoItemWriter}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>saveAll called with all chunk items</li>
 *   <li>Kafka event published for each saved association</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class HelloAssoItemWriterTest {

    @Mock
    private AssociationRepository associationRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private HelloAssoItemWriter writer;

    @Test
    @DisplayName("Should saveAll and publish Kafka events for each item")
    void shouldSaveAndPublishEvents() throws Exception {
        // Given
        Association assoc1 = new Association();
        assoc1.setId(1L);
        assoc1.setHelloAssoSlug("club-danse-paris");
        assoc1.setName("Club de Danse Paris");
        assoc1.setStatus("ACTIVE");

        Association assoc2 = new Association();
        assoc2.setId(2L);
        assoc2.setHelloAssoSlug("asso-sport-lyon");
        assoc2.setName("Association Sport Lyon");
        assoc2.setStatus("ACTIVE");

        when(associationRepository.saveAll(anyList()))
                .thenReturn(List.of(assoc1, assoc2));

        Chunk<Association> chunk = new Chunk<>(List.of(assoc1, assoc2));

        // When
        writer.write(chunk);

        // Then -- saveAll called
        verify(associationRepository).saveAll(anyList());

        // Then -- Kafka events published for each association
        verify(kafkaTemplate, times(2)).send(
                eq("family-hobbies.association.synced"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());

        // Verify first event was for club-danse-paris
        ArgumentCaptor<String> keyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(2)).send(
                eq("family-hobbies.association.synced"),
                keyCaptor.capture(),
                org.mockito.ArgumentMatchers.any());
        assertThat(keyCaptor.getAllValues())
                .containsExactly("club-danse-paris", "asso-sport-lyon");
    }
}
```

### HelloAssoSyncJobConfigTest.java (Integration Test)

```java
package com.familyhobbies.associationservice.batch.config;

import com.familyhobbies.associationservice.adapter.HelloAssoClient;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoPagination;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test for the full HelloAsso sync batch job.
 *
 * <p>Verifies that the complete job (reader -> processor -> writer)
 * executes end-to-end and completes with {@link BatchStatus#COMPLETED}.
 *
 * <p>Uses {@link SpringBatchTest} with mocked external dependencies
 * (HelloAssoClient, KafkaTemplate) and an in-memory H2 database.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class HelloAssoSyncJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @MockBean
    private HelloAssoClient helloAssoClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AssociationRepository associationRepository;

    @Autowired
    @Qualifier("helloAssoSyncJob")
    private Job helloAssoSyncJob;

    @Test
    @DisplayName("helloAssoSyncJob should complete successfully "
            + "with mock HelloAsso data")
    void jobShouldCompleteSuccessfully() throws Exception {
        // Given -- mock HelloAsso API returns one page of 2 organizations
        HelloAssoOrganization org1 = HelloAssoOrganization.builder()
                .name("Club de Danse Paris")
                .slug("club-danse-paris")
                .city("Paris")
                .postalCode("75001")
                .category("Danse")
                .build();
        HelloAssoOrganization org2 = HelloAssoOrganization.builder()
                .name("Association Sport Lyon")
                .slug("asso-sport-lyon")
                .city("Lyon")
                .postalCode("69001")
                .category("Sport")
                .build();

        HelloAssoPagination pagination = new HelloAssoPagination(
                50, 2, 1, 1, null);
        HelloAssoDirectoryResponse response =
                new HelloAssoDirectoryResponse(
                        List.of(org1, org2), pagination);

        when(helloAssoClient.searchOrganizations(
                any(HelloAssoDirectoryRequest.class)))
                .thenReturn(Mono.just(response));

        // When
        jobLauncherTestUtils.setJob(helloAssoSyncJob);
        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("trigger", "TEST")
                        .addLong("timestamp",
                                System.currentTimeMillis())
                        .toJobParameters());

        // Then
        assertThat(execution.getStatus())
                .isEqualTo(BatchStatus.COMPLETED);
        assertThat(execution.getStepExecutions())
                .hasSize(1);
        assertThat(execution.getStepExecutions().iterator().next()
                .getWriteCount())
                .isEqualTo(2);
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

- [ ] `HelloAssoItemReader` fetches paginated data from HelloAsso API via `HelloAssoClient`
- [ ] `HelloAssoItemProcessor` maps DTOs to entities, returns `null` for unchanged records
- [ ] `HelloAssoItemWriter` upserts to PostgreSQL via `AssociationRepository.saveAll()`
- [ ] `HelloAssoItemWriter` publishes `AssociationSyncedEvent` to Kafka for each saved entity
- [ ] `HelloAssoSkipPolicy` skips transient errors (429, 5xx, timeouts, connection errors)
- [ ] `HelloAssoSkipPolicy` does NOT skip non-transient client errors (400, 401, 403, 404)
- [ ] `HelloAssoSkipPolicy` enforces maximum skip count (default 10)
- [ ] `HelloAssoSyncJobConfig` creates `helloAssoSyncJob` with chunk size 50
- [ ] `HelloAssoSyncJobConfig` applies fault tolerance with custom skip policy
- [ ] `SyncJobListener` logs structured job start/end with duration and item counts
- [ ] `BatchSchedulerConfig` schedules `helloAssoSyncJob` at CRON `0 0 2 * * *`
- [ ] `AdminBatchController.triggerHelloAssoSync()` returns 202 Accepted with job execution ID
- [ ] `AdminBatchController` is secured with `@PreAuthorize("hasRole('ADMIN')")`
- [ ] Spring Batch metadata tables (`BATCH_*`) created in PostgreSQL
- [ ] `spring.batch.job.enabled=false` prevents auto-run on startup
- [ ] All 12 JUnit 5 tests pass green
