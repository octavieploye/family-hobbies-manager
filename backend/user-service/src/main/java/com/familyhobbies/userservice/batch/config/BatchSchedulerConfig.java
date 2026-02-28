package com.familyhobbies.userservice.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

/**
 * Scheduler configuration for user-service batch jobs.
 *
 * <p>Triggers the RGPD data cleanup job weekly on Sundays at 3:00 AM UTC.
 * Each execution receives a unique {@code runTimestamp} parameter.
 *
 * <p>Scheduling is disabled when {@code batch.scheduling.enabled=false}
 * (e.g., in test profiles).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "batch.scheduling.enabled", havingValue = "true", matchIfMissing = true)
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
