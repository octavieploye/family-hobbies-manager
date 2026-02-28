package com.familyhobbies.paymentservice.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Admin endpoints for manually triggering batch jobs in the payment-service.
 *
 * <p>All endpoints require the {@code ADMIN} role.
 */
@RestController
@RequestMapping("/api/v1/admin/batch")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Batch (Payment)", description = "Admin-only batch job triggers for payment-service")
public class AdminBatchController {

    private static final Logger log = LoggerFactory.getLogger(AdminBatchController.class);

    private final JobLauncher jobLauncher;
    private final Job paymentReconciliationJob;

    public AdminBatchController(JobLauncher jobLauncher,
                                 Job paymentReconciliationJob) {
        this.jobLauncher = jobLauncher;
        this.paymentReconciliationJob = paymentReconciliationJob;
    }

    /**
     * Manually trigger the payment reconciliation batch job.
     *
     * <p>POST /api/v1/admin/batch/payment-reconciliation
     *
     * @return 202 Accepted with the job execution ID and status
     */
    @PostMapping("/payment-reconciliation")
    @Operation(summary = "Trigger payment reconciliation",
               description = "Launches the payment reconciliation batch job to sync with HelloAsso")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Job launched"),
        @ApiResponse(responseCode = "403", description = "ADMIN role required"),
        @ApiResponse(responseCode = "500", description = "Failed to launch job")
    })
    public ResponseEntity<Map<String, Object>> triggerPaymentReconciliation() {
        log.info("Admin triggered payment reconciliation job");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("runTimestamp", Instant.now().toString())
                    .addString("trigger", "ADMIN_MANUAL")
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(paymentReconciliationJob, params);

            Map<String, Object> response = Map.of(
                    "jobExecutionId", execution.getId(),
                    "jobName", "paymentReconciliationJob",
                    "status", execution.getStatus().toString(),
                    "startTime", execution.getStartTime() != null
                            ? execution.getStartTime().toString() : "pending",
                    "trigger", "ADMIN_MANUAL");

            log.info("Payment reconciliation job triggered: executionId={}",
                    execution.getId());
            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error("Failed to trigger payment reconciliation job: {}",
                    e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to trigger payment reconciliation job",
                    "message", e.getMessage()));
        }
    }
}
