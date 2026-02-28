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
            usersProcessed = (int) jobExecution.getStepExecutions().stream()
                    .mapToLong(step -> step.getReadCount())
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

        RgpdCleanupLog auditLog = RgpdCleanupLog.builder()
                .executionTimestamp(Instant.now())
                .usersProcessed(usersProcessed)
                .usersAnonymized(usersAnonymized)
                .crossServiceCleanupStatus(rgpdCleanupWriter.getOverallCleanupStatus())
                .errorDetails(errorDetails)
                .build();

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
