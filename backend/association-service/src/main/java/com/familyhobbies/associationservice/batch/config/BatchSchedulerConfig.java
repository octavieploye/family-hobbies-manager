package com.familyhobbies.associationservice.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
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
@EnableScheduling
@ConditionalOnProperty(name = "batch.scheduling.enabled", havingValue = "true", matchIfMissing = true)
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
     * Processes all ACTIVE subscriptions where endDate &lt; TODAY.
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
