package com.familyhobbies.associationservice.batch.listener;

import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.common.event.SubscriptionExpiredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listener for the subscription expiry batch job.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Log job start/end with structured fields (duration, counts, status)</li>
 *   <li>Collect expired subscriptions during processing</li>
 *   <li>Publish {@link SubscriptionExpiredEvent} to Kafka after each chunk</li>
 * </ul>
 *
 * <p>Kafka topic: {@code family-hobbies.subscription.expired}
 * <p>Kafka key: subscriptionId (ensures ordering per subscription)
 *
 * <p>Thread safety: uses {@link CopyOnWriteArrayList} for the pending events
 * buffer because Spring Batch may invoke callbacks from the batch thread pool.
 */
@Component
public class SubscriptionExpiryJobListener
        implements JobExecutionListener, ChunkListener {

    private static final Logger log =
            LoggerFactory.getLogger(SubscriptionExpiryJobListener.class);

    private static final String TOPIC = "family-hobbies.subscription.expired";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Buffer of subscriptions that were expired in the current chunk.
     * Populated by the processor/writer, drained in afterChunk.
     */
    private final CopyOnWriteArrayList<Subscription> pendingEvents =
            new CopyOnWriteArrayList<>();

    public SubscriptionExpiryJobListener(
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // -- JobExecutionListener callbacks --

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Subscription expiry job STARTED: jobId={}, jobParameters={}",
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

        long writeCount = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getWriteCount)
                .sum();

        long readCount = jobExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount)
                .sum();

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("Subscription expiry job COMPLETED: jobId={}, "
                    + "duration={}ms, readCount={}, writeCount={}",
                    jobExecution.getJobId(),
                    duration.toMillis(),
                    readCount,
                    writeCount);
        } else {
            log.error("Subscription expiry job FAILED: jobId={}, "
                    + "status={}, duration={}ms, readCount={}, writeCount={}, "
                    + "exitDescription={}",
                    jobExecution.getJobId(),
                    jobExecution.getStatus(),
                    duration.toMillis(),
                    readCount,
                    writeCount,
                    jobExecution.getExitStatus().getExitDescription());
        }
    }

    // -- ChunkListener callbacks --

    @Override
    public void beforeChunk(ChunkContext context) {
        pendingEvents.clear();
    }

    @Override
    public void afterChunk(ChunkContext context) {
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Publishing {} SubscriptionExpiredEvents to Kafka",
                pendingEvents.size());

        for (Subscription subscription : pendingEvents) {
            try {
                Long activityId = subscription.getActivity() != null
                        ? subscription.getActivity().getId() : null;
                Long associationId = subscription.getActivity() != null
                        && subscription.getActivity().getAssociation() != null
                        ? subscription.getActivity().getAssociation().getId() : null;

                SubscriptionExpiredEvent event = SubscriptionExpiredEvent.of(
                        subscription.getId(),
                        subscription.getUserId(),
                        subscription.getFamilyMemberId(),
                        subscription.getFamilyId(),
                        associationId,
                        activityId,
                        subscription.getExpiredAt());

                kafkaTemplate.send(TOPIC,
                        subscription.getId().toString(),
                        event);

                log.debug("Published SubscriptionExpiredEvent: "
                        + "subscriptionId={}, userId={}",
                        subscription.getId(),
                        subscription.getUserId());
            } catch (Exception e) {
                log.error("Failed to publish SubscriptionExpiredEvent "
                        + "for subscriptionId={}: {}",
                        subscription.getId(), e.getMessage(), e);
            }
        }

        pendingEvents.clear();
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        log.warn("Chunk failed -- discarding {} pending Kafka events "
                + "to prevent phantom notifications",
                pendingEvents.size());
        pendingEvents.clear();
    }

    // -- Called by the writer after successful persist --

    /**
     * Register a subscription that was successfully expired and persisted.
     * Called by the job config or writer to buffer events for Kafka publishing.
     *
     * @param subscription the expired subscription entity (post-persist)
     */
    public void registerExpiredSubscription(Subscription subscription) {
        pendingEvents.add(subscription);
    }
}
