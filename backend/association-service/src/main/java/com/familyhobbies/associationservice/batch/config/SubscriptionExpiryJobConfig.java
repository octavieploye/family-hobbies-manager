package com.familyhobbies.associationservice.batch.config;

import com.familyhobbies.associationservice.batch.listener.SubscriptionExpiryJobListener;
import com.familyhobbies.associationservice.batch.processor.SubscriptionExpiryProcessor;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
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

import java.time.LocalDate;
import java.util.Map;

/**
 * Spring Batch job configuration for subscription expiry processing.
 *
 * <p>Job: {@code subscriptionExpiryJob}
 * <ul>
 *   <li>Step: {@code processExpiredSubscriptionsStep}</li>
 *   <li>Reader: {@link JpaPagingItemReader} -- ACTIVE subs where endDate &lt; TODAY</li>
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
     * JPA paging reader that selects ACTIVE subscriptions past their end date.
     *
     * <p>Uses a JPQL query with named parameters. The cutoff date is
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
                        + "AND s.endDate < :cutoff "
                        + "ORDER BY s.endDate ASC")
                .parameterValues(Map.of(
                        "status", SubscriptionStatus.ACTIVE,
                        "cutoff", LocalDate.now()))
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
