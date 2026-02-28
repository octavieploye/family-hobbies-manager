package com.familyhobbies.associationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin REST controller for manually triggering batch jobs.
 *
 * <p>All endpoints are secured with ADMIN role.
 * Uses the async job launcher to return immediately (202 Accepted).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/v1/admin/batch/helloasso-sync (S7-001)</li>
 *   <li>POST /api/v1/admin/batch/subscription-expiry (S7-002)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/batch")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Batch (Association)", description = "Admin-only batch job triggers for association-service")
public class AdminBatchController {

    private static final Logger log =
            LoggerFactory.getLogger(AdminBatchController.class);

    private final JobLauncher asyncJobLauncher;
    private final Job helloAssoSyncJob;
    private final Job subscriptionExpiryJob;

    public AdminBatchController(
            @Qualifier("asyncJobLauncher") JobLauncher asyncJobLauncher,
            @Qualifier("helloAssoSyncJob") Job helloAssoSyncJob,
            @Qualifier("subscriptionExpiryJob") Job subscriptionExpiryJob) {
        this.asyncJobLauncher = asyncJobLauncher;
        this.helloAssoSyncJob = helloAssoSyncJob;
        this.subscriptionExpiryJob = subscriptionExpiryJob;
    }

    /**
     * Manually trigger the HelloAsso sync batch job (S7-001).
     *
     * @return 202 Accepted with job execution ID
     */
    @PostMapping("/helloasso-sync")
    @Operation(summary = "Trigger HelloAsso sync job",
               description = "Launches the HelloAsso directory sync batch job asynchronously")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Job launched"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "500", description = "Failed to launch job")
    })
    public ResponseEntity<Map<String, Object>> triggerHelloAssoSync() {
        return launchJob("helloAssoSyncJob", helloAssoSyncJob);
    }

    /**
     * Manually trigger the subscription expiry batch job (S7-002).
     *
     * <p>Processes all ACTIVE subscriptions where endDate &lt; TODAY.
     * Publishes SubscriptionExpiredEvent to Kafka for each expired subscription.
     *
     * @return 202 Accepted with job execution ID
     */
    @PostMapping("/subscription-expiry")
    @Operation(summary = "Trigger subscription expiry job",
               description = "Launches the subscription expiry batch job to process expired subscriptions")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Job launched"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "500", description = "Failed to launch job")
    })
    public ResponseEntity<Map<String, Object>> triggerSubscriptionExpiry() {
        return launchJob("subscriptionExpiryJob", subscriptionExpiryJob);
    }

    private ResponseEntity<Map<String, Object>> launchJob(
            String jobName, Job job) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("trigger", "ADMIN_REST")
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            log.info("Admin triggering batch job: jobName={}", jobName);
            JobExecution execution = asyncJobLauncher.run(job, params);

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "jobName", jobName,
                            "jobExecutionId", execution.getId(),
                            "status", execution.getStatus().toString(),
                            "message", "Job launched asynchronously"));
        } catch (Exception e) {
            log.error("Failed to launch batch job: jobName={}, error={}",
                    jobName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "jobName", jobName,
                            "error", e.getMessage()));
        }
    }
}
