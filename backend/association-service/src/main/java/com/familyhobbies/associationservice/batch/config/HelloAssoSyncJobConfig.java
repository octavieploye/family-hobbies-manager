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
 *   <li>Chunk size: 50 (matches HelloAsso API page size)</li>
 *   <li>Skip policy: {@link HelloAssoSkipPolicy}</li>
 *   <li>Retry: 3 attempts on {@link ExternalApiException}</li>
 * </ul>
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
                .<HelloAssoOrganization, Association>chunk(CHUNK_SIZE, transactionManager)
                .reader(helloAssoItemReader)
                .processor(helloAssoItemProcessor)
                .writer(helloAssoItemWriter)
                .faultTolerant()
                .skipPolicy(new HelloAssoSkipPolicy(MAX_SKIP_COUNT))
                .retry(ExternalApiException.class)
                .retryLimit(RETRY_LIMIT)
                .build();
    }
}
