package com.familyhobbies.associationservice.batch.config;

import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Shared Spring Batch infrastructure for association-service.
 *
 * <p><b>Convention</b>: Spring Boot 3.2 / Spring Batch 5.x -- do NOT use
 * {@code @EnableBatchProcessing} as it disables auto-configuration. Let Spring Boot
 * auto-configure {@code JobRepository} and {@code PlatformTransactionManager} beans.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@code batchTaskExecutor} -- thread pool for async job execution</li>
 *   <li>{@code asyncJobLauncher} -- non-blocking job launcher for REST triggers</li>
 * </ul>
 */
@Configuration
public class BatchConfig {

    /**
     * Thread pool for background batch execution.
     * Sized conservatively: 2 core, 4 max, 10 queue depth.
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
     */
    @Bean(name = "asyncJobLauncher")
    public JobLauncher asyncJobLauncher(JobRepository jobRepository,
                                        TaskExecutor batchTaskExecutor) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(batchTaskExecutor);
        launcher.afterPropertiesSet();
        return launcher;
    }
}
