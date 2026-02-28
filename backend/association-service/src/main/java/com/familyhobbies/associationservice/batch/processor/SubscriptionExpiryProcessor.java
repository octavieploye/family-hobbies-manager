package com.familyhobbies.associationservice.batch.processor;

import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Processor that transitions an ACTIVE subscription to EXPIRED status.
 *
 * <p>Sets:
 * <ul>
 *   <li>{@code status} = {@link SubscriptionStatus#EXPIRED}</li>
 *   <li>{@code expiredAt} = current timestamp (batch processing time)</li>
 * </ul>
 *
 * <p>Precondition: the reader already filtered for ACTIVE subscriptions
 * past their end date, so this processor does NOT re-validate eligibility.
 * It trusts the reader's query.
 *
 * <p>Returns the modified entity (never {@code null}): every subscription
 * delivered by the reader is guaranteed to need expiry processing.
 */
@Component
public class SubscriptionExpiryProcessor
        implements ItemProcessor<Subscription, Subscription> {

    private static final Logger log =
            LoggerFactory.getLogger(SubscriptionExpiryProcessor.class);

    @Override
    public Subscription process(Subscription subscription) {
        log.debug("Expiring subscription: id={}, userId={}, activityId={}, endDate={}",
                subscription.getId(),
                subscription.getUserId(),
                subscription.getActivity() != null ? subscription.getActivity().getId() : null,
                subscription.getEndDate());

        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setExpiredAt(Instant.now());

        return subscription;
    }
}
