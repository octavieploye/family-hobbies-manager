package com.familyhobbies.userservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Admin endpoints for manually triggering batch jobs in the user-service.
 *
 * <p>All endpoints require the {@code ADMIN} role.
 */
@RestController
@RequestMapping("/api/v1/admin/batch")
public class AdminBatchController {

    private static final Logger log = LoggerFactory.getLogger(AdminBatchController.class);

    private final JobLauncher jobLauncher;
    private final Job rgpdDataCleanupJob;

    public AdminBatchController(JobLauncher jobLauncher,
                                 Job rgpdDataCleanupJob) {
        this.jobLauncher = jobLauncher;
        this.rgpdDataCleanupJob = rgpdDataCleanupJob;
    }

    /**
     * Manually trigger the RGPD data cleanup batch job.
     *
     * <p>POST /api/v1/admin/batch/rgpd-cleanup
     *
     * @return 200 OK with the job execution ID and status
     */
    @PostMapping("/rgpd-cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerRgpdCleanup() {
        log.info("Admin triggered RGPD data cleanup job");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("runTimestamp", Instant.now().toString())
                    .addString("trigger", "ADMIN_MANUAL")
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(rgpdDataCleanupJob, params);

            Map<String, Object> response = Map.of(
                    "jobExecutionId", execution.getId(),
                    "jobName", "rgpdDataCleanupJob",
                    "status", execution.getStatus().toString(),
                    "startTime", execution.getStartTime() != null
                            ? execution.getStartTime().toString() : "pending",
                    "trigger", "ADMIN_MANUAL"
            );

            log.info("RGPD data cleanup job triggered: executionId={}", execution.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to trigger RGPD data cleanup job: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to trigger RGPD data cleanup job",
                    "message", e.getMessage()
            ));
        }
    }
}
