package com.familyhobbies.userservice.batch.config;

import com.familyhobbies.userservice.adapter.AssociationServiceClient;
import com.familyhobbies.userservice.adapter.PaymentServiceClient;
import com.familyhobbies.userservice.batch.listener.RgpdCleanupJobListener;
import com.familyhobbies.userservice.batch.processor.RgpdAnonymizationProcessor;
import com.familyhobbies.userservice.batch.reader.RgpdEligibleUserItemReader;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

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
}
