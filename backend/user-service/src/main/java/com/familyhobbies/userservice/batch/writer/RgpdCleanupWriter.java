package com.familyhobbies.userservice.batch.writer;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.userservice.adapter.AssociationServiceClient;
import com.familyhobbies.userservice.adapter.PaymentServiceClient;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.enums.CrossServiceCleanupStatus;
import com.familyhobbies.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writes anonymized users and triggers cross-service data cleanup.
 *
 * <p>For each anonymized user in the chunk:
 * <ol>
 *     <li>Saves the anonymized user entity to the database</li>
 *     <li>Calls association-service to delete/anonymize user data</li>
 *     <li>Calls payment-service to delete/anonymize user data</li>
 * </ol>
 *
 * <p>Cross-service failures are logged but do not prevent the local anonymization
 * from being persisted. The cross-service cleanup status is tracked and reported
 * in the {@link com.familyhobbies.userservice.entity.RgpdCleanupLog}.
 *
 * <p>Uses Spring Batch 5.x {@link Chunk} API.
 */
public class RgpdCleanupWriter implements ItemWriter<User> {

    private static final Logger log = LoggerFactory.getLogger(RgpdCleanupWriter.class);

    private final UserRepository userRepository;
    private final AssociationServiceClient associationServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    // Tracks cross-service cleanup outcomes for the job listener
    private final AtomicInteger anonymizedCount = new AtomicInteger(0);
    private final AtomicReference<CrossServiceCleanupStatus> overallCleanupStatus =
            new AtomicReference<>(CrossServiceCleanupStatus.SUCCESS);
    private final List<String> errorMessages = new ArrayList<>();

    public RgpdCleanupWriter(UserRepository userRepository,
                              AssociationServiceClient associationServiceClient,
                              PaymentServiceClient paymentServiceClient) {
        this.userRepository = userRepository;
        this.associationServiceClient = associationServiceClient;
        this.paymentServiceClient = paymentServiceClient;
    }

    @Override
    public void write(Chunk<? extends User> chunk) throws Exception {
        log.info("Writing {} anonymized users", chunk.size());

        // 1. Batch persist all anonymized users at once
        var users = new ArrayList<>(chunk.getItems());
        userRepository.saveAll(users);
        anonymizedCount.addAndGet(users.size());

        // 2. Trigger cross-service cleanup per user
        for (User user : users) {
            triggerCrossServiceCleanup(user.getId());
        }

        log.info("Successfully wrote {} anonymized users", chunk.size());
    }

    /**
     * Call association-service and payment-service to cleanup user data.
     * Failures are logged but do not abort the batch.
     */
    private void triggerCrossServiceCleanup(Long userId) {
        boolean associationSuccess = callServiceSafely(
                () -> associationServiceClient.cleanupUserData(userId),
                "association-service", userId);

        boolean paymentSuccess = callServiceSafely(
                () -> paymentServiceClient.cleanupUserData(userId),
                "payment-service", userId);

        if (!associationSuccess || !paymentSuccess) {
            if (!associationSuccess && !paymentSuccess) {
                overallCleanupStatus.set(CrossServiceCleanupStatus.FAILED);
            } else {
                // Only upgrade to PARTIAL_FAILURE if not already FAILED
                overallCleanupStatus.compareAndSet(
                        CrossServiceCleanupStatus.SUCCESS,
                        CrossServiceCleanupStatus.PARTIAL_FAILURE);
            }
        }
    }

    /**
     * Call a cross-service cleanup, catching and logging any errors.
     *
     * @return {@code true} if the call succeeded, {@code false} otherwise
     */
    private boolean callServiceSafely(Runnable serviceCall, String serviceName, Long userId) {
        try {
            serviceCall.run();
            return true;
        } catch (ExternalApiException e) {
            String error = String.format("%s cleanup failed for userId=%d: %s",
                    serviceName, userId, e.getMessage());
            log.error(error);
            errorMessages.add(error);
            return false;
        } catch (Exception e) {
            String error = String.format("Unexpected error calling %s for userId=%d: %s",
                    serviceName, userId, e.getMessage());
            log.error(error, e);
            errorMessages.add(error);
            return false;
        }
    }

    // --- Accessors for the job listener ---

    public int getAnonymizedCount() {
        return anonymizedCount.get();
    }

    public CrossServiceCleanupStatus getOverallCleanupStatus() {
        return overallCleanupStatus.get();
    }

    public String getErrorDetailsAsString() {
        return errorMessages.isEmpty() ? null : String.join("\n", errorMessages);
    }

    /**
     * Reset counters for a new job execution.
     */
    public void resetCounters() {
        anonymizedCount.set(0);
        overallCleanupStatus.set(CrossServiceCleanupStatus.SUCCESS);
        errorMessages.clear();
    }
}
