package com.familyhobbies.associationservice.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Structured logging listener for the HelloAsso sync batch job.
 *
 * <p>Logs job start, job end with metrics (duration, read/write/skip counts),
 * and failure details.
 */
@Component
public class SyncJobListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(SyncJobListener.class);

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
                .mapToLong(StepExecution::getReadCount).sum();
        long itemsWritten = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount).sum();
        long itemsSkipped = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getSkipCount).sum();
        long itemsFiltered = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getFilterCount).sum();

        log.info("Batch job {}: jobName={}, jobId={}, duration={}s, "
                        + "itemsRead={}, itemsWritten={}, itemsFiltered={}, itemsSkipped={}",
                jobExecution.getStatus(),
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobId(),
                duration.getSeconds(),
                itemsRead, itemsWritten, itemsFiltered, itemsSkipped);

        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            jobExecution.getAllFailureExceptions().forEach(ex ->
                    log.error("Batch job failure: jobName={}, exception={}",
                            jobExecution.getJobInstance().getJobName(),
                            ex.getMessage(), ex));
        }
    }
}
