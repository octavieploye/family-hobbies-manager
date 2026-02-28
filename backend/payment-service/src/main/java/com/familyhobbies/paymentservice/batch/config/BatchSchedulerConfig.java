package com.familyhobbies.paymentservice.batch.config;

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
 * Scheduler configuration for payment batch jobs.
 *
 * <p>Triggers the payment reconciliation job daily at 8:00 AM UTC.
 * Each execution receives a unique {@code runTimestamp} parameter to ensure
 * Spring Batch treats it as a new job instance (required by the framework).
 *
 * <p>Scheduling can be disabled via {@code batch.scheduling.enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "batch.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class BatchSchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchSchedulerConfig.class);

    private final JobLauncher jobLauncher;
    private final Job paymentReconciliationJob;

    public BatchSchedulerConfig(JobLauncher jobLauncher,
                                 Job paymentReconciliationJob) {
        this.jobLauncher = jobLauncher;
        this.paymentReconciliationJob = paymentReconciliationJob;
    }

    /**
     * Run payment reconciliation daily at 8:00 AM UTC.
     */
    @Scheduled(cron = "${batch.reconciliation.cron:0 0 8 * * *}")
    public void runPaymentReconciliation() {
        log.info("Scheduled payment reconciliation job starting");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("runTimestamp", Instant.now().toString())
                    .addString("trigger", "CRON")
                    .toJobParameters();

            jobLauncher.run(paymentReconciliationJob, params);
            log.info("Scheduled payment reconciliation job completed");
        } catch (Exception e) {
            log.error("Scheduled payment reconciliation job failed: {}",
                    e.getMessage(), e);
        }
    }
}
